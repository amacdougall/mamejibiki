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
| 3 | Entry page header and tabs | ✅ | The Kanji tab appears under the spec's condition but shows a "coming soon" placeholder. |
| 4 | Word tab | ✅ | Headword, other forms, readings with all pitch patterns, senses grouped by part of speech under Japanese label headers (する folded into 他動詞（する）), numbered senses with their own labels, notes and tappable See also / Antonym links. |
| 5 | Kanji tab, kanji pages, practice canvas | ⬜ | Blocked on the kanji half of the database. |
| 6 | Forms tab and conjugation engine | ✅ | `conjugation/` (`architecture.md` §5). Unit tests check the 引く and 高い tables cell by cell, plus each class's distinctive cells. Checked on the device: 引く, 食べる, 来る, 勉強, 高い, いい. |
| 7 | Pitch accent rendering | ✅ | `pitch/Pitch.kt` (parsing, unit-tested) and `ui/PitchText.kt` (drawing). |
| 8 | Send to AnkiDroid | ✅ | Fields checked against golden vectors from anki-scripts' code. On the device: the permission prompt, adding a note (槍, checked by the user in AnkiDroid and desktop Anki), and the duplicate path (食べる). |
| 9 | Database: words half | ✅ | JMdict entries, Japanese and English search keys, JPDB frequency, Wadoku pitch, JmdictFurigana, tag descriptions. `pipeline/README.md`. |
| 9 | Japanese grammar-label table | ✅ | `pipeline/grammar_labels.tsv` → `tags.label_ja`, covering all 266 JMdict codes. The Anki Notes field's combining rules (§8) are separate and not built yet. |
| 9 | Database: kanji half | ⬜ | KANJIDIC2 (jmdict-simplified `kanjidic2-en` JSON) and the KanjiVG component and stroke tables. Needed for §5. |

## Next task

**Kanji (§5):** the kanji half of the database (KANJIDIC2 and the KanjiVG component and stroke tables), then the Kanji tab, kanji pages, component tree and practice canvas. Start by planning it in this section, the way the conjugation task was planned: pipeline tables, then screens, in buildable parts.

## Suggested order

1. Collapsible senses on result rows.

## Open questions

None.

## Known issues

- **Play Protect:** Google Play Protect's on-device scan classified the debug APK as "HARMFUL". This is typical for sideloaded unsigned apps. The app still runs.
- **History lost when Android kills the app:** the back stack lives in a ViewModel, so it survives configuration changes but not process death. Reopening the app then starts at an empty search.
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
