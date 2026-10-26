package com.alanmacdougall.mamejibiki.search

/**
 * Kana normalization, identical to pipeline/lib/kana.rb. Both are tested
 * against shared/kana-vectors.json.
 */
object Kana {
    private const val VOICED = "がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽゔ"
    private const val PLAIN = "かきくけこさしすせそたちつてとはひふへほはひふへほう"

    private val vowels: Map<Char, Char> = mapOf(
        'a' to "あかさたなはまやらわぁゃゎゕ",
        'i' to "いきしちにひみりゐぃ",
        'u' to "うくすつぬふむゆるぅゅ",
        'e' to "えけせてねへめれゑぇゖ",
        'o' to "おこそとのほもよろをぉょ",
    ).flatMap { (vowel, chars) -> chars.map { it to vowel } }.toMap()

    /** Katakana → hiragana (ァ–ヶ shift down by 0x60); everything else passes through. */
    fun toHiragana(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
    }

    /** The exact-match key: katakana folded to hiragana. */
    fun searchKey(text: String): String = toHiragana(text)

    /** The fuzzy reading key: hiragana, voicing removed, long-vowel extensions collapsed. */
    fun fuzzyKey(reading: String): String = buildString {
        for (raw in toHiragana(reading)) {
            val c = VOICED.indexOf(raw).let { if (it >= 0) PLAIN[it] else raw }
            val previous = lastOrNull()?.let { vowels[it] }
            if (c == 'ー') continue
            if (c == 'う' && (previous == 'o' || previous == 'u')) continue
            if (c == 'お' && previous == 'o') continue
            append(c)
        }
    }

    fun isKanji(c: Char): Boolean = c in '㐀'..'鿿' || c in '豈'..'﫿' || c == '々'

    fun hasJapanese(text: String): Boolean = text.any {
        it in '぀'..'ヿ' || it in '㐀'..'鿿' || it in '豈'..'﫿' || it == '々'
    }
}
