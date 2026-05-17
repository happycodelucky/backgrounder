/*
 * Backgrounder — KMP background work scheduling library.
 *
 * /backgrounder is the only subproject in v1: the headless KMP module that
 * contains all business logic. The directory name matches the Gradle module
 * name and the published Maven artifact id (`com.happycodelucky.backgrounder:backgrounder`),
 * mirroring the convention used by Ktor, kotlinx, and our sibling
 * `com.happycodelucky.reachable:reachable` library — the redundant ":shared"
 * coordinate would only add noise once published.
 *
 * Platform apps (androidApp, iOSApp, macOSApp) live outside this Gradle build
 * and consume /backgrounder via KMMBridge → Maven → SPM (CLAUDE.md §9).
 */

@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Project-level repos win; subprojects must not redeclare.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "backgrounder"

include(":backgrounder")
include(":background-monitor")
