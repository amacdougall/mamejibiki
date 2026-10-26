# frozen_string_literal: true

require 'json'
require 'minitest/autorun'
require_relative '../lib/kana'

class KanaTest < Minitest::Test
  VECTORS = JSON.parse(File.read(File.expand_path('../../shared/kana-vectors.json', __dir__)))

  def test_search_key
    VECTORS['search_key'].each { |input, expected| assert_equal expected, Kana.search_key(input), input }
  end

  def test_fuzzy_key
    VECTORS['fuzzy_key'].each { |input, expected| assert_equal expected, Kana.fuzzy_key(input), input }
  end
end
