# Progress

Status of the app against the spec in `2026-10-26-ui-notes.md` (§ numbers below refer to it), plus open questions and known issues. Update this file at the end of every work session: change statuses in place, update the next task, and add a session entry to the log.

Legend: ✅ done · 🟡 partial · ⬜ not started

## Status by spec section

| § | Area | Status | Notes |
|---|---|---|---|
| — | Toolchain, device loop, project skeleton | ✅ | See `2026-10-26-dev-environment.md`. |
| 1 | Navigation and back stack | ✅ | `AppModel` (a ViewModel) holds the stack. Each screen keeps a state object, so back restores the query, results, scroll position and tab. Survives configuration changes. Expanded practice areas (§5) aren't built yet. See `architecture.md` §2. |
| 1 | Settings screen | ⬜ | Not needed yet: the AnkiDroid connection is hardcoded. |
| 2 | Search screen layout | ✅ | The field is focused with the cursor at the end whenever the screen is shown, except a See also search's first showing. Paste and clear buttons. Empty-state help. |
| 2 | Live search, including IME composition | ✅ | Confirmed on the device. |
| 2 | Prefix, `?` wildcard, fuzzy, English | ✅ | `?` and `？` are both wildcards. Whole-word fuzzy matches rank right after exact matches. English search uses three tiers and ranks first-sense matches higher. |
| 2 | Deinflection (+ "past, negative" label) | ✅ | The user tried conjugated words on the phone. |
| 2 | Result rows | 🟡 | Done: forms with match highlight and prefix-remainder shade, rare forms greyed, common/する badges, numbered senses, pitch accent on readings, tap to open. Missing: collapsible senses (rows currently clip at 2 lines). |
| 2 | Long-press menu (Copy, Send to AnkiDroid) | ✅ | |
| 3 | Entry page header and tabs | ✅ | |
| 4 | Word tab | ✅ | Headword, other forms, readings with all pitch patterns, senses grouped by part of speech under Japanese label headers (する folded into 他動詞（する）), numbered senses with their own labels, notes and tappable See also / Antonym links. |
| 5 | Kanji tab, kanji pages, practice canvas | 🟡 | Done: the Kanji tab, kanji pages, header with reading highlight, component tree, Contained in with "More". Missing: the practice area (part 3 below). |
| 6 | Forms tab and conjugation engine | ✅ | `conjugation/` (`architecture.md` §5). Unit tests check the 引く and 高い tables cell by cell, plus each class's distinctive cells. Checked on the device: 引く, 食べる, 来る, 勉強, 高い, いい. |
| 7 | Pitch accent rendering | ✅ | `pitch/Pitch.kt` (parsing, unit-tested) and `ui/PitchText.kt` (drawing). |
| 8 | Send to AnkiDroid | ✅ | Fields checked against golden vectors from anki-scripts' code. On the device: the permission prompt, adding a note (槍, checked by the user in AnkiDroid and desktop Anki), and the duplicate path (食べる). |
| 9 | Database: words half | ✅ | JMdict entries, Japanese and English search keys, JPDB frequency, Wadoku pitch, JmdictFurigana, tag descriptions. `pipeline/README.md`. |
| 9 | Japanese grammar-label table | ✅ | `pipeline/grammar_labels.tsv` → `tags.label_ja`, covering all 266 JMdict codes. The Anki Notes field's combining rules (§8) are separate and not built yet. |
| 9 | Database: kanji half | ✅ | `kanji` (KANJIDIC2 + JLPT N levels), `kanji_vg` (strokes and component trees), `kanji_parts` (reverse index). `pipeline/README.md`. |

## Next task: kanji data, Kanji tab, kanji pages and practice canvas

Spec: §5 (Kanji tab, kanji block, kanji page) and §9 (data sources and tables). Build in this order. Each part is its own session and ends with a device check.

### 1. Pipeline: kanji tables ✅

Done. The bullets below describe what was built.


- **Sources** (`lib/sources.rb`, recorded in `versions.json` and `meta`):
  - KANJIDIC2: jmdict-simplified's `kanjidic2-en-*.json.tgz` from the same release as JMdict, re-fetched by `--refresh`.
  - KanjiVG: the `kanjivg-<date>.xml.gz` asset of KanjiVG's latest GitHub release (r20260714 at the time of writing: 6,703 characters, all 2,136 jōyō kanji), re-fetched by `--refresh`.
  - JLPT: davidluzgouveia/kanji-data `kanji.json` pinned at commit `00fd7079c3890f430759536f91aa5e854ec0ca4f`, SHA-256 `561b72ea9df703c58fae0c68585812e172aaa7611d2fcee6719d55fd2dfd4fa4`, checked like Wadoku. Only `jlpt_new` is used.
- **Tables** (comment each in `lib/schema.sql`, bump `schema_version`):
  - `kanji(literal PK, data)`: compact JSON with ON readings, KUN readings (already filtered by the §5 affix rule), meanings, stroke count, grade, frequency and JLPT N level. Every KANJIDIC2 character gets a row. Empty fields are dropped, as for entries.
  - `kanji_vg(literal PK, strokes, tree)`: `strokes` is a JSON array of SVG path strings in drawing order (KanjiVG's 109 × 109 space). `tree` is JSON nodes `{e, o?, phon?, c?}` (element, `kvg:original`, phonetic flag, children), built by the rules in UI spec §5 item 3: flatten element-less groups, merge `kvg:part` pieces under the same parent, replace groups without a single-character element (`CDP-…` and IDS names) by their children, NFC-normalize element names, skip KanjiVG's compatibility code point entries. The root node is the kanji itself. A rowid table, since its rows are too large to store well `WITHOUT ROWID`.
  - `kanji_parts(component, kanji, ord)`, PK `(component, ord)`: the reverse index. Every node of every tree indexes its kanji under its element and, if present, its original. One row per (component, kanji), with `ord` in Contained in order (frequency, then grade, then stroke count; unranked kanji last). A kanji isn't listed under itself.
- **Size:** the kanji tables add about 10 MB. The database is 146 MB.
- **Ruby tests** (`test/kanjivg_test.rb`, using small inline XML snippets copied from the release):
  - 陽 gives the research doc §2 tree (⻖ with original 阜, 昜 marked phonetic, 旦 → 日 一, 勿 → 勹);
  - 哀's two 衣 parts become one node beside 口;
  - a `CDP-…` node's children are promoted;
  - an IDS-named node's children are promoted;
  - a compatibility-ideograph element comes out NFC-normalized, and KanjiVG's compatibility code point entries are skipped;
  - the KUN affix filter.
- **Checks against the built database:** `kanji_parts` for 昜 starts 場 傷 陽 揚 湯 腸; 阜 lists kanji built with ⻖; every jōyō kanji has a `kanji_vg` row.
- Update `pipeline/README.md` (sources table, schema notes).

### 2. Kanji tab and kanji page ✅

Done. The bullets below describe what was built.


- **Data:** `data/Kanji.kt` holds the models (`KanjiInfo`, `KanjiNode`, `KanjiVg`). `Dictionary` has `kanji(literals)`, which returns rows for many characters in one query (the tree's nodes need them too), `kanjiVg(literal)`, `containedIn(component)` and `furigana(form, reading)`.
- **Pure Kotlin, unit-tested** (`kanji/WordKanji.kt`, `WordKanjiTest`):
  - the Kanji tab's character order (UI spec §5 examples: 憂鬱 → 憂 鬱 幽 悒; 懸ける → 懸 掛), by code point and without 々;
  - the reading-highlight matcher. Test cases: 掛ける → か.ける only, 掛け → か.け, 掛かり → か.かり, 心掛け → か.け and -が.け; 憂鬱 → ユウ and ウツ; 本棚 → たな and -だな (rendaku); 学校 → ガク and 出発 → ハツ (gemination, handakuten); 今日/きょう → nothing.
- **UI:** `ui/KanjiBlock.kt`, a shared block with the header row, the component tree and Contained in (the Practice button comes in part 3). The Kanji tab is a column of these blocks. `KanjiState` + `ui/KanjiScreen.kt` make the kanji page (top bar: ← and the kanji), wired in as `architecture.md` §2 describes. Tree nodes and Contained in kanji push a kanji page. The "More" expansion lives in the screen's state object, so back restores it.
- **Device check (screenshots):** "sunshine" → 陽光 (陽's tree, ヨウ and コウ highlighted), tapping through 陽 → 昜 → 日 → "More", and back to the entry on its Kanji tab; ⻖ (borrows 阜's meaning); "hang" → 下げる (さ.げる highlighted, not さ.がる); "depression" → 憂鬱 (憂 鬱 幽 … with ユウ and ウツ); dark mode.

### 3. Practice canvas

- **Pure Kotlin, unit-tested** (`practice/` package):
  - an SVG path parser for the commands KanjiVG uses: `M m C c S s` only (checked over the whole release). It flattens each stroke to a polyline in the 109-unit space;
  - resampling and the research doc §3 score (direction profile, position error, length error, thresholds 2 and 4, strokes under 2 units ignored);
  - a practice session state machine: the next expected stroke, misses (an automatic Hint after every third consecutive miss), warnings, "correct after a miss", extra strokes and completion grade.
  - Tests: each reference stroke of a few kanji scores as correct against itself; a reversed stroke, a stroke shifted far away and a half-length stroke all fail; a stroke slightly off passes with a warning.
- **UI:** `ui/PracticeCanvas.kt`, a square Compose `Canvas` at full content width with the guide grid. Touch input (`pointerInput`) consumes events, so drawing doesn't scroll the page. Use Compose's `PathParser` to draw reference strokes, and the `practice/` polylines for scoring. Animations: an accepted stroke morphs onto the reference while the reference fades in; a miss flashes red and fades; Hint draws the next stroke; Show Me reveals the remaining strokes; completion tints the result green, yellow or red. Clear resets.
- The Practice button expands and collapses the area. The expanded flag is kept per kanji in the screen's state object (Kanji tab and kanji page), so back restores it (UI spec §1). The drawing itself isn't kept.
- **Device check:** the user draws a few kanji on the phone (e.g. 日, 陽, 書): correct strokes snap into place, a reversed stroke is rejected, Hint and Show Me work, and drawing doesn't scroll the page.

### Done when

Status rows §5 and §9 kanji half are ✅, `architecture.md` lists the new files, `2026-10-26-ui-notes.md` records any decisions made along the way, and the log records what was checked on the device.

## Suggested order

1. Collapsible senses on result rows.

## Open questions

None.

## Known issues

- **Play Protect:** Google Play Protect's on-device scan classified the debug APK as "HARMFUL". This is typical for sideloaded unsigned apps. The app still runs.
- **History lost when Android kills the app:** the back stack lives in a ViewModel, so it survives configuration changes but not process death. Reopening the app then starts at an empty search.
- **KanjiVG glyph shapes differ from KANJIDIC2 for a few kanji:** KanjiVG draws traditional shapes for some 2010 jōyō additions, so its stroke count is one higher for 僅 葛 謎 賭 遜 遡 餅 餌 (two-dot ⻌, older 食 radical and similar) and one lower for 牙. The header shows KANJIDIC2's count, and the practice canvas expects KanjiVG's strokes.
- **Search speed only partly measured:** search timings are written to the log (`adb logcat -s Search:I`). Seen so far: a 100-result English search ("hang") takes 120–400 ms, and short Japanese searches take under 10 ms. One-character prefix searches and the first-launch database copy haven't been timed.

## Log

One entry per work session, newest last.

### 2026-10-26, session 1: setup and search
- Set up the toolchain and paired the device wirelessly.
- Created the project, built the words-half pipeline, and built the search screen.
- The user tried searching on the device and confirmed it behaves as intended.

### 2026-10-26, session 2: entry page and navigation
- Ranked whole-word fuzzy matches above prefix matches (the user's decision).
- Added the back stack, the entry page with the Word tab, the Japanese label table and pitch accent drawing. Checked on the device: opening results, See also links, back restoring results and scroll, tab state across a dark-mode switch. Japanese search was exercised through See also links and `pipeline/query.rb`.
- From the user's feedback: grouped Word tab senses by part of speech, folded する into 自動詞/他動詞 labels, and made the search field take focus (cursor at the end) whenever the search screen is shown.
- Fixed a startup race where the app could stay on "Preparing dictionary…" (`architecture.md` §3).
- Planned the next task (conjugation engine, Forms tab, deinflection).
- Reviewed the plan docs: added `architecture.md`, adb driving notes and test commands, and commit conventions in CLAUDE.md. Dropped romaji input from scope. Recorded the settings screen as an open question.

### 2026-10-26, session 3: conjugation and deinflection
- Built the conjugation engine (`conjugation/`) for every §6 class, plus `v1-s` (くれる). Also built the Forms tab, and the deinflected search tier with its result label.
- Decisions recorded in the UI spec: the label order (innermost first, then "polite", then "negative"); ambiguity merging (simplest readings only); one form per line in table cells; `vs-s` forms (愛さない, 愛せる). The ichidan stem isn't deinflected.
- `pipeline/query.rb` doesn't run the deinflected tier (`architecture.md` §4).
- Checked the Forms tab on the device: 引く (all rows), 食べる, 来る, 勉強, 高い, いい. Deinflection lookups were checked against the built database (e.g. いった → 言う, 行く, 要る…; 勉強した → 勉強), but not yet by typing on the phone.

### 2026-10-26, session 4: Send to AnkiDroid
- The user confirmed deinflection on the phone.
- The AnkiDroid connection is hardcoded (Kaishi 1.5k, deck 色んな言葉), so the settings screen isn't needed yet. The app fills only fields it can fill deterministically. Furigana is left to the user's anki-scripts review. Frequency is filled only when JPDB has a rank.
- Built `anki/` (fields ported from anki-scripts, AnkiDroid sender), the long-press menu (Copy, Send to AnkiDroid), the permission flow and the snackbar. `pipeline/anki_vectors.rb` generates golden vectors with anki-scripts' own code, and `KaishiTest` checks the app against all 21 words.
- On the device: the duplicate path works end to end (食べる). The user added 槍 from the phone, and it worked.
- AnkiDroid showed a double line break between senses, because it renders the newline after each `<br>` as a second break. Desktop Anki ignores newlines, so Word Meaning joins senses with `<br>` alone.
- Dropped the "Open in AnkiDroid" action (the user's decision). Its `anki://` link opened a deck-name search, not the note. A duplicate send now just says the word is already in Anki.

### 2026-10-26, session 5: kanji plan and kanji tables
- Planned the kanji task (Next task above) after checking the datasets. Decisions recorded in UI spec §5 and §9: JLPT N levels come from kanji-data (KANJIDIC2's JLPT field holds the pre-2010 levels), the header's stroke/JLPT/grade line, the reading-highlight matching rules (single-kanji segments only, rendaku and gemination), the KUN affix filter, the component tree rules (element-less, `CDP-…` and IDS groups promote their children; split parts merge; variants borrow their original's meaning), and Contained in (any depth, variants count for their originals, first 30 then "More").
- Built part 1: `lib/kanjidic.rb`, `lib/kanjivg.rb`, three new tables, schema version 3 and `test/kanjivg_test.rb`. Checked against the built database: 陽's tree matches the research doc, 昜 → 場 傷 陽 揚 湯 腸 …, 阜 → 院 際 防 限 …, and all 2,136 jōyō kanji have strokes. KanjiVG's stroke count differs from KANJIDIC2 on nine jōyō kanji (Known issues).
- The app installs and searches normally with the new database. It doesn't read the kanji tables yet.

### 2026-10-26, session 6: Kanji tab and kanji pages
- Built part 2 of the kanji task: `data/Kanji.kt`, `kanji/WordKanji.kt` (+ `WordKanjiTest`), `ui/KanjiBlock.kt`, `ui/KanjiScreen.kt`, and the new `Dictionary` methods. The Kanji tab replaces its placeholder.
- Decisions recorded in UI spec §5: each kanji's highlight comes from the first form containing it (so 幽 in 幽鬱 is highlighted too); KUN matching prefers the longest fitting okurigana; a variant's kanji page borrows its original's readings and meanings but not its JLPT level or grade.
- Checked on the device (see part 2's device check). The user drilled down from 海外 to 𠂉 (a component with no KANJIDIC2 entry) and checked 叱. A kanji outside the BMP (e.g. 𠮟, U+20B9F) is covered by `WordKanjiTest` but hasn't been seen on the device.
