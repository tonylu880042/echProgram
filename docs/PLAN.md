# Echelon Android implementation plan

This plan keeps each increment buildable and reviewable for the Android
landscape-oriented treadmill console. Luna implements one slice at a time; Sol
reviews the diff and evidence, reports findings, and decides whether Luna
proceeds to the next slice.

## Gate 0 — foundation (this increment)

Deliver:

- repository guidance, product specification, implementation plan, and Stitch
  brief;
- a Kotlin/Gradle Android single-activity Compose shell with strict checks;
- Clean Architecture packages and a checked dependency direction;
- a static `ProgramCatalog` contract/adapter and `ListHeroPrograms` use case;
- an accessible shell exposing four hero goals, without building twelve screens.

Acceptance:

- RED test was run before the missing use case/shell existed;
- GREEN tests and available Gradle gates pass;
- any SDK/JDK mismatch is documented rather than hidden;
- no source-material files changed;
- Sol confirms the next slice.

## Gate 1 — goal-first program library

Implement Screen 1 from `docs/STITCH_BRIEF.md`:

- category selection (“What do you want today?”);
- four hero tiles: Fat Burn, Glute Blast, Vertical, Surprise Me;
- supporting goals and a program list;
- keyboard/focus behavior for physical/touch input and landscape responsive
  layout;
- Compose semantics and 44dp minimum touch targets.

Acceptance: goal selection is tested as user-visible behavior, data comes from
the application boundary, and the screen matches `DESIGN.md` without emoji
icons.

## Gate 2 — program detail and personalization

Implement Screens 2 and 3:

- program promise, expected profile, duration, speed/incline ranges;
- duration/intensity/focus controls;
- max speed and incline safety limits;
- “Adapt to you” opt-in state and a clear start action.

Acceptance: settings validation is domain-tested; controls are accessible in
landscape and touch-friendly; the selected plan is passed to the live session
use case.

## Gate 3 — live workout and adaptive overrides

Implement Screens 4, 5, and 6:

- time remaining, current speed/incline, next segment, profile chart, effort
  control, pause/end actions;
- upcoming-change countdown;
- explicit personalized mode after a manual speed/incline override;
- no unsafe command is sent outside the device capability contract.

Acceptance: deterministic session-state tests cover countdown, pause/resume,
override, and device-limit rejection; Compose tests cover primary controls;
landscape layout keeps the stop/end action visible.

## Gate 4 — completion and detailed results

Implement Screens 7 and 8:

- completion result that stays restrained and data-first;
- summary metrics, segment/profile charts, heart-rate data, and Echelon Score;
- “new personal best” copy only when a real comparison supports it;
- estimate caveats beside calories/elevation where required.

Acceptance: result calculations are unit-tested; no fake round numbers or
unearned PR claims; summary is usable in a 16:9 display and in accessibility
font-scale settings.

## Gate 5 — saved, custom, generated, and heart-rate modes

Implement Screens 9–12:

- saved programs with favorites and create-new affordance;
- block-based Build Your Own editor;
- Surprise Me constraints (time + desired effort) and generated-plan preview;
- Zone 2/heart-rate mode with signal-loss and too-high/too-low states.

Acceptance: persistence and heart-rate capabilities are contracts with fakes in
tests; generated plans are reproducible in tests; signal loss is safe and
visible; all flows have empty/loading/error states.

## Gate 6 — hardening and release readiness

- Android screenshot/UI verification at the supported landscape breakpoints;
- Compose semantics/accessibility tree, touch order, contrast, and reduced-motion
  checks;
- performance check for chart rendering, recomposition, and animation budget;
- security/privacy review for profile, heart-rate, and device data;
- update docs and unresolved questions with decisions;
- Sol final review and release decision.

## Per-gate handoff checklist

Luna reports:

1. changed files and scope exclusions;
2. architecture boundary decisions;
3. RED command and failure, then GREEN and REFACTOR evidence;
4. `./gradlew test`, `./gradlew :app:testDebugUnitTest`, `./gradlew lint`,
   `./gradlew :app:assembleDebug`, and `./gradlew check` results;
5. commit hash and open risks, including toolchain blockers.

Sol reports findings by severity, confirms design/spec alignment, and chooses
`proceed`, `fix`, or `clarify` as the next action.

