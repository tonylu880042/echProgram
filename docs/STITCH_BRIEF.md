# Google Stitch brief — Echelon treadmill console

This is a screen-generation brief, not an instruction to operate Google Stitch
from the code workspace. Paste the master prompt only into an authorized Stitch
workflow. Keep `idea.txt`, `design/idea.png`, and `DESIGN.md` as the product and
visual references.

## Shared screen contract

All twelve screens belong to one Echelon dark treadmill console. Keep the same
64px desktop rail, carbon surfaces, 1px structural lines, Geist/Geist Mono
typography, single Echelon Cyan accent (`#28A8FF`), 4px corner radius, and
explicit telemetry units. The console must feel like hardware software: dense,
precise, calm, and immediately scannable while the user is moving.

Use line icons with text labels. Do not use emojis as icons even where the
source idea uses emoji shorthand. Do not invent extra accent colors; use cyan
opacity, line weight, dash style, and labels to differentiate data. Never use
pure black, purple/neon gradients, generic dashboard cards, centered marketing
heroes, or overlapping layers. The UI is responsive: below 768px collapse to a
single column, keep the stop/end action visible, and preserve 44px touch
targets. All screens need loading, empty, error, selected, disabled, and reduced-
motion considerations where applicable.

## Screen breakdown

### 1. Program Library / Categories

- **Purpose:** Begin with the customer’s reason for getting on the treadmill.
- **Information hierarchy:** page title “WHAT DO YOU WANT TODAY?”, four hero
  tiles in priority order — FAT BURN, GLUTE BLAST, VERTICAL, SURPRISE ME — then
  supporting goal filters and an “All programs” strip.
- **Primary interaction:** select a goal tile; keyboard focus and selected state
  are visible; “View all” reveals the long-tail programs.
- **States:** first visit, selected goal, no matching programs, catalog loading,
  catalog error, and a saved/favorite indicator.
- **Layout note:** asymmetric hero grid with one dominant tile, compact rail,
  and profile traces; no generic equal three-card row.

### 2. Program Detail / Overview

- **Purpose:** Explain what the selected workout will feel like before starting.
- **Information hierarchy:** title/promise, duration, speed range, incline
  range, profile chart, segment list, and primary “MAKE IT YOURS” / “START
  WORKOUT” actions.
- **Primary interaction:** inspect segments, preview profile, enter settings, or
  start the default plan.
- **States:** plan loading, ready, missing device capability, unavailable
  program, and favorite/saved.
- **Layout note:** left title/summary with a wide profile chart; keep units and
  machine limits explicit.

### 3. Make It Yours / Settings

- **Purpose:** Adjust the plan within safe treadmill and user constraints.
- **Information hierarchy:** selected goal, duration, intensity, max speed, max
  incline, focus (more incline/balanced/more speed), and “Adapt to you” switch.
- **Primary interaction:** choose values, toggle adaptation, then start workout.
- **States:** default, custom values, adaptation on/off, value at device limit,
  invalid combination, and unavailable heart-rate data.
- **Layout note:** stacked control rows with value/units and helper copy; no
  hidden advanced settings.

### 4. Live Workout Screen

- **Purpose:** Make the current action, next change, and safe controls legible at
  a glance.
- **Information hierarchy:** time remaining, current speed and incline, current
  segment, profile chart/current marker, next change, heart rate, calories, and
  pause/end controls.
- **Primary interaction:** pause/resume, effort −/+, manual speed/incline
  adjustment, and end workout.
- **States:** active, paused, adapting, heart-rate connected/disconnected,
  device command pending, and emergency/unknown device state.
- **Layout note:** dominant current values over a chart, narrow telemetry rail,
  fixed end action.

### 5. Upcoming Change Countdown

- **Purpose:** Give the user a calm, unmistakable countdown before the next
  speed/incline change.
- **Information hierarchy:** time remaining, “CLIMB 2 OF 7”/current segment,
  countdown number, next speed/incline, profile marker, and heart rate.
- **Primary interaction:** cancel/hold the upcoming change or accept it.
- **States:** countdown active, accepted, held, delayed by manual override,
  command timeout, and paused.
- **Layout note:** large countdown ring/number paired with the next-step panel;
  retain current workout context.

### 6. Override / Personalized Mode

- **Purpose:** Make the consequence of a manual adjustment explicit without
  punishing the user.
- **Information hierarchy:** personalized mode label, current values, next
  programmed segment, profile trace, effort control, and pause/end.
- **Primary interaction:** adjust speed/incline; choose “follow program” or stay
  personalized.
- **States:** manual override, follow-program restored, adaptation responding,
  value clamped at safety limit, and device rejection.
- **Layout note:** same live layout as Screen 4 with an unmistakable mode label;
  avoid modal takeover during movement.

### 7. Workout Complete / Results

- **Purpose:** Close the session with a restrained, evidence-based result.
- **Information hierarchy:** completion status, selected program, duration,
  distance, calories, elevation, average telemetry, Echelon Score, and any real
  personal best.
- **Primary interaction:** do it again, view summary, save program, or done.
- **States:** complete, save pending, unsynced/offline, no heart-rate data, and
  no personal-best comparison.
- **Layout note:** data-first result panel; celebration is typographic and
  minimal, with no confetti or emoji.

### 8. Workout Summary / Details

- **Purpose:** Provide a detailed post-workout breakdown for reflection and
  progress.
- **Information hierarchy:** overview/charts/segments tabs, duration, distance,
  calories, elevation, average speed/incline/heart rate, profile charts, score.
- **Primary interaction:** switch tabs, inspect segment data, share/export only
  through an approved boundary.
- **States:** full telemetry, partial telemetry, chart loading, empty heart-rate
  series, and export unavailable.
- **Layout note:** tabular labels beside charts; charts are supporting evidence,
  not the only representation.

### 9. Saved Programs / My Programs

- **Purpose:** Let returning users quickly resume saved and custom workouts.
- **Information hierarchy:** tabs (all/favorites/custom), “create new”, program
  rows with duration/profile metadata, favorite state, and overflow actions.
- **Primary interaction:** open, favorite/unfavorite, edit, duplicate, or remove
  a saved program with confirmation.
- **States:** populated, first-use empty state, filtering, sync pending, offline,
  and deletion confirmation.
- **Layout note:** dense rows with profile traces and labels; no card wall.

### 10. Build Your Own / Create Program

- **Purpose:** Compose a custom workout from explicit speed/incline blocks.
- **Information hierarchy:** duration, estimated distance, ordered block list,
  block values, repeat control, preview chart, add block, and save.
- **Primary interaction:** add/edit/reorder/delete blocks, repeat a group, save,
  and preview.
- **States:** empty editor, editing, invalid block, device limit warning, unsaved
  changes, and save success/error.
- **Layout note:** two-column editor/preview on desktop, single-column ordered
  workflow on mobile; never hide units.

### 11. Surprise Me

- **Purpose:** Turn an approachable time + effort desire into a generated plan.
- **Information hierarchy:** time choices (10/20/30/45/60 min), desired effort
  (easy/sweat/burn/hard/anything), generated preview, and one “SURPRISE ME” CTA.
- **Primary interaction:** choose constraints, generate, accept/regenerate, or
  inspect the preview before starting.
- **States:** untouched, constraints selected, generating, generated, unavailable
  machine capability, and generation error with retry.
- **Layout note:** one giant clearly labeled action; no random decorative dice or
  emoji icon. Use a line icon and supporting text if a visual cue is needed.

### 12. Heart Rate Mode / Train by HR

- **Purpose:** Keep the user in a selected heart-rate zone with transparent
  adaptation and safe fallback.
- **Information hierarchy:** target zone, current zone/status, time remaining,
  current speed/incline, profile chart, heart-rate telemetry, and end workout.
- **Primary interaction:** connect/select a compatible sensor, choose a target
  zone, accept or pause automatic adaptation, end workout.
- **States:** connected/in zone, too low, too high, signal lost, sensor pairing,
  adaptation paused, and device limit reached.
- **Layout note:** show target and current states as text plus labeled telemetry;
  never rely on color alone or imply medical advice.

## Cross-screen flow

```text
1 Library
  ├─ goal/program → 2 Detail → 3 Make It Yours → 4 Live
  │                                      ├─ next change → 5 Countdown → 4 Live
  │                                      └─ manual change → 6 Personalized → 4 Live
  ├─ surprise me → 11 Surprise Me → 3 Make It Yours → 4 Live
  ├─ heart rate → 12 Heart Rate Mode → 4 Live
  └─ saved/custom → 9 Saved Programs → 2 Detail or 10 Build Your Own
4 Live → 7 Complete → 8 Summary → 9 Saved / 4 Live again / Done
```

Every transition preserves the selected goal and safe device limits. Back
navigation must not discard unsaved settings without a clear confirmation.

## Master prompt for Google Stitch

```text
Design a responsive 12-screen Echelon treadmill console from the attached
concept reference. Treat it as hardware software for a moving user: dark,
precise, cockpit-dense, calm, and scannable in under one second. Start with the
customer goal, not treadmill jargon. The four hero programs on Screen 1 must be
FAT BURN, GLUTE BLAST, VERTICAL, and SURPRISE ME, in that order. FAT BURN means
sustained calorie-burning work and must not make a medical claim. Use line icons
with text labels; never use emojis as UI icons.

Use the Echelon Console / Runline design system: canvas #071016, carbon surfaces
#0C171E and #12232C, primary text #E5EDF2, secondary #A4B3BD, muted #6D7D88,
structural line #253842, and exactly one interactive accent #28A8FF with opacity
variants only. Use Geist for UI text and Geist Mono for telemetry. Use 4px
corners, 4px spacing multiples, 1px rules, explicit units, 44dp minimum touch
targets, and a 64dp desktop navigation rail. The hero layout is asymmetric;
the live screen is a dominant chart/current-value panel with a narrow telemetry
rail. Compose implementation should use grid/lazy layout primitives and honor
landscape system insets; the visual reference is 16:9 and touchscreen-first.

Generate these screens and preserve the shared shell: (1) goal-first program
library, (2) program detail/overview, (3) make it yours, (4) live workout, (5)
upcoming change countdown, (6) override/personalized mode, (7) workout complete,
(8) workout summary, (9) saved programs, (10) build your own, (11) surprise me,
and (12) heart-rate mode. For every screen define loading, empty, error,
selected, disabled, and reduced-motion behavior where relevant. Show current
speed/incline, units, next segment, safe stop/end action, and accessible labels.

Animate only transform and opacity with restrained spring-like feedback; do not
animate layout properties. On narrow/portrait fallback widths, collapse to one
column, move the telemetry below the current value, keep the stop/end action
above the fold, and retain 44dp controls. Do not use centered marketing heroes,
three equal cards,
overlapping layers, pure black, gradients, purple/neon glows, Inter, generic
serifs, fake metrics, filler scroll prompts, confetti, custom cursors, or AI
copywriting clichés. Produce a coherent instrument panel, not twelve unrelated
dashboard templates.
```
