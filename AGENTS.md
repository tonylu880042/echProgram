# Echelon treadmill console engineering guide

This repository is the implementation workspace for the Android Echelon
treadmill console described in `idea.txt` and visualized in
`design/idea.png`. Those two files, together with the secondary visual
references `design/home2.png`, `design/home.png`, and `design/profile.png`, are
read-only source material. Do not edit, rename, move, or regenerate them.

## Ownership and review gate

- **Luna** writes the implementation in small, reviewable increments.
- **Sol** reviews the diff, test evidence, architecture, and design alignment;
  Sol reports findings and chooses the next implementation step.
- A change is not accepted until the Sol review gate is green.
- Sol must not silently expand the task while reviewing. Findings become a
  follow-up increment or an explicit open question.

## Clean Architecture boundaries

The dependency rule points inward. Enterprise policy must not know about
Compose, Android framework APIs, device SDKs, storage, HTTP, or a concrete
vendor integration.

```text
app/src/main/kotlin/com/echelon/console/
├── domain/                 entities, value objects, business rules
├── application/usecase/    application orchestration and ports
├── data/                   concrete repositories and external mappers
├── presentation/           Compose UI, state holders, and ViewModels
└── MainActivity.kt         composition root (single-activity shell)
```

Allowed direction:

| Layer | May depend on | Must not depend on |
| --- | --- | --- |
| `domain` | Kotlin/JVM standard library | Android, Compose, storage, HTTP, `data`, `presentation` |
| `application/usecase` | `domain`, use-case contracts | Compose, `data` concretes, Android framework |
| `data` | `domain`, application contracts, Android/vendor APIs | `presentation` |
| `presentation` | `application/usecase`, domain read models, Compose/ViewModel APIs | `data` constructors, direct storage/device calls |
| `MainActivity`/composition root | all layers required to wire dependencies | business rules hidden in the activity |

Rules that keep the boundary visible:

1. Use cases receive dependencies through small contracts. Do not import a
   concrete repository from `application/usecase`.
2. Compose functions receive state and event lambdas from a ViewModel or
   presentation contract. Do not call a repository, `DataStore`, `Room`, HTTP,
   or treadmill SDK directly from a composable.
3. Domain types contain product language, not Android resources, Compose colors,
   or layout modifiers.
4. Keep mapping at boundaries. An API response, persistence record, device
   event, or heart-rate sample is translated before it reaches a use case.
5. `MainActivity` is a composition root, not a second application layer.
6. Every new outer adapter needs a focused boundary test and a use-case test for
   the behavior it enables.

The first walking skeleton wires one read path:
`StaticProgramCatalog -> ListHeroPrograms -> ProgramLibraryShell`. It proves
the direction and app boot path without pretending that the twelve screens
already exist.

## TDD gate: RED -> GREEN -> REFACTOR

Behavioral changes follow this order, and the evidence must be included in the
handoff:

1. **RED**: write a specific failing test first; run it and record the command
   and failure showing the missing behavior.
2. **GREEN**: add the smallest implementation that makes the test pass; run the
   focused test and the full suite.
3. **REFACTOR**: improve names or structure without changing behavior; rerun
   the tests after each refactor.

Build configuration and documentation may precede the first test when required
to make Gradle boot. Do not use a screenshot as proof of domain behavior.
Prefer real implementations over mocks; fake only slow, non-deterministic, or
externally mutating boundaries.

Required local checks for an implementation increment:

```bash
./gradlew test
./gradlew :app:testDebugUnitTest
./gradlew lint
./gradlew :app:assembleDebug
./gradlew check
```

`./gradlew check` is the complete local quality gate. When Android SDK or Gradle
versions differ from the official baseline, record the actual versions and the
upgrade path in `docs/SPEC.md`; never claim an unrun task passed.

## Naming and code style

- Kotlin uses explicit nullability and compiler warnings treated as errors where
  practical. Keep domain tests pure JVM.
- Use `PascalCase` for types and composables, `camelCase` for functions and
  values, and `UPPER_SNAKE_CASE` only for true constants.
- Name use cases with a verb (`listHeroPrograms`, `startWorkout`), contracts
  with a capability (`ProgramCatalog`), and domain types with a business noun
  (`HeroProgram`).
- Keep one responsibility per file; do not add an abstraction before a second
  concrete use case needs it.
- Prefer immutable `data class` models, `val`, `List`/`PersistentList` at
  boundaries, and sealed interfaces for workout state.
- Keep product decisions and invariants in domain/application code. Keep visual
  tokens, Android resources, and layout behavior in presentation.
- No emojis as UI icons. Use a line icon set or accessible text labels.

## Scope and prohibited actions

- Do not implement all twelve screens in the foundation increment. Follow
  `docs/PLAN.md` and ship one vertical slice at a time.
- Do not operate Google Stitch from this repository. `docs/STITCH_BRIEF.md` is
  the handoff prompt for an authorized design workflow.
- Do not add a database, auth, analytics, or device integration until the
  corresponding boundary and acceptance criteria are approved.
- Do not introduce a dependency without a concrete use and a test strategy.
- Do not commit secrets, `local.properties`, `.env` files, build output, or
  personal data.
- Do not edit `idea.txt` or anything under `design/` in implementation work.
- Preserve unrelated user changes; do not reset, force-push, or rewrite history.

## Increment handoff format

Every Luna increment reports:

```text
CHANGED: files and one-line intent
ARCHITECTURE: boundary decisions and dependencies
TDD: RED command/failure, GREEN command/result, REFACTOR result
VERIFICATION: unit tests, Android tests, lint, type/build checks
COMMIT: hash and message
RISKS: unresolved questions or explicit none
```

