# frozen_string_literal: true

require 'json'
require 'minitest/autorun'
require_relative '../lib/english'

class EnglishTest < Minitest::Test
  VECTORS = JSON.parse(File.read(File.expand_path('../../shared/english-vectors.json', __dir__)))

  def test_phrase_key
    VECTORS['phrase_key'].each { |input, expected| assert_equal expected, English.phrase_key(input), input }
  end

  def test_word_keys
    VECTORS['word_keys'].each { |input, expected| assert_equal expected, English.word_keys(input), input }
  end
end
