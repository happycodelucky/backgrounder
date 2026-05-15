# Installation

Backgrounder publishes to Maven Central as a Kotlin Multiplatform artifact.
The Android AAR is consumed directly from Gradle. Apple consumers come in via
the same artifact's `iosArm64`, `iosSimulatorArm64`, and `macosArm64` klibs —
i.e. by depending on `:backgrounder` from a KMP project.

A native Swift Package Manager distribution is planned for a later release
(see [Apple-side SPM](#apple-side-spm-roadmap) below).

## Platform floors

| Platform  | Floor                                                 |
| --------- | ----------------------------------------------------- |
| Android   | `arm64-v8a`, minSdk 30                                |
| iOS       | iOS 18.0                                              |
| macOS     | macOS 15.0 (Apple Silicon)                            |
| Toolchain | Kotlin 2.3.x (K2), Gradle 9.x, AGP 9.x, JVM target 21 |

These are deliberately tight floors — see [Concepts → Architecture](concepts/architecture.md)
for why we don't ship Catalyst, x86, watchOS, or tvOS.

## Gradle — Kotlin Multiplatform consumer

If your app is itself a Kotlin Multiplatform project, depend on the
`backgrounder` artifact from `commonMain` and let KMP pull the right
per-target slice automatically. Maven Central is on the default repository
list, so no `repositories { }` block changes are needed.

=== "Version catalog (`libs.versions.toml`)"

    ```toml
    [versions]
    backgrounder = "{{ version }}"

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

Backgrounder pulls `kotlinx.coroutines`, `kotlinx.serialization`,
`multiplatform-settings`, `kermit`, and (on Android only)
`androidx.work-runtime` + `androidx.startup-runtime` transitively.
**No DI container is required** — the library uses constructor injection
internally and a factory-closure seam for user code, so any DI graph you
already use plugs in cleanly.

## Android-only consumer

If your app is Android-only (not a KMP project), depend on the published
Android artifact:

```kotlin
dependencies {
    implementation("com.happycodelucky.backgrounder:backgrounder-android:{{ version }}")
}
```

You'll need to install Backgrounder's `WorkerFactory` via
`Configuration.Provider` (mandatory; see [Platforms → Android](platforms/android.md)
for the full launch-sequence snippet) and disable WorkManager's default
auto-init in your `AndroidManifest.xml`:

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

## iOS / macOS consumer

If you're working from a KMP project, the iOS and macOS targets are consumed
transparently via the `kotlinMultiplatform` metadata published alongside the
Android AAR. Depend on `:backgrounder` from `commonMain` exactly as shown in
the [Gradle — Kotlin Multiplatform consumer](#gradle-kotlin-multiplatform-consumer)
section above; KMP resolves the `iosArm64`, `iosSimulatorArm64`, and
`macosArm64` slices for you. SKIE bridging happens at your project's framework
build time, not the library's.

Add the tick identifier plus one entry per `WorkRequest.OneTime` task id you
schedule to your app's `Info.plist`:

```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>dev.example.app.background-tick</string>  <!-- mandatory: matches tickIdentifier -->
    <string>dev.example.app.sync</string>             <!-- one-shot WorkRequest.OneTime -->
</array>
```

The library reports a Kermit error during `backgrounder.start()` for any task
id missing from this list — failing close to the cause rather than at first
`schedule()`.

## Apple-side SPM (roadmap)

There's no standalone `.xcframework` or `Package.swift` published yet.
Pure-Swift apps that don't already use Kotlin Multiplatform can't consume the
library directly at the moment. A native SPM distribution — a KMMBridge-style
`XCFramework` flowing through Maven and a dedicated SPM repository — is on the
roadmap for a later release.

If you're working from a KMP project today, the iOS / macOS targets are
already consumed transparently via the published `kotlinMultiplatform`
metadata, as described above.

## Local development override

When developing against an unpublished version of Backgrounder, build the
`XCFramework` locally and point your iOS / macOS app at the build output:

```bash
# In the Backgrounder repo (raw Gradle, or `mise run xcframework` if you use mise):
./gradlew :backgrounder:assembleBackgrounderXCFramework
# → backgrounder/build/XCFrameworks/release/Backgrounder.xcframework
```

Xcode picks up the local `XCFramework` on the next build — no publish step
needed. For a KMP consumer, `./gradlew :backgrounder:publishToMavenLocal`
plus `mavenLocal()` on the consumer's repository list works the same way.

## Verify the install

After the launch sequence in [Getting started](getting-started.md) is in
place, this snippet should compile and run on every platform:

```kotlin
println(backgrounder.guarantees())
```

Output (truncated, platform-dependent — see [Guarantees](concepts/guarantees.md)):

```
SchedulerGuarantees(survivesProcessDeath=true, survivesReboot=true, survivesForceQuit=false, ...)
```
