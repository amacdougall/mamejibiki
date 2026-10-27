-- Build metadata: schema_version, built_at, and one row per source dataset version.
CREATE TABLE meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
) WITHOUT ROWID;

-- One row per JMdict entry. `data` is the compact entry JSON the entry page
-- renders (see pipeline/README.md for its shape). `common`, `rank` and `pos`
-- are lifted out for ranking and deinflection filtering.
CREATE TABLE entries (
  id     INTEGER PRIMARY KEY,   -- JMdict entry id
  common INTEGER NOT NULL,      -- 1 if any form is common (news1/ichi1/spec1/spec2/gai1)
  rank   INTEGER,               -- best JPDB rank across the entry's forms; NULL if unranked
  pos    TEXT NOT NULL,         -- space-separated distinct POS codes across all senses
  data   TEXT NOT NULL
);

-- Japanese search keys. kind 0: a written form or reading folded to hiragana
-- (Kana.search_key). kind 1: a reading's fuzzy key (Kana.fuzzy_key).
-- form_kind/form_index point back into the entry JSON so the app knows which
-- form or reading matched.
CREATE TABLE jp_keys (
  key        TEXT NOT NULL,
  kind       INTEGER NOT NULL,
  entry_id   INTEGER NOT NULL,
  form_kind  INTEGER NOT NULL,  -- 0 kanji (written form), 1 kana (reading)
  form_index INTEGER NOT NULL,
  PRIMARY KEY (kind, key, entry_id, form_kind, form_index)
) WITHOUT ROWID;

-- English search keys. kind 0: a whole gloss (English.phrase_key).
-- kind 1: a word from a gloss (English.word_keys). One row per
-- (key, kind, entry), pointing at the first sense that produced it.
CREATE TABLE en_keys (
  key         TEXT NOT NULL,
  kind        INTEGER NOT NULL,
  entry_id    INTEGER NOT NULL,
  sense_index INTEGER NOT NULL,
  PRIMARY KEY (kind, key, entry_id)
) WITHOUT ROWID;

-- JPDB v2 frequency ranks (lower = more common). reading is '' for kana
-- headwords. Each row is the better of the word's kanji-spelling rank and its
-- kana-spelling (㋕) rank.
CREATE TABLE freq (
  form    TEXT NOT NULL,
  reading TEXT NOT NULL,
  rank    INTEGER NOT NULL,
  PRIMARY KEY (form, reading)
) WITHOUT ROWID;

-- Wadoku pitch accent. reading is hiragana. hl is the per-character H/L
-- string (lowercase = small kana, optional trailing particle letter);
-- multiple patterns are comma-separated in both columns.
CREATE TABLE pitch (
  form     TEXT NOT NULL,
  reading  TEXT NOT NULL,
  patterns TEXT NOT NULL,
  hl       TEXT NOT NULL,
  PRIMARY KEY (form, reading)
) WITHOUT ROWID;

-- JmdictFurigana alignment, for forms containing kanji. ruby is a JSON array
-- of [text, reading] pairs; reading is null for kana segments.
CREATE TABLE furigana (
  form    TEXT NOT NULL,
  reading TEXT NOT NULL,
  ruby    TEXT NOT NULL,
  PRIMARY KEY (form, reading)
) WITHOUT ROWID;

-- JMdict tag codes, their English descriptions (e.g. rK → "rarely used kanji
-- form") and Japanese labels from grammar_labels.tsv (e.g. v1 → 一段動詞).
CREATE TABLE tags (
  code        TEXT PRIMARY KEY,
  description TEXT NOT NULL,
  label_ja    TEXT NOT NULL
) WITHOUT ROWID;

-- KANJIDIC2 kanji, one row per character. data is compact JSON:
-- {"on": [...], "kun": [...], "meanings": [...], "strokes", "grade", "freq", "jlpt"}
-- with empty and missing fields left out. kun keeps KANJIDIC2's "." okurigana
-- and "-" affix markers. jlpt is the N level (1-5) from kanji-data.
CREATE TABLE kanji (
  literal TEXT PRIMARY KEY,
  data    TEXT NOT NULL
) WITHOUT ROWID;

-- KanjiVG, one row per character. strokes is a JSON array of SVG path data
-- strings in drawing order (109 x 109 coordinate space). tree is the component
-- tree: nested {"e": element, "o": kvg:original, "phon": true, "c": [children]}
-- nodes, missing keys meaning none; the root is the character itself.
-- A rowid table: its rows are too large to store well WITHOUT ROWID.
CREATE TABLE kanji_vg (
  literal TEXT PRIMARY KEY,
  strokes TEXT NOT NULL,
  tree    TEXT NOT NULL
);

-- Reverse component index: the kanji whose KanjiVG tree contains component
-- at any depth, as an element or as a variant's original. ord is the
-- Contained in order: KANJIDIC2 frequency, then grade, then stroke count.
CREATE TABLE kanji_parts (
  component TEXT NOT NULL,
  ord       INTEGER NOT NULL,
  kanji     TEXT NOT NULL,
  PRIMARY KEY (component, ord)
) WITHOUT ROWID;
