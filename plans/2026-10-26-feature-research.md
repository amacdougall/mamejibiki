# Feature research: Anki integration, kanji breakdown, writing practice

Date: 2026-10-26
Status: information gathering only, nothing is being built yet

Sources examined:
- HoshiReader Android source (shallow clone of `HuangAntimony/Hoshi-Reader-Android`, `app/src/main/java/moe/antimony/hoshi/features/anki/`)
- kakimashou.com: page HTML for 陽, its JS bundle (`/js/all.js`, deminified), and `/credits`
- KanjiVG source file for 陽 (`kanji/0967d.svg`)

---

## 1. Anki integration (how HoshiReader talks to AnkiDroid)

### Mechanism

There's no network call and no file export. AnkiDroid publishes an **Android ContentProvider** (authority `com.ichi2.anki.flashcards`), and other apps on the device read and write the user's collection through it. AnkiDroid also ships a small client library that wraps the provider:

```kotlin
// gradle/libs.versions.toml
ankidroid-api = { group = "com.github.ankidroid", name = "Anki-Android", version = "api-v1.1.0" }  // via JitPack
```

The main classes are `com.ichi2.anki.api.AddContentApi` (the high-level wrapper) and `com.ichi2.anki.FlashCardsContract` (the raw provider URIs and column names).

### What the manifest needs

```xml
<uses-permission android:name="com.ichi2.anki.permission.READ_WRITE_DATABASE" />
<queries>
    <package android:name="com.ichi2.anki" />
    <provider android:authorities="com.ichi2.anki.flashcards" />
</queries>
```

- `READ_WRITE_DATABASE` is a *dangerous* permission that AnkiDroid defines, so it has to be requested at runtime. Hoshi checks it with `ContextCompat.checkSelfPermission` and asks for it through an `ActivityResultLauncher` (`AnkiView.kt:116-123`).
- The `<queries>` block is needed because of Android 11+ package visibility rules. Without it, `AddContentApi.getAnkiDroidPackageName()` returns null and AnkiDroid looks uninstalled.

### Operations Hoshi uses (`AndroidAnkiContentApi.kt`)

| Operation | How it's done |
|---|---|
| Detect AnkiDroid | `AddContentApi.getAnkiDroidPackageName(ctx) != null` |
| List decks | `api.deckList` returns `Map<Long, String>` |
| List note types | `api.modelList`, then `api.getFieldList(modelId)` for each type |
| Add a note | `api.addNote(modelId, deckId, fields: Array<String>, tags: Set<String>)` returns the note ID, or null on failure |
| Add media (audio/images) | Write the file to cache, expose it through the app's own `FileProvider`, call `grantUriPermission("com.ichi2.anki", uri, READ)`, then `api.addMediaFromUri(uri, name, "audio"/"image")`. This returns the filename to put in a field (`[sound:x.mp3]` / `<img>`) |
| Duplicate check | Query `FlashCardsContract.Note.CONTENT_URI_V2` with `csum = <checksum>` (optionally also `mid = modelId`). The checksum is Anki's own first-field checksum: NFC-normalize, strip HTML, SHA-1, take the first 8 hex digits as a long. Hoshi reimplements this in `ankiFirstFieldChecksum()`. Deck-scoped checks then look at each note's cards (`Note/<id>/cards`, `DECK_ID`) |
| Open in AnkiDroid's browser | `ACTION_VIEW` on `anki://x-callback-url/browser?search=<query>` |
| Trigger a sync | Intent action `com.ichi2.anki.DO_SYNC` sent to package `com.ichi2.anki` |

### Mapping dictionary data to card fields

The user chooses a deck and a note type, then maps each field of that note type to a placeholder such as `{expression}`, `{reading}`, `{furigana-plain}`, `{glossary-first}`, `{audio}`, `{sentence}`, `{pitch-accent-positions}`, or `{frequencies}`. Placeholders are resolved in `AnkiModels.kt:~370`. These are Yomitan-style markers. `AnkiFieldTemplates.kt` fills in default mappings automatically when the note type's name is one of the well-known community templates (Lapis, Kiku, Senren). Several named "card formats" (deck + note type + mapping) can be saved.

### Second backend: AnkiConnect

Hoshi can also talk to AnkiConnect over HTTP: JSON-RPC, `{"action": ..., "version": 6}`, using `addNote`, `canAddNotesWithErrorDetail`, and `storeMediaFile` (`AnkiConnectBackend.kt`). It works with desktop Anki or the *AnkiconnectAndroid* app. Both backends sit behind one `AnkiBackend` interface.

### Takeaways for us

- The AnkiDroid ContentProvider route is the right fit for an offline, on-device app. It's small: a permission, a `<queries>` block, a `FileProvider` for media, and roughly 300 lines of wrapper code.
- We need to decide on card templates early. We could ship our own note type (`api.addNewCustomModel` / `addNewBasicModel` exist in the API) or follow Hoshi and map onto whatever note type the user already has.
- Duplicate detection needs the checksum reimplementation. Hoshi's version is a good reference.
- Hoshi is GPL-licensed. Treat it as a reference for how things work, not as code to copy, unless we're fine with GPL.

---

## 2. Kanji component breakdown (kakimashou "Character Breakdown")

### What the page shows

For 陽 it shows a recursive tree. Every node links to `/dictionary/character/<char>`:

```
陽  sunshine, yang principle, positive   ヨウ ひ あき (3rd, N3)
├─ ⻖
└─ 昜  open, sun   ヨウ (Kentei 1)
   ├─ 旦  daybreak, dawn   (Late High School, N1)
   │  ├─ 日  day, sun, Japan (1st, N5)
   │  └─ 一  one (1st, N5)
   └─ 勿  not, must not (Kentei Pre-1)
      └─ 勹  wrapping enclosure (Kentei 1)
```

The HTML is server-rendered nested `div.kanjiItem` / `div.kanjiBreakdownIndent` elements. No client JS is involved.

### Where the tree comes from: KanjiVG

KanjiVG's SVG for 陽 (`kanji/0967d.svg`) has exactly the same structure in its nested `<g kvg:element="…">` groups:

```
<g kvg:element="陽">
  <g kvg:element="⻖" kvg:variant="true" kvg:original="阜" kvg:position="left" kvg:radical="general">
  <g kvg:element="昜" kvg:position="right" kvg:phon="昜">
    <g kvg:element="旦" kvg:position="top">
      <g kvg:element="日">
      <g kvg:element="一">
    <g kvg:element="勿" kvg:position="bottom">
      <g kvg:element="勹">
```

kakimashou's `/credits` page confirms it: "Character stroke and Composition Data: KanjiVG and AnimCJK". The glosses, readings, grade and JLPT level on each node come from KANJIDIC2 (EDRDG). Kentei levels come from some separate source.

KanjiVG carries extra information that kakimashou doesn't show and we could use:
- `kvg:position` (left/right/top/bottom/…) gives the layout, roughly like IDS ⿰⿱.
- `kvg:radical` marks the radical.
- `kvg:phon` marks the phonetic component. This is useful for explaining why 陽 reads ヨウ (from 昜). Adding phonetic-series links ("other kanji with 昜: 場 湯 腸 傷 …") would go beyond kakimashou.
- `kvg:original` maps variant forms to their base (⻖ → 阜), so a variant can link to the full kanji's entry.
- Leaf groups contain the actual stroke paths, so each component can be highlighted inside the parent kanji.

### Takeaways for us

- Preprocess KanjiVG at build time: walk each file's `<g>` tree, store `(kanji, parent, child, position, is_radical, is_phonetic, original)` rows in our SQLite database, then join against KANJIDIC2 for glosses. This gives us the full kakimashou tree, plus reverse lookup ("which kanji contain 昜?"), entirely offline.
- Some components have no KANJIDIC2 entry (⻖ shows a blank gloss on kakimashou). Map them through `kvg:original` or a small radical-name table.
- Other decomposition sources if KanjiVG's tree turns out too coarse or too fine: KRADFILE/RADKFILE (flat, EDRDG), CHISE IDS, cjkvi-ids. KanjiVG's advantage is that composition and strokes come from one consistent source.
- Licences: KanjiVG is CC BY-SA 3.0 and KANJIDIC2 is CC BY-SA 4.0. Both are fine for a personal app; attribution is required if it's ever distributed.

---

## 3. Kanji writing practice (`#practiceForm`)

### Rendering

- An SVG with `viewBox="0 0 109 109"`, which is KanjiVG's native coordinate space. It has a guide grid (two diagonals and two centre lines) and `touch-action: none`.
- `<g id="kanjiVG">` holds one `<g id="char0strokeN" opacity="0">` per stroke, each with a KanjiVG `<path d=…>`. The client builds this from JSON (`strokes[].subStrokes[].path`). An optional `shape` + `clip-path` variant handles AnimCJK-style filled glyphs for characters KanjiVG lacks.
- `<g id="userStrokes">` holds what the user draws.

### Input capture

- Pointer Events (`pointerdown`/`pointermove`/`pointerup`/`pointercancel`) with `setPointerCapture`. Touch events are `preventDefault`ed so the page doesn't scroll.
- Screen coordinates are mapped to 0–108 SVG units with `getBoundingClientRect()`.
- On move, a point is appended (`L x y`) only if it's more than 2 units from the last point. The result is an ordinary SVG `<path>`.

### Grading a stroke (on `pointerup`)

Strokes must be drawn in order. The user's path is compared only with the next expected stroke `l[h]`. Both paths are measured with the browser's `SVGPathElement.getTotalLength()` / `getPointAtLength()`:

1. **Resample** both paths to N evenly spaced points, where N = (expected stroke length / 2).
2. **Direction profile:** at each sample, take the tangent angle (`atan2` of the neighbouring points, central difference).
3. **Shape error** `L` = mean of |angle_expected − angle_user| (wrapped to ±180°) ÷ 15. Because this compares direction at every point, a reversed stroke or a hook bent the wrong way scores badly.
4. **Position error** `O` = (distance between the two strokes' start-end midpoints ÷ 20)².
5. **Length error** `G` = |1 − user_length ÷ expected_length|.
6. **Score** `P = L + O + G`:
   - `P > 4`: wrong. The user's stroke flashes red and fades. Every third consecutive miss triggers the Hint automatically.
   - `2 < P ≤ 4`: accepted with a warning (yellow).
   - `P ≤ 2`: correct.
   - A stroke that's correct but follows a miss is marked red ("correctAfterMiss").
   - Strokes shorter than 2 units are ignored, and extra strokes beyond the expected count count as errors.
7. **The animation the user noticed:** when a stroke is accepted, the user's path is animated toward the reference stroke's position, rotation and scale using an affine transform from midpoint offset, chord angle and chord-length ratio. At the same time the reference `<g>` fades from opacity 0 to 1 and the user's path fades out. The reference stroke appears to replace what you drew.
8. When a character is finished, a glow filter (`#glowMatrix`) tints the result green, yellow or red depending on the miss and warning counters.

The buttons work like this: **Hint** clones the next stroke and animates it being drawn. **Show Me** reveals the remaining strokes. **Clear** resets everything. A smoothing kernel (`ce()`, 7-tap Gaussian) is present but disabled (loop count 0).

### Takeaways for us

- The algorithm is simple and doesn't need ML: resample, compare tangent angles, add position and length penalties, then threshold. It's about 150 lines in any language.
- On Android we can port it to Kotlin using `android.graphics.Path` + `PathMeasure.getPosTan()`, which gives position and tangent directly. Or we can host the SVG in a WebView and reuse an approach very close to this one in JS. Native Compose `Canvas` is likely smoother for touch input.
- Stroke data is KanjiVG again (one path per stroke, already in drawing order). Sections 2 and 3 share one dataset.
- Ready-made alternatives to compare before writing our own:
  - **Hanzi Writer** (JS, MIT) has a quiz mode with similar matching. Its bundled data is Chinese (Make Me a Hanzi). For Japanese glyph shapes it would need AnimCJK's Japanese set or KanjiVG converted to its format. *(Not verified in this pass.)*

---

## Common data foundation

All three features point at the same offline datasets:

| Dataset | Used for | License |
|---|---|---|
| JMdict (EDRDG) | word entries, Anki card content | CC BY-SA 4.0 |
| KANJIDIC2 (EDRDG) | kanji meanings, readings, grade, JLPT | CC BY-SA 4.0 |
| KanjiVG | component tree and stroke paths | CC BY-SA 3.0 |
| AnimCJK (optional) | fallback strokes, filled outlines | Arphic PL / LGPL (verify per file) |

Pitch accent (Wadoku), JmdictFurigana and JPDB frequency are also in use. The full data source list and the UI specification are in `2026-10-26-ui-notes.md`.
