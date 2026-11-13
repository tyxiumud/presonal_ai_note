# Local Text Journal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first usable milestone of Personal AI Vault: users can write text notes, assign today or a past date, save them locally, and see them again after restarting the app.

**Architecture:** Keep the existing single `app` module. A stateless Compose screen renders `JournalUiState`; `JournalViewModel` owns draft/date state and observes a `CaptureRepository`; a Room-backed repository persists records. Store database-friendly primitive values in `CaptureEntity` and expose date/time types in the domain model.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose Material 3, AndroidX Lifecycle 2.11.0, Room 2.8.4 with KSP 2.3.13, Kotlin coroutines/Flow 1.11.0, JUnit 4, AndroidX instrumented tests.

**Spec:** `docs/superpowers/specs/2026-11-04-personal-ai-vault-design.md`

## Global Constraints

- Keep the project as one Android `app` module.
- Preserve `minSdk = 24`, `targetSdk = 37`, and `compileSdk = 37`.
- Original records remain local and are never replaced by derived AI content.
- A new record defaults to today; the user may select today or a past date, never a future date.
- Persist both content time and import time; group records by the content date.
- Saving local text must never call a network API.
- Do not add AI, images, audio, background work, navigation, cloud sync, or vector search in this milestone.
- Use Chinese user-facing copy and keep implementation boundaries explainable to a beginner.
- Do not log private note text.

## Review Focus

- Blank or whitespace-only input: Save remains disabled and the repository receives no record; pinned by `JournalViewModelTest.blankDraftCannotBeSaved`.
- Future date selection: the ViewModel rejects it and keeps the previous valid date; pinned by `JournalViewModelTest.futureDateIsRejected`.
- Date conversion near timezone boundaries: the chosen calendar date remains the `dayKey`; pinned by `RoomCaptureRepositoryTest.pastDateUsesSelectedDayKeyInPositiveTimezone`.
- Multiple records with identical content time: ordering remains deterministic using import time and ID; pinned by `CaptureDaoTest.recordsAreOrderedDeterministically`.
- Process/database reopen: previously saved records remain queryable; pinned by `CaptureDatabasePersistenceTest.recordSurvivesDatabaseReopen`.

---

## File Structure

### Existing files to modify

- `gradle/libs.versions.toml` — add Room, Lifecycle Compose, KSP, coroutines-test, and desugaring coordinates.
- `build.gradle.kts` — declare the KSP plugin without applying it globally.
- `app/build.gradle.kts` — apply KSP, enable core-library desugaring, add production/test dependencies, and export Room schemas.
- `app/src/main/java/io/github/tyxiumud/personalainote/MainActivity.kt` — replace the template greeting with the journal route.
- `app/src/main/AndroidManifest.xml` — register the custom `Application`.
- `app/src/main/res/values/strings.xml` — replace the template name and add journal UI text.
- `app/src/test/java/io/github/tyxiumud/personalainote/ExampleUnitTest.kt` — remove the generated placeholder after real unit tests exist.
- `app/src/androidTest/java/io/github/tyxiumud/personalainote/ExampleInstrumentedTest.kt` — remove the generated placeholder after real instrumented tests exist.

### New production files

- `app/src/main/java/io/github/tyxiumud/personalainote/PersonalVaultApplication.kt` — application-scoped container owner.
- `app/src/main/java/io/github/tyxiumud/personalainote/AppContainer.kt` — constructs the Room database and repository without a DI framework.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/model/Capture.kt` — UI/domain representation of a saved text capture.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/CaptureEntity.kt` — Room primitive storage model and entity/domain mapping.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/CaptureDao.kt` — insert and day-scoped ordered queries.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/PersonalVaultDatabase.kt` — Room database definition and builder.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/CaptureRepository.kt` — repository contract.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/RoomCaptureRepository.kt` — Room-backed repository implementation.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalViewModel.kt` — draft, selected date, validation, save action, and observed records.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalScreen.kt` — stateless screen, date picker, input, save action, and timeline.
- `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalPreview.kt` — stable light/dark previews using fixed sample data.

### New test and teaching files

- `app/src/test/java/io/github/tyxiumud/personalainote/MainDispatcherRule.kt` — deterministic Main dispatcher for ViewModel tests.
- `app/src/test/java/io/github/tyxiumud/personalainote/journal/ui/JournalViewModelTest.kt` — state, validation, selected-date, and save tests.
- `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/data/CaptureDaoTest.kt` — DAO filtering and deterministic ordering.
- `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/data/RoomCaptureRepositoryTest.kt` — entity mapping and timezone/day-key behavior.
- `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/data/CaptureDatabasePersistenceTest.kt` — close/reopen persistence test.
- `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/ui/JournalScreenTest.kt` — Compose semantics and event tests.
- `app/schemas/` — Room-exported schema JSON.
- `docs/tutorial/01-local-text-journal.md` — beginner explanation of the milestone’s data flow.

---

### Task 1: Room persistence and repository

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/model/Capture.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/CaptureEntity.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/CaptureDao.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/PersonalVaultDatabase.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/CaptureRepository.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/data/RoomCaptureRepository.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/data/CaptureDaoTest.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/data/RoomCaptureRepositoryTest.kt`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/data/CaptureDatabasePersistenceTest.kt`
- Delete after replacements pass: `app/src/androidTest/java/io/github/tyxiumud/personalainote/ExampleInstrumentedTest.kt`

**Interfaces:**
- Produces: `data class Capture(val id: Long, val text: String, val contentAt: Instant, val importedAt: Instant, val updatedAt: Instant, val dayKey: LocalDate)`
- Produces: `interface CaptureRepository { fun observeByDay(day: LocalDate): Flow<List<Capture>>; suspend fun addText(text: String, contentDate: LocalDate, now: ZonedDateTime): Long }`
- Produces: `CaptureDao.observeByDay(dayKey: String): Flow<List<CaptureEntity>>`
- Produces: `CaptureDao.getByDay(dayKey: String): List<CaptureEntity>`
- Produces: `PersonalVaultDatabase.create(context: Context): PersonalVaultDatabase`
- Consumes: Existing `app` module, Kotlin/Compose build.

- [ ] **Step 1: Add exact dependency aliases and build configuration**

Use stable versions: Room `2.8.4`, KSP `2.3.13`, Lifecycle `2.11.0`, coroutines-test `1.11.0`, and `desugar_jdk_libs 2.0.2`. Add `room-runtime`, `room-ktx`, `room-compiler`, `room-testing`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, and `kotlinx-coroutines-test`. Configure KSP schema output to `$projectDir/schemas`.

- [ ] **Step 2: Write failing DAO and persistence tests**

In `CaptureDaoTest`, add:
- `queryReturnsOnlySelectedDay`: insert one `2026-11-03` and one `2026-11-04` entity; assert the `2026-11-03` query returns only the first.
- `recordsAreOrderedDeterministically`: insert equal `contentAtEpochMillis` with different import times/IDs; assert descending content time, import time, then ID.

In `CaptureDatabasePersistenceTest.recordSurvivesDatabaseReopen`, insert into a named test database, close it, reopen the same database, and assert text and `dayKey` remain.

- [ ] **Step 3: Run instrumented tests to verify they fail**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest`

Expected: FAIL because the Room entity, DAO, database, and dependencies do not exist yet.

- [ ] **Step 4: Implement primitive Room storage**

Create `CaptureEntity` with table `captures`, an index on `dayKey`, auto-generated `id`, constants `type = "TEXT"` and `processingState = "RAW"`, text, epoch-millisecond timestamps, and ISO day key. Implement DAO SQL ordering as `contentAtEpochMillis DESC, importedAtEpochMillis DESC, id DESC`. Use database version 1 with `exportSchema = true`.

- [ ] **Step 5: Implement domain mapping and repository**

Map epoch values through `Instant`; map day keys through `LocalDate`. In `addText`, trim text, combine `contentDate` with `now.toLocalTime()` in `now.zone`, preserve `importedAt = now.toInstant()`, and insert one TEXT/RAW entity.

- [ ] **Step 6: Add repository timezone test**

In `RoomCaptureRepositoryTest.pastDateUsesSelectedDayKeyInPositiveTimezone`, use `Asia/Shanghai`, selected date `2026-10-31`, and now `2026-11-04T00:15+08:00`; assert persisted `dayKey == "2026-10-31"` and the record is not returned for `2026-11-04`.

- [ ] **Step 7: Run database tests**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest`

Expected: all three Room test classes PASS and schema version 1 is exported.

- [ ] **Step 8: Commit persistence milestone**

```powershell
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/java/io/github/tyxiumud/personalainote/journal app/src/androidTest/java/io/github/tyxiumud/personalainote app/schemas
git commit -m "feat: add local capture persistence"
```

---

### Task 2: Journal ViewModel and date policy

**Files:**
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalViewModel.kt`
- Create: `app/src/test/java/io/github/tyxiumud/personalainote/MainDispatcherRule.kt`
- Create: `app/src/test/java/io/github/tyxiumud/personalainote/journal/ui/JournalViewModelTest.kt`
- Delete after replacements pass: `app/src/test/java/io/github/tyxiumud/personalainote/ExampleUnitTest.kt`

**Interfaces:**
- Consumes: `CaptureRepository.observeByDay(day: LocalDate)`
- Consumes: `CaptureRepository.addText(text: String, contentDate: LocalDate, now: ZonedDateTime)`
- Produces: `data class JournalUiState(val selectedDate: LocalDate, val draft: String, val captures: List<Capture>, val isSaving: Boolean, val validationMessage: String?)`
- Produces: `JournalViewModel.onDraftChanged(value: String)`
- Produces: `JournalViewModel.onDateSelected(date: LocalDate)`
- Produces: `JournalViewModel.save()`
- Produces: `JournalViewModel.uiState: StateFlow<JournalUiState>`

- [ ] **Step 1: Write the failing ViewModel tests**

Use a fake in-memory `CaptureRepository` and fixed `Clock` at `2026-11-04T10:30:00+08:00`.

Tests:
- `startsOnTodayAndObservesToday`: assert selected date `2026-11-04`.
- `blankDraftCannotBeSaved`: enter whitespace, call save, assert repository insert count is zero and validation message is non-null.
- `futureDateIsRejected`: select `2026-11-05`, assert selected date remains `2026-11-04`.
- `pastDateIsObservedAndSaved`: select `2026-10-31`, enter text, save, assert repository received that date.
- `successfulSaveClearsDraft`: assert draft becomes empty only after the repository call succeeds.
- `saveFailureKeepsDraft`: make the fake throw, assert text remains and a message is exposed.

- [ ] **Step 2: Run unit tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "io.github.tyxiumud.personalainote.journal.ui.JournalViewModelTest"`

Expected: FAIL because `JournalViewModel`, `JournalUiState`, and dispatcher rule do not exist.

- [ ] **Step 3: Implement ViewModel state flow**

Use `MutableStateFlow` for selected date, draft, saving state, and message. Use `flatMapLatest` to switch repository observation when the chosen date changes and `combine` to publish one immutable `JournalUiState`. Validate in the ViewModel even though the UI also disables invalid actions.

- [ ] **Step 4: Implement save behavior**

Trim only the persisted text; do not mutate the displayed draft before success. Reject blank input and dates after `LocalDate.now(clock)`. Call `repository.addText(trimmed, selectedDate, ZonedDateTime.now(clock))` in `viewModelScope`. Clear draft on success; retain it and expose a Chinese error message on failure.

- [ ] **Step 5: Run unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "io.github.tyxiumud.personalainote.journal.ui.JournalViewModelTest"`

Expected: all six tests PASS.

- [ ] **Step 6: Commit state milestone**

```powershell
git add app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalViewModel.kt app/src/test/java/io/github/tyxiumud/personalainote
git commit -m "feat: add journal state and date rules"
```

---

### Task 3: Compose journal screen and app wiring

**Files:**
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/PersonalVaultApplication.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/AppContainer.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalScreen.kt`
- Create: `app/src/main/java/io/github/tyxiumud/personalainote/journal/ui/JournalPreview.kt`
- Modify: `app/src/main/java/io/github/tyxiumud/personalainote/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/ui/JournalScreenTest.kt`

**Interfaces:**
- Consumes: `JournalUiState` and `JournalViewModel` events from Task 2.
- Produces: `@Composable JournalRoute(viewModel: JournalViewModel)`
- Produces: `@Composable JournalScreen(state: JournalUiState, today: LocalDate, onDraftChanged: (String) -> Unit, onDateSelected: (LocalDate) -> Unit, onSave: () -> Unit)`
- Produces: `internal class PastOrPresentSelectableDates(private val today: LocalDate) : SelectableDates`
- Produces: application-scoped `AppContainer.captureRepository`.

- [ ] **Step 1: Write failing Compose UI tests**

Tests:
- `blankDraftDisablesSave`: render empty state and assert the “保存随记” button is disabled.
- `typingEnablesSaveAndDispatchesDraft`: input “第一次记录”, assert draft callback receives it and a populated recomposed state enables save.
- `selectedPastDateIsVisible`: render `2026-10-31`, assert the formatted date is displayed.
- `futureDatesAreNotSelectable`: instantiate `PastOrPresentSelectableDates(today)` and assert it accepts today and rejects the UTC calendar day after today; separately assert tapping the date field opens the picker.
- `timelineShowsSavedCaptures`: render two captures and assert both texts appear.

Use semantic test tags `journal_date`, `journal_input`, `journal_save`, and `journal_timeline` only where text semantics are insufficient.

- [ ] **Step 2: Run UI tests to verify they fail**

Run: `.\gradlew.bat :app:connectedDebugAndroidTest`

Expected: FAIL because the journal composables do not exist.

- [ ] **Step 3: Implement the stateless screen**

Use `Scaffold`, a clear title, selected-date button, `OutlinedTextField`, save `Button`, and `LazyColumn`. Display an empty state when no records exist. Use Material 3 `DatePickerDialog`; convert its UTC calendar-day value to `LocalDate` using UTC. Implement `PastOrPresentSelectableDates` once and use it both to disable future calendar days and to support the direct policy test.

- [ ] **Step 4: Add stable previews**

Create fixed light and dark previews with a selected past date and two sample captures. Previews must not create Room or a ViewModel.

- [ ] **Step 5: Wire application dependencies**

`PersonalVaultApplication` owns one lazy `AppContainer`; `AppContainer` owns one database and one `RoomCaptureRepository`. Register the Application in the manifest. `JournalRoute` collects `viewModel.uiState` with `collectAsStateWithLifecycle` and renders `JournalScreen`. In `MainActivity`, create `JournalViewModel` with an explicit factory from the container and render `JournalRoute` inside `Test0Theme`.

- [ ] **Step 6: Update user-facing strings**

Set `app_name` to “个人 AI 资料库”. Add Chinese strings for title, date, input hint, save, empty state, validation, and persistence failure. Keep user-facing text out of production Kotlin where practical.

- [ ] **Step 7: Run UI and unit tests**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest`

Expected: unit and instrumented test tasks PASS.

- [ ] **Step 8: Render both Compose previews**

Render `JournalPreview` and `JournalDarkPreview`.

Expected: date selector, input, save action, and timeline are readable; no content overlaps system bars or the IME-safe bottom area.

- [ ] **Step 9: Commit UI milestone**

```powershell
git add app/src/main/java/io/github/tyxiumud/personalainote app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/androidTest/java/io/github/tyxiumud/personalainote/journal/ui
git commit -m "feat: build dated local journal"
```

---

### Task 4: Teaching note and end-to-end verification

**Files:**
- Create: `docs/tutorial/01-local-text-journal.md`
- Modify only if verification finds a defect: files from Tasks 1–3.

**Interfaces:**
- Consumes: completed local journal flow.
- Produces: a beginner-readable explanation of `UI → ViewModel → Repository → Room → Flow → UI`.

- [ ] **Step 1: Write the teaching note**

Explain, using the actual file and symbol links:
- what an Activity and a Composable do;
- why UI state lives in a ViewModel;
- why the screen talks to a repository instead of SQL;
- how Room persists after restart;
- the difference between content date and import time;
- how Flow updates the timeline automatically;
- where to set a breakpoint for each layer;
- one small exercise: change the empty-state text and add a character counter.

- [ ] **Step 2: Run full milestone verification**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`; all unit and instrumented tests PASS; debug APK is produced.

- [ ] **Step 3: Deploy and verify the acceptance flow**

On an emulator/device:

1. Save two notes for today.
2. Select a past date and save one old note.
3. Switch between both dates and confirm correct grouping.
4. Force-stop and relaunch the app.
5. Confirm all three notes remain under their chosen dates.
6. Confirm a future date cannot be selected.
7. Confirm blank input cannot be saved.

Expected: all seven checks pass without crash or private note text in logs.

- [ ] **Step 4: Inspect changed production files**

Run Android Studio inspections on every modified/created production Kotlin file.

Expected: no errors; warnings are either fixed or documented.

- [ ] **Step 5: Commit documentation and verification fixes**

```powershell
git add docs/tutorial/01-local-text-journal.md
git commit -m "docs: explain local journal data flow"
```

- [ ] **Step 6: Record the next boundary**

In the handoff, state that milestone 2 begins with API settings and manual AI Markdown generation; do not start it as part of this plan.
