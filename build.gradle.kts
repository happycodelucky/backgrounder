/*
 * Backgrounder — root build script.
 *
 * Plugins are declared here with `apply false`; they're applied in :shared.
 * This keeps `gradle/libs.versions.toml` as the single source of truth for
 * versions (CLAUDE.md §10).
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.skie) apply false
    // KSP is not used in v1 (no Koin Annotations, no codegen). Add when needed.
}
