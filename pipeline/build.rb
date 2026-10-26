#!/usr/bin/env ruby
# frozen_string_literal: true

# Builds the app's dictionary database from the source datasets.
#
#   bundle exec ruby build.rb             # fetch missing sources, build, install into the app
#   bundle exec ruby build.rb --refresh   # also re-fetch the latest JMdict and JmdictFurigana

require 'fileutils'
require 'json'
require 'sqlite3'
require 'time'
require_relative 'lib/sources'
require_relative 'lib/kana'
require_relative 'lib/english'

SCHEMA_VERSION = 2
BUILD_PATH = File.join(Sources::DATA_DIR, 'mamejibiki.sqlite')
INSTALL_PATH = File.expand_path('../app/src/main/assets/mamejibiki.sqlite', __dir__)
# The app re-copies the database out of the APK whenever this file's contents change.
VERSION_PATH = File.expand_path('../app/src/main/assets/mamejibiki.version', __dir__)
MIN_ENTRIES = 150_000
BUILT_AT = Time.now.utc.iso8601
GRAMMAR_LABELS_PATH = File.join(__dir__, 'grammar_labels.tsv')

def step(title)
  started = Time.now
  print "#{title}... "
  result = yield
  puts format('%.1fs', Time.now - started)
  result
end

def has_kanji?(text)
  text.match?(/\p{Han}/)
end

# --- Frequency ---------------------------------------------------------------

# { [form, reading] => rank }, reading '' for kana headwords. Keeps the best
# (lowest) rank, which merges each word's kanji-spelling and ㋕ kana-spelling
# ranks.
def load_frequency
  ranks = {}
  Dir.glob(File.join(Sources::JPDB_DIR, 'term_meta_bank_*.json')).each do |bank|
    JSON.parse(File.read(bank)).each do |form, mode, data|
      next unless mode == 'freq'

      reading = data['reading'] ? Kana.to_hiragana(data['reading']) : ''
      value = data.dig('frequency', 'value') || data['value']
      key = [form, reading]
      ranks[key] = value if ranks[key].nil? || value < ranks[key]
    end
  end
  ranks
end

# --- JMdict ------------------------------------------------------------------

def compact_entry(entry)
  kanji = entry['kanji'].map do |k|
    { 'text' => k['text'], 'common' => (true if k['common']), 'tags' => presence(k['tags']) }.compact
  end
  kana = entry['kana'].map do |r|
    { 'text' => r['text'], 'common' => (true if r['common']), 'tags' => presence(r['tags']),
      'appliesToKanji' => applies(r['appliesToKanji']) }.compact
  end
  senses = entry['sense'].map do |s|
    types = s['gloss'].map { |g| g['type'] }
    {
      'partOfSpeech' => s['partOfSpeech'],
      'appliesToKanji' => applies(s['appliesToKanji']),
      'appliesToKana' => applies(s['appliesToKana']),
      'related' => presence(s['related']),
      'antonym' => presence(s['antonym']),
      'field' => presence(s['field']),
      'dialect' => presence(s['dialect']),
      'misc' => presence(s['misc']),
      'info' => presence(s['info']),
      'gloss' => s['gloss'].map { |g| g['text'] },
      'glossType' => (types if types.any?)
    }.compact
  end
  { 'kanji' => presence(kanji), 'kana' => kana, 'sense' => senses }.compact
end

def presence(array)
  array unless array.nil? || array.empty?
end

def applies(list)
  list unless list.nil? || list == ['*']
end

# Readings that apply to a written form, per the entry's appliesToKanji lists.
def readings_for(entry, kanji_text)
  entry['kana'].reject { |r| r['noKanji'] }.select do |r|
    r['appliesToKanji'].include?('*') || r['appliesToKanji'].include?(kanji_text)
  end
end

def entry_rank(entry, ranks)
  candidates =
    if entry['kanji'].empty?
      entry['kana'].map { |r| ranks[[r['text'], '']] }
    else
      entry['kanji'].flat_map do |k|
        readings_for(entry, k['text']).map { |r| ranks[[k['text'], Kana.to_hiragana(r['text'])]] }
      end
    end
  candidates.compact.min
end

# { code => Japanese label } from grammar_labels.tsv.
def load_grammar_labels
  File.readlines(GRAMMAR_LABELS_PATH, chomp: true).reject { |l| l.empty? || l.start_with?('#') }
      .to_h { |l| l.split("\t", 2) }
end

def import_jmdict(db, ranks)
  data = JSON.parse(File.read(Sources::JMDICT_PATH))
  words = data['words']
  raise "JMdict looks incomplete (#{words.size} entries)" if words.size < MIN_ENTRIES

  labels = load_grammar_labels
  missing = data['tags'].keys - labels.keys
  raise "grammar_labels.tsv has no Japanese label for: #{missing.join(', ')}" if missing.any?

  insert_tags = db.prepare('INSERT INTO tags VALUES (?, ?, ?)')
  data['tags'].each { |code, description| insert_tags.execute(code, description, labels[code]) }

  insert_entry = db.prepare('INSERT INTO entries VALUES (?, ?, ?, ?, ?)')
  insert_jp = db.prepare('INSERT OR IGNORE INTO jp_keys VALUES (?, ?, ?, ?, ?)')
  insert_en = db.prepare('INSERT INTO en_keys VALUES (?, ?, ?, ?)')

  words.each do |entry|
    id = entry['id'].to_i
    common = (entry['kanji'] + entry['kana']).any? { |f| f['common'] } ? 1 : 0
    pos = entry['sense'].flat_map { |s| s['partOfSpeech'] }.uniq.join(' ')
    insert_entry.execute(id, common, entry_rank(entry, ranks), pos, JSON.generate(compact_entry(entry)))

    entry['kanji'].each_with_index do |k, i|
      insert_jp.execute(Kana.search_key(k['text']), 0, id, 0, i)
    end
    entry['kana'].each_with_index do |r, i|
      insert_jp.execute(Kana.search_key(r['text']), 0, id, 1, i)
      insert_jp.execute(Kana.fuzzy_key(r['text']), 1, id, 1, i)
    end

    seen = {}
    entry['sense'].each_with_index do |sense, sense_index|
      sense['gloss'].each do |gloss|
        phrase = English.phrase_key(gloss['text'])
        keys = [[phrase, 0]] + English.word_keys(gloss['text']).map { |w| [w, 1] }
        keys.each do |key|
          next if key[0].empty? || seen[key]

          seen[key] = true
          insert_en.execute(key[0], key[1], id, sense_index)
        end
      end
    end
  end

  [words.size, data['version'], data['dictDate']]
end

# --- Other datasets ----------------------------------------------------------

def import_frequency(db, ranks)
  insert = db.prepare('INSERT INTO freq VALUES (?, ?, ?)')
  ranks.each { |(form, reading), rank| insert.execute(form, reading, rank) }
  ranks.size
end

# Wadoku rows can repeat a (form, reading) key; the last one wins, as in
# anki-scripts' PitchDatabase.
def import_pitch(db)
  insert = db.prepare('INSERT OR REPLACE INTO pitch VALUES (?, ?, ?, ?)')
  count = 0
  File.foreach(Sources::WADOKU_PATH, encoding: 'UTF-8') do |line|
    fields = line.chomp.split("␞")
    next if fields.length < 5

    orthographies, reading, _notation, patterns, hl = fields
    reading = Kana.to_hiragana(reading).gsub(/[^ぁ-ゖー]/, '')
    orthographies.split("␟").map(&:strip).uniq.each do |form|
      insert.execute(form, reading, patterns, hl)
    end
    count += 1
  end
  count
end

def import_furigana(db)
  insert = db.prepare('INSERT OR IGNORE INTO furigana VALUES (?, ?, ?)')
  rows = JSON.parse(File.read(Sources::FURIGANA_PATH, encoding: 'bom|utf-8'))
  rows.each do |row|
    next unless has_kanji?(row['text'])

    ruby = row['furigana'].map { |seg| [seg['ruby'], seg['rt']] }
    insert.execute(row['text'], row['reading'], JSON.generate(ruby))
  end
  rows.size
end

# --- Main --------------------------------------------------------------------

versions = step('Fetching sources') { Sources.fetch_all!(refresh: ARGV.include?('--refresh')) }

FileUtils.rm_f(BUILD_PATH)
db = SQLite3::Database.new(BUILD_PATH)
db.execute_batch(File.read(File.expand_path('lib/schema.sql', __dir__)))
db.execute('PRAGMA journal_mode = OFF')
db.execute('PRAGMA synchronous = OFF')

db.transaction do
  ranks = step('Loading JPDB frequency') { load_frequency }
  count, version, date = step('Importing JMdict') { import_jmdict(db, ranks) }
  puts "  #{count} entries (JMdict #{version}, #{date})"
  step('Importing frequency') { import_frequency(db, ranks) }
  step('Importing Wadoku pitch') { import_pitch(db) }
  step('Importing JmdictFurigana') { import_furigana(db) }

  meta = { 'schema_version' => SCHEMA_VERSION.to_s, 'built_at' => BUILT_AT }
  versions.each { |name, v| meta["source.#{name}"] = v }
  meta.each { |k, v| db.execute('INSERT INTO meta VALUES (?, ?)', [k, v]) }
end

step('Indexing') do
  db.execute('ANALYZE')
end
step('Compacting') { db.execute('VACUUM') }
db.close

FileUtils.mkdir_p(File.dirname(INSTALL_PATH))
FileUtils.cp(BUILD_PATH, INSTALL_PATH)
File.write(VERSION_PATH, "#{SCHEMA_VERSION} #{BUILT_AT}\n")
puts format('Installed %<path>s (%<mb>.1f MB)', path: INSTALL_PATH, mb: File.size(INSTALL_PATH) / 1_048_576.0)
