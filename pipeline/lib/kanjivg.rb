# frozen_string_literal: true

require 'rexml/document'
require 'rexml/streamlistener'

# Reads KanjiVG's single-file XML release into per-character stroke paths and
# component trees, following the rules in UI spec §5 (component breakdown).
module KanjiVG
  # One character: its literal, its stroke paths in drawing order, and its
  # component tree ({ 'e', 'o'?, 'phon'?, 'c'? } nodes, the root being the
  # character itself).
  Character = Struct.new(:literal, :strokes, :tree)

  # A <g> group as written in the file, before the tree rules are applied.
  RawGroup = Struct.new(:element, :original, :phon, :part, :number, :children)

  class << self
    # Yields a Character for each <kanji> in the XML. Entries at compatibility
    # code points (alternate glyphs of characters KanjiVG also has at their
    # standard code points, e.g. U+F9A8 for 令) are skipped.
    def each_character(io)
      listener = Listener.new do |literal, strokes, raw|
        next unless literal.unicode_normalize(:nfc) == literal

        yield Character.new(literal, strokes, tree(raw, literal))
      end
      REXML::Parsers::StreamParser.new(io, listener).parse
    end

    # Applies the tree rules to the root group. The root's element is always
    # the character itself.
    def tree(raw, literal)
      { 'e' => literal, 'c' => children_of(raw) }.compact
    end

    # Every component in a tree below the root, as [element, original] pairs.
    def components(node)
      (node['c'] || []).flat_map { |child| [[child['e'], child['o']]] + components(child) }
    end

    private

    def children_of(raw)
      presence(merge_parts(raw.children.flat_map { |child| nodes_for(child) }))
    end

    # A group with a single-character element becomes one node. Any other group
    # (no element, a CDP-… name or an IDS description) gives its children,
    # unmerged, so their split pieces can still join pieces in the parent.
    def nodes_for(raw)
      element = raw.element&.unicode_normalize(:nfc)
      return raw.children.flat_map { |child| nodes_for(child) } unless element && element.length == 1

      node = { 'e' => element }
      node['o'] = raw.original.unicode_normalize(:nfc) if raw.original
      node['phon'] = true if raw.phon
      node['c'] = children_of(raw)
      node[:part_key] = [element, raw.number] if raw.part
      [node.compact]
    end

    # Joins the pieces of a split component (kvg:part) that ended up under the
    # same parent into the first piece's position.
    def merge_parts(nodes)
      first_piece = {}
      nodes.each_with_object([]) do |node, result|
        key = node.delete(:part_key)
        target = key && first_piece[key]
        if target
          target['phon'] = true if node['phon']
          children = merge_parts((target['c'] || []) + (node['c'] || []))
          target['c'] = children unless children.empty?
        else
          first_piece[key] = node if key
          result << node
        end
      end
    end

    def presence(array)
      array unless array.nil? || array.empty?
    end
  end

  # Stream listener that collects one <kanji> at a time.
  class Listener
    include REXML::StreamListener

    def initialize(&on_character)
      @on_character = on_character
    end

    def tag_start(name, attrs)
      case name
      when 'kanji'
        @literal = [attrs['id'].delete_prefix('kvg:kanji_').split('-').first.to_i(16)].pack('U')
        @strokes = []
        @stack = []
        @root = nil
      when 'g'
        group = RawGroup.new(attrs['kvg:element'], attrs['kvg:original'], attrs['kvg:phon'],
                             attrs['kvg:part'], attrs['kvg:number'], [])
        if @stack.empty?
          @root = group
        else
          @stack.last.children << group
        end
        @stack.push(group)
      when 'path'
        @strokes << attrs['d']
      end
    end

    def tag_end(name)
      case name
      when 'g' then @stack.pop
      when 'kanji' then @on_character.call(@literal, @strokes, @root)
      end
    end
  end
end
