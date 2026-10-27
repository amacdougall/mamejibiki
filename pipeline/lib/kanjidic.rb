# frozen_string_literal: true

require 'json'

# Reads KANJIDIC2 (jmdict-simplified JSON) and the kanji-data JLPT levels into
# the compact kanji JSON stored in the kanji table.
module Kanjidic
  class << self
    # { literal => { 'on', 'kun', 'meanings', 'strokes', 'grade', 'freq', 'jlpt' } },
    # with empty and missing fields left out.
    def load(kanjidic_path, kanji_data_path)
      jlpt = JSON.parse(File.read(kanji_data_path)).transform_values { |k| k['jlpt_new'] }
      JSON.parse(File.read(kanjidic_path))['characters'].to_h do |c|
        [c['literal'], compact(c, jlpt[c['literal']])]
      end
    end

    # Drops a KUN reading with a `-` affix marker when the same reading without
    # the marker is also listed (UI spec §5).
    def filter_kun(readings)
      readings.reject { |r| r.include?('-') && readings.include?(r.delete('-')) }
    end

    private

    def compact(character, jlpt)
      groups = character.dig('readingMeaning', 'groups') || []
      readings = groups.flat_map { |g| g['readings'] }
      misc = character['misc']
      {
        'on' => presence(readings.select { |r| r['type'] == 'ja_on' }.map { |r| r['value'] }),
        'kun' => presence(filter_kun(readings.select { |r| r['type'] == 'ja_kun' }.map { |r| r['value'] })),
        'meanings' => presence(groups.flat_map { |g| g['meanings'] }.select { |m| m['lang'] == 'en' }.map { |m| m['value'] }),
        'strokes' => misc['strokeCounts'].first,
        'grade' => misc['grade'],
        'freq' => misc['frequency'],
        'jlpt' => jlpt
      }.compact
    end

    def presence(array)
      array unless array.nil? || array.empty?
    end
  end
end
