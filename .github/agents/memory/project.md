# Project Memory — Gadgetbridge (shared)

Durable facts about the codebase that all agents rely on. Keep entries short and factual. Update as you learn. Remove stale/incorrect entries.

## Overview
- **App**: Gadgetbridge — Android app to use Bluetooth gadgets (mostly smartwatches) without vendor apps/accounts. It supports MANY devices, but our work is scoped to ONE.
- **Repo**: codeberg.org/Freeyourgadget/Gadgetbridge. Package `nodomain.freeyourgadget.gadgetbridge`.
- **UI goal**: The app UI is being **completely redesigned from scratch**, taking inspiration from the **Garmin workout app**.

## Device focus (IMPORTANT — scope all work to this)
- **Target device: Mi Band Pulse 1s** (a Xiaomi Mi Band). This is the ONLY device we care about.
- The `workout` branch commits are all focused on this device's **workout** feature.
- The Mi Band Pulse 1s is handled by the **generic MiBand support** (there is no dedicated `*Pulse1s*` class). Key files:
  - `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/devices/miband/` — `MiBandCoordinator.java`, `MiBandService.java`, `MiBandConst.java`, sample providers, FW helpers.
  - `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/activities/workouts/` — the new workout feature (Kotlin): `MiBandWorkoutHubActivity.kt`, `MiBandWorkoutSessionFragment.kt`, `MiBandWorkoutDetailActivity.kt`, `MiBandWorkoutHistoryFragment.kt`, `MiBandWorkoutManager.kt`, `MiBandWorkoutHistoryStore.kt`, `MiBandWorkoutPagerAdapter.kt`.
  - Workout layouts: `activity_miband_workout_hub.xml`, `activity_miband_workout_detail.xml`, `fragment_miband_workout_session.xml`, `fragment_miband_workout_history.xml`, `item_workout_history.xml`.
- **Do NOT** add features, fix bugs, or redesign UI for other devices (Garmin, Fossil, Pebble, Bangle.js, etc.). If a change would affect other devices, keep it minimal and flag it.

## Tech stack
- **Language**: Java + Kotlin (mixed). Newer feature code (workouts) is Kotlin.
- **UI**: XML layouts (`app/src/main/res/layout/`), Material Components, Activities/Fragments.
- **SDK**: `minSdk 23`, `targetSdk 34`, `compileSdk 36`. Java toolchain 21, source/target 17.
- **Build**: Gradle — Groovy `app/build.gradle` + Kotlin DSL root (`build.gradle.kts`, `settings.gradle.kts`). Version catalog `gradle/libs.versions.toml`.
- **DB**: GreenDAO (generator in `GBDaoGenerator/`). **Protobuf**: `app/src/main/proto/`.
- **Flavors**: `main`, `mainline`, `nightly`, `banglejs`, `banglejsNightly`, `nopebble`.

## Key locations
- Root Java/Kotlin: `app/src/main/java/nodomain/freeyourgadget/gadgetbridge/`
  - `activities/` — all Activities/Fragments. `activities/workouts/` — workout feature (Kotlin).
  - `devices/` — device implementations. `database/`, `entities/`, `model/` — data layer.
  - `service/` — background services. `util/`, `prefs/`, `capabilities/`.
- Layouts: `app/src/main/res/layout/` (e.g. `activity_miband_workout_*.xml`, `fragment_workout_*.xml`, `workout_charts.xml`, `activity_main.xml`).
- Workout UI files (current, to be redesigned): `activities/workouts/` — `MiBandWorkoutHubActivity.kt`, `MiBandWorkoutSessionFragment.kt`, `WorkoutDetailsActivity.kt`, `WorkoutListActivity.kt`, etc.
- Main shell: `activities/ControlCenterv2.java` + `activity_main.xml` (DrawerLayout + NavigationView).

## Conventions (confirm & extend as you go)
- Newer feature code prefers **Kotlin**.
- Resource naming: `activity_*`, `fragment_*`, `item_*`, `dialog_*`.
- Build/verify commands: `./gradlew :app:compileDebugKotlin`, `:app:compileDebugJavaWithJavac`, `:app:lintDebug`.

## Git
- Current feature branch: `workout`. Orchestrator owns commits (per logical task, imperative messages). No push/branch-switch without explicit user request.

## Gotchas
- (none recorded yet — add build/lint/structure gotchas here as discovered)
