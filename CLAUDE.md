# CLAUDE.md — Kotlin Multiplatform Project Guide

Rules for working in this repo. Read before starting any task.

---

## 1. Scope

**Shared:** business logic, networking, persistence, domain models, formatting, validation. Anything UI-adjacent that isn't actually UI.

**Not shared:** UI. Each platform ships its own native UI layer (SwiftUI on iOS, Jetpack Compose on Android, SwiftUI/AppKit on macOS desktop, native web on the web target). **Do not add Compose Multiplatform.** Do not propose it. The shared module is headless.

**Targets — ARM only, no exceptions:**

- `iosArm64` (device) + `iosSimulatorArm64` (Apple Silicon simulator)
- Android `arm64-v8a`
- `macosArm64` (desktop)
- `wasmJs` — stretch goal; design to not preclude it, don't block Tier 1 work on it

**Out of scope:** all x86/x86_64, `armeabi-v7a`, Intel Macs, watchOS, tvOS, Linux, Windows, Kotlin/JS legacy.

---

## 2. Versions

Use the **latest stable**. Never EAP, RC, or beta on `main`. All versions live in `gradle/libs.versions.toml`.

Floors as of last edit:

- Kotlin 2.3.21
- Gradle 9.x
- AGP 9.x with `com.android.kotlin.multiplatform.library` (use the new `android` block, not `androidTarget`)
- JVM target 21
- Latest stable Xcode that the current Kotlin release supports

**Before adding or bumping any dependency: web-search the latest stable version.** Versions in your training data are stale. Don't guess.

K2 only. No K1 fallback.

---

## 3. Language standards

- `languageVersion` and `apiVersion` set to current stable.
- Stable APIs only. Experimental APIs require an explicit `@OptIn` with a one-line comment explaining why.
- No `!!` in production code.
- `internal` by default. Widen visibility only when needed.
- `data class`, `value class`, `sealed interface` over open hierarchies. Use `value class` for typed IDs and units — free at runtime.
- `kotlin.time` for durations. `kotlin.uuid.Uuid` for UUIDs.
- KDoc on all public API. Comments explain *why*, not *what*.
- 4-space indent, 120-col max, trailing commas on multi-line.
- ktlint + detekt must pass.

**Apple platform names — preserve their casing.** `iOS`, `macOS`, `tvOS`, `watchOS`, `iPadOS`, `visionOS` are the canonical spellings; never lowercase the trailing acronym in identifiers, file names, types, packages, or comments. The standard Kotlin convention of camel-casing acronyms (`HtmlParser`, not `HTMLParser`) does **not** apply to these — they're Apple platform brand names and we keep them recognisable.

| Wrong                  | Right                  |
| ---------------------- | ---------------------- |
| `IosCoroutineBridge`   | `IOSCoroutineBridge`   |
| `BackgrounderIos`      | `BackgrounderIOS`      |
| `MacosScheduler`       | `MacOSScheduler`       |
| `TvosWidget`           | `TvOSWidget`           |
| `iosBackgrounderModule`| `iOSBackgrounderModule`|

Allowed exceptions:
- **Identifiers we don't own** — `applyDefaultHierarchyTemplate { withIos() / withMacos() / withTvos() / withWatchos() }`, the JetBrains-supplied source-set names (`iosMain` / `macosMain`), and the K/N target names (`iosArm64`, `macosArm64`, etc.). The spelling is fixed by JetBrains; we follow what the tool requires.
- **Package names** — Kotlin / Java packages are conventionally all-lowercase across the entire ecosystem (`com.happycodelucky.backgrounder.ios`, not `com.happycodelucky.backgrounder.iOS`). The casing rule applies inside types and identifiers within those packages, not to package segments themselves.

Everything else we author — classes, files, top-level functions, top-level `val`s, comments, KDoc — follows the casing-preserving rule.

**Concurrency:**

- `kotlinx.coroutines` only. Every `CoroutineScope` has a clear owner with a defined cancellation lifecycle. The library's per-platform schedulers and the iOS coroutine bridge each own a `SupervisorJob`-rooted scope; `Backgrounder.shutdown()` is the documented cancellation handle. There are no top-level scopes.
- No `GlobalScope`. Ever.
- `Flow`/`StateFlow`/`SharedFlow` over callbacks and `LiveData`.
- For shared mutable state guarded **across `suspend` boundaries**, use `kotlinx.coroutines.sync.Mutex` or actor-style coroutines.
- For short, **non-suspending** critical sections (e.g., a couple of map operations), use `kotlinx.atomicfu.locks.synchronized` with a `kotlinx.atomicfu.locks.SynchronizedObject`. It's the KMP-portable equivalent of a JVM monitor — lowers to a `synchronized` block on JVM, an internal lock on K/N. Single-flag state belongs in `kotlinx.atomicfu.atomic` instead.
- **Never** `kotlin.synchronized` (JVM-only), `java.util.concurrent.locks.*`, `@Synchronized`, `volatile`, or `Object.wait/notify`. None are portable to K/N or wasm.
- A `kotlinx.atomicfu.locks.synchronized` block must not call `suspend` functions. If you reach for one and the body needs to suspend, you wanted a `Mutex`. Leave a `// MUST NOT call suspend functions inside this block` comment on the lock declaration.
- New memory model is the only one we support. No legacy freezing logic.

---

## 4. Module layout

```
/backgrounder         headless KMP module — business logic only.
  /src/commonMain     Directory + Gradle module name + Maven artifact id all
  /src/androidMain    match (`com.happycodelucky.backgrounder:backgrounder`).
  /src/iosMain
  /src/macosMain
  /src/wasmJsMain     stretch
/iOSApp               Xcode project, native SwiftUI, consumes /backgrounder via SPM
/androidApp           Android entrypoint, native Jetpack Compose UI
/macOSApp             macOS desktop, native SwiftUI/AppKit
/webApp               native web (stretch)
```

`applyDefaultHierarchyTemplate()`. Don't hand-roll source set wiring.

`expect`/`actual` surface stays minimal. If an `actual` is more than ~20 lines, refactor to an interface in `commonMain` with platform implementations injected at the entrypoint.

---

## 5. Libraries — Kotlin-first, always

A "Kotlin-first" library is written in Kotlin, designed for KMP, idiomatic (suspend, Flow, sealed types), and published by JetBrains, Kotlin Foundation, or a Kotlin-focused vendor (Touchlab, etc.).

### Step 1 — Use these. No substitutions without a written reason.

| Concern | Library |
|---|---|
| HTTP (low-level) | **Ktor Client** |
| REST API definitions | **Ktorfit** (KSP, on top of Ktor) |
| Serialization | **kotlinx.serialization** |
| Coroutines | **kotlinx.coroutines** |
| Atomics | **kotlinx.atomicfu** |
| Date/time | **kotlinx.datetime** (never `java.time` in `commonMain`) |
| I/O / buffers | **kotlinx.io** |
| Immutable collections | **kotlinx.collections.immutable** |
| Logging | **Kermit** (Touchlab) |
| Dependency injection | **Koin** (recommended for the consumer's own graph; not required by the library) |
| Database | **SQLDelight**, or **Room** |
| Key-value storage | **multiplatform-settings** |
| ViewModel | **androidx.lifecycle.viewmodel** 2.9+ |
| DataStore | **androidx.datastore** 1.1+ |
| Testing | **kotlin.test** + **Turbine** + **kotlinx.coroutines.test** |
| Property tests | **Kotest** (when invariants are clear) |

**DI is a user choice.** Library code in `:backgrounder` uses constructor injection — no `Module`, no service locator, no top-level `KoinPlatform.getKoin()` reads. The consumer's app graph is what wires the library's public types together. Koin is recommended for the consumer's own graph; alternatives (Hilt on Android, kotlin-inject, hand-wired) are equally supported. The library *itself* must not introduce a runtime dependency on a DI container.

### Step 2 — KMP-capable third-party

If no Step 1 option exists, check **klibs.io**. Required: active maintenance (commits in last 6 months), supports our Tier 1 targets, compatible with current Kotlin, permissive license.

### Step 3 — Native per-platform via `expect`/`actual`

iOS: Apple first-party frameworks (Foundation, CryptoKit, Network, AVFoundation). Android: AndroidX. macOS desktop: Apple frameworks. Wasm: document the gap.

### Step 4 — Roll our own

Section 6.

---

## 6. Roll our own — when and when not

Default answer: use the library. Override only with a measured benchmark or a clearly missing capability.

**Roll our own when:**

- Library does substantially more than we need and we pay for the unused 95% in binary size.
- Profiling on the slowest target device shows the library in a hot path with measurable overhead.
- Library forces an allocation pattern that hurts (image processing, audio, tight loops). No buffer reuse → replace.
- We need behavior the library doesn't expose.
- The platform primitive is genuinely simple (one verb on `NSURLSession` + `OkHttp` is often less code than the wrapper).

**Don't roll our own for:** crypto, TLS, JSON, HTTP, date/time arithmetic, Unicode, font shaping, databases. These are correctness-hard and battle-tested.

**Process — required when proposing a hand-written replacement:**

1. Build with the recommended library.
2. Profile on the slowest target device we ship.
3. If the library is the bottleneck, write a `kotlinx-benchmark` micro-benchmark.
4. Prototype the hand-written version, benchmark on the same device.
5. Switch only if win is ≥2x **or** library blocks a feature we need.
6. Document the benchmark numbers in a comment above the implementation.

**Common wins:** hashing/checksums via platform crypto, base64/hex encoding, bit manipulation with `kotlinx.io` `Buffer`, fixed-size ring buffers / LRUs, image transforms via `vImage`/`CoreImage` on iOS, audio sample processing via `AVAudioEngine` / `AudioTrack`.

**Common losses:** anything that competes with Ktor, kotlinx.serialization, SQLDelight, or platform crypto.

---

## 7. UI — native per platform

UI lives in the platform apps. The shared module exposes:

- State as `StateFlow<T>` (consumed via SKIE as `AsyncSequence` on Swift, or collected directly in Compose on Android).
- Events as `SharedFlow<T>`.
- Commands as `suspend fun` returning sealed result types.

iOS UI: SwiftUI. Android UI: Jetpack Compose. macOS desktop UI: SwiftUI/AppKit. Web UI: platform-appropriate.

The shared module **never** depends on a UI framework. No `androidx.compose.*` in `commonMain` or any source set inside `/backgrounder`.

---

## 8. Swift interop

Default Kotlin → ObjC → Swift bridge is bad. We fix it with SKIE + annotations.

### SKIE

**SKIE (Touchlab) is mandatory** on the iOS framework build. It gives us:

- Real Swift enums for Kotlin `enum class` (exhaustive `switch`).
- Sealed class/interface exhaustivity via `onEnum(of:)`.
- `suspend` → `async`/`await` with cancellation.
- `Flow` → `AsyncSequence` with element types preserved.
- Default arguments preserved as Swift overloads.
- Function-name overloads preserved (no ObjC mangling).

Rules:

- Don't disable SKIE.
- SKIE lags Kotlin by a few days after each release. If SKIE doesn't yet support a Kotlin version, we wait. Don't bump Kotlin past SKIE's supported range.
- Don't enable JetBrains' direct Swift export until it's stable. SKIE is the path.

### Annotations

Every public Kotlin API consumed from Swift must read like Swift at the call site.

**`@ObjCName(swiftName = "...")` to rename.** Strip the noun from the verb; let the parameter label carry it.

```kotlin
// Wrong: Swift sees browser.openUrl(url: someUrl)
fun openUrl(url: String)

// Right: Swift sees browser.open(url: someUrl)
@ObjCName(swiftName = "open")
fun openUrl(url: String)
```

| Kotlin | Default Swift | Target Swift |
|---|---|---|
| `loadImageWithUrl(url)` | `loadImageWithUrl(url:)` | `load(url:)` |
| `findUserById(id)` | `findUserById(id:)` | `findUser(id:)` |
| `setEnabled(enabled)` | `setEnabled(enabled:)` | `setEnabled(_:)` |
| `closeConnection()` | `closeConnection()` | `close()` |

**`@HiddenFromObjC`** — hide Kotlin-only APIs from the generated header (raw `Flow` accessors, Kotlin-specific extensions).

**`@ShouldRefineInSwift`** (with `@HiddenFromObjC`) — when SKIE generates a Swift wrapper and the raw Kotlin should be hidden.

**`@Throws(...)`** — required on every public `suspend fun` and any public function that can throw across the boundary. Without it, exceptions become unrecoverable iOS crashes (NSGenericException, no Swift catch site).

List the **domain exceptions** the function actually throws — `IOException`, `SerializationException`, `IllegalArgumentException`, etc.

```kotlin
@Throws(NetworkException::class)
suspend fun fetchUser(id: String): User
```

**Do NOT include `CancellationException` in the `@Throws` list.** This repo uses SKIE, which bridges `suspend fun` as Swift `async throws` and routes coroutine cancellation through Swift's native `Task.cancel()` / `CancellationError` machinery. KMP handles cancellation transparently — adding `CancellationException::class` to `@Throws` pollutes the generated Swift signature and forces consumers to write a meaningless `catch is CancellationError` arm.

The legacy pattern `@Throws(CancellationException::class)` was correct for direct Kotlin/Native ObjC export (no SKIE). It's incorrect here. Existing call sites that include it should be cleaned up.

### Sealed result types over `kotlin.Result<T>`

**No `kotlin.Result<T>` in any public Swift-facing signature.** Not as a return type, not as a `val` / `var` type, not nested in a `Flow<Result<T>>` / `StateFlow<Result<T>>` / `SharedFlow<Result<T>>`. Use a project-defined `sealed interface` instead.

**Why `kotlin.Result<T>` doesn't bridge.** SKIE has no special-case mapping for `kotlin.Result<T>`, and it doesn't bridge to Swift's `Result<Success, Failure>` either — Swift's `Result` requires `Failure: Error` and a different generic shape, and the K/N → ObjC layer erases `kotlin.Result`'s payload. What Swift sees instead is an opaque `KotlinResult` wrapper with no exhaustive `switch`, no `try?` / `catch` integration, and no value-type semantics. Consumers end up writing reflective `isSuccess` / `getOrNull` calls that defeat the point.

**There is no KMP-friendly `Result` library.** None of our Step-1 libraries ship one and SKIE does not synthesise one. Don't go shopping. The answer is a sealed interface defined in `commonMain`, which SKIE renders as an exhaustive Swift `enum` via `onEnum(of:)` — *richer* than Swift's two-case `Result` because the cases can carry arbitrary structured payloads.

```kotlin
// Wrong — Swift sees an opaque KotlinResult, no exhaustive switch.
suspend fun fetchUser(id: String): Result<User>

// Right — SKIE renders this as a Swift enum; `onEnum(of:)` makes it exhaustive.
sealed interface FetchUserOutcome {
    data class Success(val user: User) : FetchUserOutcome
    data class NotFound(val id: String) : FetchUserOutcome
    data class Failure(val reason: String) : FetchUserOutcome
}

suspend fun fetchUser(id: String): FetchUserOutcome
```

| `kotlin.Result<T>` | Project sealed interface |
|---|---|
| Opaque `KotlinResult` in Swift | Native Swift `enum` via SKIE `onEnum(of:)` |
| No exhaustive `switch` | Compiler-enforced exhaustivity |
| Two cases: success / failure | Arbitrary cases with structured payloads |
| Failure is `Throwable` — opaque from Swift | Failure case carries domain-typed fields |
| Generic `T` erased at the bridge | Concrete payload types preserved |

**Templates already in this repo.** Mirror their shape:

- `backgrounder/src/commonMain/kotlin/com/happycodelucky/backgrounder/WorkResult.kt`
- `backgrounder/src/commonMain/kotlin/com/happycodelucky/backgrounder/ScheduleOutcome.kt`
- `backgrounder/src/commonMain/kotlin/com/happycodelucky/backgrounder/CancelOutcome.kt`

**Internal use of `runCatching` is fine.** This rule is about return types crossing the Swift boundary. `runCatching { ... }.getOrElse { ... }` inside an `internal` function — to swallow-on-purpose, log, or fold into a sealed outcome before returning — does not violate it. The line is drawn at `public` visibility on `commonMain` / `appleMain` / `iosMain` / `macosMain` declarations.

The same rule applies to `Pair<A, B>` / `Triple<…>` at the public boundary — same root cause (opaque generic wrapper, no exhaustivity), same fix (named `data class`).

### API design for Swift consumers

1. Verbs without the object. `open(url:)`, not `openUrl(url:)`.
2. `sealed interface` for results, not nullable + error code. SKIE makes it exhaustive.
3. `Flow<T>` over callbacks. Never a callback-based public API in `commonMain`.
4. No `kotlin.Result<T>` at the boundary. Use a named sealed interface — see "Sealed result types over `kotlin.Result<T>`" above.
5. No `Pair`/`Triple` in public API. Define a `data class`.
6. No star-projected generics across the boundary. Concrete types.
7. No companion-object factories for Swift-facing entry points. Top-level functions or constructors.
8. `Int` over `Long` when the range allows.

---

## 9. Distribution — Maven Central via vanniktech, local SPM for sample apps

**CocoaPods is forbidden.** Do not add `cocoapods { ... }` blocks. See §13.

### Maven Central (active, KMP consumers)

The primary distribution channel is **Maven Central** via the vanniktech `maven-publish` plugin.

**Coordinates:** `com.happycodelucky.backgrounder:backgrounder`

**What gets published in one Gradle invocation:**

- The Android AAR.
- The `kotlinMultiplatform` metadata module (`.module` file) that ties every target together.
- Per-target klibs: `backgrounder-iosarm64`, `backgrounder-iossimulatorarm64`, `backgrounder-macosarm64`, `backgrounder-android`.
- Sources / javadoc jars next to each, with detached GPG signatures.

KMP consumers just add `mavenCentral()` to their repositories and write:

```kotlin
// commonMain
implementation("com.happycodelucky.backgrounder:backgrounder:X.Y.Z")
```

Gradle resolves the right per-target klib automatically. No extra setup needed on the consumer side.

### Release pipeline

Releases are triggered via `workflow_dispatch` on `.github/workflows/release.yml`:

1. The runner picks `bumpType` (patch / minor / major). The patch is always `GITHUB_RUN_NUMBER` — a monotonic counter that auto-advances on re-runs.
2. `dryRun=true` (the default) uploads to Central Portal staging only — the deployment sits in "validated" state. Review it at https://central.sonatype.com/ and click Publish (or Drop). This is safe to run frequently.
3. `dryRun=false` runs `publishAndReleaseToMavenCentral` — irreversible. Tags the commit and creates a GitHub Release.

The `automaticRelease = false` flag in `backgrounder/build.gradle.kts` is what makes dry-run behaviour correct. Do not flip it without reading the comment there.

**Secrets:** the four `MAVEN_CENTRAL_*` secrets live on the `continous-deloyment` GitHub environment (yes, the typo — it's the literal environment name; don't fix it without renaming the environment on both `backgrounder` and `reachable` in lockstep).

### Local SPM (sample apps inside this repo)

Pure-Swift consumers (no Kotlin in their project) need the XCFramework directly. The root `Package.swift` exposes a `.binaryTarget(path:)` pointing at the debug XCFramework build output:

```
backgrounder/build/XCFrameworks/debug/Backgrounder.xcframework
```

Sample apps under `/iOSApp` and `/macOSApp` consume this via `.package(path: "..")` in their own `Package.swift`. This is suitable for apps that live inside this repo and for vendored consumption. It is **not** a remote distribution path.

**Rebuilding the debug XCFramework:**

```sh
mise run spm:dev
# or equivalently:
./gradlew :backgrounder:assembleBackgrounderDebugXCFramework
```

**Rebuilding the release XCFramework:**

```sh
mise run xcframework
# or equivalently:
./gradlew :backgrounder:assembleBackgrounderXCFramework
```

### Future: remote SPM distribution (KMMBridge)

Remote SPM distribution — where a hosted XCFramework zip is referenced by URL + checksum from a tagged `Package.swift` in an SPM Git repository — is **future work**. The original architectural sketch used Touchlab's KMMBridge to automate this pipeline (build XCFramework → zip → publish zip to Maven → generate `Package.swift` → push to SPM repo → tag). That wiring was never completed and is intentionally absent. See git history for the architectural sketch; re-introduce when an SPM-side delivery story is needed for non-KMP iOS / macOS consumers.

---

## 10. Build & tooling

- Single source of truth: `gradle/libs.versions.toml`.
- Configuration cache + build cache on. Don't disable.
- Per-PR CI: build commonMain + every Tier 1 target. Run JVM and Android unit tests. iOS tests run nightly.

---

## 11. Testing

- All shared logic gets `commonTest` coverage.
- `kotlinx.coroutines.test` with `runTest` and virtual time. Never `Thread.sleep`.
- Turbine for Flow assertions.
- Kotest for property-based tests on parsers, serializers, anything with a clear invariant.

---

## 12. Task workflow

When starting any task:

1. Read this file. Read `gradle/libs.versions.toml`.
2. Adding a dependency? Web-search the latest stable version first. Don't invent versions.
3. Need platform-specific behavior? Walk Section 5 in order. Don't skip to `expect`/`actual`.
4. Considering a hand-written replacement? Section 6 process. Default answer is "use the library."
5. Adding a public API consumed from Swift? Apply Section 8 rules at design time, not after.
6. Done means: `./gradlew check` passes and `./gradlew :backgrounder:linkDebugFrameworkIosArm64` builds clean.
7. Opting into experimental APIs? One-line comment explaining what's experimental and the rollback path.
8. Wasm gap? `// TODO(wasm)` and ship Tier 1.

---

## 13. Hard rules

- No Compose Multiplatform.
- No CocoaPods.
- No x86/x86_64.
- No `GlobalScope`.
- No `!!`.
- No `java.time` in `commonMain`.
- No `kotlin.synchronized`, `@Synchronized`, `java.util.concurrent.locks.*`, or `volatile` — only `kotlinx.atomicfu.locks.synchronized` (non-suspending) and `kotlinx.coroutines.sync.Mutex` (suspending).
- No suspend calls inside a `kotlinx.atomicfu.locks.synchronized` block.
- No EAP/RC/beta on `main`.
- No callback-based public APIs in `commonMain`.
- No UI dependencies in `/backgrounder`.
- No hand-edited `Package.swift`.
- No vendored `XCFramework` in the iOS repo.
