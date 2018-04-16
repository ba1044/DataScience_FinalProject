package edu.unh.cs980.paragraph

import edu.unh.cs980.CONTENT
import edu.unh.cs980.WordEmbedding.ResultQuery
import edu.unh.cs980.getIndexSearcher
import edu.unh.cs980.language.KernelDist
import edu.unh.cs980.language.KotlinGramAnalyzer
import edu.unh.cs980.language.KotlinKernelAnalyzer
import edu.unh.cs980.language.TopicMixtureResult
import edu.unh.cs980.misc.AnalyzerFunctions
import edu.unh.cs980.withTime
import org.apache.lucene.index.Term
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis


class KotlinEmbedding(indexLoc: String) {
//    private val gramAnalyzer = KotlinGramAnalyzer(gramLoc)
    private val searcher = getIndexSearcher(indexLoc)
    private val memoizedFreqs = ConcurrentHashMap<String, Double>()
    private val kernelAnalyzer =
            KotlinKernelAnalyzer(0.0, 300.0, corpus = {s -> null}, partitioned = true)


    var topicTime: Long = 0

//    private val totalCorpusFreq = gramAnalyzer.indexSearcher.indexReader
//        .getSumTotalTermFreq("unigram")
//        .toDouble()

    fun query(query: String): List<String> {
        val boolQuery = AnalyzerFunctions.createQuery(query)
        val results = searcher.search(boolQuery, 10)
        return results.scoreDocs.map { scoreDoc ->
            val doc = searcher.doc(scoreDoc.doc)
            doc.get(CONTENT)
        }
    }

    fun scoreResults(combined: TopicMixtureResult, docs: List<Pair<String, TopicMixtureResult>>) {
        docs.map { doc -> doc to doc.second.deltaDensity(combined) }
            .sortedBy { (_, distance) -> distance }
            .forEach { (doc, distance) -> println("${doc.first}:\n\t $distance") }

    }

    fun reportQueryResults(queryString: String, smoothDocs: Boolean = false, smoothCombined: Boolean = false) {
        val docs = query(queryString)

        val total = docs.joinToString("\n")
        val results = ArrayList<Pair<String, TopicMixtureResult>>()

        docs.forEach { doc ->
            val result = embed(doc, nSamples = 1000, nIterations = 400, smooth = smoothDocs)
            result.reportResults()
            results += doc to result
            println(doc + "\n\n")
        }

        val otherText = """
            During Microsoft's early history, Gates was an active software developer, particularly in the company's programming language products, but his basic role in most of the company's history was primarily as a manager and executive. Gates has not officially been on a development team since working on the TRS-80 Model 100,[65] but as late as 1989 he wrote code that shipped with the company's products.[63] He remained interested in technical details; in 1985, Jerry Pournelle wrote that when he watched Gates announce Microsoft Excel, "Something else impressed me. Bill Gates likes the program, not because it's going to make him a lot of money (although I'm sure it will do that), but because it's a neat hack."[66]
            """
        results += otherText to embed(otherText, nSamples = 1000, nIterations = 400, smooth = smoothDocs)

        println("COMBINED")
        val result = embed(total, nSamples = 1000, nIterations = 400, smooth = smoothCombined)
        result.reportResults()
        println("\n\n")

        println("QUERY")
        embed(queryString, nSamples = 1000, nIterations = 400, smooth = false).reportResults()
        println("\n\n")
        scoreResults(result, results)
    }

//    fun getUnigramFrequency(unigram: String): Double? {
//        val gramFreq = memoizedFreqs.computeIfAbsent(unigram) {
//            gramAnalyzer
//                .indexSearcher
//                .indexReader
//                .totalTermFreq(Term("unigram", unigram)) / totalCorpusFreq
//        }
//
//        return if (!gramFreq.isFinite() || gramFreq == 0.0) null else gramFreq
//    }

    fun loadTopics(directory: String, filterList: List<String> = listOf()) {
        val time = measureTimeMillis {
            kernelAnalyzer.analyzeTopicDirectories(directory, filterList)
            kernelAnalyzer.normalizeTopics()
        }
        topicTime = time
    }

    fun embed(text: String, nSamples: Int = 300, nIterations: Int = 500, smooth: Boolean = false): TopicMixtureResult {
        val kernelDist = KernelDist(0.0, 1.0)
            .apply { analyzePartitionedDocument(text) }
            .apply { normalizeKernels() }


        val identityFreqs = "identity" to kernelDist.getKernelFreqs()

        if (smooth) {
            val smoothTime = measureTimeMillis { kernelDist.equilibriumCovariance() }

        }
//        kernelDist.normalizeByCond2()

        val (timePerturb, samples) = withTime { kernelDist.perturb(nSamples) }

        val (timeIntegrals, integrals) = withTime {
            val topicStats = kernelAnalyzer.retrieveTopicFrequencies() + identityFreqs
            val stochasticIntegrator = KotlinStochasticIntegrator(samples, topicStats, corpus = {_ -> null})
            stochasticIntegrator.integrate()
        }


        val (timeGradient, results) = withTime {
            kernelAnalyzer.classifyByDomainSimplex2(integrals, nIterations = nIterations,smooth = false)
        }


        println("Loaded topics in $topicTime ms")
//        println("Smoothed in $smoothTime ms")
        println("Created $nSamples perturbations in $timePerturb ms")
        println("Created integrals in $timeIntegrals ms")
        println("Finished Gradient Descent in $timeGradient ms\n")
        return results
    }
}


fun main(args: Array<String>) {
    val gramLoc = "/home/hcgs/data_science/gram"
    val indexLoc = "/home/hcgs/data_science/index"

    val embedder = KotlinEmbedding(indexLoc)
    embedder.loadTopics("paragraphs/",
            filterList = listOf())
//    val testText = """
//        A party is a gathering of people who have been invited by a host for the purposes of socializing, conversation, recreation, or as part of a festival or other commemoration of a special occasion. A party will typically feature food and beverages, and often music and dancing or other forms of entertainment. In many Western countries, parties for teens and adults are associated with drinking alcohol such as beer, wine or distilled spirits.
//        """

//    val testText = """
//        A cuisine is a style of cooking characterized by distinctive ingredients, techniques and dishes, and usually associated with a specific culture or geographic region. A cuisine is primarily influenced by the ingredients that are available locally or through trade. Religious food laws, such as Hindu, Islamic and Jewish dietary laws, can also exercise a strong influence on cuisine. Regional food preparation traditions, customs and ingredients often combine to create dishes unique to a particular region.[1]
//        """
    val testText =
            File("paragraphs/Statistics/doc_10.txt").readText() +
                    File("paragraphs/Computers/doc_0.txt").readText()
//                    File("paragraphs/Environments/doc_3.txt").readText() +
//                    File("paragraphs/Environments/doc_1.txt").readText() +
//            File("paragraphs/Computers/doc_3.txt").readText() +
//            File("paragraphs/Medicine/doc_2.txt").readText()
//            File("paragraphs/Computers/doc_0.txt").readText() +
//                    File("paragraphs/Medicine/doc_2.txt").readText() +
//                    File("paragraphs/Medicine/doc_2.txt").readText() +
//                    File("paragraphs/Medicine/doc_2.txt").readText()

    val result = embedder.embed(testText, nSamples = 1000, nIterations = 400, smooth = false)
    result.reportResults()
//    embedder.reportQueryResults("Variational calculus and bayes theorem", smoothDocs = false, smoothCombined = false)
//    embedder.reportQueryResults("Health benefits of chocolate", smoothDocs = false, smoothCombined = false)
}