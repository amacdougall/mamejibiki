# Progress

Status of the app against the spec in `2026-10-26-ui-notes.md` (§ numbers below refer to it), plus open questions and known issues. Update this file at the end of every work session: change statuses in place, update the next task, and add a session entry to the log.

Legend: ✅ done · 🟡 partial · ⬜ not started

## Status by spec section

| § | Area | Status | Notes |
|---|---|---|---|
| — | Toolchain, device loop, project skeleton | ✅ | See `2026-10-26-dev-environment.md`. |
| 1 | Navigation and back stack | ✅ | `AppModel` (a ViewModel) holds the stack. Each screen keeps a state object, so back restores the query, results, scroll position, tab and expanded practice areas. Survives configuration changes. See `architecture.md` §2. |
| 1 | Settings screen | ⬜ | Not needed yet: the AnkiDroid connection is hardcoded. |
| 2 | Search screen layout | ✅ | The field is focused with the cursor at the end whenever the screen is shown, except a See also search's first showing. Paste and clear buttons. Empty-state help. |
| 2 | Live search, including IME composition | ✅ | Confirmed on the device. |
| 2 | Prefix, `?` wildcard, fuzzy, English | ✅ | `?` and `？` are both wildcards. Whole-word fuzzy matches rank right after exact matches. English search uses three tiers and ranks first-sense matches higher. |
| 2 | `*` wildcard (`A*`, `*A`, `*A*`, `A*B`) | ✅ | `search/Wildcard.kt` (+ `WildcardTest`) and the pattern search in `search/Search.kt` and `pipeline/query.rb`. The kanji block's "See more" searches `*<kanji>*`. |
| 2 | Deinflection (+ "past, negative" label) | ✅ | The user tried conjugated words on the phone. |
| 2 | Result rows | ✅ | Forms with match highlight and prefix-remainder shade, rare forms greyed, common/する badges, numbered collapsible senses, pitch accent on readings, tap to open. |
| 2 | Long-press menu (Copy, Send to AnkiDroid) | ✅ | |
| 3 | Entry page header and tabs | ✅ | |
| 4 | Word tab | ✅ | Headword, other forms, readings with all pitch patterns, senses grouped by part of speech under Japanese label headers (する folded into 他動詞（する）), numbered senses with their own labels, notes and tappable See also / Antonym links. |
| 5 | Kanji tab, kanji pages, practice canvas | ✅ | The Kanji tab, kanji pages, header with reading highlight, component tree, Entries with "See more", Contained in with "More", tap-the-tile collapsing, long-press Copy on any kanji, and the practice canvas (`practice/`, `ui/PracticeCanvas.kt`). The user uses the practice canvas by hand on the phone, and it works as intended. |
| 6 | Forms tab and conjugation engine | ✅ | `conjugation/` (`architecture.md` §5). Unit tests check the 引く and 高い tables cell by cell, plus each class's distinctive cells. Checked on the device: 引く, 食べる, 来る, 勉強, 高い, いい. |
| 7 | Pitch accent rendering | ✅ | `pitch/Pitch.kt` (parsing, unit-tested) and `ui/PitchText.kt` (drawing). |
| 8 | Send to AnkiDroid | ✅ | Fields checked against golden vectors from anki-scripts' code. On the device: the permission prompt, adding a note (槍, checked by the user in AnkiDroid and desktop Anki), and the duplicate path (食べる). |
| 9 | Database: words half | ✅ | JMdict entries, Japanese and English search keys, JPDB frequency, Wadoku pitch, JmdictFurigana, tag descriptions. `pipeline/README.md`. |
| 9 | Japanese grammar-label table | ✅ | `pipeline/grammar_labels.tsv` → `tags.label_ja`, covering all 266 JMdict codes. The Anki Notes field's combining rules (§8) are separate and not built yet. |
| 9 | Database: kanji half | ✅ | `kanji` (KANJIDIC2 + JLPT N levels), `kanji_vg` (strokes and component trees), `kanji_parts` (reverse index). `pipeline/README.md`. |

## Next task

None chosen. Everything specified is built except the settings screen (§1), which isn't needed while the AnkiDroid connection is hardcoded. Ask the user what comes next.

## Later tasks

None queued.

## Open questions

None.

## Known issues

- **Play Protect:** Google Play Protect's on-device scan classified the debug APK as "HARMFUL". This is typical for sideloaded unsigned apps. The app still runs.
- **History lost when Android kills the app:** the back stack lives in a ViewModel, so it survives configuration changes but not process death. Reopening the app then starts at an empty search.
- **KanjiVG glyph shapes differ from KANJIDIC2 for a few kanji:** KanjiVG draws traditional shapes for some 2010 jōyō additions, so its stroke count is one higher for 僅 葛 謎 賭 遜 遡 餅 餌 (two-dot ⻌, older 食 radical and similar) and one lower for 牙. The header shows KANJIDIC2's count, and the practice canvas expects KanjiVG's strokes.
- **Half-length straight strokes can pass:** the research doc §3 score lets a straight stroke drawn at about half its length through (e.g. half of 日's middle bar scores under 1, well inside "correct"). Strokes that turn, like 日's second stroke, fail when drawn halfway.
- **Search speed only partly measured:** search timings are written to the log (`adb logcat -s Search:I`). Seen so far: a 100-result English search ("hang") takes 120–400 ms, short Japanese searches take under 10 ms, and `*食*` (100 results) takes 63 ms. One-character prefix searches and the first-launch database copy haven't been timed.

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
- Checked on the device (see part 2's device check). The user drilled down from 海外 to 𠂉 (a component with no KANJIDIC2 entry) and checked 叱. Kanji outside the BMP (e.g. 𠮟, U+20B9F) are covered by `WordKanjiTest`. The user decided a device check isn't needed.

### 2026-10-26, session 7: practice canvas
- Built part 3 of the kanji task: `practice/` (`Stroke.kt` path flattening and resampling, `StrokeScore.kt` the research doc §3 score, `PracticeSession.kt` the session state machine) with `PracticeTest`, and `ui/PracticeCanvas.kt`. The Practice button sits under the header's stroke line, and its expanded flag lives in `KanjiBlockState`.
- All 79,821 KanjiVG strokes in the database parse, including implicit repeated commands.
- Decisions recorded in UI spec §5: the completion grade rule, stroke colours, and Show Me writing the remaining strokes out in order.
- Checked on the device by drawing with `adb shell input motionevent`: on 日, a reversed first stroke flashed red and faded, the corrected stroke turned red (correct after a miss), the hook stroke snapped into place, Hint wrote the next stroke, finishing gave a yellow tint, and Clear and Show Me (red tint) worked. The page didn't scroll while drawing vertical strokes. On 陽光's Kanji tab: Show Me for 陽, back from a component's kanji page restored the open practice area and scroll position, and drawing and Hint in dark mode.

### 2026-10-26, session 8: kanji block Entries
- Slowed the accepted-stroke morph to 500 ms (the user's request).
- Added the kanji block's Entries section (UI spec §5 item 4): `Dictionary.entriesWith`, a `GLOB` over written-form keys ordered by rank. It takes about 0.1 s on the Mac for 食.
- Checked on the device: 食 on 食事's Kanji tab shows 食べる, 食う, 食事. "See more" opens a search for 食, back returns to the Kanji tab, and tapping 食べる opens its entry.

### 2026-10-26, session 9: collapsible senses
- Made result rows' senses collapsible (UI spec §2 Result rows): two lines collapsed, one sense per line expanded, toggled by a chevron that shows only when there's more to see. Kanji block Entries rows work the same way.
- Checked on the device with "run": 走る expanded to its 8 senses, opening the entry and going back kept it expanded, and collapsing restored the two-line row. The chevron keeps its place when the row expands, so two taps on the same spot expand and collapse it.

### 2026-10-26, session 10: the `*` wildcard
- Built the `*` wildcard (UI spec §2 Lookup behaviour): trailing `*`s fall back to the normal search; a leading or inner `*` runs a pattern search over plain keys. `FormMatch.highlight` is now a list of ranges, so the highlight covers the query's literal text wherever it sits. The empty-state help mentions `*`, and the kanji block's "See more" searches `*<kanji>*`.
- `pipeline/query.rb` runs the pattern search too: `*食` → 食, 夕食, 朝食, 昼食…; `食*会` → 食事会 first. It no longer treats a query with no word characters (`**`) as an English search matching everything.
- Checked on the device: "See more" under 食 on 食事's Kanji tab searched `*食*` (100 results in 63 ms), with 食 highlighted in 夕食 and 食っ付く. The empty-state help shows the `*` line. Japanese `*` queries typed on the keyboard haven't been tried yet (adb can't type Japanese).

### 2026-10-26, session 10 (continued): launcher icon
- Added an adaptive launcher icon (`res/mipmap-anydpi/ic_launcher.xml` and three vector drawables in `res/drawable/`): a tilted red book with a cream label and a yellow ribbon on navy, plus a monochrome layer for themed icons. Checked on the device's app info page.

### 2026-10-27, session 11: README
- Wrote `README.md`: screenshots (`docs/screenshots/`, taken on the device by driving the app over adb, including drawing 陽 on the practice canvas with `input motionevent`), a feature list / user's manual, and a Getting Started section for another developer's AI agent.
- The user confirmed the practice canvas works when drawing by hand. They've been using it.

### 2026-10-27, session 11 (continued): collapsing kanji blocks
- Tapping a kanji block's tile collapses it to its header, hiding the Practice button and everything below (UI spec §5 Kanji block). The flag is `KanjiBlockState.collapsed`.
- Checked on the device on 陽光's Kanji tab: both blocks collapse and expand, and back from a component's kanji page keeps the block's state.

### 2026-10-28, session 12: copying kanji
- Long-pressing any kanji in a kanji block (the tile, a component row, a Contained in kanji) opens a one-item Copy menu (UI spec §5 Kanji block). `CopyMenu` in `ui/KanjiBlock.kt` shares `copyText` with the search screen.
- Checked on the device on 陽光's Kanji tab: the menu appears under the 陽 tile and under the ⻖ component row, and copying ⻖ then pasting into the search field gave ⻖. Plain taps (collapse, open a component) and Contained in use the same code path but weren't tried on the device.
