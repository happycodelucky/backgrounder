# Page templates

Five blank templates the agent fills in. Each template lists required H2s, where the code block goes, and where platform tabs are appropriate.

Voice and tone come from `voice.md`. Patterns come from `doc-patterns.md`. These templates are the *shape*, not the *style*.

---

## Recipe page (`docs/recipes/<task>.md`)

The most common page type. Strongest reference: OkHttp recipes.

```markdown
# <Imperative title — "Schedule a one-shot", not "One-shot scheduling">

<One sentence stating the task and the conditions under which the reader needs it.>

\```kotlin
// Complete, runnable, real types. Five-to-fifteen lines.
// Show the call site for the most common configuration.
\```

<One sentence linking the snippet to what follows. Optional if the snippet is self-explanatory.>

## <Optional: a sub-task or variation>

<If the recipe has a meaningful variant, show it here. Skip this section if the recipe is single-task.>

\```kotlin
// The variant. Keep it as concise as the main snippet.
\```

## What can go wrong

- **<cause>** — <one-line consequence + fix>.
- **<cause>** — <one-line consequence + fix>.
- **<cause>** — <one-line consequence + fix>.

## See also

- [<Related recipe>](../recipes/<file>.md)
- [<Related concept>](../concepts/<file>.md)
```

**Required H2s:** `## What can go wrong` (always), `## See also` (almost always — link to one related recipe and one concept).

**Required code:** at least one fenced Kotlin block within the first 200 words. If the API differs across platforms, use `pymdownx.tabbed` blocks labelled `Common` / `Android` / `iOS` / `macOS`.

**Length target:** 50–150 lines. Recipes longer than 150 lines are probably two recipes.

---

## Concept page (`docs/concepts/<noun>.md`)

Reference material. One concept per page. Strongest references: Koin's concept pages, JetBrains KMP "Hierarchical project structure".

```markdown
# <Noun — "Scheduler", "Worker context", "Guarantees">

<One-sentence definition. The noun in the title is the subject of the sentence.>

<Optional: a Mermaid diagram if the concept has structure that's faster to see than to read.>

\```mermaid
graph LR
  A[Caller] --> B[Scheduler]
  B --> C[Runtime]
  C --> D[Worker]
\```

## <Aspect 1>

<Two or three paragraphs. Each paragraph one idea.>

## <Aspect 2>

<Two or three paragraphs.>

## <Optional: comparison table>

| Aspect | Variant A | Variant B |
|---|---|---|
| <thing> | <value> | <value> |

## See also

- [<Related concept>](../concepts/<file>.md)
- [<Recipe that uses this concept>](../recipes/<file>.md)
```

**Required H2s:** at least two aspect sections; `## See also` if there are obvious cross-links.

**Required code:** not always. A concept page can be diagram + prose + table. If the concept has a representative call site, show it once.

**Length target:** 80–250 lines. Concepts longer than 250 lines need to be split.

---

## Platform page (`docs/platforms/<name>.md`)

One per supported platform. Identical structure across platforms — readers learn the shape once, navigate any platform in seconds. Strongest reference: Coil 3.x platform pages.

```markdown
# <Platform name — exact casing: "Android", "iOS", "macOS">

<One sentence summarizing what's distinctive on this platform.>

## Requirements

- Minimum OS version: <e.g. iOS 17.0, Android API 26>
- Required entitlements: <list, or "none">
- Required manifest entries: <list, or "none">

## Setup

<Manifest or Info.plist snippet, complete and copy-pasteable.>

\```xml
<!-- AndroidManifest.xml or Info.plist -->
\```

\```kotlin
// Construction snippet specific to this platform.
\```

## Behavior

<What this platform does that's different from the others. Two or three paragraphs.>

## Known limitations

- <limitation 1, with link to issue tracker if open>
- <limitation 2>

## See also

- [<Related concept>](../concepts/<file>.md)
- [<Cross-platform recipe>](../recipes/<file>.md)
```

**Required H2s:** `## Requirements`, `## Setup`, `## Behavior`, `## Known limitations`. All four. Identical order across every platform page.

**Required code:** the manifest/config snippet and a construction snippet. Without these, the page is aspirational, not operational.

---

## Contributing page (`docs/contributing.md`)

Operational, not aspirational. Strongest reference: Koin's contributing page.

```markdown
# Contributing

<One sentence stating what kind of contributions are welcome.>

## Filing a bug

Open an issue at <repo URL>/issues with the following:

- **Library version**: <how to find it>
- **Platform(s) affected**: <e.g. iOS 17.4, Android API 34>
- **Minimal reproduction**: a code snippet or link to a small repo
- **Expected behavior**: what you thought would happen
- **Actual behavior**: what happened, including any logs or stack traces
- **Workaround** (if any): how you got past it

## Proposing a change

1. <First step — usually "Open an issue first to discuss the change.">
2. <Fork, branch, etc.>
3. <Run tests / linters>
4. Open a pull request. The PR description should answer:
   - What problem does this solve?
   - What's the user-facing change?
   - Are there breaking changes? If so, what's the migration?

## Building from source

\```sh
# Clone
git clone <repo URL>
cd <repo name>

# Build everything
./gradlew check

# Run platform-specific test suite
./gradlew :backgrounder:<target>Test
\```

## Conventions

<Brief — point at any contributor guide files. Do NOT cite agent-targeted files.>

## Releasing

<Maintainer-facing, optional. Brief release process or link to release docs.>
```

**Required H2s:** `## Filing a bug`, `## Proposing a change`, `## Building from source`. Optionally `## Conventions` and `## Releasing`.

**Required content:** the bug template fields, the build command, the PR checklist. Without these, the page is "PRs welcome" with extra steps.

---

## Changelog page (`docs/changelog.md`)

Reverse chronological. One H2 per release. Strongest reference: Wire's `CHANGELOG.md` (Keep-a-Changelog adapted).

```markdown
# Changelog

All notable changes to this project. Reverse chronological.

## <Version> — <YYYY-MM-DD>

### Breaking changes

- <change> — <migration path>.

### New

- <feature>.

### Fixed

- <bug fix>.

### Deprecated

- `<symbol>` — use `<replacement>` instead. Removal in <version>.

## <Earlier version> — <YYYY-MM-DD>

…
```

**Required H2s:** one per release.

**Required H3s within a release:** include only the categories with content. Order is fixed: `### Breaking changes` → `### New` → `### Fixed` → `### Deprecated`. Breaking changes always first when present — that's what readers scan for first.

**Voice:** past tense, third person. "Added flex intervals." "Fixed Info.plist validation crash on duplicate identifiers."

**Length:** unbounded — but each release entry should be scannable in 30 seconds.
