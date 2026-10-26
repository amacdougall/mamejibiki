package com.alanmacdougall.mamejibiki.conjugation

import com.alanmacdougall.mamejibiki.conjugation.Inflection.CAUSATIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.NEGATIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.PASSIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.POLITE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.PROGRESSIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.REQUEST
import com.alanmacdougall.mamejibiki.conjugation.Inflection.SHORT_CAUSATIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.TE

/** A possible dictionary form of a conjugated word: [text] as a [wordClass] word, reached by [inflections] (innermost first). */
data class Deinflection(val text: String, val wordClass: String, val inflections: List<Inflection>)

/** Deinflection search (UI spec §2): the conjugation rules run in reverse, chained. */
object Deinflector {
    private const val MAX_DEPTH = 6

    /**
     * Every candidate dictionary form of [word], in a JMdict POS class. A
     * rule undoes only if the word so far belongs to that rule's result
     * class, so 食べられない → 食べられる (v1) → 食べる (v1). The rules
     * with an empty ending (the ichidan stem) never undo: they'd turn every
     * query into a candidate.
     */
    fun deinflect(word: String): List<Deinflection> {
        val found = ArrayList<Deinflection>()
        val seen = HashSet<Triple<String, String, List<Inflection>>>()
        var frontier = listOf(Step(word, null, emptyList()))
        repeat(MAX_DEPTH) {
            frontier = frontier.flatMap { step ->
                Conjugation.rules
                    .filter { it.to.isNotEmpty() && step.text.endsWith(it.to) }
                    .filter { step.wordClass == null || it.resultClass == step.wordClass }
                    .map { Step(step.text.dropLast(it.to.length) + it.from, it.wordClass, it.inflections + step.inflections) }
                    .filter { it.text.isNotEmpty() && seen.add(Triple(it.text, it.wordClass!!, it.inflections)) }
            }
            for (step in frontier) {
                if (step.wordClass in Conjugation.dictionaryClasses) found += Deinflection(step.text, step.wordClass!!, step.inflections)
            }
        }
        return found
    }

    private data class Step(val text: String, val wordClass: String?, val inflections: List<Inflection>)

    /**
     * A chain's English description: Forms tab row names, innermost first,
     * with "polite" and then "negative" last, so 引かなかった is "past,
     * negative" and 引きませんでした is "past, polite, negative". The te-form
     * inside the progressive and request isn't named, and a causative
     * followed by a passive is "causative passive".
     */
    fun labelParts(inflections: List<Inflection>): List<String> {
        val parts = ArrayList<String>()
        var i = 0
        while (i < inflections.size) {
            val inflection = inflections[i]
            val next = inflections.getOrNull(i + 1)
            when {
                inflection == POLITE || inflection == NEGATIVE -> {}
                inflection == TE && (next == PROGRESSIVE || next == REQUEST) -> {}
                (inflection == CAUSATIVE || inflection == SHORT_CAUSATIVE) && next == PASSIVE -> {
                    parts += "causative passive"
                    i++
                }
                else -> parts += inflection.label
            }
            i++
        }
        if (POLITE in inflections) parts += POLITE.label
        repeat(inflections.count { it == NEGATIVE }) { parts += NEGATIVE.label }
        return parts
    }

    /**
     * One label for all the chains that reach the same entry. Only the
     * simplest readings count: the fewest inflections, then the fewest label
     * parts (引かせられる is "causative passive", not "causative, potential").
     * Readings that differ in one place are merged there ("potential or
     * passive, negative"); otherwise whole labels are joined with " or ".
     */
    fun label(chains: Collection<List<Inflection>>): String {
        val shortest = chains.minOf { it.size }
        val candidates = chains.filter { it.size == shortest }.map { labelParts(it) }.distinct()
        val fewest = candidates.minOf { it.size }
        val labels = candidates.filter { it.size == fewest }
        if (labels.size == 1) return labels[0].joinToString(", ")
        val differing = (0 until fewest).filter { i -> labels.any { it[i] != labels[0][i] } }
        if (differing.size == 1) {
            val at = differing[0]
            val merged = labels.map { it[at] }.distinct().joinToString(" or ")
            return labels[0].mapIndexed { i, part -> if (i == at) merged else part }.joinToString(", ")
        }
        return labels.joinToString(" or ") { it.joinToString(", ") }
    }
}
