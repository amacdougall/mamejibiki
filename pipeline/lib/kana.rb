# frozen_string_literal: true

# Kana normalization shared by the database keys and (re-implemented in
# Kotlin) the app's query handling. Test vectors both sides must agree on live
# in shared/kana-vectors.json.
module Kana
  VOICED = 'がぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽゔ'
  PLAIN  = 'かきくけこさしすせそたちつてとはひふへほはひふへほう'

  VOWELS = {
    'a' => 'あかさたなはまやらわぁゃゎゕ',
    'i' => 'いきしちにひみりゐぃ',
    'u' => 'うくすつぬふむゆるぅゅ',
    'e' => 'えけせてねへめれゑぇゖ',
    'o' => 'おこそとのほもよろをぉょ'
  }.flat_map { |vowel, chars| chars.chars.map { |c| [c, vowel] } }.to_h.freeze

  module_function

  # Katakana → hiragana (ァ–ヶ shift down by 0x60). ー and other characters,
  # including kanji, pass through unchanged.
  def to_hiragana(text)
    text.tr('ァ-ヶ', 'ぁ-ゖ')
  end

  # The exact-match search key: written forms and readings with katakana
  # folded to hiragana, so ここあ finds ココア.
  def search_key(text)
    to_hiragana(text)
  end

  # The fuzzy reading key: hiragana, voicing removed, and long-vowel
  # extensions collapsed (ー always; う after an o/u mora; お after an o mora).
  def fuzzy_key(reading)
    out = +''
    to_hiragana(reading).tr(VOICED, PLAIN).each_char do |c|
      previous = VOWELS[out[-1]]
      next if c == 'ー'
      next if c == 'う' && %w[o u].include?(previous)
      next if c == 'お' && previous == 'o'

      out << c
    end
    out
  end
end
