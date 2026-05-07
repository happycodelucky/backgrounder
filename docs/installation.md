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
    backgrounder-shared = { module = "dev.backgrounder:shared", version.ref = "backgrounder" }
    ```

=== "Kotlin DSL (`build.gradle.kts`)"

    ```kotlin
    kotlin {
        sourceSets {
            commonMain.dependencies {
                implementation(libs.backgrounder.shared)
            }
        }
    }
    ```

## Android-only consumer

If your app is Android-only (not a KMP project), depend on the published Android artifact. It pulls `WorkManager` and `koin-androidx-workmanager` transitively.

```kotlin
dependencies {
    implementation("dev.backgrounder:backgrounder-android:0.1.0")
}
```

Add this to your `AndroidManifest.xml` so WorkManager doesn't auto-start before we sweep ephemeral work:

```xml
<provider
    android:name="androidx.work.impl.WorkManagerInitializer"
    android:authorities="${applicationId}.workmanager-init"
    tools:node="remove" />
```

## iOS consumer (Swift Package Manager)

Backgrounder publishes via [KMMBridge](https://touchlab.co/kmmbridge), which produces an `XCFramework` distributed through a Swift Package Manager Git repository.

```swift title="Package.swift"
// In your iOS app's Package.swift:
.package(url: "https://github.com/paulbates/backgrounder-spm.git", from: "0.1.0"),
```

Add every Backgrounder task id to `Info.plist`:

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>dev.example.app.sync</string>
</array>
```

The library reports a Kermit error during `registerHandlers()` for any task id missing from this list — failing close to the cause rather than at first `schedule()`.

## macOS consumer

Use the same SPM repository as iOS. The `XCFramework` carries a `macosArm64` slice in addition to the iOS ones.

## Local development override

When developing against an unpublished version of Backgrounder, point your iOS app's SPM dependency at a local checkout:

```bash
# In the Backgrounder repo:
./gradlew :shared:assembleBackgrounderXCFramework
# → shared/build/XCFrameworks/release/Backgrounder.xcframework

# In your iOS app's Package.swift, replace the remote URL with:
.package(path: "../backgrounder")
```

Xcode will pick up the local `XCFramework` on the next build — no publish step needed.

## Verify the install

After the launch sequence in [Getting started](getting-started.md) is in place, this snippet should compile and run on every platform:

```kotlin
val scheduler = GlobalContext.get<Scheduler>()
println(scheduler.guarantees())
```

Output (truncated, platform-dependent — see [Guarantees](concepts/guarantees.md)):

```
SchedulerGuarantees(survivesProcessDeath=true, survivesReboot=true, survivesForceQuit=false, ...)
```
