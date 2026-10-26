# Development environment

Date: 2026-10-26

How the app is built, installed and checked. Everything runs from the command line. Android Studio isn't installed and isn't needed.

---

## 1. Project identity

| Setting | Value |
|---|---|
| App name (launcher) | 豆字引 (`app/src/main/res/values/strings.xml`) |
| Application ID / namespace | `com.alanmacdougall.mamejibiki` |
| Gradle root project | `mamejibiki` |
| Source root | `app/src/main/java/com/alanmacdougall/mamejibiki/` |

The application ID is permanent. Changing it makes Android treat the build as a different app.

## 2. Toolchain (macOS, Apple Silicon)

| Tool | Location / version |
|---|---|
| JDK | Homebrew `openjdk@21`, at `/opt/homebrew/opt/openjdk@21` |
| Android SDK | `~/Library/Android/sdk` |
| SDK packages | `cmdline-tools;latest`, `platform-tools` (adb 37.0.1), `platforms;android-37.0`, build-tools 36.1.0 and 37.0.0 |
| Gradle | Wrapper pinned to 9.8.2 (`./gradlew`). Homebrew `gradle` is installed but only needed to regenerate the wrapper. |

The shell profile doesn't set these variables. Every command that needs them sets them itself:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
```

`local.properties` (gitignored) holds `sdk.dir` for Gradle.

## 3. Build configuration

- AGP 9.4.1. AGP compiles Kotlin itself, so there's no separate `org.jetbrains.kotlin.android` plugin. The Compose compiler plugin is `org.jetbrains.kotlin.plugin.compose` 2.4.21.
- Kotlin JVM toolchain 21.
- `compileSdk` 37, `targetSdk` 37, `minSdk` 33.
- Jetpack Compose with Material 3, versioned through the Compose BOM 2026.09.00.
- Dependency versions live in `gradle/libs.versions.toml`.
- Gradle configuration cache and build cache are enabled in `gradle.properties`.

## 4. Test device

- **Phone:** Pixel 10 Pro (`blazer`), Android 17 (API 37), 1080×2410.
- **Installed apps that matter:**
  - AnkiDroid (`com.ichi2.anki`), with the user's real collection and Kaishi 1.5k note type.
  - AnkiConnect Android (`com.kamwithk.ankiconnectandroid`).
  - Gboard and FUTO Keyboard, for testing live search on uncommitted IME composition text.
- **Connection:** wireless debugging, already paired with this Mac. adb finds the phone through mDNS (`adb devices` lists it as `adb-57171FDCH00755-…._adb-tls-connect._tcp`), so `adb connect` isn't needed.
- **If the phone is missing from `adb devices`:** wireless debugging has turned itself off (it does this after a timeout or a network change). The user switches it back on under Settings → System → Developer options → Wireless debugging. Pairing doesn't need repeating.
- **If adb reports `protocol fault`:** a stale adb server from an older SDK may be running. Check with `ps aux | grep fork-server`, kill it, then run `adb start-server`.

## 5. Build → install → check loop

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.alanmacdougall.mamejibiki/.MainActivity
adb exec-out screencap -p > <scratch>/shot.png    # then downscale, e.g. sips -Z 1000
adb logcat -s Search:I AndroidRuntime:E            # app logs and crashes (this Pixel filters out Log.d, so use Log.i)
adb shell input tap X Y / input text … / uiautomator dump   # drive the UI
```

Screenshots are the main way to check UI work, because the user doesn't read the code. Debug builds use the auto-generated debug signing key. There's no Play Store distribution and no release signing.

## 6. App-wide conventions

- **Japanese glyph shapes:** text that contains Japanese is marked with the Japanese locale (Compose `LocaleList("ja")`). Otherwise the phone's English locale can render Han-unified kanji (直, 骨, …) in their Chinese forms.
- **Theme:** Material 3, with light and dark colour schemes following the system setting.

## 7. Data pipeline

- **What it does:** a Ruby program in `pipeline/` reads the source datasets and writes one SQLite database to `app/src/main/assets/mamejibiki.sqlite`.
- **Built so far:** the words half (JMdict, JPDB frequency, Wadoku pitch, JmdictFurigana).
- **Still to add:** the kanji half (KANJIDIC2 from jmdict-simplified's `kanjidic2-en` JSON release, and KanjiVG).
- **Commands, sources, schema and entry JSON shape:** see `pipeline/README.md`.
- **Where it runs:** only on the dev machine, never on the phone.
- **When it runs:** only when a source dataset is updated or the schema changes. A build takes about 30 s. Ordinary app builds reuse the existing database file.
- **Size:** about 136 MB on disk and about 70 MB compressed in the APK. That's acceptable for a sideloaded personal app.
- **Why Ruby:** the Send to AnkiDroid field formats must match `../anki-scripts` exactly. The app builds those fields in Kotlin at send time, and golden test vectors generated with anki-scripts' own Ruby code (`pitch_accent_renderer.rb`, the grammar-label table, the dedup key) prove they match.
- **Ruby setup:** Ruby 3.4.11 through rbenv (`pipeline/.ruby-version`), with dependencies in `pipeline/Gemfile` managed by Bundler.
- **Shared normalization:** query normalization is implemented twice, in Ruby for building keys and in Kotlin for queries. Both implementations are tested against the vectors in `shared/*.json`.
- **What stays out of git:** the downloaded datasets (`pipeline/data/`) and the generated database. The pipeline can rebuild both.
- **How the app uses the database:** it opens the shipped database read-only. SQLite can't open a file that's still compressed inside the APK, so the app copies it from assets into app storage on first launch, and again whenever `meta.schema_version` or `meta.built_at` changes.
