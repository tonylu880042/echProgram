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
