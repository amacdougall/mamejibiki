# frozen_string_literal: true

require 'minitest/autorun'
require 'stringio'
require_relative '../lib/kanjivg'
require_relative '../lib/kanjidic'

# Snippets are from KanjiVG r20260714, with path data and stroke types shortened.
class KanjiVGTest < Minitest::Test
  def parse(kanji_xml)
    xml = "<kanjivg xmlns:kvg='http://kanjivg.tagaini.net'>#{kanji_xml}</kanjivg>"
    result = []
    KanjiVG.each_character(StringIO.new(xml)) { |c| result << c }
    result
  end

  def test_yang_tree
    c = parse(<<~XML).first
      <kanji id="kvg:kanji_0967d">
      <g id="kvg:0967d" kvg:element="陽">
        <g id="kvg:0967d-g1" kvg:element="⻖" kvg:variant="true" kvg:original="阜" kvg:position="left" kvg:radical="general">
          <path id="kvg:0967d-s1" d="M1"/><path id="kvg:0967d-s2" d="M2"/><path id="kvg:0967d-s3" d="M3"/>
        </g>
        <g id="kvg:0967d-g2" kvg:element="昜" kvg:position="right" kvg:phon="昜">
          <g id="kvg:0967d-g3" kvg:element="旦" kvg:position="top">
            <g id="kvg:0967d-g4" kvg:element="日"><path id="kvg:0967d-s4" d="M4"/></g>
            <g id="kvg:0967d-g5" kvg:element="一"><path id="kvg:0967d-s5" d="M5"/></g>
          </g>
          <g id="kvg:0967d-g6" kvg:element="勿" kvg:position="bottom">
            <g id="kvg:0967d-g7" kvg:element="勹"><path id="kvg:0967d-s6" d="M6"/></g>
            <path id="kvg:0967d-s7" d="M7"/>
          </g>
        </g>
      </g>
      </kanji>
    XML
    assert_equal '陽', c.literal
    assert_equal %w[M1 M2 M3 M4 M5 M6 M7], c.strokes
    expected = { 'e' => '陽', 'c' => [
      { 'e' => '⻖', 'o' => '阜' },
      { 'e' => '昜', 'phon' => true, 'c' => [
        { 'e' => '旦', 'c' => [{ 'e' => '日' }, { 'e' => '一' }] },
        { 'e' => '勿', 'c' => [{ 'e' => '勹' }] }
      ] }
    ] }
    assert_equal expected, c.tree
    assert_equal [%w[⻖ 阜], ['昜', nil], ['旦', nil], ['日', nil], ['一', nil], ['勿', nil], ['勹', nil]],
                 KanjiVG.components(c.tree)
  end

  def test_split_component_merges_across_elementless_group
    c = parse(<<~XML).first
      <kanji id="kvg:kanji_054c0">
      <g id="kvg:054c0" kvg:element="哀">
        <g id="kvg:054c0-g1" kvg:element="衣" kvg:part="1" kvg:position="top" kvg:phon="衣1">
          <g id="kvg:054c0-g2" kvg:element="亠" kvg:position="top" kvg:radical="nelson">
            <path id="kvg:054c0-s1" d="M1"/><path id="kvg:054c0-s2" d="M2"/>
          </g>
        </g>
        <g id="kvg:054c0-g3" kvg:position="bottom">
          <g id="kvg:054c0-g4" kvg:element="口" kvg:radical="tradit"><path id="kvg:054c0-s3" d="M3"/></g>
          <g id="kvg:054c0-g5" kvg:element="衣" kvg:part="2" kvg:position="bottom" kvg:phon="衣2">
            <path id="kvg:054c0-s6" d="M6"/>
          </g>
        </g>
      </g>
      </kanji>
    XML
    expected = { 'e' => '哀', 'c' => [{ 'e' => '衣', 'phon' => true, 'c' => [{ 'e' => '亠' }] }, { 'e' => '口' }] }
    assert_equal expected, c.tree
  end

  def test_cdp_component_is_replaced_by_its_children
    c = parse(<<~XML).first
      <kanji id="kvg:kanji_050b3">
      <g id="kvg:050b3" kvg:element="傳">
        <g id="kvg:050b3-g1" kvg:element="亻" kvg:variant="true" kvg:original="人" kvg:position="left"><path id="kvg:050b3-s1" d="M1"/></g>
        <g id="kvg:050b3-g2" kvg:element="專" kvg:position="right" kvg:phon="專">
          <g id="kvg:050b3-g3" kvg:element="CDP-8BD0" kvg:position="top">
            <g id="kvg:050b3-g4" kvg:element="十" kvg:part="1"><path id="kvg:050b3-s3" d="M3"/></g>
            <path id="kvg:050b3-s4" d="M4"/>
            <g id="kvg:050b3-g5" kvg:element="十" kvg:part="2"><path id="kvg:050b3-s8" d="M8"/></g>
          </g>
          <g id="kvg:050b3-g6" kvg:element="寸" kvg:position="bottom"><path id="kvg:050b3-s11" d="M11"/></g>
        </g>
      </g>
      </kanji>
    XML
    expected = { 'e' => '傳', 'c' => [
      { 'e' => '亻', 'o' => '人' },
      { 'e' => '專', 'phon' => true, 'c' => [{ 'e' => '十' }, { 'e' => '寸' }] }
    ] }
    assert_equal expected, c.tree
  end

  def test_ids_component_is_replaced_by_its_children
    c = parse(<<~XML).first
      <kanji id="kvg:kanji_066b9">
      <g id="kvg:066b9" kvg:element="暹">
        <g id="kvg:066b9-g1" kvg:element="⿱日隹" kvg:position="nyoc">
          <g id="kvg:066b9-g2" kvg:element="日"><path id="kvg:066b9-s1" d="M1"/></g>
          <g id="kvg:066b9-g3" kvg:element="隹"><path id="kvg:066b9-s2" d="M2"/></g>
        </g>
        <g id="kvg:066b9-g4" kvg:element="辶" kvg:position="nyo"><path id="kvg:066b9-s3" d="M3"/></g>
      </g>
      </kanji>
    XML
    assert_equal({ 'e' => '暹', 'c' => [{ 'e' => '日' }, { 'e' => '隹' }, { 'e' => '辶' }] }, c.tree)
  end

  def test_compatibility_elements_are_normalized_and_compatibility_entries_skipped
    chars = parse(<<~XML)
      <kanji id="kvg:kanji_09818">
      <g id="kvg:09818" kvg:element="領">
        <g id="kvg:09818-g1" kvg:element="\u{F9A8}" kvg:position="left"><path id="kvg:09818-s1" d="M1"/></g>
        <g id="kvg:09818-g2" kvg:element="頁" kvg:position="right"><path id="kvg:09818-s2" d="M2"/></g>
      </g>
      </kanji>
      <kanji id="kvg:kanji_0f9a8">
      <g id="kvg:0f9a8" kvg:element="\u{F9A8}"><path id="kvg:0f9a8-s1" d="M1"/></g>
      </kanji>
    XML
    assert_equal ['領'], chars.map(&:literal)
    assert_equal '令', chars.first.tree['c'].first['e']
  end

  def test_kun_affix_duplicates_are_dropped
    assert_equal %w[ひ -び -か], Kanjidic.filter_kun(%w[ひ -び -か])
    assert_equal %w[か.ける -が.け], Kanjidic.filter_kun(%w[か.ける -か.ける -が.け])
    assert_equal %w[なま], Kanjidic.filter_kun(%w[なま なま-])
  end
end
