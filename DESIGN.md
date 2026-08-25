# Design System: Echelon Console / Runline

## 1. Visual Theme & Atmosphere

An industrial, night-time Android treadmill console: cockpit-dense but legible,
calibrated for a user who is moving and needs to understand the next decision in
under a second. The visual language is **dark control-room utility** with
measured glow-free cyan focus, crisp telemetry lines, and intentional negative
space around the current workout state.

- Density: **8/10 — Cockpit Dense** for live workout screens; **6/10** for the
  goal library so the customer can scan without decoding firmware labels.
- Variance: **6/10 — Offset Asymmetric**. Use a left rail, an asymmetric hero
  composition, and one dominant current-state panel; never a centered marketing
  hero.
- Motion: **5/10 — Fluid Compose**. Motion communicates treadmill state and control
  feedback; it never competes with the workout.
- Material: thin structural rules, matte charcoal surfaces, compact telemetry
  labels, and restrained 2–6dp corner radii. This is hardware software, not a
  wellness scrapbook.

The customer’s goal is always the first readable noun. Use the four hero
promises from the concept: **FAT BURN**, **GLUTE BLAST**, **VERTICAL**, and
**SURPRISE ME**. “Fat Burn” describes sustained calorie-burning effort; it does
not promise a unique physiological effect.

`design/idea.png` is the primary 12-screen storyboard. `design/home2.png`
refines the goal-first Programs landing screen, `design/home.png` refines the
Fat Burn detail/configuration screen, and `design/profile.png` refines the
profile-chart language. These references are visual inputs only; the shipped
runtime is a landscape Android/Compose console and must preserve touch targets,
system insets, and accessibility semantics.

## 2. Color Palette & Roles

There is exactly one interactive accent. Opacity changes of that accent are
allowed; introducing another hue for decoration is not.

- **Console Void** (`#071016`) — page canvas and outer frame; never pure black.
- **Carbon Surface** (`#0C171E`) — primary panel and side rail fill.
- **Raised Carbon** (`#12232C`) — focused tile, control well, and chart frame.
- **Steel Text** (`#E5EDF2`) — primary text, values, and active labels.
- **Instrument Text** (`#A4B3BD`) — secondary copy and chart labels.
- **Muted Steel** (`#6D7D88`) — metadata, helper text, disabled labels.
- **Structural Line** (`#253842`) — 1dp dividers and quiet outlines.
- **Echelon Cyan** (`#28A8FF`) — the sole accent for CTA, focus, selected
  navigation, current segment, and active data trace.
- **Cyan Wash** (`rgba(40, 168, 255, 0.14)`) — selected surface tint only; it is
  not a second accent.

Do not use gradients, neon purple/blue glows, pure black, or hue-shifting gray
surfaces. If a workout chart has two metrics, use line style, opacity, dash
pattern, and a labeled legend before introducing another color.

## 3. Typography Rules

- **Display / workout values:** `Geist` when the approved brand font is bundled
  as an Android font resource; otherwise `sans-serif` as the foundation
  fallback. Weight 650–750, track-tight, controlled rather than oversized.
- **Body / controls:** `Geist` when approved and bundled, otherwise Android
  `sans-serif`; 14–16sp
  minimum, relaxed 1.45 line-height, and 65ch maximum for explanatory copy.
- **Telemetry / numbers:** `Geist Mono` when approved and bundled, otherwise
  Android `monospace`;
  tabular numerals, uppercase micro-labels at 10–12sp, and clear unit suffixes.
- **Brand-font note:** Geist/Geist Mono are design targets, not dependencies in
  the foundation. Licensing, packaging under `res/font`, and fallback metrics
  must be approved before adding font assets.
- Headings use weight and contrast for hierarchy. Never solve hierarchy with a
  huge all-caps headline or six-line wraps.
- Use sentence case for explanatory copy and concise all-caps only for console
  labels (`TIME REMAINING`, `NEXT UP`, `EFFORT`).

`Inter` is banned for this system. Generic serif fonts are banned in the
console. Minimum body size is 14sp; minimum interactive label size is 12sp.

## 4. Component Stylings

### Navigation rail

The supported console landscape uses a 64dp left rail with line icons, an active
cyan rule, and a text label visible on the goal library. A narrow Android window
uses a 44dp+ top bar or a labeled menu; never hide the current location behind
an unlabeled icon.

### Goal and program tiles

Use an asymmetric grid: one large hero tile may share a row with two compact
tiles, followed by a horizontal program strip on wide screens. Each tile has a
clear title, promise, duration/range metadata, and a low-contrast profile line.
The whole tile is a keyboard target, but its icon is a line icon with an
accessible label — never an emoji. Selected tiles use the cyan wash and a 1dp
cyan edge; inactive tiles stay carbon-neutral.

### Buttons and controls

Primary action is a solid Echelon Cyan rectangle with 4dp radius, steel text,
and a tactile `graphicsLayer { translationY = 1.dp.toPx() }` press state.
Secondary actions are carbon surfaces with structural lines. Focus uses a 2dp
cyan outline with 2dp offset. Every touch target is at least 44×44dp.

Speed/incline controls show the value first, unit second, and `−`/`+` controls
as labeled buttons. Do not rely on color alone to show an active state.

### Charts and live telemetry

Charts are compact instrument panels with explicit axis units, current marker,
next-segment label, and visible profile. Animate only the trace/marker with
`Modifier.graphicsLayer` translation/alpha; do not animate layout. Important
values stay in the
telemetry column so users never have to infer them from a graph.

### Inputs, switches, and states

Labels sit above controls. Helper text follows the control; errors sit below it
and state what to do next. “Adapt to you” is an explicit switch with `on/off`
text. Loading uses structural skeletons matching the final shape, never a
generic spinner. Empty states explain how to create or save a program. Errors
are inline, concise, and safe: stop/hold commands must be visible when device
state is unknown.

## 5. Layout Principles

- Use a max-width 1440dp console frame with 24–40dp outer padding in the
  supported landscape window.
- Use Compose grid primitives (`LazyVerticalGrid`/`LazyRow`) for major layout.
  Landscape library is a 12-column-inspired grid; live workout is a dominant
  center chart plus a narrow telemetry rail.
- Every element occupies its own spatial zone. Do not overlap text, charts,
  controls, or decorative images.
- Use a left-aligned/asymmetric hero with a single dominant goal action. A
  centered hero and a generic row of three equal cards are prohibited.
- Use 4dp base spacing; common gaps are 8, 12, 16, 24, and 32dp. Keep panel
  padding predictable so the moving user can form muscle memory.
- Full-height surfaces use the Compose window insets and available height; do
  not hard-code a fixed screen height that breaks landscape system bars.
- Charts and controls may scroll only inside a clearly labeled region; page-level
  horizontal overflow is forbidden.

## 6. Responsive Rules

- Treat `WindowWidthSizeClass.Expanded` as the primary landscape console mode.
  At compact/medium widths (or a `BoxWithConstraints` width below 840dp),
  multi-column grids become one column and the telemetry rail moves below the
  current value.
- Preserve the current value, next segment, and stop/end action above the fold
  at every width. Do not make a user scroll to stop a workout.
- Headings choose bounded `TextUnit` tokens from the current window class; body
  text stays at least 14sp. Keep labels short rather than shrinking type.
- Touch targets remain at least 44×44dp; adjacent speed/incline controls need an
  8dp separation.
- The expanded rail collapses into a labeled touch menu at narrow widths; no
  icon-only navigation.
- Reduce vertical gaps through window-class spacing tokens (24dp compact,
  32–64dp expanded) while preserving grouping. Honor the Android system
  animator duration scale and an explicit `reducedMotion` presentation flag by
  removing perpetual loops and keeping state changes instantaneous but explicit.

## 7. Motion & Interaction

- Default interaction feel: Compose spring-like `stiffness: 100`, `damping: 20`
  where an animation is used; otherwise use a short `tween` with the platform
  animator duration scale. Render motion through `graphicsLayer` translation
  and alpha. Never animate layout size/position modifiers to communicate a
  program state.
- On entry, stagger goal tiles by 40–60ms so the scan path is evident; do not
  delay safety controls or the primary action.
- Live workout has a quiet marker pulse for the current segment and a subtle
  trace update. The marker must remain legible with reduced motion enabled.
- Active controls get a tactile press state. Avoid bouncing, confetti, and
  perpetual decoration unrelated to workout progress.

## 8. Anti-Patterns — NEVER DO

- Never use emojis anywhere in UI copy or as icons; use line icons plus text.
- Never use `Inter`, generic serif fonts, or fake brand names.
- Never use pure black (`#000000`), purple/neon gradients, outer glow shadows,
  oversaturated accents, or gradient text.
- Never use centered hero sections, overlapping elements, or a 3-column equal
  card row as the primary information architecture.
- Never use `calc()` percentage hacks for layout or page-level horizontal scroll.
- Never hide stop/end controls, active values, errors, or device safety state.
- Never display fake round achievements such as `99.99%`, `50%`, or an invented
  “new PR” without a real comparison.
- Never use copy clichés such as “Elevate”, “Seamless”, “Unleash”, or “Next-Gen”.
- Never use filler text like “Scroll to explore”, bouncing chevrons, or custom
  mouse cursors.
- Never load broken remote image links or rely on imagery to explain a workout
  state. The console should work as a labeled instrument panel.
