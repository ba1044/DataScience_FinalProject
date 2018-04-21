package edu.unh.cs980.features

import edu.unh.cs980.CONTENT
import edu.unh.cs980.language.KotlinMetaKernelAnalyzer
import edu.unh.cs980.language.MixtureDistanceMeasure
import edu.unh.cs980.language.ReductionMethod
import edu.unh.cs980.language.Sheaf
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.paragraph.KotlinEmbedding
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopDocs


enum class SheafQueryEmbeddingMethod {
    MEAN, QUERY, QUERY_EXPANSION
}

fun featUseEmbeddedMean(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                        embedder: KotlinEmbedding): List<Double> {
    val paragraphs = tops.scoreDocs
        .map { indexSearcher.doc(it.doc).get(CONTENT)}

    val embeddings = paragraphs
        .map { paragraph ->  embedder.embed(paragraph, 500)}

    val centroid = paragraphs
        .joinToString("\n")
        .let { jointParagraph -> embedder.embed(jointParagraph) }

    return embeddings.map { projection -> projection.deltaSim(centroid)}
}

fun featUseEmbeddedQuery(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                            embedder: KotlinEmbedding): List<Double> {

    val paragraphs = tops.scoreDocs
        .map { indexSearcher.doc(it.doc).get(CONTENT)}

    val embeddings = paragraphs
        .map { paragraph ->  embedder.embed(paragraph, 500)}

    val queryEmbedding = embedder.embed(query, 500)
    return embeddings.map { projection -> projection.deltaSim(queryEmbedding)}
}

private fun expandQuery(query: String, indexSearcher: IndexSearcher): String =
    AnalyzerFunctions.createQueryList(query, useFiltering = true)
        .map { booleanQuery ->
            indexSearcher.search(booleanQuery, 1)
                .scoreDocs.first()
                .doc
                .let { docId -> indexSearcher.doc(docId).get(CONTENT) } }
        .joinToString("\n")


fun featUseExpandedEmbeddedQuery(query: String, tops: TopDocs, indexSearcher: IndexSearcher,
                         embedder: KotlinEmbedding): List<Double> {

    val paragraphs = tops.scoreDocs
        .map { indexSearcher.doc(it.doc).get(CONTENT)}

    val embeddings = paragraphs
        .map { paragraph ->  embedder.embed(paragraph, 500)}

    val expanded = expandQuery(query, indexSearcher)

    val queryEmbedding = embedder.embed(expanded, 500)
    return embeddings.map { projection -> projection.deltaSim(queryEmbedding)}
}


fun featSheafDist(query: String, tops: TopDocs, indexSearcher: IndexSearcher, analyzer: KotlinMetaKernelAnalyzer,
                  startLayer: Int, measureLayer: Int, reductionMethod: ReductionMethod,
                  normalize: Boolean, mixtureDistanceMeasure: MixtureDistanceMeasure,
                  queryEmbeddingMethod: SheafQueryEmbeddingMethod): List<Double> {

    val paragraphs = tops.scoreDocs
        .map { indexSearcher.doc(it.doc).get(CONTENT)}

    val queryText = when (queryEmbeddingMethod) {
        SheafQueryEmbeddingMethod.MEAN -> paragraphs.joinToString("\n")
        SheafQueryEmbeddingMethod.QUERY -> query
        SheafQueryEmbeddingMethod.QUERY_EXPANSION -> expandQuery(query, indexSearcher) }

    val queryEmbedding = analyzer.inferMetric(
            text = queryText, startingLayer = startLayer, doNormalize = normalize,
            measureLayer = measureLayer, reductionMethod = reductionMethod)

    val embeddedParagraphs = paragraphs.map { paragraph ->
        analyzer.inferMetric(text = paragraph, startingLayer = startLayer, doNormalize = normalize,
            measureLayer = measureLayer, reductionMethod = reductionMethod) }

    return embeddedParagraphs
        .map { embeddedParagraph ->
            queryEmbedding.distance(embeddedParagraph, mixtureDistanceMeasure) }

}
