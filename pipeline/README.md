# Dictionary pipeline

Builds `app/src/main/assets/mamejibiki.sqlite` from the source datasets. It runs on the dev machine only, whenever a source dataset or the schema changes.

```sh
cd pipeline
bundle install
bundle exec ruby build.rb             # fetch missing sources, build, install into the app (~30 s)
bundle exec ruby build.rb --refresh   # also re-fetch the latest JMdict and JmdictFurigana
bundle exec ruby query.rb ゆううつ     # try the app's search tiers against the built database
bundle exec ruby anki_vectors.rb      # regenerate shared/anki-vectors.json from ../anki-scripts' code
bundle exec ruby -Itest -e 'Dir["test/*_test.rb"].each { |f| require_relative f }'
```

Downloads go to `pipeline/data/` (gitignored). Their versions are recorded in `data/versions.json` and in the database's `meta` table.

## Sources

| Dataset | Fetched from | Updates |
|---|---|---|
| JMdict (English) | jmdict-simplified latest release, `jmdict-eng-*.json.tgz` | `--refresh` |
| JmdictFurigana | Doublevil/JmdictFurigana latest release, `JmdictFurigana.json` | `--refresh` |
| JPDB v2.2 frequency (Kana edition) | Kuuuube/yomitan-dictionaries, pinned zip | static |
| Wadoku pitch DB | IllDepence/anki_add_pitch_plugin, pinned, SHA-256 checked | static |

## Japanese labels

`grammar_labels.tsv` maps every JMdict tag code to the Japanese label the app shows (e.g. `v1` → 一段動詞). The build stores it in `tags.label_ja` and stops with an error naming any JMdict code that has no label, so a new JMdict code needs a line added there.

## Schema

`lib/schema.sql` is the reference. Every table is commented there. `meta.schema_version` is bumped whenever the schema or the entry JSON changes, and the app re-copies the database when it sees a new value.

## Entry JSON (`entries.data`)

This is jmdict-simplified's entry shape with empty and default values removed:

```json
{
  "kanji": [{"text": "掛ける", "common": true}, {"text": "懸ける"}],
  "kana":  [{"text": "かける", "common": true}],
  "sense": [{
    "partOfSpeech": ["v1", "vt"],
    "related": [["壁にかける"]],
    "misc": ["uk"],
    "gloss": ["to hang up (e.g. a coat, a picture on the wall)", "to let hang"]
  }]
}
```

- **Defaults:** a missing array means empty. A missing `common` means false. A missing `appliesToKanji` or `appliesToKana` means "applies to all" (`["*"]` in the source). Entries with no kanji forms omit `kanji`.
- **Optional fields:**
  - `tags` on forms.
  - `appliesToKanji` on kana.
  - On senses: `appliesToKanji`, `appliesToKana`, `related`, `antonym`, `field`, `dialect`, `misc`, `info`, and `glossType`. `glossType` is parallel to `gloss`, with values `literal`, `figurative`, `explanation`, `trademark` or null, and is present only when some gloss has a type.
- **Cross-references** (`related`, `antonym`) use the jmdict-simplified shape: `[kanji, kana?, senseIndex?]` or `[kana, senseIndex?]`.
- **Form indexes:** `jp_keys.form_index` indexes into `kanji` (form_kind 0) or `kana` (form_kind 1). `en_keys.sense_index` indexes into `sense`.

## Normalization shared with the app

`lib/kana.rb` (search key, fuzzy key) and `lib/english.rb` (phrase key, word keys) define how keys are built. The app applies identical rules to queries. The expected input/output pairs live in `shared/kana-vectors.json` and `shared/english-vectors.json`, and both the Ruby tests and the app's Kotlin tests run against them.
