# App architecture

How the Android app's code is organised, and the rules that keep it working. The data pipeline is documented separately in `pipeline/README.md`.

Source root: `app/src/main/java/com/alanmacdougall/mamejibiki/`.

---

## 1. Code map

| File | Role |
|---|---|
| `MainActivity.kt` | Creates `AppModel`, then sets the Compose content to `App(model)`. |
| `ui/App.kt` | `AppModel` (the back stack and the `Dictionary`), the `Screen` interface, and `App()`, which shows the top of the stack and handles system back. |
| `ui/SearchScreen.kt` | `SearchState` and the search screen: field, live search, result rows. |
| `ui/EntryScreen.kt` | `EntryState` and the entry page: top bar, tabs, per-tab scrolling. Kanji is a placeholder. |
| `ui/WordTab.kt` | The Word tab (UI spec §4). |
| `ui/FormsTab.kt` | The Forms tab (UI spec §6), drawing `EntryState.formsTables`. |
| `ui/PitchText.kt` | `PitchText`, a `Text` with pitch accent overlines drawn over chosen character ranges. |
| `ui/Theme.kt`, `ui/Icons.kt` | Material 3 theme with Japanese-locale typography; icons missing from material-icons-core. |
| `data/DictionaryDatabase.kt` | Copies the SQLite asset into app storage when its version stamp changes, and opens it read-only. |
| `data/Dictionary.kt` | The only way screens read the database: search, per-reading pitch lookup, tag descriptions and Japanese labels. |
| `data/Entry.kt` | The entry JSON model (`Entry`, `KanjiForm`, `KanaForm`, `Sense`), `FormRef`, and the headword rule (UI spec §2). |
| `search/Search.kt` | The ranked search tiers (`Tier`) over `jp_keys` and `en_keys`. |
| `search/Kana.kt`, `search/English.kt` | Query normalization, identical to `pipeline/lib/kana.rb` and `english.rb`. |
| `pitch/Pitch.kt` | Mora splitting and Wadoku H/L parsing into high runs. |
| `conjugation/Conjugation.kt` | The conjugation engine: the suffix rules, forward conjugation, and the Forms tab row definitions. |
| `conjugation/Deinflector.kt` | The same rules run in reverse for deinflection search, and the English result labels. |

Unit tests live in `app/src/test/`. They're plain JVM tests and can't use Android classes such as `SQLiteDatabase`.

## 2. Navigation and screen state

- **The back stack** is `AppModel.stack`, a list of `Screen` objects. `push` adds one, and `pop` removes one (system back and the top-bar ← both call it). On the root screen, back isn't intercepted, so Android closes the app.
- **Each screen is a state object** (`SearchState`, `EntryState`) that holds everything the screen shows: query, results, `LazyListState`, selected tab, `ScrollState`s, loaded data. Composables read and write that object instead of using `remember`, so returning to a screen restores it exactly.
- **`App()` wraps each screen in `key(screen)`,** so `remember`ed helpers such as `FocusRequester` belong to one history entry.
- **Adding a new kind of screen** (e.g. the kanji page) takes three steps: a class implementing `Screen` that holds its state, a composable that takes it, and a branch in `App()`'s `when`. Then add an `AppModel` method that pushes it.
- **Lifetime:** the stack lives in a ViewModel, so it survives configuration changes but not process death.

## 3. Database access and threading

- `AppModel` opens the `Dictionary` once at startup. Screens receive it as a nullable parameter, which is null until it's ready, and show "Preparing dictionary…" until then.
- `Dictionary` methods block. Call them inside `withContext(Dispatchers.IO)`, and put the results into the screen's state object.
- **Two rules prevent a startup hang** where the dictionary loads but the screen never learns it's ready:
  - `MainActivity` creates `AppModel` in `onCreate`, before `setContent`. It must not be created lazily during the first composition.
  - `AppModel` opens the database on `Dispatchers.IO` but assigns the `dictionary` state on the main thread.

  Keep both rules whenever new Compose state is written from background work.

## 4. Search

- **Japanese queries** go through these tiers in order: exact key, whole-word fuzzy key, deinflected, prefix key, fuzzy prefix key. All but the deinflected tier are a `GLOB` on `jp_keys`. Results are de-duplicated across tiers and capped at 100.
- **The deinflected tier** runs `Deinflector` on the query, looks up every candidate dictionary form as an exact key (kind 0), and keeps an entry only if `entries.pos` contains the candidate's class. It sorts in Kotlin (common, then rank) and sets `SearchResult.inflection` to the label.
- **Any input without Japanese characters** is an English query, matched against `en_keys` in three tiers.
- **Every result** carries a `FormMatch` (which form or reading matched, and how many characters to highlight) and its readings' pitch patterns.
- `pipeline/query.rb` runs the same tiers against the built database from the command line, except the deinflected tier (that would need a Ruby copy of the conjugation rules). Keep the two in sync when the other tiers change. Deinflection is covered by `DeinflectorTest`.

## 5. Conjugation engine

- **Rules are data:** each `Rule` says that a word of one class ending in `from` becomes one ending in `to` through a list of `Inflection`s, giving a word of `resultClass` (or a final form when that's null). Classes are JMdict POS codes, plus three internal ones: `NAI` (a verb's plain negative, which conjugates like an い-adjective and adds ないで), `MASU` and `TE_FORM` (which takes いる and ください).
- **Forwards:** each Forms tab cell is one or more inflection chains (e.g. Past, negative = NEGATIVE then PAST). `Conjugation.conjugate` applies a chain rule by rule.
- **Backwards:** `Deinflector.deinflect` undoes any rule whose `to` ends the word, then keeps undoing rules whose `resultClass` is the class just reached, up to six steps. Each dictionary-class state it reaches is a candidate.
- **Adding a class or form:** add rules (and a table row if it's a new form). `DeinflectorTest.reverseRegeneratesTables` checks that every generated cell deinflects back.

## 6. Conventions

- **Japanese text** uses the theme's typography, which sets the Japanese locale (`2026-10-26-dev-environment.md` §7).
- **Grammar labels** come from `Dictionary.labels(codes)`, which also folds `vs` into 他動詞（する）/自動詞（する）. Never show raw JMdict codes or English POS names.
- **Logging:** use info level with a short tag (`Log.i("Search", …)`). The device's log buffer rolls over quickly (`2026-10-26-dev-environment.md` §5).
