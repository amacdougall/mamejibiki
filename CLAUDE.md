# 豆字引 (mamejibiki)

A personal, offline Japanese dictionary app for Android (Kotlin + Jetpack Compose). The user vibecodes it: they test on their phone and don't read the code, so check work with builds, logs and screenshots from the device.

## Specs

The `plans/` directory is the spec. Read it before starting work:
- `plans/2026-10-26-ui-notes.md`: UI and behaviour specification.
- `plans/2026-10-26-feature-research.md`: how AnkiDroid integration, the KanjiVG component breakdown and stroke-practice matching work.
- `plans/2026-10-26-dev-environment.md`: toolchain, test device, and the build → install → screenshot loop.

## Writing plan docs

Docs in `plans/` describe only the current decisions. Leave out decision history ("we originally planned…", "the user changed X"), struck-through ideas and discarded options. When a decision changes, rewrite the affected section so it states the new decision as if it had always been the plan. Keep an open question only while it's genuinely open, and remove it once it's answered.

Why: future agents read these docs as the spec, and process history confuses them about what's actually wanted.
