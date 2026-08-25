# Echelon treadmill console — foundation specification

## Objective

Build a consumer-friendly Echelon treadmill experience that starts with the
customer's goal instead of an exercise-program vocabulary. The first surface
answers **“What do you want today?”** and routes the user toward a workout
promise such as sustained calorie output, glute-focused hills, a vertical
challenge, or a generated session.

The product language in `idea.txt` is the behavioral source of truth. The
12-screen storyboard in `design/idea.png` is the primary information-architecture
and visual source. `design/home2.png`, `design/home.png`, and
`design/profile.png` are secondary visual references for the goal-first library,
detail/configuration treatment, and program-profile charts. The first code
increment is deliberately an Android walking skeleton, not a production
implementation of those screens.

## Tech Stack

- Kotlin/JVM for domain and use-case code.
- Android application module with a single `Activity` and Jetpack Compose UI.
- Gradle Kotlin DSL and Android Gradle Plugin (AGP).
- Kotlin/JUnit for pure JVM domain/use-case tests; Android/Compose tests are
  added only when a screen behavior needs them.
- Android lifecycle-aware state (`ViewModel` + `StateFlow`) for future screens;
  no state-management framework in the foundation increment.
- The design target is Geist/Geist Mono, but no font asset or font dependency is
  bundled in this increment. Use Android `sans-serif`/`monospace` fallbacks until
  brand licensing and `res/font` packaging are approved.
- No backend, database, authentication, charting library, device SDK, or heart
  rate integration in this increment.

## Official sources

Framework and tooling decisions are grounded in these official Android
Developers references:

- Architecture recommendations: <https://developer.android.com/topic/architecture/recommendations>
- Compose Bill of Materials and compiler relationship: <https://developer.android.com/develop/ui/compose/bom>
- Android Gradle Plugin 9.1.1 compatibility: <https://developer.android.com/build/releases/agp-9-1-0-release-notes>
- Android testing overview: <https://developer.android.com/training/testing>
- Compose UI testing and semantics: <https://developer.android.com/develop/ui/compose/testing>
- Robolectric local-test strategies: <https://developer.android.com/training/testing/local-tests/robolectric>

The local toolchain below intentionally uses older cached versions; these
references are the upgrade authority before moving to the official baseline.

### Local toolchain audit (2026-08-26)

The requested official baseline is compile/target SDK 37, AGP 9.1.1, Gradle
9.3.1, JDK 17, and Compose BOM 2026.08.00. This workspace currently exposes
Android SDK platforms through **36**, Gradle **9.3.0** on PATH (with a cached
Gradle **8.13** distribution), JDK **24.0.1**, cached AGP up to **8.13.2**, and
cached Compose artifacts around **1.8.0**. The foundation therefore targets
`compileSdk 36`/`targetSdk 36`, AGP `8.13.2`, Gradle `8.13`, Kotlin `2.2.21`,
and the locally available Compose BOM `2025.04.01` so that the project can be
validated here. Upgrade to the requested baseline when SDK 37 and its
dependencies are installed; no product API depends on this temporary choice.

If the Android plugin cannot run on the local JDK, the pure JVM tests remain the
authoritative fallback and the exact Gradle blocker must be reported.

## Commands

```bash
# This workspace has JDK 17 installed alongside a newer default JDK.
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"

./gradlew test                       # all JVM unit tests
./gradlew :app:testDebugUnitTest    # app module JVM tests
./gradlew architectureCheck         # Clean Architecture import direction
./gradlew lint                      # Android lint
./gradlew :app:assembleDebug        # compile/package the walking skeleton
./gradlew check                     # complete local quality gate
```

When the Android SDK is not installed, run the domain tests with the Gradle
wrapper and report the Android task as blocked; do not substitute a Web build.

## Project Structure

```text
.
├── AGENTS.md
├── DESIGN.md
├── docs/
│   ├── PLAN.md
│   ├── SPEC.md
│   └── STITCH_BRIEF.md
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/echelon/console/
│       │   │   ├── MainActivity.kt
│       │   │   ├── application/usecase/
│       │   │   ├── data/
│       │   │   ├── domain/
│       │   │   └── presentation/
│       │   └── res/values/
│       └── test/                     # pure JVM TDD tests
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/                    # reproducible Gradle wrapper
├── settings.gradle.kts
└── gradlew
```

The composition root (`MainActivity`) constructs the concrete static catalog,
passes it to the use case, and hands the result to the Compose shell. Future
screens should preserve this seam and move state into a lifecycle-aware
ViewModel when it becomes interactive.

## Code Style — real foundation shape

The domain model has no Android imports:

```kotlin
// app/src/main/java/com/echelon/console/domain/HeroProgram.kt
data class HeroProgram(
    val id: ProgramId,
    val title: String,
    val promise: String,
)
```

The use case depends on an application contract, not on a concrete data class:

```kotlin
// app/src/main/java/com/echelon/console/application/usecase/ListHeroPrograms.kt
fun interface ProgramCatalog {
    fun listHeroPrograms(): List<HeroProgram>
}

class ListHeroPrograms(private val catalog: ProgramCatalog) {
    operator fun invoke(): List<HeroProgram> = catalog.listHeroPrograms()
}
```

The concrete static catalog remains in `data`, and Compose receives the
use-case result through a presentation boundary. No composable constructs a
repository or calls an Android API.

## Testing Strategy

The test pyramid starts with deterministic pure Kotlin tests and adds Android
tests only when behavior crosses a UI/device boundary:

1. Pure JVM use-case tests verify program identifiers, required hero ordering,
   and product invariants without an Android runtime.
2. Use-case tests use the real static catalog or a tiny in-memory fake to verify
   application behavior; interaction mocks are avoided.
3. Compose UI tests verify accessible text, focus, touch targets, and state
   transitions once a real screen is introduced.
4. Instrumentation tests are reserved for Android lifecycle, persistence,
   device, or sensor behavior.
5. Every behavior change records RED, GREEN, and REFACTOR evidence. Tests do
   not skip or disable pending behavior.

The foundation intentionally has no Compose UI behavior test, emulator run, or
manual rendering claim. The static shell is compile/package-verified; accessible
interaction tests and real-device verification begin with Gate 1.

Foundation acceptance tests:

- the use case returns the four goal-first hero programs in the required order;
- the Compose shell source exposes those labels and the app name, with runtime
  UI verification deferred to Gate 1;
- the shell does not instantiate data infrastructure inside a composable;
- Android lint, unit tests, and debug assemble pass when the local SDK supports
  them.

## Boundaries

### Domain

Own program identifiers, goal categories, workout promises, and invariants such
as hero ordering. No Android, Compose, resource, or storage imports.

### Application/usecase

Orchestrate intents such as listing goals, configuring a workout, starting a
session, recording an override, and completing a workout. Contracts describe
catalog, workout-plan, telemetry, persistence, and heart-rate capabilities
without selecting a provider.

### Data

Translate and implement external data sources. Concrete local storage, APIs,
treadmill controls, and heart-rate integrations belong here. The first
increment uses only a static catalog so it is deterministic and offline.

### Presentation

Compose owns layout, semantics, screen state, responsive 16:9/touchscreen
behavior, and design tokens. ViewModels expose lifecycle-aware `StateFlow` when
state survives recomposition. Presentation calls use cases; it does not manage
treadmill motor commands or persistence directly.

### Safety and product language

“Fat Burn” is a customer-facing goal label, not a medical or physiological
guarantee. Copy should describe sustained calorie-burning work and include
normal estimate caveats where calories are shown. Device safety limits and
heart-rate decisions must be validated before any automatic-control feature is
implemented.

## Success Criteria

The foundation increment succeeds when:

- a fresh `./gradlew test` completes successfully;
- `./gradlew :app:assembleDebug` completes when SDK 36 is available;
- the single-activity Compose shell boots without a runtime crash;
- the shell exposes the four hero goals in this order: Fat Burn, Glute Blast,
  Vertical, Surprise Me;
- no twelve-screen production UI is implied by the skeleton;
- Clean Architecture import direction is enforced by package structure and a
  focused architecture test/check;
- documentation gives Luna an unambiguous next slice and gives Sol a review
  gate with evidence to inspect;
- `idea.txt` and all files under `design/` remain byte-for-byte untouched.

## Open Questions

1. Which treadmill models and firmware APIs define the safe speed/incline
   envelope for automatic adjustments?
2. Which heart-rate sources are officially supported, and what is the fallback
   when the signal drops or is unavailable?
3. Does the product need an authenticated profile and cloud sync in the first
   release, or can saved programs begin as local-only?
4. Which Android icon set and font delivery method are approved for the final
   console?
5. Are `Vertical` and `Climb` the same customer goal, or should the library
   expose both labels with different promises?
6. What accessibility hardware/input constraints exist on the treadmill display
   (touch, physical keys, voice, contrast mode, reduced motion)?
7. Which metrics are estimates versus machine-measured values, and what legal
   copy is required beside calories, elevation, and Echelon Score?
