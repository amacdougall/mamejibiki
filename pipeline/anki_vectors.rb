#!/usr/bin/env ruby
# frozen_string_literal: true

# Writes shared/anki-vectors.json: Kaishi 1.5k field values produced by
# ../anki-scripts' own code, which the app's Kotlin tests check its Send to
# AnkiDroid fields against (UI spec §8).
#
#   bundle exec ruby anki_vectors.rb
#
# Needs the built database (data/mamejibiki.sqlite) and anki-scripts with its
# data downloaded (JMdict and the Wadoku pitch CSV).

require 'json'
require 'sqlite3'

ANKI_SCRIPTS = File.expand_path('../../anki-scripts', __dir__)
require File.join(ANKI_SCRIPTS, 'lib/word_lookup')
require File.join(ANKI_SCRIPTS, 'lib/pitch_database')
require File.join(ANKI_SCRIPTS, 'lib/pitch_accent_renderer')
require File.join(ANKI_SCRIPTS, 'add-jmdict-grammar-notes')

# [word, reading]: chosen to cover one and several senses, every grammar-note
# rule, and pitch patterns that are flat, odaka, multi-pattern, small-kana and
# katakana.
WORDS = [
  %w[食べる たべる], %w[勉強 べんきょう], %w[静か しずか], %w[高い たかい], %w[ちょっと ちょっと],
  %w[東京 とうきょう], %w[男 おとこ], %w[コーヒー コーヒー], %w[引く ひく], %w[ゆっくり ゆっくり],
  %w[枚 まい], %w[召し上がる めしあがる], %w[ドキドキ ドキドキ], %w[来る くる], %w[走る はしる],
  %w[ござる ござる], %w[御 ご], %w[的 てき], %w[憂鬱 ゆううつ], %w[出発 しゅっぱつ], %w[掛ける かける]
].freeze

db = SQLite3::Database.new(File.join(__dir__, 'data/mamejibiki.sqlite'), readonly: true)
pitch_db = PitchDatabase.new(File.join(ANKI_SCRIPTS, 'data/wadoku_pitchdb.csv')).load

vectors = WORDS.map do |word, reading|
  entry = WordLookup.entry_for(word, reading) or abort "No JMdict entry for #{word}"
  data = db.get_first_value('SELECT data FROM entries WHERE id = ?', entry['id'].to_i) or
    abort "#{word}: entry #{entry['id']} isn't in the app database"

  pitch = pitch_db.lookup(word, reading)
  if pitch
    # The app reads Wadoku through its own pitch table; it must hold the same pattern.
    ours = db.get_first_value('SELECT hl FROM pitch WHERE form = ? AND reading = ?', word, reading.tr('ァ-ン', 'ぁ-ん'))
    abort "#{word}: app pitch #{ours.inspect} != Wadoku #{pitch[:pitch].inspect}" unless ours == pitch[:pitch]
  end

  {
    word: word,
    reading: reading,
    entry: JSON.parse(data),
    meaning: WordLookup.extract_definitions(entry),
    notes: extract_grammar_notes(entry).join(GRAMMAR_SEPARATOR),
    pitch: pitch && { reading: pitch[:reading], hl: pitch[:pitch], html: PitchAccentRenderer.to_kaishi_html(pitch[:reading], pitch[:pitch]) }
  }
end

path = File.expand_path('../shared/anki-vectors.json', __dir__)
File.write(path, "#{JSON.pretty_generate(vectors)}\n")
puts "Wrote #{vectors.size} vectors to #{path}"
