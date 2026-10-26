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

-- JMdict tag codes and their English descriptions (e.g. rK → "rarely used kanji form").
CREATE TABLE tags (
  code        TEXT PRIMARY KEY,
  description TEXT NOT NULL
) WITHOUT ROWID;
