package com.alanmacdougall.mamejibiki.search

/**
 * The `*` wildcard (UI spec §2). A query whose only `*`s are trailing is the
 * normal search; any leading or inner `*` makes it a pattern matched as a whole
 * against plain search keys. Pure Kotlin.
 */
object Wildcard {
    /** Folds the full-width wildcards ＊ and ？ to `*` and `?`. */
    fun normalize(query: String): String = query.replace('＊', '*').replace('？', '?')

    /** True for a query of only `*` and `?`, which shows no results. */
    fun isOnlyWildcards(query: String): Boolean = query.all { it == '*' || it == '?' }

    /** The query for the normal search (trailing `*`s dropped), or null when it needs a pattern search. */
    fun plainQuery(query: String): String? = query.trimEnd('*').takeIf { '*' !in it }

    /** A pattern as a SQLite GLOB: `*` and `?` kept, `[` escaped. */
    fun glob(pattern: String): String = pattern.replace("[", "[[]")

    /** The pattern's literal characters, used to rank an exact key match first. */
    fun literal(pattern: String): String = pattern.filter { it != '*' && it != '?' }

    /**
     * The ranges of [key] that the pattern's literal text matched, matching as
     * GLOB does (each `*` takes as little as it can). Null if it doesn't match.
     */
    fun highlight(pattern: String, key: String): List<IntRange>? {
        val regex = buildString {
            append('^')
            var run = StringBuilder()
            fun flush() {
                if (run.isNotEmpty()) append('(').append(Regex.escape(run.toString())).append(')')
                run = StringBuilder()
            }
            for (c in pattern) when (c) {
                '*' -> { flush(); append(".*?") }
                '?' -> { flush(); append('.') }
                else -> run.append(c)
            }
            flush()
            append('$')
        }
        val match = Regex(regex).find(key) ?: return null
        return match.groups.drop(1).mapNotNull { it?.range }
    }
}
