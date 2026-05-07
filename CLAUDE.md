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
- AGP 8.x with `com.android.kotlin.multiplatform.library` (use the new `android` block, not `androidTarget`)
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

**Concurrency:**

- `kotlinx.coroutines` only. Every `CoroutineScope` has a clear owner with a defined cancellation lifecycle.
- No `GlobalScope`. Ever.
- `Flow`/`StateFlow`/`SharedFlow` over callbacks and `LiveData`.
- For shared mutable state: actor-style coroutine or `Mutex`. Never `synchronized`.
- New memory model is the only one we support. No legacy freezing logic.

---

## 4. Module layout

```
/shared               headless KMP module — business logic only
  /src/commonMain
  /src/androidMain
  /src/iosMain
  /src/macosMain
  /src/wasmJsMain     stretch
/iosApp               Xcode project, native SwiftUI, consumes /shared via SPM
/androidApp           Android entrypoint, native Jetpack Compose UI
/macosApp             macOS desktop, native SwiftUI/AppKit
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
| Dependency injection | **Koin** |
| Database | **SQLDelight**, or **Room** |
| Key-value storage | **multiplatform-settings** |
| ViewModel | **androidx.lifecycle.viewmodel** 2.9+ |
| DataStore | **androidx.datastore** 1.1+ |
| Testing | **kotlin.test** + **Turbine** + **kotlinx.coroutines.test** |
| Property tests | **Kotest** (when invariants are clear) |

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

The shared module **never** depends on a UI framework. No `androidx.compose.*` in `commonMain` or any source set inside `/shared`.

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

**`@Throws(...)`** — required on every `suspend fun` and any function that can throw across the boundary. Without it, exceptions become unrecoverable iOS crashes.

```kotlin
@Throws(NetworkException::class, CancellationException::class)
suspend fun fetchUser(id: String): User
```

### API design for Swift consumers

1. Verbs without the object. `open(url:)`, not `openUrl(url:)`.
2. `sealed interface` for results, not nullable + error code. SKIE makes it exhaustive.
3. `Flow<T>` over callbacks. Never a callback-based public API in `commonMain`.
4. No `Result<T>` at the boundary. Use a named sealed interface (`Outcome.Success` / `Outcome.Failure`).
5. No `Pair`/`Triple` in public API. Define a `data class`.
6. No star-projected generics across the boundary. Concrete types.
7. No companion-object factories for Swift-facing entry points. Top-level functions or constructors.
8. `Int` over `Long` when the range allows.

---

## 9. iOS distribution — KMMBridge → Maven → SPM

We use **Touchlab's KMMBridge** to publish the iOS framework. **CocoaPods is forbidden.** Do not add `cocoapods { ... }` blocks.

**Pipeline:**

1. Gradle builds an `XCFramework` with `iosArm64` + `iosSimulatorArm64` slices. No x86 simulator. SKIE-enhanced.
2. KMMBridge zips the `XCFramework` and **publishes the zip as a Maven artifact** to our Maven repository.
3. KMMBridge generates a `Package.swift` referencing the Maven-hosted zip by URL + checksum, and pushes that `Package.swift` to a dedicated **SPM Git repository** (tagged with the version).
4. The iOS app's `Package.swift` depends on the SPM repo, pinned to a version tag.

**Rules:**

- Maven coordinates and the SPM repo URL are configured in `gradle/kmmbridge.properties`.
- Versioning is automated by KMMBridge (timestamp- or git-tag-based, configured per branch).
- iOS engineers never open a Gradle file. They `swift package update` and consume tagged versions.
- Don't vendor `XCFramework` zips into the iOS repo. Everything flows through Maven + SPM.
- `Package.swift` is generated. Don't hand-edit.

**Local development override:** the iOS app supports a local SPM path pointing at the Gradle build output. Run `./gradlew :shared:assembleXCFramework`, then Xcode picks up changes without a publish step. The path override is documented in `iosApp/README.md`.

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
6. Done means: `./gradlew check` passes and `./gradlew :shared:linkDebugFrameworkIosArm64` builds clean.
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
- No EAP/RC/beta on `main`.
- No callback-based public APIs in `commonMain`.
- No UI dependencies in `/shared`.
- No hand-edited `Package.swift`.
- No vendored `XCFramework` in the iOS repo.
