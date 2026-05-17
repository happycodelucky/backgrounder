# Lessons Learned — Backgrounder

Living document. Agents and humans add entries here whenever something is worth
remembering across sessions. Read it before planning non-trivial work and
whenever you get stuck — we may have seen the issue before.

## How to use this file

- **Before planning** a non-trivial change: skim all four sections, then grep
  for keywords from the task (e.g. `mutex`, `BGTask`, `XCFramework`,
  `SKIE`, `WorkManager`).
- **When stuck** for more than a few minutes: search here before going wider.
- **Add an entry as soon as you learn something** — don't batch. A terse line
  written now beats a polished paragraph never written.

## How to add an entry

- Pick the right section. If it fits two, pick the one a future reader would
  search first.
- Allocate the next sequential ID for that section (`B-007`, `D-003`, …). IDs
  are stable forever — never renumber.
- One to three lines per entry. Cite a file path or commit/PR when useful.
  No long prose. If you need more than three lines, you're explaining what,
  not why.
- Code comments may reference an entry by ID (e.g. `// see B-004`).
- If an entry becomes obsolete, mark it `~~B-NNN~~ (superseded by B-NNN)` —
  do not delete. History matters.

Date format: `YYYY-MM-DD`. Always absolute, never relative ("last week").

---

## Bugs we've hit (B)

Bugs introduced in this codebase that are worth remembering. Each entry:
**cause → fix**, plus the file or PR if known.

The `H1`–`M6` review catalogue in
[`.claude/agents/kmp-pro.md`](../agents/kmp-pro.md) is the kmp-pro agent's
review checklist. Entries below are the wider set of bugs that have actually
landed in this repo — review-catalogue items are listed here as well so a
single grep finds them.

### B-001 — iOS retry backoff off-by-one — 2026-04
**Cause:** Retry paths called `delayFor(attempt + 1)` / `delayFor(nextAttempt)`, doubling the first-retry wait vs Android.
**Fix:** Pass the attempt that *just failed* — `delayFor(attempt)`. `delayFor(0)` is the pre-first-retry wait; `nextAttempt` is only for the persisted counter and `shouldGiveUp(...)`.
**Ref:** commit `24db731`. Test `BackoffPolicyTest.firstRetryWaitsInitialDelay`.

### B-002 — `IOSTaskMutexes` per-task map leak — 2026-04
**Cause:** `MutableMap<TaskId, Mutex>` only ever grew; entries never removed on terminal paths.
**Fix:** `forget(taskId)` / `forgetAll()` under the same `synchronized` block, called from cancel, cancelAll, one-shot terminal branches, give-up, unknown-kind. Do not drop on periodic-active branches.
**Ref:** commit `24db731`. Test seam: `trackedCount()`.

### B-003 — `NSBackgroundActivityScheduler` tolerance > interval — 2026-04
**Cause:** Floored `interval` and `tolerance` at `1.0`, producing `tolerance > interval` on one-shots (Foundation: undefined behaviour).
**Fix:** Allow `interval = 0` for one-shots; cap `tolerance` with `coerceAtMost(interval)`. Apply for periodics too.
**Ref:** commit `24db731`.

### B-004 — `BGTaskScheduler.submitTaskRequest(error = null)` is a hard crash — 2026-04
**Cause:** Passing `null` for the `NSError**` out-pointer raises `NSException`; K/N `try/catch` does NOT catch ObjC exceptions.
**Fix:** Always allocate a real `ObjCObjectVar<NSError?>` via `memScoped` and surface failures as `BGSubmitResult.Failure`. Helper: `submitBGTaskRequest`.
**Ref:** commit `24db731`.

### B-005 — `EphemeralRegistry` read-modify-write race — 2026-04
**Cause:** `add { snapshot().toMutableSet().add(item); write(...) }` without a lock; `NSUserDefaults` / `SharedPreferences` are write-atomic, not RMW-atomic.
**Fix:** Wrap every RMW in `kotlinx.atomicfu.locks.synchronized` against a `SynchronizedObject`. Single-flag state → `kotlinx.atomicfu.atomic`.
**Ref:** commit `24db731`. Test `concurrentAddsDoNotLoseEntries` fans 64 concurrent adds.

### B-006 — `BGTask` double-completion / wrong-outcome race — 2026-04
**Cause:** Both `applyResult` (success) and the iOS expiration handler (cancel fallback) could call `setTaskCompletedWithSuccess(_:)`. Race → Apple "BGTask completed twice" assertion or success reported as failure.
**Fix:** Per-invocation `CompletionGuard` (single-fire atomicfu latch in `appleMain`); every completion site is `guard.runOnce { ... }`.
**Ref:** PR #5, commit `67fa08d`. See [`backgrounder/src/appleMain/.../CompletionGuard.kt`](../../backgrounder/src/appleMain/kotlin/com/happycodelucky/backgrounder/CompletionGuard.kt).

### B-007 — `IOSCoroutineBridge` scope had no cancellation path — 2026-04
**Cause:** `SupervisorJob`-rooted scope created in a Koin `single`, never cancelled — violates CLAUDE.md §3 "every scope has a clear owner".
**Fix:** `IOSCoroutineBridge.shutdown()` (mirrors macOS); exposed via public `Backgrounder.shutdown()`. Call from `applicationWillTerminate` / test `tearDown`.
**Ref:** PR #5, commit `67fa08d`.

### B-008 — `cancelAll` skipped `setActive(false)` ordering — 2026-04
**Cause:** Single `cancel()` did `cancelTaskRequestWithIdentifier → setActive(false) → clear`. `cancelAll()` skipped the middle step; a handler firing between cancel and clear saw `active=true` and resubmitted.
**Fix:** Mirror the single-cancel ordering inside the `cancelAll` `forEach`.
**Ref:** PR #5, commit `67fa08d`.

### B-009 — Expiration handler registered *after* `scope.launch` — 2026-04
**Cause:** iOS can fire expiration any time after the handler closure returns; an unset expiration handler at that moment crashes.
**Fix:** Register the expiration handler **first**, capturing a `var job: Job? = null` slot. Apple guarantees expiration only fires once the BGTask handler closure has returned — by then `job` is assigned.
**Ref:** PR #5, commit `67fa08d`.

### B-010 — macOS one-shot `Retry` was a silent no-op — 2026-04
**Cause:** Returning `WorkResult.Retry` from a one-shot called `completion(NSBackgroundActivityResultDeferred)`. `Deferred` is only meaningful when `repeats=true`; for one-shots it's a drop. Library lied about `supportsRetryBackoff`.
**Fix:** `handleOneShotRetry(...)` invalidates the current activity and schedules a fresh `repeats=false` activity with `interval = backoff.delayFor(attempt)`, honouring `maxAttempts`.
**Ref:** PR #7, commit `393421b`.

### B-011 — iOS `Keep` policy ran side effects before early-return — 2026-04
**Cause:** `schedule()` added to `ephemeral` and fired `onScheduled` *before* the `Keep && active` early-return — metrics drifted, ephemeral state corrupted on no-op reschedule.
**Fix:** Probe `state.readActive(...)` first; only on a real schedule do we mutate `ephemeral` and fire `onScheduled`. Same bug & fix in macOS `NSBackgroundActivityBackedScheduler`.
**Ref:** PR #7, commit `393421b`.

### B-012 — Android `cancel()` always returned `Cancelled(1)` — 2026-04
**Cause:** No process-local id tracking; cancel of an unknown id reported success and fired `onCancelled` regardless.
**Fix:** `ScheduledIdsTracker` (a `MutableSet<TaskId>` under `kotlinx.atomicfu.locks.synchronized`). `cancel` returns `NoSuchTask` when absent. Still calls `cancelUniqueWork` regardless (cross-process best effort).
**Ref:** PR #6, commit `e586cc0`.

### B-013 — Android `Data.Builder.build()` overflow surfaced late — 2026-04
**Cause:** `WorkInput.MAX_SERIALIZED_BYTES` caps the JSON payload; `Data.Builder.build()` caps the *combined* form (JSON + 4 metadata key strings + Boolean + Int). A WorkInput under the per-WorkInput cap could overflow `Data` at `enqueueUniqueWork` time, not at `schedule()` time.
**Fix:** Catch the `IllegalStateException` from `Data.Builder.build()` in the mapper; re-throw as `IllegalArgumentException` naming the cap. Surface fails at the library boundary the user can act on.
**Ref:** PR #6, commit `e586cc0`.

### B-014 — `readMaxAttempts` defaulted to `Int.MAX_VALUE` — 2026-04
**Cause:** Legacy `Data` blobs missing `KEY_MAX_ATTEMPTS` returned `Int.MAX_VALUE`; the cap check `if (attempt + 1 >= cap)` never fired — retry cap silently disabled on upgrades.
**Fix:** Default to `BackoffPolicy.DEFAULT_MAX_ATTEMPTS` (10).
**Ref:** PR #6, commit `e586cc0`.

### B-015 — `WorkInput.entries()` returned non-bridgeable `Map.Entry` — 2026-04
**Cause:** `Set<Map.Entry<String, WorkValue>>` has no usable Swift form (no exhaustive switch, no value-type semantics) — violates CLAUDE.md §8.
**Fix:** `@HiddenFromObjC` on `entries()`; add `keysSnapshot()` for Swift, callers look up values via `get(key:)`.
**Ref:** PR #8, commit `75d1407`.

### B-016 — `RegistryDispatchWorker` captured thread name at field init — 2026-04
**Cause:** `Thread.currentThread().name` read at field-init runs on the construction thread, not the work thread.
**Fix:** Capture inside `doWork()` instead.
**Ref:** commit `24db731`.

### B-017 — `AndroidEphemeralSweep` per-id `get(timeout)` on cold start — 2026-04
**Cause:** Per-item deadline produced total wall budget of `5s × N` — ANR risk if N is large.
**Fix:** Cap the **total wall budget** across all ephemeral ids at 5s.
**Ref:** commit `24db731`.

### B-018 — Release workflow Maven secrets resolved empty — 2026-05-13
**Cause:** `release.yml` referenced `environment: continous-deloyment` (typo), but the GitHub Environment is `continuous-deployment`. Actions silently tolerates the mismatch; `secrets.*` resolves to empty strings; `publishToMavenCentral` runs with blank credentials and fails.
**Fix:** Rename the workflow binding to the correct spelling. No silent tolerance from GitHub for this case.
**Ref:** PR #18, commit `c857ead`.

### B-019 — Release `patch` bump used `GITHUB_RUN_NUMBER` — 2026-05-14
**Cause:** Holdover from the no-version-base era. `bumpType=patch` produced a fresh run-number patch on every retry instead of `X.Y.Z → X.Y.Z+1`.
**Fix:** Increment the real patch component; `minor`/`major` zero components below. Re-runs now compute the same version; the duplicate guard step + tag-exists check catch accidents.
**Ref:** PR #19, commit `178c551`.

### B-020 — `IOSBackoffEmulation.nextRunEpochMs` read wall clock — 2026-04
**Cause:** Read `Clock.System.now()` directly; tests' `runTest` virtual time can't reach it — tests were lying about timing.
**Fix:** Dispatcher computes the backoff delay locally using the policy and adds to its **injected** `now` — no wall-clock read inside dispatcher math.
**Ref:** commit `fa573fa`.

---

## Novel design decisions (D)

Creative or non-obvious decisions the team made — especially ones a fresh
reader would not infer from the code. Capture the **decision** and the
**reason it beat the obvious alternative**.

### D-001 — DI-free Backgrounder (drop Koin entirely) — 2026-05-10
**Decision:** Tore out Koin (BOM + core + android + workmanager + test). Single constructed `Backgrounder` per app; constructor injection threads dependencies through per-platform builders.
**Why over the obvious alternative:** Koin was a global service locator we didn't actually need — the forward-reference puzzle it solved (lambda capturing not-yet-built object) dissolves with explicit construction order. DI is a user choice; the library must not impose a container. Replaced `koin-androidx-workmanager` with a 3-line hand-rolled `BackgrounderWorkerFactory`.
**Ref:** PR #10, commit `9e1f4f3`.

### D-002 — iOS library-owned tick identifier replaces per-TaskId periodic registration — 2026-05-10
**Decision:** Periodics no longer get per-TaskId `BGProcessingTaskRequest`s. The library registers a **single** consumer-supplied `BGAppRefreshTaskRequest` identifier and an in-process foreground feed; `IOSPeriodicDispatcher` coalesces both paths.
**Why over the obvious alternative:** Per-TaskId registration had two gaps: (a) `BGAppRefreshTaskRequest` doesn't fire while the app is foregrounded, (b) iOS can't coalesce multiple due periodics — picks one identifier, the rest wait indefinitely. The dispatcher pattern advances `nextRunEpochMs` **before** the worker runs so concurrent dispatches coalesce by TaskId. Cost: one consumer-supplied tick identifier in Info.plist `BGTaskSchedulerPermittedIdentifiers`.
**Ref:** PR #12, commit `fa573fa`. See [`backgrounder/src/iosMain/.../ios/IOSPeriodicDispatcher.kt`](../../backgrounder/src/iosMain/kotlin/com/happycodelucky/backgrounder/ios/IOSPeriodicDispatcher.kt).

### D-003 — Library-managed network gate, not WorkerContext.network plumbing — 2026-05-11
**Decision:** A `ReachabilityGate` in commonMain wraps `Reachability.shared` from `com.happycodelucky.reachable`; iOS/macOS schedulers call `gate.awaitReachable(requirement, budget)` before invoking the worker. Wait window: `min(5.seconds, budget / 4)`. Timeout → `WorkResult.Retry`.
**Why over the obvious alternative:** A `WorkerContext.network` API would proxy a singleton the worker can already read. Workers needing soft network policy read `Reachability.shared` themselves inside `execute()`. CLAUDE.md §5 "DI is a user choice" — don't proxy upstream.
**Ref:** PR #13 / #14 / #15, commits `dfd4bba`, `417b8be`, `89b63fb`. Files: [`ReachabilityGate.kt`](../../backgrounder/src/commonMain/kotlin/com/happycodelucky/backgrounder/ReachabilityGate.kt).

### D-004 — Sealed result types over `kotlin.Result<T>` at the Swift boundary — 2026-04
**Decision:** Public Swift-facing APIs return project-defined `sealed interface`s (`WorkResult`, `ScheduleOutcome`, `CancelOutcome`, `FetchUserOutcome`-style). Internal `runCatching` is fine.
**Why over the obvious alternative:** SKIE doesn't bridge `kotlin.Result<T>` to Swift `Result<Success, Failure>` — generic erasure differs, `Failure: Error` constraint isn't satisfied. What Swift sees is opaque `KotlinResult`: no exhaustive switch, no `try?`. Sealed types render via SKIE as exhaustive Swift `enum`s with structured payloads — *richer* than two-case `Result`. Same fix for `Pair`/`Triple` at the boundary.
**Ref:** CLAUDE.md §8; PR #4 elevates the rule, commit `4d9e293`.

### D-005 — Periodic missed cycles fire once on next wake (no catch-up) — 2026-05-10
**Decision:** `WorkRequest.Periodic` documents that N missed cycles fire **once** on the next wake — never N times back-to-back. Workers needing catch-up compute it from their own persisted state.
**Why over the obvious alternative:** Apple's BGAppRefresh, Android's WorkManager periodic, and our foreground feed all converge on "fire once when due"; emulating catch-up would either thrash on resume or require shared persistence beyond the library's remit. Contract holds across all three platforms.
**Ref:** PR #12, commit `fa573fa`. Documented in `docs/recipes/periodic.md`.

### D-006 — `AndroidScheduledTaskMapper` projection (`WorkInfoView`) instead of mocking `WorkInfo` — 2026-04
**Decision:** Extract the WorkInfo→ScheduledTask transform behind a `WorkInfoView` data class. Tests construct `WorkInfoView` directly; no Robolectric, no 13-parameter `WorkInfo` constructor that drifts across androidx.work minor versions.
**Why over the obvious alternative:** Robolectric or Mockito for a pure data transform is dead weight. Projection isolates what we actually consume (tags, state, attempts, nextScheduleTimeMillis) from the framework type's churn.
**Ref:** PR #9, commit `035ac15`. See `AndroidScheduledTaskMapperTest`.

### D-007 — Directory / Gradle module / Maven artifact id all match — 2026-05-13
**Decision:** Renamed `/shared` → `/backgrounder`; module path `:backgrounder`; published artifact `com.happycodelucky.backgrounder:backgrounder`. Triplet matches.
**Why over the obvious alternative:** `:shared` as a coordinate adds noise once published. Mirrors Ktor / kotlinx / sibling `com.happycodelucky.reachable:reachable`. Done via `git mv` so blame follows.
**Ref:** PR #16, commit `08887c8`.

### D-008 — Maven Central via vanniktech; SPM is local-only — 2026-05-13
**Decision:** Active distribution = Maven Central (`com.happycodelucky.backgrounder:backgrounder`) via vanniktech `maven-publish`. Pure-Swift consumers in this repo use the root `Package.swift` with `.binaryTarget(path:)` pointing at the debug XCFramework that `mise run spm:dev` rebuilds. Remote SPM (hosted zip) is future work.
**Why over the obvious alternative:** KMM consumers (the majority) resolve transparently through `mavenCentral()`. Pure-Swift remote SPM via KMMBridge was sketched but never wired — held back rather than half-shipping.
**Ref:** PR #17, commit `c1dc264`. CLAUDE.md §9.

### D-009 — `automaticRelease = false` in `mavenPublishing { }` is load-bearing — 2026-05-13
**Decision:** Keep `publishToMavenCentral(automaticRelease = false)` so `dryRun=true` workflow runs upload to Central Portal **staging** and stop. Runner reviews + clicks Publish in the Portal UI.
**Why over the obvious alternative:** Flipping it to `true` silently turns every dry run into an irreversible publish. Releases are uncancellable once auto-released.
**Ref:** PR #17, commit `c1dc264`. CLAUDE.md §9 release pipeline.

### D-010 — Promote scheduling verbs onto `Backgrounder`; hide `Scheduler` interface — 2026-05-14
**Decision:** Removed public `Backgrounder.scheduler` property and `Scheduler` interface. `schedule`/`cancel`/`cancelAll`/`scheduled`/`guarantees` are now methods on `Backgrounder` itself. Scheduler becomes an internal seam (FakeScheduler still uses it).
**Why over the obvious alternative:** One facade is easier to thread through an app graph than two coupled handles. No deprecation path needed — no external consumers yet.
**Ref:** PR #22, commit `7f01ea4`.

### D-011 — `BackgroundWorkerFactory` for bulk registration — 2026-05-14
**Decision:** Add a factory interface so consumers can register one factory for many TaskIds (lazy `create()`), as an alternative to per-id closure registration. Resolution order: per-id first, then factories in registration order. Declared id sets must not overlap; overlap throws.
**Why over the obvious alternative:** Per-id closures are great for small apps but force eager construction. Factories let big graphs lazy-resolve. A factory declaring an id but returning `null` from `create()` throws `FactoryDeclinedException` — declared-but-declined is a client sync bug, not a fall-through.
**Ref:** PR #21, commit `215a19d`.

### D-012 — Apple platform-name casing rule overrides Kotlin acronym convention — 2026-04
**Decision:** `iOS`, `macOS`, `tvOS`, `watchOS`, `iPadOS`, `visionOS` keep their brand casing in identifiers we author (`IOSCoroutineBridge`, not `IosCoroutineBridge`). Standard Kotlin `HtmlParser`-style camel-casing does NOT apply.
**Why over the obvious alternative:** They're trademark spellings; readability across iOS/macOS developer audiences matters more than within-Kotlin consistency. JetBrains-supplied identifiers (`iosMain`, `iosArm64`, `withIos()`) and package segments stay lowercase — we don't fight the tool.
**Ref:** CLAUDE.md §3; commit `247417a`.

### D-013 — Library version literal lives in `build.gradle.kts`; CI never writes back — 2026-04
**Decision:** `allprojects { version = providers.gradleProperty("version").getOrElse("0.1.0-SNAPSHOT") }`. Humans bump major/minor in the literal; CI stamps `-Pversion=...`. CI never commits a version change.
**Why over the obvious alternative:** Version-as-property avoids commit-loops on every release. Mirrors appletv-client / reachable.
**Ref:** commit `41a2e23`.

### D-014 — Mise is the canonical build entry; gradle is the second-level — 2026-05-13
**Decision:** `mise.toml` pins JDK, Python, Gradle bootstrap, gh. Contributor docs lead with `mise run check / test / xcframework / docs:*`; raw `./gradlew` shown as power-user fallback. CI is mise-driven.
**Why over the obvious alternative:** Without `mise install`, contributors pick up whatever JDK the runner image / their laptop happens to ship. Mise pins it to match CI exactly.
**Ref:** PR #16 / #17, commits `08887c8`, `0e2ebe2`.

### D-015 — `setup-gradle@v5`, not `v6` — 2026-05-13
**Decision:** Pin `gradle/actions/setup-gradle@v5` in CI workflows.
**Why over the obvious alternative:** v6 moved caching to a commercially-licensed component. v5 keeps the freely-licensed cache engine and runs on Node 24. Rationale is in-file in the workflows.
**Ref:** PR #16, commit `08887c8`.

### D-016 — Docs site uses `mkdocs-macros` for version injection — 2026-05-14
**Decision:** `main.py` (mkdocs-macros entry point) resolves `version` from `BACKGROUNDER_VERSION` env → latest GitHub release tag → `main` fallback. `docs/installation.md` uses `{{ version }}` placeholders. README pins manually (it renders raw on GitHub, can't use the macro).
**Why over the obvious alternative:** Hand-edited version strings rot. Release workflow calls `docs.yml` via `workflow_call` after publish so the site re-resolves immediately.
**Ref:** PR #20, commit `2ecee6a`.

### D-017 — `mkdocs build --strict` + `docs/check.py` gate the docs — 2026-04
**Decision:** mkdocs runs strict (broken links / nav drift fail the build). `docs/check.py` asserts every `.md` is in `nav:` and every recipe/platform page has at least one fenced code block.
**Why over the obvious alternative:** Strict mode catches what humans miss; `check.py` catches what mkdocs doesn't (stub pages with no content).
**Ref:** commit `08f522e`.

### D-018 — `expect`/`actual` cap ~20 lines, otherwise refactor to interface — 2026-04
**Decision:** Codified in CLAUDE.md §4. If an `actual` is more than ~20 lines, refactor to a `commonMain` interface with platform implementations injected at the entrypoint.
**Why over the obvious alternative:** Large `expect`/`actual` surfaces hide architecture. Interfaces declare contract explicitly and survive refactors better.
**Ref:** CLAUDE.md §4.

### D-019 — `MonitorEventEmitter` is `tryEmit`-only — 2026-05-17
**Decision:** Every emit site funnels through `MonitorEventEmitter.emit(...)`, which fans out synchronously to the legacy `BackgrounderEventListener` and `tryEmit`s into the public `SharedFlow(replay=0, extraBufferCapacity=64, onBufferOverflow=DROP_OLDEST)`. Never the suspending `MutableSharedFlow.emit`.
**Why over the obvious alternative:** Suspending `emit` lets a slow collector backpressure-block the producer. The iOS bridge holds the per-task `Mutex` while emitting; a slow `events()` collector would serialise every subsequent dispatch on that id. CLAUDE.md §3 forbids that. `DROP_OLDEST` is honest — collectors see the gap rather than the producer stalling.
**Ref:** PR #28 (wave 1).

---

## NEVER DO (N)

Things we have explicitly tried, undone, or declared off-limits in this
project — beyond CLAUDE.md §13's general hard rules. Section here is for the
**contextual** never-dos: the ones a fresh reader would not infer.

### N-001 — Never include `CancellationException` in `@Throws` on SKIE-bridged APIs — 2026-04
**Don't:** `@Throws(CancellationException::class)` on `suspend fun` consumed from Swift.
**Why:** SKIE bridges `suspend fun` as Swift `async throws` and routes cancellation through Swift's native `Task.cancel()` / `CancellationError`. Adding `CancellationException` pollutes the generated signature and forces consumers to write `catch is CancellationError` — meaningless. The legacy pattern is correct for direct K/N ObjC export only; this repo uses SKIE. Flag existing call sites for cleanup.
**Ref:** CLAUDE.md §8; PR #8 swept the existing sites, commit `75d1407`.

### N-002 — Never call `KoinPlatform.getKoin()` without `OrNull` — 2026-04
**Don't:** `runCatching { KoinPlatform.getKoin() }`.
**Why:** Koin has `getKoinOrNull()` for exactly this. The `runCatching` wrap is louder and lies about intent (we're not handling an exception — we're checking initialization).
**Ref:** kmp-pro anti-patterns; relevant to legacy code only — Koin is now gone (see D-001).

### N-003 — Never call `suspend` functions inside a `kotlinx.atomicfu.locks.synchronized` block — 2026-04
**Don't:** Suspend inside a `synchronized(lockObject) { ... }`.
**Why:** The non-suspending two-tier lock is for short critical sections. If the body needs to suspend, you wanted `kotlinx.coroutines.sync.Mutex.withLock { ... }`. Lock declarations carry a `// MUST NOT call suspend functions inside this block` comment.
**Ref:** CLAUDE.md §3, §13. Pattern shown in `IOSTaskMutexes`.

### N-004 — Never add CocoaPods, Compose Multiplatform, x86/x86_64, watchOS, or tvOS — 2026-04
**Don't:** Touch any of the above.
**Why:** Out of scope. Targets are ARM-only by policy (CLAUDE.md §1, §13). CocoaPods is forbidden as a distribution channel; SKIE + Maven Central + local SPM is the path (CLAUDE.md §9, D-008).
**Ref:** CLAUDE.md §1, §9, §13.

### N-005 — Never use `as? List<SomeT>` with `@Suppress("UNCHECKED_CAST")` — 2026-04
**Don't:** Suppress unchecked casts on lists at the K/N boundary.
**Why:** K/N erases element types — the cast says nothing about element correctness, and a wrong-typed entry crashes deep in the call stack. Use `(it as? List<*>)?.filterIsInstance<SomeT>()` instead.
**Ref:** kmp-pro anti-patterns; commit `24db731` swept two such sites.

### N-006 — Never pin a dependency you don't wire in — 2026-05-13
**Don't:** Leave a version in `gradle/libs.versions.toml` for a plugin/library that isn't actually applied.
**Why:** KMMBridge was pinned at 1.2.1 but never wired into any module's `plugins { }` block. Pinning gave the false impression it was on the build path and produced dead docs. Either wire it or remove it.
**Ref:** PR #17, commit `c1dc264`.

### N-007 — Never use `kotlin.synchronized`, `@Synchronized`, `java.util.concurrent.locks.*`, or `volatile` — 2026-04
**Don't:** Reach for any JVM-only lock primitive.
**Why:** Not portable to K/N or wasm. Only `kotlinx.atomicfu.locks.synchronized` (non-suspending) and `kotlinx.coroutines.sync.Mutex` (suspending) are KMP-portable. `kotlin.synchronized` is JVM-only despite the package name.
**Ref:** CLAUDE.md §3, §13.

### N-008 — Never edit the workflow's environment name to "fix" the typo without coordinating — 2026-05-13
**Don't:** Rename `continuous-deployment` ↔ any variant without updating both the GitHub Environment name *and* every workflow that binds to it, in lockstep.
**Why:** Actions silently tolerates a non-matching environment name — secrets resolve empty, the job runs, the publish fails with cryptic auth errors. B-018 burned us once.
**Ref:** PR #18, commit `c857ead`.

### N-009 — Never use `kotlin.Result<T>` / `Pair<…>` / `Triple<…>` in public Swift-facing signatures — 2026-04
**Don't:** Return or expose them on public declarations in `commonMain` / `appleMain` / `iosMain` / `macosMain`.
**Why:** SKIE has no special-case bridge; Swift sees opaque generic wrappers with no exhaustive switch and no value-type semantics. Use a sealed interface (D-004) or a named `data class`. Internal `runCatching` is fine — the rule is about the **public** boundary.
**Ref:** CLAUDE.md §8; PR #4, commit `4d9e293`.

### N-010 — Never bump Kotlin past SKIE's supported range — 2026-04
**Don't:** Bump Kotlin without checking the SKIE compatibility matrix first.
**Why:** SKIE lags Kotlin releases by a few days. Bumping past the supported range disables SKIE; the framework falls back to default K/N ObjC export and the Swift surface regresses dramatically.
**Ref:** CLAUDE.md §2, §8.

### N-011 — Never read wall-clock time inside dispatcher / scheduler logic — 2026-05-10
**Don't:** Call `Clock.System.now()` from code under test that uses `runTest` virtual time.
**Why:** `runTest` can't intercept the wall-clock read; tests silently lie about timing. Inject a `now: () -> Long` and add deltas to that. See B-020.
**Ref:** PR #12, commit `fa573fa`.

### N-012 — Never add a `WorkerContext.network` (or similar) proxy for `Reachability.shared` — 2026-05-12
**Don't:** Wrap upstream singletons in thin library-side proxies.
**Why:** Workers needing soft network policy can read `Reachability.shared` directly inside `execute()`. The library only gates on `WorkConstraints.networkRequired`; finer-grained logic is the worker's job. See D-003.
**Ref:** PR #15, commit `89b63fb`.

---

## Troubleshooting (T)

When we got stuck, what unstuck us. Symptom-first so a future reader can
match against what they're seeing.

### T-001 — Maven publish fails with blank credentials — 2026-05-13
**Symptom:** `publishToMavenCentral` reaches the upload step but the request has empty `Authorization`. Run logs show the env step succeeding.
**Cause:** Workflow `environment:` binding doesn't match the actual GitHub Environment name (typo). Actions tolerates the mismatch silently → secrets resolve to empty strings.
**Unstuck by:** Match the binding to the Environment name exactly. Check via the repo's Settings → Environments. See B-018.
**Ref:** PR #18.

### T-002 — `assembleBackgrounderXCFramework` fails on reachable's bundled `Reachability+Shared.swift` — 2026-05-12
**Symptom:** XCFramework build fails with namespacing conflict around `BackgrounderReachableReachability`.
**Cause:** Reachable 0.9.0 shipped a `.swift` file inside its klib that conflicted with transitive consumption from a SKIE-enabled framework.
**Unstuck by:** Bump to reachable 0.11.10 (no `.swift` sources in the klib). Companion change: drop the `@HiddenFromObjC` overload-split on `Backgrounder.{ios,macos}.kt::create(...)`; tests use upstream `withFakeReachability { ... }` instead of an injection seam.
**Ref:** PR #14, commit `417b8be`.

### T-003 — Dispatcher tests pass but production behaviour drifts — 2026-05-10
**Symptom:** `runTest` tests verify backoff timing perfectly; manual runs show different delays.
**Cause:** A helper (`IOSBackoffEmulation.nextRunEpochMs`) read `Clock.System.now()` directly — `runTest` virtual time can't reach it. Tests were measuring the policy curve, not the dispatcher's actual `now` advancement.
**Unstuck by:** Dispatcher computes the delay locally using the policy and adds to its injected `now`. No wall-clock read in dispatcher math. See B-020.
**Ref:** PR #12.

### T-004 — `BGTask` registered but never fires in the simulator — 2026-05-10
**Symptom:** App registers a BGTask identifier; no fire on the device or simulator after the expected interval.
**Cause:** Several possibilities — most commonly (a) the identifier is missing from `Info.plist` `BGTaskSchedulerPermittedIdentifiers`, (b) the consumer-supplied tick identifier mismatches the Info.plist entry, (c) the user has force-quit the app (see `docs/platforms/force-quit.md`).
**Unstuck by:** Use the LLDB `e -l objc -- (void)[[BGTaskScheduler sharedScheduler] _simulateLaunchForTaskWithIdentifier:@"..."]` recipe in `docs/recipes/test-workers.md`. `Backgrounder.start()` logs a Kermit error if the Info.plist entry is missing.
**Ref:** `docs/platforms/force-quit.md`, `docs/recipes/test-workers.md`.

### T-005 — Android `WorkInfo` test constructor breaks on minor `androidx.work` bumps — 2026-04
**Symptom:** `AndroidScheduledTaskMapperTest` fails to compile after an androidx.work bump; `WorkInfo` constructor signature changed.
**Cause:** `WorkInfo` has 13+ constructor parameters and the order shifts across minor versions. Tests that build a `WorkInfo` directly break frequently.
**Unstuck by:** Use the `WorkInfoView` projection — tests construct the projection, production maps `WorkInfo → WorkInfoView` once at the boundary. See D-006.
**Ref:** PR #9, commit `035ac15`.

### T-006 — Pre-flight check says version is already on Maven Central, but the local literal looks newer — 2026-05-14
**Symptom:** Release workflow `Verify version not already published` step fails on a version that should be brand new.
**Cause:** Likely a previous **non-dry-run** publish or a leftover staged Portal deployment that was auto-released. The Central Portal is the source of truth.
**Unstuck by:** Check `central.sonatype.com` → Deployments. If a stuck deployment exists, Drop it. Then bump the version literal in `build.gradle.kts` (humans bump major/minor; the workflow bumps patch). See D-009.
**Ref:** PR #19.
