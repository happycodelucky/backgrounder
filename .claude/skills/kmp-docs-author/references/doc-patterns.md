# Documentation patterns — Kotlin library reference bible

Captured once. The synthesis of what makes the best Kotlin library documentation effective. Each pattern is named, attributed, and reusable. Load this on every authoring run.

The reference set: Square (Wire, OkHttp, Retrofit, Moshi, Okio), Coil 3.x, Ktor, Koin, Dagger/Hilt, JetBrains KMP docs.

---

## Navigation patterns

### Pattern: Explicit, flat-as-possible nav

**What.** Hand-author the navigation tree in `mkdocs.yml`. Never rely on auto-discovery. Keep the tree shallow — two levels at most.

**Why.** Auto-discovery makes page ordering non-deterministic and lets new files leak into the public site without review. Explicit nav makes "is this page shipped?" a code-review question. Shallow trees make navigation discoverable on mobile and reduce decision fatigue.

**Cited from.** Wire (`square.github.io/wire`), OkHttp, Retrofit. All three use a flat top-level: Overview → Getting started → Recipes → reference. None nest more than two deep.

**Example shape (mirroring Wire):**

```yaml
nav:
  - Overview: index.md
  - Getting started: getting-started.md
  - Installation: installation.md
  - Concepts:
    - concepts/architecture.md
    - concepts/lifecycle.md
  - Recipes:
    - recipes/one-shot.md
    - recipes/periodic.md
  - Platforms:
    - platforms/android.md
    - platforms/ios.md
  - Contributing: contributing.md
  - Changelog: changelog.md
```

**Rejected alternatives.** Ktor uses a deep concept hierarchy (Plugins → Server → Routing → …); it works because Ktor is a framework with hundreds of pages, but for a focused library it's overkill and hides everything below the fold.

---

### Pattern: "Recipes" as a top-level section

**What.** A dedicated top-level section named `Recipes` containing one page per task. Each page answers "how do I do X?" in five minutes or less.

**Why.** Users land on docs to do a thing, not to learn an architecture. A Recipes section is the answer to "I have a task, where do I look?" Concepts and reference exist for the second-pass reader.

**Cited from.** OkHttp's recipes section is the canonical example — each page is a self-contained problem statement → code → caveats. Wire mirrors the same shape.

**Format conventions:**

- Page title is imperative: `Schedule a one-shot`, not `One-shot scheduling`.
- First sentence states the task. No preamble.
- Code block within the first 200 words.
- Every recipe ends with a `## What can go wrong` (or `## Common mistakes`) H2. This is non-negotiable — it's what separates documentation from a tutorial.

---

## Page taxonomy

### Pattern: Six-section canonical structure

**What.** Every well-documented Kotlin library converges on six top-level sections: Overview, Getting Started/Installation, Concepts, Recipes, Platforms (when multi-platform), Contributing, Changelog.

**Why.** This shape lets readers self-select: a new user enters at Overview → Getting Started, an integrator goes to Recipes, an advanced user reads Concepts, a maintainer reads Contributing. Skipping a section forces readers into the wrong path.

**Cited from.** Wire, OkHttp, Retrofit, Coil, Koin all converge here independently. Dagger/Hilt adds a "Tutorials" section but the core six are present.

**Per-section purpose, in one line each:**

| Section | Answers |
|---|---|
| Overview | "What does this library do, and is it for me?" |
| Getting started / Installation | "How do I add it to my build and run my first call?" |
| Concepts | "What model do I need to hold in my head to use this well?" |
| Recipes | "How do I do specific task X?" |
| Platforms | "What's different on each platform I ship?" |
| Contributing | "How do I file a bug, propose a change, build the source?" |
| Changelog | "What's new, what broke, what's deprecated?" |

---

### Pattern: Overview as a one-screen pitch + what-you-write code block

**What.** The Overview page (`index.md`) gives the library's purpose in two sentences, lists what it is and isn't, and shows a representative code block within the first screen.

**Why.** Readers decide in 10 seconds whether your library is what they want. The Overview is the only chance to make that decision easy.

**Cited from.** Okio's "Why Okio?" page is the gold standard — it's an essay, but it's an essay that opens with a concrete code block. OkHttp's index opens with a five-line GET. Coil opens with a one-line `ImageRequest`.

**Skeleton:**

```markdown
# <Library name>

<One-sentence statement of purpose. Active voice, present tense.>

<One-sentence statement of what's distinctive about it.>

\```kotlin
// The most representative possible call. Five lines or fewer.
\```

## What it does

- <Capability 1>
- <Capability 2>
- <Capability 3>

## What it doesn't do

- <Non-goal 1, with the recommended alternative>
- <Non-goal 2, with the recommended alternative>
```

---

## Voice and tone

### Pattern: Active voice, present tense, user-first framing

**What.** Lead with what the user does or sees. "The scheduler runs your worker after the delay." Not "Your worker will be run by the scheduler when the delay elapses."

**Why.** Active present-tense prose is shorter, scannable, and reads less like a legal disclaimer. User-first framing ("Schedule a one-shot") puts the reader's task at the head of the sentence.

**Cited from.** All Square docs. JetBrains' Kotlin docs are slightly more passive but their KMP migration guides have moved active.

**Forbidden words.** `powerful`, `easy-to-use`, `blazingly fast`, `simply`, `just`, `seamlessly`, `cutting-edge`, `state-of-the-art`, `under the hood` (almost always a tell that the next sentence will be vague). Replace with the concrete fact.

---

### Pattern: Lead with the user's task, not the library's capability

**What.** Page titles and intros are framed around what the reader does, not what the library supports.

**Why.** Capability-first framing forces the reader to translate "library supports X" into "I want to do Y." Task-first framing skips that step.

| Capability-first (avoid) | Task-first (use) |
|---|---|
| "Backgrounder supports periodic scheduling" | "Schedule periodic work" |
| "OkHttp provides caching" | "Cache responses" |
| "Coil handles GIFs via decoders" | "Display an animated GIF" |

**Cited from.** OkHttp recipe titles: "Posting form parameters", "Canceling a call", "Detecting MIME types". Wire: "Generate Kotlin code", "Send a request".

---

## Code example conventions

### Pattern: Code block within the first 200 words

**What.** Every page that involves an API surface shows code within the first screen.

**Why.** Prose is slower to scan than code. A reader who lands on a recipe page should see the call before they finish the first paragraph. If you can't show code in the first 200 words, the page probably needs to be split.

**Cited from.** Square's recipes consistently put code above the fold. Coil's index is code in the first 50 words.

---

### Pattern: Realistic, runnable, copy-pasteable

**What.** Code samples must be complete enough to copy-paste. Include the imports if they're non-obvious. Use real type names from the library, not placeholders.

**Why.** Snippets with `// ...` or `MyType` force the reader to re-derive what to write. The snippet is the documentation; everything else is gloss.

**Cited from.** Retrofit and Wire both ship complete samples. Counter-example: framework docs that say `val client = createClient(...)` — useless.

**Concrete rules:**

- Show imports when the symbol could come from multiple packages.
- Use `kotlin.time.Duration.Companion.seconds` syntax — this reads at the call site.
- Don't elide error handling unless the recipe is explicitly about the happy path. If you elide it, say so: `// error handling omitted; see [Cancelling a call]`.

---

### Pattern: Platform deltas in `pymdownx.tabbed` blocks

**What.** When a snippet differs per platform, use mkdocs-material content tabs labelled exactly `Common`, `Android`, `iOS`, `macOS`. The tabs link across pages (set `content.tabs.link` in theme features) so a reader on iOS stays on iOS as they navigate.

**Why.** Platform-specific snippets buried in prose are missed. Tabs put them at the same visual level and let cross-page navigation remember the reader's choice.

**Cited from.** Coil 3.x uses this pattern for Android vs Compose vs Image loaders. JetBrains KMP docs use it for `expect`/`actual` examples.

**Casing rules.** `iOS`, `macOS`, `tvOS`, `watchOS`, `iPadOS`, `visionOS` — never lowercased. Apple platform brand names. The standard Kotlin acronym convention does not apply.

**Example:**

```markdown
=== "Common"
    \```kotlin
    val backgrounder = Backgrounder.start(...)
    \```

=== "Android"
    \```kotlin
    // Android-specific construction
    \```

=== "iOS"
    \```swift
    let backgrounder = try Backgrounder.start(...)
    \```
```

---

### Pattern: Show the Swift call site for KMP libraries

**What.** When documenting a public API consumed from Swift, show both the Kotlin declaration *and* the Swift call site. Tab them as `Kotlin` and `Swift`.

**Why.** A KMP library lives or dies by how it reads from the consumer's language. Showing the Swift call site is the only way to verify the reader understands the API.

**Cited from.** Touchlab's SKIE docs (the only KMP library that gets this consistently right). Most KMP libraries fail this — they show only Kotlin and leave the Swift reader to guess.

---

## Installation snippets

### Pattern: Version through a build-time macro, never hard-coded

**What.** Install snippets show `{{ version }}` (or library-specific equivalent). A mkdocs plugin (mkdocs-macros) resolves the placeholder at site build time from the latest published release.

**Why.** Hard-coded versions go stale within weeks. Every stale `0.9.0` in install snippets is a paper cut for new users and a maintenance burden for owners.

**Cited from.** Wire (`{{ versions.wire }}`), OkHttp (`{{ versions.okhttp }}`). Both use the same plugin.

**Example:**

```markdown
\```kotlin
// build.gradle.kts
dependencies {
    implementation("com.happycodelucky.backgrounder:backgrounder:{{ version }}")
}
\```
```

---

### Pattern: Per-target install matrix for KMP libraries

**What.** A KMP library's installation page shows the per-target dependency notation in a table or per-target tabs: shared module, Android consumer, iOS SPM, macOS SPM. Each row/tab links to its respective build file format.

**Why.** A KMP user is integrating across two or three build systems at once. They need to see the whole picture in one place.

**Cited from.** Coil 3.x installation page. JetBrains KMP "Add dependencies" page.

**Example:**

| Consumer | Snippet |
|---|---|
| KMP `commonMain` | `implementation("com.happycodelucky.backgrounder:backgrounder:{{ version }}")` |
| Android-only Gradle | `implementation("com.happycodelucky.backgrounder:backgrounder:{{ version }}")` (Gradle resolves the Android variant) |
| iOS SPM (sample app) | `.package(path: "../path/to/backgrounder")` |
| iOS SPM (vendored) | XCFramework via `.binaryTarget(path:)` |

---

## "What can go wrong" — the make-or-break section

### Pattern: Mandatory `What can go wrong` H2 on every recipe

**What.** Every recipe-style page closes with a `## What can go wrong` (or `## Common mistakes`) section. Bulleted, terse, action-oriented.

**Why.** This is the section that turns docs from a tutorial into reference material. It's also the section new users skim first when their code doesn't work. A recipe without it is incomplete.

**Cited from.** OkHttp recipes universally include "Caveats" or similar. Okio's "Why Okio?" enumerates trade-offs.

**Format:**

```markdown
## What can go wrong

- **<short cause>** — <one-line consequence + how to fix>.
- **<short cause>** — <one-line consequence + how to fix>.
```

**Style rules:**

- Lead with the cause, not the symptom. The reader's symptom led them here; what they need is the cause.
- One line per item. If it needs two lines, it needs its own H3.
- Link to the relevant recipe or concept page when applicable.

---

### Pattern: Platform-specific gotchas as their own callout

**What.** When a gotcha is platform-specific, surface it via a `!!! warning "iOS"` admonition rather than burying it in prose.

**Why.** Platform-specific bugs are the most expensive class of documentation failure — readers on the affected platform need them at eye level.

**Example:**

```markdown
!!! warning "iOS"
    Every `TaskId` you schedule must appear in `BGTaskSchedulerPermittedIdentifiers`
    in your app's Info.plist. Missing entries fail at `schedule()` time with
    `ScheduleOutcome.Rejected`.
```

---

## Concepts pages

### Pattern: One concept per page, named as the noun

**What.** Each concept gets a dedicated page. The page title is the noun (`Architecture`, `Lifecycle`, `Worker context`, `Guarantees`) — never a verb phrase.

**Why.** Concept pages are reference material. Readers find them by searching for the noun they encountered in code or in another page.

**Cited from.** Koin's concept pages (`Modules`, `Scopes`, `Definitions`). Wire's (`Schemas`, `Profiles`).

---

### Pattern: Diagram + prose + table

**What.** A concept page that explains an architecture or model uses, in order: a diagram (Mermaid), a few paragraphs of prose, then a summary table of the moving parts.

**Why.** Different readers learn from different formats. The diagram serves visual scanners; the prose serves linear readers; the table serves reference-lookup readers. All three on one page hits everyone.

**Cited from.** JetBrains KMP "Hierarchical project structure" page. Coil 3.x "Image loaders" page.

---

## Platform pages

### Pattern: One page per platform, structured identically

**What.** Each supported platform gets a dedicated page. Each page follows the same skeleton: requirements → manifest/config setup → API differences → known limitations.

**Why.** Identical structure means a reader who has read one platform page can navigate any other in seconds.

**Cited from.** Coil 3.x platform pages. KMM "Connect to platform-specific APIs" pages.

**Skeleton:**

```markdown
# <Platform name>

<One-sentence summary of what's distinctive on this platform.>

## Requirements

- <Min OS version>
- <Required entitlements / permissions>
- <Required Info.plist or AndroidManifest entries>

## Setup

\```kotlin or xml
<minimum config>
\```

## Behavior

<What this platform does that's different from the others.>

## Known limitations

- <limitation 1>
- <limitation 2>
```

---

## Contributing pages

### Pattern: Bug report template + PR checklist + build instructions

**What.** Contributing page has three sections: how to file a bug (with required fields), how to propose a change (PR checklist), how to build and test the source.

**Why.** Most contributing pages are vague aspiration. The good ones are operational: fill in this template, run these commands, check these boxes.

**Cited from.** Koin's contributing page. JetBrains' Kotlin contribution guide.

**Required template fields for bug reports:**

- Library version
- Platform(s) affected
- Minimal repro (code snippet or repo link)
- Expected vs actual behavior
- Workaround if any

---

## Changelog pages

### Pattern: Reverse-chronological, semver-grouped, breaking-changes flagged

**What.** Most-recent first. One H2 per release tagged with version + date. Within a release: `### Breaking changes` (if any) → `### New` → `### Fixed` → `### Deprecated`.

**Why.** A changelog is read for two reasons: "what's new since I last upgraded?" and "is it safe to upgrade?" Both questions need breaking changes at the top.

**Cited from.** Wire's `CHANGELOG.md`. Coil 3.x. Both follow Keep-a-Changelog conventions adapted for Kotlin libraries.

**Format:**

```markdown
## 1.2.0 — 2026-05-15

### Breaking changes
- `Foo.bar(x: Int)` is now `Foo.bar(value: Int)` for Swift call-site clarity.

### New
- `WorkRequest.Periodic` now supports flex intervals.

### Fixed
- iOS Info.plist validation no longer crashes on duplicate identifiers.

### Deprecated
- `OldType` — use `NewType` instead. Removal in 2.0.
```

---

## DI, framework integration, and "bring your own" framing

### Pattern: "No annotation processor required" framing

**What.** When the library does not require a DI container or annotation processor, say so explicitly in the Overview and again in Getting Started.

**Why.** Kotlin developers who've been burned by KAPT or Hilt builds will not adopt a library if they think it adds another annotation processor to their build. Saying "no annotation processor" up front removes the objection.

**Cited from.** Koin's homepage opens with this exact framing. Multiplatform-Settings does the same.

---

### Pattern: "Bring your own DI" — show wiring with multiple options

**What.** When the library is DI-agnostic, the install or getting-started page shows wiring with at least two DI options: hand-wired construction and one container (Koin, Hilt, kotlin-inject). No preference is expressed; both are first-class.

**Why.** Readers come from different DI cultures. Showing only one looks like an endorsement; showing none looks like a gap. Showing two communicates "we don't care, here's how to plug in."

**Cited from.** Dagger/Hilt's Android documentation includes a "manual DI" page for comparison. Multiplatform-Settings docs show both Koin and hand-wired examples.

---

## Mkdocs-specific patterns

### Pattern: `strict: true` is the contract

**What.** Set `strict: true` in `mkdocs.yml`. Every broken link, every dead anchor, every `nav:` entry pointing at a non-existent file fails the build.

**Why.** Strict mode is the only way to catch documentation drift in CI. Without it, broken links accumulate and the docs site decays silently.

**Implication for authors.** When linking to a Kotlin symbol page, verify the page exists before referencing it. When adding a `nav:` entry, create the file in the same change. When deleting a page, grep the docs for inbound links first.

---

### Pattern: `pymdownx.snippets` for shared install blocks

**What.** Install snippets that appear on multiple pages (Overview, Getting Started, Installation) are defined once in a snippet file and included via `--8<--`. A version bump touches one file.

**Why.** Install snippets duplicated across pages drift independently. Snippets enforce single-source-of-truth.

**Cited from.** Wire's docs use this for install snippets. Koin's docs include version-pinned blocks via the same mechanism.

---

### Pattern: `mkdocs-macros` for `{{ version }}` substitution

**What.** Use `mkdocs-macros` (configured via a `main.py` at the repo root) to compute `version` from the latest published GitHub release at build time. Author markdown uses `{{ version }}` as a placeholder.

**Why.** Hand-editing version pins on release is a process bug waiting to happen. Computing the version at build time eliminates the class.

**Cited from.** Wire (the prototype). Coil 3.x uses a similar approach.

---

## Anti-patterns to reject

These show up in mediocre Kotlin library docs and must not appear in authored output.

| Anti-pattern | Why it's bad | Use instead |
|---|---|---|
| "Powerful and easy-to-use" | Marketing words, no information | Show what it does in one line |
| Hard-coded version like `1.2.0` in snippet | Goes stale | `{{ version }}` macro |
| Screenshots of code | Can't copy-paste | Fenced code block |
| `// rest of code omitted` | Not copy-pasteable | Show full snippet or link to a complete example |
| `Kotlin` as a tab label when the alternative is `Java` | Misleading; KMP libraries have multiple Kotlin source sets | `Common`, `Android`, `iOS`, `macOS` |
| Tab labels `Ios`, `Macos` | Wrong casing for Apple brand names | `iOS`, `macOS` |
| Concept pages titled with verbs (`Scheduling work`) | Concept pages are nouns | `Scheduler` |
| Recipe pages titled with nouns (`One-shot scheduling`) | Recipes are tasks | `Schedule a one-shot` |
| Showing only Kotlin for a Swift-facing API | Half the audience misses the call site | Show Swift call site too |
| Burying platform gotchas in prose | Readers miss them | `!!! warning "iOS"` admonition |
| Changelog with newest entries at the bottom | Wrong order for the actual reading task | Reverse chronological |
| Contributing page with only "PRs welcome" | Useless | Bug template, PR checklist, build commands |

---

## Library-family quick reference

When authoring a specific page, this table maps page type to the strongest reference.

| Page being authored | Strongest reference |
|---|---|
| `index.md` (Overview) | Okio "Why Okio?" + OkHttp index |
| `getting-started.md` | Wire Getting Started + Coil index |
| `installation.md` | Wire Install + JetBrains KMP "Add dependencies" |
| `concepts/<noun>.md` | Koin Modules/Scopes pages + JetBrains KMP "Hierarchical project structure" |
| `recipes/<task>.md` | OkHttp Recipes (the gold standard) |
| `platforms/<name>.md` | Coil 3.x platform pages |
| `contributing.md` | Koin Contributing |
| `changelog.md` | Wire CHANGELOG.md (Keep-a-Changelog adapted) |
| `AGENT_README.md` (LLM-targeted, repo root) | No prior art — skeleton in `agent-readme-template.md` |
