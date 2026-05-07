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

    // Dokka v2: Kotlin API doc generator. Produces HTML for the public API of
    // every source set. The HTML is copied into docs/api/ for mkdocs to bundle.
    alias(libs.plugins.dokka)
}

// Apply Dokka to the :shared module and aggregate into docs/api/.
dokka {
    moduleName.set("Backgrounder")
}

dependencies {
    // Aggregate Dokka HTML from :shared into the root build (Dokka v2 pattern).
    dokka(project(":shared"))
}

/**
 * Copies Dokka v2 HTML output into docs/api/, where mkdocs picks it up.
 *
 * The aggregated HTML lives at build/dokka/html after dokkaGeneratePublicationHtml.
 * mkdocs looks at docs/api/ when it builds the site; CI runs Dokka before mkdocs.
 */
tasks.register<Copy>("copyDokkaToDocs") {
    group = "documentation"
    description = "Copies aggregated Dokka HTML into docs/api/ for mkdocs."

    dependsOn("dokkaGeneratePublicationHtml")
    from(layout.buildDirectory.dir("dokka/html"))
    into(layout.projectDirectory.dir("docs/api"))
}
