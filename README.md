# Echelon treadmill console

Android/Kotlin foundation for a landscape treadmill touchscreen built with
Jetpack Compose and Clean Architecture. The current increment is a walking
skeleton: it wires a static goal-first program catalog into a single-activity
Compose shell that exposes **Fat Burn**, **Glute Blast**, **Vertical**, and
**Surprise Me**. The twelve production screens are planned, but not implemented
yet; Gate 1 is the next slice.

## Prerequisites and verified toolchain

- JDK 17 (verified locally with Temurin `17.0.11`)
- Android SDK platform/build tools 36
- Gradle wrapper 8.13
- AGP 8.13.2, Kotlin 2.2.21, compile/target SDK 36
- Compose BOM 2025.04.01

The official target baseline is newer (AGP 9.1.1 / Gradle 9.3.1 / SDK 37 / BOM
2026.08.00). The checked-in versions are the highest locally verifiable
combination because this workspace currently has SDK 36 and cached AGP 8.13.2.
See the toolchain audit and upgrade path in [`docs/SPEC.md`](docs/SPEC.md).

## Build and test

On this macOS workspace, select JDK 17 before invoking Gradle:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
./gradlew clean test lint architectureCheck :app:assembleDebug check
```

The clean gate covers pure JVM tests, Android lint, import-direction checking,
debug packaging, and the aggregate `check` task. No emulator/device is
currently connected, so instrumentation tests and manual Compose rendering are
deferred until an Android target is available; this is intentionally not
claimed as runtime verification.

## Architecture

```text
presentation (Compose) ───────→ application/usecase ─────→ domain
data (adapters) ──────────────→ application/usecase ─────→ domain
MainActivity (composition root): wires presentation + data
```

Domain code is Android-free. Use cases depend on contracts, `data` provides the
static adapter, and presentation receives the use-case result. Run
`./gradlew architectureCheck` to verify forbidden import directions.

## Project guidance

- [`AGENTS.md`](AGENTS.md) — Luna implementation and Sol review gates
- [`docs/SPEC.md`](docs/SPEC.md) — objective, boundaries, commands, testing, and
  official Android sources
- [`docs/PLAN.md`](docs/PLAN.md) — staged implementation and acceptance gates
- [`DESIGN.md`](DESIGN.md) — Echelon Console visual system for Android/Compose
- [`docs/STITCH_BRIEF.md`](docs/STITCH_BRIEF.md) — 12-screen Stitch handoff

## Source references

The product source material is tracked read-only: [`idea.txt`](idea.txt),
[`design/idea.png`](design/idea.png), [`design/home2.png`](design/home2.png),
[`design/home.png`](design/home.png), and [`design/profile.png`](design/profile.png).
`idea.png` is the primary 12-screen storyboard; the other images are secondary
visual references. Google Stitch is not operated by the code workflow.
