# frozen_string_literal: true

# English gloss keys for searching by meaning. Each gloss yields one "phrase"
# key (the whole gloss, normalized) and a "word" key per content word. The app
# normalizes queries with the same rules (shared/english-vectors.json).
module English
  STOPWORDS = %w[a an the to of in on at by for with or and as be is].freeze
  LEADING = /\A(?:to|a|an|the) /

  module_function

  # "to hang up (e.g. a coat)" → "hang up"; "the Netherlands" → "netherlands"
  def phrase_key(gloss)
    text = gloss.downcase
                .gsub(/\([^)]*\)/, ' ')
                .gsub(/[^\p{Alnum}' -]/, ' ')
                .squeeze(' ').strip
    text.sub(LEADING, '').strip
  end

  # Content words of the gloss, parentheticals included.
  def word_keys(gloss)
    gloss.downcase.scan(/\p{Alnum}[\p{Alnum}'-]*/).reject { |w| w.length < 2 || STOPWORDS.include?(w) }.uniq
  end
end
