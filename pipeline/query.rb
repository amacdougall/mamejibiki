#!/usr/bin/env ruby
# frozen_string_literal: true

# Development tool: runs the app's search tiers against the built database.
#
#   bundle exec ruby query.rb ゆううつ
#   bundle exec ruby query.rb 'eat'

require 'json'
require 'sqlite3'
require_relative 'lib/kana'
require_relative 'lib/english'

DB = SQLite3::Database.new(File.join(__dir__, 'data/mamejibiki.sqlite'), readonly: true)
LIMIT = 15

# Escapes GLOB metacharacters except '?', which is the user-facing wildcard.
def glob_literal(text)
  text.gsub(/[*\[]/) { |c| "[#{c}]" }
end

def japanese(query)
  key = glob_literal(Kana.search_key(query))
  tiers = [
    ['exact', 'k.kind = 0 AND k.key GLOB ?', key],
    ['prefix', 'k.kind = 0 AND k.key GLOB ?', "#{key}*"],
    ['fuzzy', 'k.kind = 1 AND k.key GLOB ?', "#{glob_literal(Kana.fuzzy_key(query))}*"]
  ]
  run(tiers, 'jp_keys', 'k.form_kind, k.form_index', '')
end

def english(query)
  phrase = English.phrase_key(query)
  tiers = [
    ['exact', 'k.kind = 0 AND k.key = ?', phrase],
    ['prefix', 'k.kind = 0 AND k.key GLOB ?', "#{glob_literal(phrase)}*"],
    ['word', 'k.kind = 1 AND k.key = ?', phrase]
  ]
  run(tiers, 'en_keys', 'k.sense_index', 'k.sense_index > 0, ')
end

# English matches in an entry's first sense outrank matches in later senses.
def run(tiers, table, extra, first_order)
  seen = {}
  tiers.flat_map do |name, where, arg|
    sql = <<~SQL
      SELECT e.id, e.common, e.rank, e.data, #{extra}
      FROM #{table} k JOIN entries e ON e.id = k.entry_id
      WHERE #{where}
      ORDER BY #{first_order}e.common DESC, e.rank IS NULL, e.rank, length(k.key)
      LIMIT 100
    SQL
    DB.execute(sql, [arg]).filter_map do |id, common, rank, data, *_|
      next if seen[id]

      seen[id] = true
      [name, id, common, rank, JSON.parse(data)]
    end
  end.first(LIMIT)
end

query = ARGV.join(' ')
results = query.match?(/[\p{Hiragana}\p{Katakana}\p{Han}ー]/) ? japanese(query) : english(query)
results.each do |tier, _id, common, rank, entry|
  forms = ((entry['kanji'] || []) + entry['kana']).map { |f| f['text'] }.join(', ')
  gloss = entry['sense'].first['gloss'].first(3).join('; ')
  puts format('%-6s %s %7s  %s — %s', tier, common == 1 ? '★' : ' ', rank || '-', forms, gloss)
end
