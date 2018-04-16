package edu.unh.cs980.paragraph

import edu.unh.cs980.normalize
import edu.unh.cs980.smooth
import kotlin.math.log2


class KotlinStochasticIntegrator(val perturbations: Pair<List<String>, List<List<Double>>>,
                                 topics: List<Pair<String, Map<String, Double>>>,
                                 val corpus: (String) -> Double?,
                                 val smooth: Boolean = false) {

    val restrictions = topics.map(this::getRestrictedTopic)
    val topicNames = topics.map { (name, _) -> name }

    private fun getRestrictedTopic(topic: Pair<String, Map<String, Double>>): List<Double> {
        val topicHash = topic.second
        val uniqueKeys = topic.second.keys - perturbations.first
        val uniqueTotalFreq = uniqueKeys.sumByDouble { word -> topicHash[word]!! }
        val restrictedFreq = perturbations.first.sumByDouble { word -> topicHash[word] ?: 0.000000001 }

        val restrictedDist = perturbations.first
//            .map { word -> topicHash[word] ?: 1 / perturbations.first.size.toDouble() }
//            .map { word -> topicHash[word] ?: perturbations.first.size.toDouble() }
//            .map { word -> topicHash[word]  ?: 1 / topic.second.keys.size.toDouble() }
//            .map { word -> topicHash[word]  ?: 1 / perturbations.first.size.toDouble() }
//            .map { word -> topicHash[word]  ?: 1000 * perturbations.first.size.toDouble() }
            .map { word -> topicHash[word]  ?: topic.second.keys.size * perturbations.first.size.toDouble() }
//            .map { word -> topicHash[word] ?: 1.0 }
//            .map { word -> topicHash[word] ?: (corpus(word)?: 1 / perturbations.first.size.toDouble()) }
            .let { curMap ->
                val mapSum = curMap.sum()
                val finalTotal = mapSum + uniqueTotalFreq

//                curMap.map { value -> value /  mapSum }}
                    curMap.map { value -> value /  perturbations.first.size.toDouble() }}
            .toList()

        return restrictedDist
    }

    private fun kldToTopic(topic: List<Double>) =
            perturbations.second
                .map { perturbs ->
                    perturbs.zip(topic).sumByDouble { (k1, k2) -> k1 * log2(k1 / k2) / perturbs.size } }
                .normalize()
                .let {if (smooth) it.smooth() else it }

    fun integrate() =
            topicNames.zip(restrictions.map(this::kldToTopic))
}
