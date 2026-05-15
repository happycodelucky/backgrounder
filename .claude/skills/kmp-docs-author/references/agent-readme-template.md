# AGENT_README.md template

The skeleton for the LLM-targeted README at the repo root. Authored by `kmp-doc-writer` in `--agent-readme` mode.

**Audience.** LLMs and code-generation agents consuming this library. Not humans. Optimize for dense, terse, copy-paste-ready content. No narrative warmth, no marketing, no scene-setting.

**Length budget.** ≤300 lines total. Each section has its own budget below. Hard cap.

**Hard rule.** This file is public-facing — it lives at the repo root and is linked from README.md and docs/index.md. Do **not** cite `CLAUDE.md`, `.claude/`, or any agent file path. If a CLAUDE.md rule is load-bearing for users (e.g. "use sealed result types over `kotlin.Result`"), restate it as the library's own API design. The reader should never know an agent file exists.

---

## The 10-section skeleton

Author the file in this order. Section numbers are fixed; section titles are fixed; H2s are exact.

```markdown
# AGENT_README

LLM-targeted reference for the <library name> library. For human-facing docs, see [README.md](./README.md) and the [documentation site](<docs URL>).

## What this is

<One paragraph, ≤80 words. Library purpose, target platforms, problem solved. No marketing.>

## When to use this library

Use <library name> when:

- <decision rule>
- <decision rule>
- <decision rule>

Do not use it when:

- <anti-decision rule with the recommended alternative>
- <anti-decision rule with the recommended alternative>

## Public surface

Every public type from `commonMain` and per-target source sets, grouped by role.

### Entry points
- `<fully.qualified.Type>` — <one line purpose>. See [docs page](<link>).
- `<fully.qualified.Type>` — <one line purpose>. See [docs page](<link>).

### Requests
- `<fully.qualified.Type>` — <one line purpose>.
- `<fully.qualified.Type>` — <one line purpose>.

### Outcomes
- `<fully.qualified.Type>` — <one line purpose>. Sealed type; cases listed in KDoc.
- `<fully.qualified.Type>` — <one line purpose>. Sealed type.

### Identity
- `<fully.qualified.Type>` — <one line purpose>.

### Worker contract
- `<fully.qualified.Type>` — <one line purpose>.
- `<fully.qualified.Type>` — <one line purpose>.

### Inspection
- `<fully.qualified.Type>` — <one line purpose>.

## Platform capability matrix

| Capability | Android | iOS | macOS | wasm |
|---|---|---|---|---|
| One-shot | <Yes/No/Emulated/N/A> | <…> | <…> | <…> |
| Periodic | <…> | <…> | <…> | <…> |
| Network constraint | <…> | <…> | <…> | <…> |
| Charging constraint | <…> | <…> | <…> | <…> |
| Force-quit survival | <…> | <…> | <…> | <…> |

`Yes` — fully supported by the platform's native primitive.
`Emulated` — implemented by the library on top of a less specialized primitive; behavior approximates but is not identical.
`No` — the platform cannot offer the capability.
`N/A` — the capability does not apply to this platform.

## Install

Add the dependency. Replace `{{ version }}` with the version your project pins (or use the macro if your docs build supports it).

\```kotlin
// build.gradle.kts (commonMain or Android-only)
dependencies {
    implementation("<group>:<artifact>:{{ version }}")
}
\```

For iOS / macOS sample apps that consume the source directly:

\```swift
// Package.swift
.package(path: "../path/to/<library>")
\```

For non-KMP iOS / macOS consumers, see <link to installation page>.

## Recipes

Five copy-paste recipes. Each is one fenced block + ≤3 lines of context.

### 1. Register and start

<≤3 lines of context.>

\```kotlin
// Complete code, real types, no placeholders.
\```

### 2. Schedule a one-shot with constraints

<≤3 lines of context.>

\```kotlin
// Complete code.
\```

### 3. Schedule periodic work with backoff

<≤3 lines of context.>

\```kotlin
// Complete code.
\```

### 4. Cancel a scheduled task by id

<≤3 lines of context.>

\```kotlin
// Complete code.
\```

### 5. Inspect status

<≤3 lines of context.>

\```kotlin
// Complete code.
\```

## API design rules an LLM must obey

When generating code that uses this library, follow these rules. They reflect how the API is shaped — violations compile but read poorly at the Swift call site or break invariants the runtime depends on.

- **Apple platform names preserve casing.** Identifiers and type names use `iOS`, `macOS`, `tvOS`, `watchOS`, `iPadOS`, `visionOS`. Never `Ios`, `Macos`. Apple platform brand names override the standard Kotlin acronym convention.
- **Use sealed result types, not `kotlin.Result<T>`.** Public APIs return named sealed types (`WorkResult`, `ScheduleOutcome`, `CancelOutcome`). `kotlin.Result<T>` does not bridge to Swift cleanly — Swift sees an opaque wrapper with no exhaustive `switch`. Sealed types render as Swift enums via `onEnum(of:)`.
- **Throws annotations exclude `CancellationException`.** Cancellation flows through Swift's native `Task.cancel()` machinery — adding `CancellationException::class` to a `@Throws` list pollutes the generated Swift signature.
- **`@ObjCName(swiftName = "…")` strips redundant nouns.** Kotlin `openUrl(url:)` becomes Swift `open(url:)`. Show the renamed Swift call site whenever you show a Kotlin declaration.
- **KDoc on every public symbol.** Every public class, interface, function, property has a KDoc paragraph. The first sentence is the symbol's purpose; subsequent sentences cover constraints, exceptions, threading.
- **No callback-based public APIs.** Use `suspend fun` returning sealed types, or `Flow<T>` for streams.
- **No `Pair`/`Triple` at the public boundary.** Define a `data class`. Same reason as `kotlin.Result` — opaque wrapper at the Swift call site.
- **Construct `Duration` with the `kotlin.time` extensions.** `30.seconds`, `5.minutes`. Reads at the call site, type-safe, KMP-portable.

## Common mistakes

If you wrote → you meant:

- **`Result<T>` in a public function signature** → use a sealed `<library>Outcome` type. See [Outcomes](#outcomes) above.
- **`@Throws(CancellationException::class)`** → drop the annotation. SKIE handles cancellation.
- **`openUrl(url:)` without `@ObjCName`** → add `@ObjCName(swiftName = "open")` so Swift sees `open(url:)`.
- **`Ios` or `Macos` in a type name** → rename to `iOS` or `macOS`. Apple brand casing.
- **`java.time.Duration` in `commonMain`** → use `kotlin.time.Duration` and the `Companion` extensions.
- **`GlobalScope.launch { … }`** → take a `CoroutineScope` parameter or use the library's owned scope.
- **A `synchronized(lock) { … }` block in `commonMain`** → if the body suspends, use `kotlinx.coroutines.sync.Mutex`. If not, use `kotlinx.atomicfu.locks.synchronized`.
- **Hard-coded `0.x.y` version in install snippets** → use the `{{ version }}` macro or the value from `libs.versions.toml`.

## Where to read more

- [Documentation site](<docs URL>) — full reference, recipes, concepts.
- [API reference (KDoc)](<dokka URL>) — generated from source.
- [GitHub](<repo URL>) — source, issues, releases.
- [Releases](<releases URL>) — changelog and version history.

## Provenance

- **Last updated**: <YYYY-MM-DD> from commit `<short SHA>`.
- **Generated by**: `/kmp-docs-author --agent-readme` against the public surface in `commonMain` and per-target source sets.
- **Public-surface enumeration source**: `grep -E '^(public )?(class|interface|object|sealed|fun|val|var)' commonMain/**/*.kt` plus per-target overrides.
```

---

## Section budgets (to stay within 300 lines)

| Section | Target lines | Hard cap |
|---|---|---|
| Header + "What this is" | 6 | 10 |
| When to use this library | 12 | 20 |
| Public surface | 60 | 100 |
| Platform capability matrix | 15 | 20 |
| Install | 25 | 35 |
| Recipes (5 of them) | 80 | 120 |
| API design rules | 25 | 35 |
| Common mistakes | 20 | 30 |
| Where to read more | 8 | 10 |
| Provenance | 6 | 10 |
| **Total** | **257** | **300** |

If a section runs over its hard cap, the section needs to be split off into the regular docs site and replaced here with a one-line link. AGENT_README is *index*, not *encyclopedia*.

---

## Authoring rules specific to this file

1. **Enumerate the public surface from source, not from memory.** Run `Grep` against `backgrounder/src/commonMain/kotlin/**/*.kt` and per-target source sets. Match `^(public )?(class|interface|object|sealed|fun|val|var)`. Cross-reference with `internal` modifier — internal types must not appear here.

2. **Every type listed gets a one-line purpose.** No purpose line means the type is internal-only or hasn't earned its keep on this list.

3. **No long-form prose.** Every paragraph in this file is a single sentence. The agent reading it has no patience for warmth.

4. **No screenshots, no diagrams.** This file is consumed by agents that may strip non-text. Text only.

5. **Recipes are exhaustive.** A recipe that says `// error handling omitted` defeats the file's purpose. Show the full call site, including the `when` over the sealed outcome.

6. **No agent-file citations.** Do not reference `CLAUDE.md`, `.claude/`, agent names, or skill names. Restate every rule in the library's own voice.

7. **The matrix is honest.** `Emulated` is not a polite word for "almost works." If a capability isn't fully supported on a platform, say `No` and document the alternative in the regular docs.

8. **Provenance is real.** The "Last updated" date and commit SHA are filled in from `git log -1 --format='%h %ad' --date=short` at generation time, not invented.
