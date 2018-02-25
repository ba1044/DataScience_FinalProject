package edu.unh.cs980.variations;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

import edu.unh.cs980.utils.ProjectUtils;

public class QueryExpansion_variation {
	private static int top_k_term = 10; // Include top k terms for QE
	private static int top_k_doc = 10; // Initial top k documents for QE
	private static QueryParser parser = new QueryParser("content", new StandardAnalyzer());

	public static ArrayList<String> getSearchResult(ArrayList<String> queriesStr, int max_result, String index_dir)
			throws IOException, ParseException {
		ArrayList<String> runFileStr = new ArrayList<String>();

		IndexSearcher searcher = new IndexSearcher(
				DirectoryReader.open(FSDirectory.open((new File(index_dir).toPath()))));
		searcher.setSimilarity(new BM25Similarity());

		for (String queryStr : queriesStr) {
			Query q0 = parser.parse(QueryParser.escape(queryStr));

			TopDocs init_tops = searcher.search(q0, top_k_doc);
			ScoreDoc[] init_scoreDoc = init_tops.scoreDocs;

			// Get top k terms with relevance mode
			ArrayList<String> expanded_terms = getExpandedTerms(top_k_term, searcher, init_scoreDoc);

			// Create new expanded query
			Query q_rm = generateWeightedQuery(queryStr, expanded_terms);
			TopDocs tops = searcher.search(q_rm, max_result);
			ScoreDoc[] scoreDoc = tops.scoreDocs;
			for (int i = 0; i < scoreDoc.length; i++) {
				ScoreDoc score = scoreDoc[i];
				Document doc = searcher.doc(score.doc);
				String paraId = doc.getField("paraid").stringValue();
				float rankScore = score.score;
				int rank = i + 1;

				String runStr = "enwiki:" + queryStr.replace(" ", "%20") + " Q0 " + paraId + " " + rank + " "
						+ rankScore + " QueryExpansion";
				runFileStr.add(runStr);
			}
		}

		return runFileStr;
	}

	private static ArrayList<String> getExpandedTerms(int top_k, IndexSearcher searcher, ScoreDoc[] scoreDoc)
			throws IOException {
		ArrayList<String> q_rm = new ArrayList<String>();
		HashMap<String, Float> term_map = new HashMap<String, Float>();

		for (int i = 0; i < scoreDoc.length; i++) {
			ScoreDoc score = scoreDoc[i];
			Document doc = searcher.doc(score.doc);
			String paraId = doc.getField("paraid").stringValue();
			String paraBody = doc.getField("content").stringValue();
			float rankScore = score.score;
			// Relevance Feedback to create new query?
			// Get single term list without stopwords
			ArrayList<String> unigram_list = analyzeByUnigram(paraBody);
			if (unigram_list.isEmpty()) {
				System.out.println("Can't get terms list from : " + paraBody);
			}
			int rank = i + 1;
			// HashMap<String, Float> term_score;
			float initial_p = (float) 1 / (rank + 1); // p always < 1
			// iterate through unique term list
			for (String termStr : getVocabularyList(unigram_list)) {
				int tf_w = countExactStrFreqInList(termStr, unigram_list);
				int tf_list = unigram_list.size();
				float term_score = initial_p * ((float) tf_w / tf_list);
				if (term_map.keySet().contains(termStr)) {
					term_map.put(termStr, term_map.get(termStr) + term_score);

				} else {
					term_map.put(termStr, term_score);
				}

			}
		}

		Set<String> termSet = ProjectUtils.getTopValuesInMap(term_map, top_k_term).keySet();
		q_rm.addAll(termSet);

		return q_rm;
	}

	// Boost q0 with 0.6, expanded query with 0.4
	private static Query generateWeightedQuery(String initialQ, ArrayList<String> rm_list) throws ParseException {
		if (!rm_list.isEmpty()) {
			String rm_str = String.join(" ", rm_list);
			Query q = parser.parse(QueryParser.escape(initialQ) + "^0.6" + QueryParser.escape(rm_str) + "^0.4");
			return q;
		} else {
			Query q = parser.parse(QueryParser.escape(initialQ));
			return q;
		}
	}

	// Get exact count.
	private static int countExactStrFreqInList(String term, ArrayList<String> list) {
		int occurrences = Collections.frequency(list, term);
		return occurrences;
	}

	private static ArrayList<String> getVocabularyList(ArrayList<String> unigramList) {
		ArrayList<String> list = new ArrayList<String>();
		Set<String> hs = new HashSet<>();

		hs.addAll(unigramList);
		list.addAll(hs);
		return list;
	}

	private static ArrayList<String> analyzeByUnigram(String inputStr) throws IOException {
		Reader reader = new StringReader(inputStr);
		// System.out.println("Input text: " + inputStr);
		ArrayList<String> strList = new ArrayList<String>();
		Analyzer analyzer = new UnigramAnalyzer();
		TokenStream tokenizer = analyzer.tokenStream("content", inputStr);

		CharTermAttribute charTermAttribute = tokenizer.addAttribute(CharTermAttribute.class);
		tokenizer.reset();
		while (tokenizer.incrementToken()) {
			String token = charTermAttribute.toString();
			strList.add(token);
			// System.out.println(token);
		}
		tokenizer.end();
		tokenizer.close();
		return strList;
	}

}