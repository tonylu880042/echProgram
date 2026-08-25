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
  labels, and restrained 2–6px corner radii. This is hardware software, not a
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
- **Structural Line** (`#253842`) — 1px dividers and quiet outlines.
- **Echelon Cyan** (`#28A8FF`) — the sole accent for CTA, focus, selected
  navigation, current segment, and active data trace.
- **Cyan Wash** (`rgba(40, 168, 255, 0.14)`) — selected surface tint only; it is
  not a second accent.

Do not use gradients, neon purple/blue glows, pure black, or hue-shifting gray
surfaces. If a workout chart has two metrics, use line style, opacity, dash
pattern, and a labeled legend before introducing another color.

## 3. Typography Rules

- **Display / workout values:** `Geist`, `ui-sans-serif`, `system-ui`, sans-serif;
  weight 650–750, tracking `-0.02em`, controlled rather than oversized.
- **Body / controls:** `Geist`, `ui-sans-serif`, `system-ui`, sans-serif; 14–16px
  minimum, relaxed 1.45 line-height, and 65ch maximum for explanatory copy.
- **Telemetry / numbers:** `Geist Mono`, `ui-monospace`, `SFMono-Regular`, monospace;
  tabular numerals, uppercase micro-labels at 10–12px, and clear unit suffixes.
- Headings use weight and contrast for hierarchy. Never solve hierarchy with a
  huge all-caps headline or six-line wraps.
- Use sentence case for explanatory copy and concise all-caps only for console
  labels (`TIME REMAINING`, `NEXT UP`, `EFFORT`).

`Inter` is banned for this system. Generic serif fonts are banned in the
console. Minimum body size is 14px; minimum interactive label size is 12px.

## 4. Component Stylings

### Navigation rail

Desktop uses a 64px left rail with line icons, an active cyan rule, and a text
label visible on the goal library. Mobile moves navigation into a 44px+ top bar
or a labeled menu; never hide the current location behind an unlabeled icon.

### Goal and program tiles

Use an asymmetric grid: one large hero tile may share a row with two compact
tiles, followed by a horizontal program strip on wide screens. Each tile has a
clear title, promise, duration/range metadata, and a low-contrast profile line.
The whole tile is a keyboard target, but its icon is a line icon with an
accessible label — never an emoji. Selected tiles use the cyan wash and a 1px
cyan edge; inactive tiles stay carbon-neutral.

### Buttons and controls

Primary action is a solid Echelon Cyan rectangle with 4px radius, steel text,
and a tactile `transform: translateY(1px)` press state. Secondary actions are
carbon surfaces with structural lines. Focus uses a 2px cyan outline with 2px
offset. Every tap target is at least 44×44px.

Speed/incline controls show the value first, unit second, and `−`/`+` controls
as labeled buttons. Do not rely on color alone to show an active state.

### Charts and live telemetry

Charts are compact instrument panels with explicit axis units, current marker,
next-segment label, and visible profile. Animate only the trace/marker with
transform or opacity; do not animate layout. Important values stay in the
telemetry column so users never have to infer them from a graph.

### Inputs, switches, and states

Labels sit above controls. Helper text follows the control; errors sit below it
and state what to do next. “Adapt to you” is an explicit switch with `on/off`
text. Loading uses structural skeletons matching the final shape, never a
generic spinner. Empty states explain how to create or save a program. Errors
are inline, concise, and safe: stop/hold commands must be visible when device
state is unknown.

## 5. Layout Principles

- Use a max-width 1440px console frame with 24–40px outer padding on desktop.
- Use Compose grid primitives (`LazyVerticalGrid`/`LazyRow`) for major layout.
  Landscape library is a 12-column-inspired grid; live workout is a dominant
  center chart plus a narrow telemetry rail.
- Every element occupies its own spatial zone. Do not overlap text, charts,
  controls, or decorative images.
- Use a left-aligned/asymmetric hero with a single dominant goal action. A
  centered hero and a generic row of three equal cards are prohibited.
- Use 4px base spacing; common gaps are 8, 12, 16, 24, and 32px. Keep panel
  padding predictable so the moving user can form muscle memory.
- Full-height surfaces use the Compose window insets and available height; do
  not hard-code a fixed screen height that breaks landscape system bars.
- Charts and controls may scroll only inside a clearly labeled region; page-level
  horizontal overflow is forbidden.

## 6. Responsive Rules

- Mobile-first collapse below 768px: multi-column grids become one column, and
  the telemetry rail moves below the current value.
- Preserve the current value, next segment, and stop/end action above the fold
  at every width. Do not make a user scroll to stop a workout.
- Headings use `clamp()` with a controlled upper bound; body text stays at least
  14px. Keep labels short rather than shrinking type.
- Touch targets remain at least 44×44px; adjacent speed/incline controls need an
  8px separation.
- The desktop rail collapses into a labeled touch menu at narrow widths; no
  icon-only navigation.
- Reduce vertical gaps with `clamp(24px, 5vw, 64px)` while preserving grouping.
- Honor `prefers-reduced-motion: reduce` by removing perpetual loops and keeping
  state changes instantaneous but still explicit.

## 7. Motion & Interaction

- Default interaction feel: spring-like `stiffness: 100`, `damping: 20` where a
  motion library is used; otherwise use a short cubic-bezier approximation.
- Animate only `transform` and `opacity`. Never animate `top`, `left`, `width`,
  or `height` to communicate a program state.
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
