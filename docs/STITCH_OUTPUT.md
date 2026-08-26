# Stitch output

The live Stitch project is [Echelon treadmill console](https://stitch.withgoogle.com/projects/13520170002351782106).

The supplied export is tracked, without extracting its web prototype into the
runtime or source tree:

`design/stitch_echelon_android_treadmill_console.zip`

The archive contains twelve screen folders. Each folder includes both
`code.html` and `screen.png`:

1. `1._goal_library`
2. `2._program_detail`
3. `3._make_it_yours`
4. `4._live_workout`
5. `5._change_countdown`
6. `6._personalized_mode`
7. `7._workout_complete`
8. `8._workout_summary`
9. `9._saved_programs`
10. `10._build_your_own`
11. `11._surprise_me`
12. `12._heart_rate_mode_train_by_hr`

The export's visual guidance is included inside the archive at
`echelon_tactical_instrument/DESIGN.md`. Gate 1 uses its carbon tonal surfaces,
cyan accent, compact rules, small corner radii, and accessible 48dp targets.

The reference PNGs are 1600 × 1280 (5:4). The Android implementation targets
the console's 16:9 landscape viewport instead: the program library uses
`BoxWithConstraints` to render four all-program columns on a wide landscape
surface and two columns on a narrower landscape surface. This preserves the
reference hierarchy without hard-coding the export's 5:4 canvas.

## Gate 2 Android/Compose adaptation notes

Gate 2 adapts Stitch screens 2 and 3 as pure Compose state surfaces while
keeping the shared console chrome from Gate 1:

- Screen 2 (`2._program_detail`) is `ProgramDetailScreen`. It keeps the
  customer-safe Fat Burn promise, the 45-minute default, `2.8–5.5 MPH`, and
  `1.0–12.0%` ranges. The ordered segment profile is rendered as a flat
  Canvas/module visualization rather than shipping the Stitch web prototype or
  a screenshot into the app. `MAKE IT YOURS` and `START WORKOUT` are explicit
  48dp callback seams.
- Screen 3 (`3._make_it_yours`) is `ProgramPersonalizationScreen`. Duration,
  intensity, maximum speed, maximum incline, focus, and Adapt to You are typed
  UDF actions. The screen displays current values and units, keeps field errors
  beside their controls, and offers a projected trajectory plus selected-plan
  summary. It uses 48dp stepper/toggle/action targets and only uses the error
  color for validation feedback.
- `ProgramSetupScreen`/`ProgramSetupRoute` map immutable ViewModel states to
  loading, detail, personalization, unavailable/error recovery, and the
  restrained `WORKOUT READY` handoff. `MainActivity` is the composition root
  that wires the static detail adapter, device capability input, and temporary
  Gate 3 starter seam.
- The shared rail/header consumes Compose safe-drawing insets. `BoxWithConstraints`
  selects a two-column wide-landscape arrangement and a scrollable compact
  arrangement; no 5:4 export dimensions are hard-coded.

The current runtime scope is Gate 1's program library plus the Gate 2 detail
and Make It Yours setup journey. `WORKOUT READY` proves the validated setup
boundary only; live workout UI, treadmill commands, and Gate 3 device
integration are intentionally out of scope. Gate 1 is runtime-verified on an
Android 36 emulator; Gate 2's final runtime review remains with Sol.
