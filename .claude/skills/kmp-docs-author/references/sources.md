# Source references

URLs the agent can re-consult. The patterns in `doc-patterns.md` and `voice.md` were synthesized from these. When in doubt about how a specific page should read, fetch one of these and study the shape.

---

## Square family — the primary reference

Square's open-source documentation is the strongest reference set for Kotlin libraries on mkdocs Material. The repo's `mkdocs.yml` is intentionally modeled on Wire's setup.

| Library | Docs URL | What to study |
|---|---|---|
| Wire | https://square.github.io/wire/ | The canonical mkdocs Material setup. Explicit `nav:`, version macro, `strict: true`. Study the Overview, Getting Started, and Recipes. |
| OkHttp | https://square.github.io/okhttp/ | The recipes section is the gold standard. Each page is task-titled, code-first, with a "Caveats" close. |
| Retrofit | https://square.github.io/retrofit/ | Concise reference. Study the Configuration page for how to document a builder API. |
| Moshi | https://github.com/square/moshi/blob/master/README.md | Single-README documentation done well. Study how it sequences install → first call → advanced. |
| Okio | https://square.github.io/okio/ | "Why Okio?" essay-style overview. The model for an Overview page that needs to make an argument. |

---

## Coil — Kotlin Multiplatform image loading

Coil 3.x is the strongest KMP-native documentation in the ecosystem. Their content-tabs pattern (Compose / Coil / Image loaders) is the template for our Android / iOS / macOS tabs.

| Resource | URL | What to study |
|---|---|---|
| Coil 3.x docs | https://coil-kt.github.io/coil/ | Multiplatform install matrix. Per-target tabs. |
| Coil GitHub | https://github.com/coil-kt/coil | README and CONTRIBUTING.md — model of an operational contributor guide. |

---

## Ktor — JetBrains framework documentation

Ktor's docs are deep and structured (more framework than library), but the Plugins framing and the "Routing" concept page are worth studying for cross-cutting extensibility patterns.

| Resource | URL | What to study |
|---|---|---|
| Ktor docs | https://ktor.io/docs/ | Plugin pattern documentation; suspend / Flow examples. |

Note: Ktor uses Hugo, not mkdocs. The structural patterns translate; the syntax doesn't.

---

## Koin — DI documentation done friendly

Koin's docs are friendly without being marketing-fluffy. The "no annotation processor" framing on the homepage is the model for the "bring your own DI" pitch when the library doesn't impose one.

| Resource | URL | What to study |
|---|---|---|
| Koin docs | https://insert-koin.io/docs/quickstart/kotlin | Quickstart sequencing. Concept pages for Modules, Scopes, Definitions. |
| Koin GitHub | https://github.com/InsertKoinIO/koin | CONTRIBUTING.md — clear bug template and PR checklist. |

---

## Dagger / Hilt — Android DI documentation at scale

Dagger and Hilt's docs are the reference for documenting a complex DI library across multiple platforms. Useful for: install matrix per platform, version compatibility tables, "manual DI for comparison" framing.

| Resource | URL | What to study |
|---|---|---|
| Dagger | https://dagger.dev/ | Component / Module / Provides documentation pattern. |
| Hilt | https://dagger.dev/hilt/ | Android-specific install and setup pages. |
| Hilt Android dev docs | https://developer.android.com/training/dependency-injection/hilt-android | Tutorial-style approach — useful for a Getting Started page. |

---

## JetBrains KMP docs — the platform reference

The canonical reference for Kotlin Multiplatform itself. Useful for: `expect`/`actual` documentation pattern, target tier tables, supported-version callouts, "shared module" framing.

| Resource | URL | What to study |
|---|---|---|
| KMP overview | https://kotlinlang.org/docs/multiplatform.html | Top-level structure and tier classification. |
| Hierarchical project structure | https://kotlinlang.org/docs/multiplatform-hierarchy.html | Diagram + prose + table — the model for a concept page. |
| Add dependencies | https://kotlinlang.org/docs/multiplatform-add-dependencies.html | Per-target install matrix. |
| Dokka | https://kotlinlang.org/docs/dokka-introduction.html | KDoc → site reference. |

---

## Tooling references

| Resource | URL | What to study |
|---|---|---|
| mkdocs-material | https://squidfunk.github.io/mkdocs-material/ | Theme reference — features, content tabs, admonitions. |
| pymdownx extensions | https://facelessuser.github.io/pymdown-extensions/ | Tabbed, snippets, superfences, highlight. |
| mkdocs-macros | https://mkdocs-macros-plugin.readthedocs.io/ | The `{{ version }}` substitution mechanism. |
| Keep a Changelog | https://keepachangelog.com/ | The convention adapted by Wire for `CHANGELOG.md`. |

---

## Style references

| Resource | URL | What to study |
|---|---|---|
| Google Developer Documentation Style Guide | https://developers.google.com/style | Reference for active voice, sentence structure, tone. |
| Microsoft Writing Style Guide | https://learn.microsoft.com/en-us/style-guide/welcome/ | Reference for technical writing conventions. |
| Diátaxis | https://diataxis.fr/ | The taxonomy that explains why Tutorials / How-to / Reference / Explanation are different page types. Our Recipes ↔ How-to, Concepts ↔ Explanation. |

---

## When to fetch vs when to remember

**Don't fetch on every authoring run.** The patterns in `doc-patterns.md` and `voice.md` are durable. Refetch only when:

- Authoring a page type that doesn't have a template yet (rare — the five templates cover every canonical section).
- A specific source's structure has materially changed since `doc-patterns.md` was written.
- The user explicitly asks "look at how X documents Y."

**Always cite when borrowing a non-obvious pattern.** If a page uses a structural choice from a specific source (e.g. Okio's essay-style Overview), the page's accompanying commit message or PR description names the source — but the page itself does not (don't insert citations into user-facing docs).
