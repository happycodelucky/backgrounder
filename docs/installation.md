# Installation

!!! info "Distribution status (TBD)"
    The first published artifact is being prepared. The snippets below show the *intended* coordinates and version; replace `0.1.0` with the actual published version once it lands. Until then, use the local build path described at the bottom of this page.

## Platform floors

| Platform | Floor                         |
| -------- | ----------------------------- |
| Android  | `arm64-v8a`, minSdk 30        |
| iOS      | iOS 18.0                      |
| macOS    | macOS 15.0 (Apple Silicon)    |
| Toolchain | Kotlin 2.3.20, Gradle 9.4.1, AGP 9.2.0, JVM target 21 |

These are deliberately tight floors — see [Concepts → Architecture](concepts/architecture.md) for why we don't ship Catalyst, x86, watchOS, or tvOS.

## Gradle — Kotlin Multiplatform consumer

If your app is itself a Kotlin Multiplatform project, depend on the `shared` artifact from `commonMain` and let KMP pull the right per-target slice automatically.

=== "Version catalog (`libs.versions.toml`)"

    ```toml
    [versions]
    backgrounder = "0.1.0"

    [libraries]
    backgrounder = { module = "com.happycodelucky.backgrounder:backgrounder", version.ref = "backgrounder" }
    ```

=== "Kotlin DSL (`build.gradle.kts`)"

    ```kotlin
    kotlin {
        sourceSets {
            commonMain.dependencies {
                implementation(libs.backgrounder)
            }
        }
    }
    ```

Backgrounder pulls `kotlinx.coroutines`, `kotlinx.serialization`, `multiplatform-settings`, `kermit`, and (on Android only) `androidx.work-runtime` + `androidx.startup-runtime` transitively. **No DI container is required** — the library uses constructor injection internally and a factory-closure seam for user code, so any DI graph you already use plugs in cleanly.

## Android-only consumer

If your app is Android-only (not a KMP project), depend on the published Android artifact:

```kotlin
dependencies {
    implementation("com.happycodelucky.backgrounder:backgrounder-android:0.1.0")
}
```

You'll need to install Backgrounder's `WorkerFactory` via `Configuration.Provider` (mandatory; see [Platforms → Android](platforms/android.md) for the full launch-sequence snippet) and disable WorkManager's default auto-init in your `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

## iOS consumer (Swift Package Manager)

Backgrounder publishes via [KMMBridge](https://touchlab.co/kmmbridge), which produces an `XCFramework` distributed through a Swift Package Manager Git repository.

```swift title="Package.swift"
// In your iOS app's Package.swift:
.package(url: "https://github.com/happycodelucky/backgrounder-spm.git", from: "0.1.0"),
```

Add every Backgrounder task id to `Info.plist`:

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>dev.example.app.sync</string>
</array>
```

The library reports a Kermit error during `backgrounder.start()` for any task id missing from this list — failing close to the cause rather than at first `schedule()`.

## macOS consumer

Use the same SPM repository as iOS. The `XCFramework` carries a `macosArm64` slice in addition to the iOS ones.

## Local development override

When developing against an unpublished version of Backgrounder, point your iOS app's SPM dependency at a local checkout:

```bash
# In the Backgrounder repo (raw Gradle, or `mise run xcframework` if you use mise):
./gradlew :backgrounder:assembleBackgrounderXCFramework
# → backgrounder/build/XCFrameworks/release/Backgrounder.xcframework

# In your iOS app's Package.swift, replace the remote URL with:
.package(path: "../backgrounder")
```

Xcode will pick up the local `XCFramework` on the next build — no publish step needed.

## Verify the install

After the launch sequence in [Getting started](getting-started.md) is in place, this snippet should compile and run on every platform:

```kotlin
println(backgrounder.scheduler.guarantees())
```

Output (truncated, platform-dependent — see [Guarantees](concepts/guarantees.md)):

```
SchedulerGuarantees(survivesProcessDeath=true, survivesReboot=true, survivesForceQuit=false, ...)
```
