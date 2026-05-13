@file:Suppress("UnstableApiUsage")

/*
 * Backgrounder — :backgrounder module.
 *
 * Headless KMP module: business logic only, no UI dependencies (CLAUDE.md §1, §7).
 * Targets are ARM-only per CLAUDE.md §1: iosArm64, iosSimulatorArm64, Android
 * arm64-v8a (via the new com.android.kotlin.multiplatform.library plugin), and
 * macosArm64. No x86, no Catalyst, no watchOS / tvOS / Linux / Windows.
 */

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.skie)
    alias(libs.plugins.dokka)
    // Vanniktech Maven Publish (CLAUDE.md §9) — publishes signed artifacts to
    // the Sonatype Central Portal. Applies `maven-publish` and `signing`
    // transitively, so we don't apply `maven-publish` separately. The
    // `mavenPublishing { }` block below configures the Central Portal target,
    // POM metadata, and in-memory GPG signing.
    //
    // KMMBridge / GitHub Packages distribution is future work for non-KMP
    // Swift consumers who need a hosted XCFramework zip via SPM. For now,
    // Maven Central is the only active distribution channel. See CLAUDE.md §9
    // and git history for the original KMMBridge architectural sketch.
    alias(libs.plugins.maven.publish)
}

kotlin {
    // Library code: every public API symbol must carry an explicit visibility
    // modifier (public / internal / private). Without this, a contributor can
    // forget a modifier on a top-level helper and silently widen the published
    // API surface — which becomes a permanent contract once shipped to Maven
    // Central. The compiler now enforces what was previously convention.
    //
    // See https://kotlinlang.org/docs/whatsnew14.html#explicit-api-mode-for-library-authors
    explicitApi()

    // CLAUDE.md §4: applyDefaultHierarchyTemplate. Don't hand-roll source set wiring.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        // Coalesce iosMain + macosMain into a shared "appleMain" intermediate. The
        // BGTask coroutine bridge pattern (SupervisorJob + invokeOnCompletion) is
        // identical between iOS and macOS — see plan §iOS / §macOS.
        common {
            group("apple") {
                withIos()
                withMacos()
            }
        }
    }

    // --- Apple targets (CLAUDE.md §1) ---------------------------------------
    // The XCFramework aggregator bundles all three slices (iosArm64 device,
    // iosSimulatorArm64, macosArm64) into a single `Backgrounder.xcframework`
    // directory at `build/XCFrameworks/{debug,release}/`.
    //
    // Local-dev consumption: sample apps inside this repo reference the debug
    // XCFramework via `.binaryTarget(path: …)` in the root `Package.swift`.
    // Run `mise run spm:dev` to rebuild that debug artifact; Xcode picks it up
    // without a publish step.
    //
    // Maven Central distribution does NOT use the XCFramework — it publishes
    // the per-target klibs and `kotlinMultiplatform` metadata; KMP consumers
    // resolve those automatically via `implementation("com.happycodelucky.backgrounder:backgrounder:X.Y.Z")`.
    //
    // Remote SPM distribution (hosted XCFramework zip → Package.swift → SPM)
    // is future work; see CLAUDE.md §9 for the architectural sketch.
    val xcf = XCFramework("Backgrounder")
    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Backgrounder"
            isStatic = true
            // Pin the bundle id so SKIE doesn't fall back to the framework name.
            binaryOption("bundleId", "com.happycodelucky.backgrounder.shared")
            // CLAUDE.md §8: SKIE wraps the framework export.
            xcf.add(this)
        }
    }

    // --- Android target (CLAUDE.md §1, §4) ----------------------------------
    // Use the new com.android.kotlin.multiplatform.library plugin's android {} block.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    android {
        namespace = "com.happycodelucky.backgrounder"
        compileSdk =
            libs.versions.android.compile.sdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.min.sdk
                .get()
                .toInt()

        // CLAUDE.md §1: arm64-v8a only. The new KMP Android plugin doesn't wire
        // ABI filters directly; consumers' app modules pin the splits. The
        // shared library itself produces all ABIs the build asks for. We test
        // arm64-v8a only; document this in README.

        withHostTestBuilder { /* enables androidUnitTest */ }
    }

    // --- JVM toolchain (CLAUDE.md §2: JVM target 21) ------------------------
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        // K2 stable APIs only (CLAUDE.md §3).
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        // Fail builds on stable-API misuse, not just experimental.
        allWarningsAsErrors.set(false) // bump to true once codebase settles.
    }

    // Per-target JVM toolchain knobs — Android compilation needs JVM target 21.
    targets.withType<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget>().configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.kermit)
            // Drives the pre-execution `WorkConstraints.networkRequired` gate.
            // Apple platforms back this with `nw_path_monitor` (Network framework);
            // Android relies on WorkManager's native gating, but `reachable` is
            // still on commonMain because the public type leaks into builder
            // signatures uniformly across all platforms.
            implementation(libs.reachable)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kermit.test)
            // Upstream-blessed FakeReachability + withFakeReachability install
            // helper. Replaces our hand-rolled commonTest fake; tests interact
            // with `Reachability.shared` directly via the install/uninstall hook.
            implementation(libs.reachable.testing)
        }

        androidMain.dependencies {
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.startup.runtime)
            implementation(libs.kotlinx.coroutines.android)
        }

        // appleMain (iOS + macOS) needs the no-arg multiplatform-settings
        // companion artifact for `NSUserDefaultsSettings(NSUserDefaults(suiteName:))`.
        getByName("appleMain").dependencies {
            implementation(libs.multiplatform.settings.no.arg)
        }

        // androidUnitTest source set is created by withHostTestBuilder above.
        // Configure its deps here.
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.work.testing)
            implementation(libs.turbine)
        }
    }
}

skie {
    // SKIE handles the Kotlin → Swift bridge enhancements (CLAUDE.md §8):
    // exhaustive sealed switching, suspend → async/await, Flow → AsyncSequence,
    // default-arg overloads. We keep all defaults on; tighten only when something
    // bites.
    features {
        group {
            // Keep SKIE-generated Swift names visible in stack traces.
            // (Default behaviour, listed for clarity.)
        }
    }
    analytics {
        // Disable opt-in analytics; we'll revisit if useful.
        disableUpload.set(true)
    }
}

// KMMBridge / GitHub Packages distribution previously lived here. It has
// been removed in favour of Maven-Central-only distribution; see the
// `mavenPublishing { }` block below. The previous wiring is in git
// history — restore from there when an SPM-side delivery story is needed
// for non-KMP iOS / macOS consumers again.

// --- Maven Central publishing (CLAUDE.md §9) ---------------------------------
//
// Single distribution channel: Maven Central via vanniktech's `maven-publish`
// plugin. One Gradle invocation publishes:
//
//   * The Android AAR.
//   * The `kotlinMultiplatform` metadata module (`backgrounder-0.2.0.module`)
//     that ties every target together so KMP consumers can write
//     `implementation("com.happycodelucky.backgrounder:backgrounder:X.Y.Z")` from
//     `commonMain` and have Gradle resolve the right per-target klib.
//   * Per-target klibs: `backgrounder-iosarm64`, `backgrounder-iossimulatorarm64`,
//     `backgrounder-macosarm64`, `backgrounder-android` — one Maven artifact per
//     publication the KMP plugin registers automatically.
//   * Sources / javadoc jars next to each, with detached GPG signatures.
//
// Consumers in another KMP project just add `mavenCentral()` to their
// repositories and depend on the coordinate; no extra setup needed on the
// consumer side.
//
// Credentials: vanniktech reads `mavenCentralUsername`, `mavenCentralPassword`,
// `signingInMemoryKey`, and `signingInMemoryKeyPassword` as Gradle properties.
// Gradle auto-populates those from `ORG_GRADLE_PROJECT_*` env vars in CI. The
// release workflow wires the four `MAVEN_CENTRAL_*` GitHub Actions secrets to
// those env names. Locally these properties are unset and signing is silently
// skipped — fine for `publishToMavenLocal` dry-runs.
mavenPublishing {
    // SonatypeHost.CENTRAL_PORTAL targets the new central.sonatype.com
    // endpoint. Do NOT use SonatypeHost.DEFAULT — that's the legacy
    // s01.oss.sonatype.org OSSRH endpoint, which Sonatype is decommissioning.
    //
    // `automaticRelease = false` is intentional and load-bearing. It controls
    // what `./gradlew :backgrounder:publishToMavenCentral` does:
    //   * `false` — uploads to the Central Portal staging area and stops.
    //     The deployment sits in "validated" state until someone clicks
    //     Publish (or Drop) in the Portal web UI. This is what makes the
    //     release workflow's `dryRun=true` branch an actual dry run.
    //   * `true` — uploads *and* auto-releases on success. Every "dry run"
    //     becomes an irreversible public publish. Do NOT flip this without
    //     understanding the cascade in `.github/workflows/release.yml`.
    //
    // The `publishAndReleaseToMavenCentral` task is unaffected by this flag —
    // it always closes & releases the deployment regardless, and the
    // release workflow uses it on the `dryRun=false` branch.
    publishToMavenCentral(automaticRelease = false)

    // Required by Central — every artifact (jar, aar, klib, module, pom)
    // must carry a detached GPG signature next to it. signAllPublications()
    // applies the signing plugin across every publication the KMP plugin
    // registered (`kotlinMultiplatform`, `android`, `iosArm64`,
    // `iosSimulatorArm64`, `macosArm64`). Central rejects unsigned uploads.
    signAllPublications()

    // The coordinate triple. groupId here intentionally matches the
    // namespace claimed on the Central Portal (`com.happycodelucky`); the
    // root build.gradle.kts wires `group = "com.happycodelucky.backgrounder"`
    // and we mirror that here for the artifact suffix.
    coordinates(
        groupId = "com.happycodelucky.backgrounder",
        artifactId = "backgrounder",
        version = project.version.toString(),
    )

    pom {
        name.set("Backgrounder")
        description.set(
            "Kotlin Multiplatform background-work scheduler for iOS, macOS, and Android.",
        )
        url.set("https://github.com/happycodelucky/backgrounder")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("happycodelucky")
                name.set("Paul Bates")
                url.set("https://github.com/happycodelucky")
            }
        }
        scm {
            url.set("https://github.com/happycodelucky/backgrounder")
            connection.set("scm:git:https://github.com/happycodelucky/backgrounder.git")
            developerConnection.set("scm:git:ssh://git@github.com/happycodelucky/backgrounder.git")
        }
    }
}
