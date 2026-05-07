/*
 * Backgrounder — KMP background work scheduling library.
 *
 * /shared is the only subproject in v1: the headless KMP module that contains
 * all business logic. Platform apps (androidApp, iOSApp, macOSApp) live outside
 * this Gradle build and consume /shared via KMMBridge → Maven → SPM (CLAUDE.md §9).
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

include(":shared")
