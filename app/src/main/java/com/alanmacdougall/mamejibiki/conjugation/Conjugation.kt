package com.alanmacdougall.mamejibiki.conjugation

import com.alanmacdougall.mamejibiki.conjugation.Inflection.ADVERBIAL
import com.alanmacdougall.mamejibiki.conjugation.Inflection.CAUSATIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.CONDITIONAL
import com.alanmacdougall.mamejibiki.conjugation.Inflection.DESIDERATIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.IMPERATIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.NEGATIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.PASSIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.PAST
import com.alanmacdougall.mamejibiki.conjugation.Inflection.POLITE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.POTENTIAL
import com.alanmacdougall.mamejibiki.conjugation.Inflection.PROGRESSIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.REQUEST
import com.alanmacdougall.mamejibiki.conjugation.Inflection.SHORT_CAUSATIVE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.STEM
import com.alanmacdougall.mamejibiki.conjugation.Inflection.TARA
import com.alanmacdougall.mamejibiki.conjugation.Inflection.TARI
import com.alanmacdougall.mamejibiki.conjugation.Inflection.TE
import com.alanmacdougall.mamejibiki.conjugation.Inflection.VOLITIONAL

/** One inflection step, labelled with its Forms tab row name (UI spec §6). */
enum class Inflection(val label: String) {
    POLITE("polite"),
    PAST("past"),
    TE("te-form"),
    PROGRESSIVE("progressive"),
    VOLITIONAL("volitional"),
    DESIDERATIVE("desiderative"),
    CONDITIONAL("conditional (ば)"),
    TARA("conditional (たら)"),
    TARI("alternative (たり)"),
    POTENTIAL("potential"),
    PASSIVE("passive"),
    CAUSATIVE("causative"),
    /** The godan short causative (引かす), which only appears inside the causative passive 引かされる. */
    SHORT_CAUSATIVE("causative"),
    IMPERATIVE("imperative"),
    REQUEST("request"),
    STEM("stem"),
    ADVERBIAL("adverbial"),
    NEGATIVE("negative"),
}

/**
 * A suffix replacement: a word of class [wordClass] ending in [from] becomes
 * one ending in [to] by applying [inflections] (innermost first). The result
 * is a word of class [resultClass], or can't inflect further when that's null.
 * Classes are JMdict POS codes, plus the internal classes NAI, MASU and TE_FORM.
 */
data class Rule(
    val wordClass: String,
    val from: String,
    val to: String,
    val inflections: List<Inflection>,
    val resultClass: String?,
)

/** One Forms tab row: each cell holds zero or more forms. */
data class FormsRow(val label: String, val affirmative: List<String>, val negative: List<String>)

/**
 * The conjugation engine (UI spec §6). Forms are generated forwards from
 * [rules]; Deinflector runs the same rules backwards.
 */
object Conjugation {
    /** A verb's plain negative (引かない): conjugates like an い-adjective, plus 引かないで. */
    const val NAI = "nai"
    /** A verb's ます form. */
    const val MASU = "masu"
    /** A verb's te-form, which takes いる (progressive) and ください (request). */
    const val TE_FORM = "te"

    val rules: List<Rule> = buildList {
        addAll(godan("v5u", "う", "わ", "い", "え", "お", "って", "った"))
        addAll(godan("v5k", "く", "か", "き", "け", "こ", "いて", "いた"))
        addAll(godan("v5g", "ぐ", "が", "ぎ", "げ", "ご", "いで", "いだ"))
        addAll(godan("v5s", "す", "さ", "し", "せ", "そ", "して", "した"))
        addAll(godan("v5t", "つ", "た", "ち", "て", "と", "って", "った"))
        addAll(godan("v5n", "ぬ", "な", "に", "ね", "の", "んで", "んだ"))
        addAll(godan("v5b", "ぶ", "ば", "び", "べ", "ぼ", "んで", "んだ"))
        addAll(godan("v5m", "む", "ま", "み", "め", "も", "んで", "んだ"))
        addAll(godan("v5r", "る", "ら", "り", "れ", "ろ", "って", "った"))
        // 行く: 行った, 行って.
        addAll(godan("v5k-s", "く", "か", "き", "け", "こ", "って", "った"))
        // 問う: 問うた, 問うて.
        addAll(godan("v5u-s", "う", "わ", "い", "え", "お", "うて", "うた"))
        // ある: the negative is ない.
        addAll(godan("v5r-i", "る", "ら", "り", "れ", "ろ", "って", "った").filter { it.inflections != listOf(NEGATIVE) })
        for (aru in listOf("ある", "有る", "在る")) add(Rule("v5r-i", aru, "ない", listOf(NEGATIVE), NAI))
        // くださる: ください, くださいます.
        addAll(godan("v5aru", "る", "ら", "い", "れ", "ろ", "って", "った").filter { it.inflections != listOf(IMPERATIVE) })
        add(Rule("v5aru", "る", "い", listOf(IMPERATIVE), null))

        addAll(ichidan("v1", imperative = "ろ"))
        // くれる: くれ.
        addAll(ichidan("v1-s", imperative = ""))
        addAll(kuru("来る", "来", "来", "来"))
        addAll(kuru("くる", "こ", "き", "く"))
        addAll(suru("vs-i", negative = "しない", potential = "できる"))
        // 愛する: 愛さない, 愛せる.
        addAll(suru("vs-s", negative = "さない", potential = "せる"))

        addAll(adjective("adj-i", "い", ""))
        // いい → よくない. Spelled 良い, it conjugates like any い-adjective.
        addAll(adjective("adj-ix", "いい", "よ"))
        addAll(adjective("adj-ix", "い", ""))

        add(Rule(NAI, "ない", "ないで", listOf(TE), TE_FORM))
        addAll(adjective(NAI, "い", ""))
        add(Rule(MASU, "ます", "ません", listOf(NEGATIVE), null))
        add(Rule(MASU, "ます", "ました", listOf(PAST), null))
        add(Rule(MASU, "ます", "ませんでした", listOf(NEGATIVE, PAST), null))
        add(Rule(MASU, "ます", "ましょう", listOf(VOLITIONAL), null))
        for (te in listOf("て", "で")) {
            add(Rule(TE_FORM, te, te + "いる", listOf(PROGRESSIVE), "v1"))
            add(Rule(TE_FORM, te, te + "ください", listOf(REQUEST), null))
        }
    }

    private val byClass: Map<String, List<Rule>> = rules.groupBy { it.wordClass }

    private val internalClasses = setOf(NAI, MASU, TE_FORM)

    /** The JMdict POS codes the Forms tab conjugates; `vs` nouns conjugate as noun + する. */
    val dictionaryClasses: Set<String> = rules.map { it.wordClass }.toSet() - internalClasses + "vs"

    fun supports(pos: String): Boolean = pos in dictionaryClasses

    /**
     * Every way to apply [chain] (innermost first) to [word] of class
     * [wordClass]. Where several rules for the same inflections and result
     * class match, only the longest `from` applies (いい takes よくない, not
     * いくない).
     */
    fun conjugate(word: String, wordClass: String, chain: List<Inflection>): List<String> {
        if (chain.isEmpty()) return listOf(word)
        val matching = byClass[wordClass].orEmpty().filter { word.endsWith(it.from) }
        val longest = matching.groupBy { it.inflections to it.resultClass }.mapValues { (_, rs) -> rs.maxOf { it.from.length } }
        return matching
            .filter { it.from.length == longest[it.inflections to it.resultClass] }
            .filter { chain.size >= it.inflections.size && chain.subList(0, it.inflections.size) == it.inflections }
            .flatMap { rule ->
                val result = word.dropLast(rule.from.length) + rule.to
                val rest = chain.drop(rule.inflections.size)
                when {
                    rest.isEmpty() -> listOf(result)
                    rule.resultClass == null -> emptyList()
                    else -> conjugate(result, rule.resultClass, rest)
                }
            }
            .distinct()
    }

    /**
     * The Forms tab table for [word] as a [pos] word, or null if the engine
     * can't conjugate this spelling. A `vs` noun conjugates as noun + する.
     */
    fun table(word: String, pos: String): List<FormsRow>? {
        if (!supports(pos)) return null
        val (stem, wordClass) = if (pos == "vs") word + "する" to "vs-i" else word to pos
        val rows = if (pos.startsWith("adj")) ADJECTIVE_ROWS else VERB_ROWS
        val table = rows.map { row ->
            FormsRow(
                row.label,
                row.affirmative.flatMap { conjugate(stem, wordClass, it) }.distinct(),
                row.negative.flatMap { conjugate(stem, wordClass, it) }.distinct(),
            )
        }
        return table.takeIf { t -> t.drop(1).any { it.affirmative.isNotEmpty() } }
    }

    private class TableRow(val label: String, val affirmative: List<List<Inflection>>, val negative: List<List<Inflection>>)

    private val VERB_ROWS = listOf(
        TableRow("Non-past", listOf(listOf()), listOf(listOf(NEGATIVE))),
        TableRow("Non-past, polite", listOf(listOf(POLITE)), listOf(listOf(POLITE, NEGATIVE))),
        TableRow("Past", listOf(listOf(PAST)), listOf(listOf(NEGATIVE, PAST))),
        TableRow("Past, polite", listOf(listOf(POLITE, PAST)), listOf(listOf(POLITE, NEGATIVE, PAST))),
        TableRow("Te-form", listOf(listOf(TE)), listOf(listOf(NEGATIVE, TE))),
        TableRow("Progressive", listOf(listOf(TE, PROGRESSIVE)), listOf(listOf(TE, PROGRESSIVE, NEGATIVE))),
        TableRow(
            "Progressive, polite",
            listOf(listOf(TE, PROGRESSIVE, POLITE)),
            listOf(listOf(TE, PROGRESSIVE, POLITE, NEGATIVE)),
        ),
        TableRow("Volitional", listOf(listOf(VOLITIONAL)), listOf(listOf(VOLITIONAL, NEGATIVE))),
        TableRow("Volitional, polite", listOf(listOf(POLITE, VOLITIONAL)), listOf()),
        TableRow("Desiderative", listOf(listOf(DESIDERATIVE)), listOf(listOf(DESIDERATIVE, NEGATIVE))),
        TableRow("Conditional (ば)", listOf(listOf(CONDITIONAL)), listOf(listOf(NEGATIVE, CONDITIONAL))),
        TableRow("Conditional (たら)", listOf(listOf(TARA)), listOf(listOf(NEGATIVE, TARA))),
        TableRow("Alternative (たり)", listOf(listOf(TARI)), listOf(listOf(NEGATIVE, TARI))),
        TableRow("Potential", listOf(listOf(POTENTIAL)), listOf(listOf(POTENTIAL, NEGATIVE))),
        TableRow("Passive", listOf(listOf(PASSIVE)), listOf(listOf(PASSIVE, NEGATIVE))),
        TableRow("Causative", listOf(listOf(CAUSATIVE)), listOf(listOf(CAUSATIVE, NEGATIVE))),
        TableRow(
            "Causative passive",
            listOf(listOf(CAUSATIVE, PASSIVE), listOf(SHORT_CAUSATIVE, PASSIVE)),
            listOf(listOf(CAUSATIVE, PASSIVE, NEGATIVE), listOf(SHORT_CAUSATIVE, PASSIVE, NEGATIVE)),
        ),
        TableRow("Imperative", listOf(listOf(IMPERATIVE)), listOf(listOf(IMPERATIVE, NEGATIVE))),
        TableRow("Request", listOf(listOf(TE, REQUEST)), listOf(listOf(NEGATIVE, TE, REQUEST))),
        TableRow("Stem (連用形)", listOf(listOf(STEM)), listOf()),
    )

    private val ADJECTIVE_ROWS = listOf(
        TableRow("Non-past", listOf(listOf()), listOf(listOf(NEGATIVE))),
        TableRow("Non-past, polite", listOf(listOf(POLITE)), listOf(listOf(NEGATIVE, POLITE))),
        TableRow("Past", listOf(listOf(PAST)), listOf(listOf(NEGATIVE, PAST))),
        TableRow("Past, polite", listOf(listOf(PAST, POLITE)), listOf(listOf(NEGATIVE, PAST, POLITE))),
        TableRow("Te-form", listOf(listOf(TE)), listOf(listOf(NEGATIVE, TE))),
        TableRow("Adverbial", listOf(listOf(ADVERBIAL)), listOf()),
        TableRow("Conditional (ば)", listOf(listOf(CONDITIONAL)), listOf(listOf(NEGATIVE, CONDITIONAL))),
        TableRow("Conditional (たら)", listOf(listOf(TARA)), listOf(listOf(NEGATIVE, TARA))),
    )
}

/** Rules for a godan class, given the dictionary ending, its a/i/e/o-row kana, and the te and ta forms of the ending. */
private fun godan(cls: String, u: String, a: String, i: String, e: String, o: String, te: String, ta: String) = rules(cls, u) {
    add(a + "ない", NEGATIVE, result = Conjugation.NAI)
    add(i + "ます", POLITE, result = Conjugation.MASU)
    add(ta, PAST)
    add(te, TE, result = Conjugation.TE_FORM)
    add(ta + "ら", TARA)
    add(ta + "り", TARI)
    add(o + "う", VOLITIONAL)
    add(u + "まい", VOLITIONAL, NEGATIVE)
    add(i + "たい", DESIDERATIVE, result = "adj-i")
    add(e + "ば", CONDITIONAL)
    add(e + "る", POTENTIAL, result = "v1")
    add(a + "れる", PASSIVE, result = "v1")
    add(a + "せる", CAUSATIVE, result = "v1")
    // 話さされる is avoided, so す-verbs have no short causative.
    if (u != "す") add(a + "す", SHORT_CAUSATIVE, result = "v5s")
    add(e, IMPERATIVE)
    add(u + "な", IMPERATIVE, NEGATIVE)
    add(i, STEM)
}

private fun ichidan(cls: String, imperative: String) = rules(cls, "る") {
    add("ない", NEGATIVE, result = Conjugation.NAI)
    add("ます", POLITE, result = Conjugation.MASU)
    add("た", PAST)
    add("て", TE, result = Conjugation.TE_FORM)
    add("たら", TARA)
    add("たり", TARI)
    add("よう", VOLITIONAL)
    add("まい", VOLITIONAL, NEGATIVE)
    add("たい", DESIDERATIVE, result = "adj-i")
    add("れば", CONDITIONAL)
    add("られる", POTENTIAL, result = "v1")
    add("られる", PASSIVE, result = "v1")
    add("させる", CAUSATIVE, result = "v1")
    add(imperative, IMPERATIVE)
    add("るな", IMPERATIVE, NEGATIVE)
    add("", STEM)
}

/** 来る, given the stem spelled for its こ, き and く readings (来 for all three in kanji). */
private fun kuru(dictionary: String, ko: String, ki: String, ku: String) = rules("vk", dictionary) {
    add(ko + "ない", NEGATIVE, result = Conjugation.NAI)
    add(ki + "ます", POLITE, result = Conjugation.MASU)
    add(ki + "た", PAST)
    add(ki + "て", TE, result = Conjugation.TE_FORM)
    add(ki + "たら", TARA)
    add(ki + "たり", TARI)
    add(ko + "よう", VOLITIONAL)
    add(ko + "まい", VOLITIONAL, NEGATIVE)
    add(ki + "たい", DESIDERATIVE, result = "adj-i")
    add(ku + "れば", CONDITIONAL)
    add(ko + "られる", POTENTIAL, result = "v1")
    add(ko + "られる", PASSIVE, result = "v1")
    add(ko + "させる", CAUSATIVE, result = "v1")
    add(ko + "い", IMPERATIVE)
    add(ku + "るな", IMPERATIVE, NEGATIVE)
    add(ki, STEM)
}

private fun suru(cls: String, negative: String, potential: String) = rules(cls, "する") {
    add(negative, NEGATIVE, result = Conjugation.NAI)
    add("します", POLITE, result = Conjugation.MASU)
    add("した", PAST)
    add("して", TE, result = Conjugation.TE_FORM)
    add("したら", TARA)
    add("したり", TARI)
    add("しよう", VOLITIONAL)
    add("するまい", VOLITIONAL, NEGATIVE)
    add("したい", DESIDERATIVE, result = "adj-i")
    add("すれば", CONDITIONAL)
    add(potential, POTENTIAL, result = "v1")
    add("される", PASSIVE, result = "v1")
    add("させる", CAUSATIVE, result = "v1")
    add("しろ", IMPERATIVE)
    add("するな", IMPERATIVE, NEGATIVE)
    add("し", STEM)
}

/** い-adjective rules, given the dictionary ending and the stem that replaces it (いい → よ). */
private fun adjective(cls: String, ending: String, stem: String) = rules(cls, ending) {
    add(stem + "くない", NEGATIVE, result = "adj-i")
    add(ending + "です", POLITE)
    add(stem + "くありません", NEGATIVE, POLITE)
    add(stem + "かった", PAST)
    add(stem + "かったです", PAST, POLITE)
    add(stem + "くて", TE)
    add(stem + "く", ADVERBIAL)
    add(stem + "ければ", CONDITIONAL)
    add(stem + "かったら", TARA)
    add(stem + "かったり", TARI)
}

private class RulesBuilder(val cls: String, val from: String) {
    val rules = ArrayList<Rule>()
    fun add(to: String, vararg inflections: Inflection, result: String? = null) {
        rules += Rule(cls, from, to, inflections.toList(), result)
    }
}

private fun rules(cls: String, from: String, build: RulesBuilder.() -> Unit): List<Rule> =
    RulesBuilder(cls, from).apply(build).rules
