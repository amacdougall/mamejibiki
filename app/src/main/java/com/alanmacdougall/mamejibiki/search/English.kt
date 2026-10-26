package com.alanmacdougall.mamejibiki.search

/**
 * English gloss key normalization, identical to pipeline/lib/english.rb. Both
 * are tested against shared/english-vectors.json.
 */
object English {
    private val stopwords = setOf("a", "an", "the", "to", "of", "in", "on", "at", "by", "for", "with", "or", "and", "as", "be", "is")
    private val parenthetical = Regex("""\([^)]*\)""")
    private val punctuation = Regex("""[^\p{IsAlphabetic}\p{IsDigit}' -]""")
    private val spaces = Regex(" {2,}")
    private val leading = Regex("""^(?:to|a|an|the) """)
    private val word = Regex("""[\p{IsAlphabetic}\p{IsDigit}][\p{IsAlphabetic}\p{IsDigit}'-]*""")

    fun phraseKey(gloss: String): String {
        val text = gloss.lowercase()
            .replace(parenthetical, " ")
            .replace(punctuation, " ")
            .replace(spaces, " ")
            .trim()
        return text.replaceFirst(leading, "").trim()
    }

    fun wordKeys(gloss: String): List<String> =
        word.findAll(gloss.lowercase()).map { it.value }
            .filter { it.length >= 2 && it !in stopwords }
            .distinct()
            .toList()
}
