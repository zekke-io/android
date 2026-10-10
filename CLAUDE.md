# Zekke for Android — agent guide

Read [README.md](README.md) and [tasks.md](tasks.md) before changing anything here; the plan is [../mobile/android.md](../mobile/android.md).

## Design

**Every screen is drawn from the Zekke design system in [../design-system](../design-system/README.md).** Before building or changing UI, read:

1. [../design-system/README.md](../design-system/README.md) — the voice, colour, type, layout, motion and the security rules the interface carries;
2. [../design-system/platforms.md](../design-system/platforms.md) § Android — how each token maps to `ZekkePalette`, `ZekkeTypography`, `ZekkeShapes` and Material 3;
3. the component's file in [../design-system/components](../design-system/components) — what the screen provides, its tokens, and the composable that implements it;
4. [ui/README.md](ui/README.md) — the Compose implementation, its tests and screenshots.

`ui` is the implementation of that system and the only place it lives in code:

- **Screens use `ui`'s components and tokens only**: `Zekke.palette`, `MaterialTheme.typography`, `MaterialTheme.shapes`, `R.drawable.ic_*` and `R.drawable.mark_*`. No `Color(0x…)`, font size or corner radius inside a `feature` module.
- **A missing component or token is added to `ui` and to `../design-system`** in the same change — its `components/<Name>.md` or `tokens.json` entry — never written inline in a screen.
- **Icons are converted from `../design-system/assets`**, never drawn by hand: outline icons to `ui/src/main/res/drawable/ic_<name>.xml` (black, tinted at use), feature marks to `mark_<name>.xml` (their own gradient, drawn with `Image`).
- **A visual change to `ui` re-records its screenshots** (`./gradlew :ui:recordRoborazziDebug`) and the contrast pairs in `PaletteContrastTest` cover any new colour.
- **The copy follows the brand book's voice**: sentence case, verbs on buttons, *this phone*, one message for a wrong PIN, no emoji.

## Working on a task

**Load the `android-feature-work` skill** (`.claude/skills/android-feature-work/SKILL.md`) before
implementing, reviewing or closing any task here: the web app's outcomes and copy, the lifecycle
holds, the focus rules, the tests a task needs and how to verify it end to end.

## Conventions

- No comments in code; each module's `README.md` holds its documentation.
- Git: read diffs freely; commit messages are a short description of what changed, without a
  co-author line or mention of internal docs and tasks; never push without asking.
