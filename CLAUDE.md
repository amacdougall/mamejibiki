# 豆字引 (mamejibiki)

A personal, offline Japanese dictionary app for Android (Kotlin + Jetpack Compose). The user vibecodes it: they test on their phone and don't read the code, so check work with builds, logs and screenshots from the device.

## Specs

The `plans/` directory is the spec. Read it before starting work:
- `plans/2026-10-26-ui-notes.md`: UI and behaviour specification.
- `plans/2026-10-26-feature-research.md`: how AnkiDroid integration, the KanjiVG component breakdown and stroke-practice matching work.
- `plans/architecture.md`: how the app's code is organised (back stack, screen state objects, database access and threading rules).
- `plans/2026-10-26-dev-environment.md`: toolchain, test device, the build → install → screenshot loop, driving the phone from adb, and running tests.
- `plans/progress.md`: what's done and what remains, compared with the spec. Read it first to find the next task. Update it at the end of every work session.

## Ending a session

Develop and check work with the debug build. Once the user says they're satisfied, uninstall the debug build, build a new release APK with a bumped version, install it on the phone and check that it runs. The steps are in `plans/2026-10-26-dev-environment.md` §5, under "End of every dev session".

## Commits

Commit directly to `main`, and only when the user asks.

## Writing plan docs

Docs in `plans/` describe only the current decisions. Leave out decision history ("we originally planned…", "the user changed X"), struck-through ideas and discarded options. When a decision changes, rewrite the affected section so it states the new decision as if it had always been the plan. Keep an open question only while it's genuinely open, and remove it once it's answered.

Why: future agents read these docs as the spec, and process history confuses them about what's actually wanted.
