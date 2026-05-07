@file:Suppress("UnstableApiUsage")

/*
 * Backgrounder — :shared module.
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
}

kotlin {
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
    // KMMBridge consumes the XCFramework that this block produces; it bundles all
    // the per-target slices (iosArm64 device, iosSimulatorArm64 simulator,
    // macosArm64 desktop) into a single artifact for SPM to reference.
    val xcf = XCFramework("Backgrounder")
    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Backgrounder"
            isStatic = true
            // Pin the bundle id so SKIE doesn't fall back to the framework name.
            binaryOption("bundleId", "dev.backgrounder.shared")
            // CLAUDE.md §8: SKIE wraps the framework export.
            xcf.add(this)
        }
    }

    // --- Android target (CLAUDE.md §1, §4) ----------------------------------
    // Use the new com.android.kotlin.multiplatform.library plugin's android {} block.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    android {
        namespace = "dev.backgrounder"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()

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

            // Koin core via BOM. Platform-specific Koin artifacts live in
            // platform source sets where the dispatching infrastructure lives.
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kermit.test)
            implementation(libs.koin.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.startup.runtime)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)
        }

        // iOS / macOS reach into Koin's GlobalContext directly (in BackgrounderIos.kt /
        // BackgrounderMacos.kt) to drive registerHandlers. commonMain only hides koin-core
        // behind `implementation` scope, so platforms that touch GlobalContext directly
        // need their own explicit dependency.
        getByName("appleMain").dependencies {
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
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
