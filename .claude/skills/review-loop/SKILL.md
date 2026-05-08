---
name: review-loop
description: Multi-agent code review loop for the Backgrounder KMP library. Spawns kmp-pro, code-reviewer, and a modernization scout in parallel; reconciles divergent findings; runs kmp-docs-sync against the same diff; loops until budget is exhausted or the user calls stop. Use when the user says "review-loop", "deep review", "multi-agent review", or wants a thorough audit before a PR. Defaults to one round if no count given.
---

# Review loop

A structured multi-agent code review against a base ref (default `origin/main`). Three perspectives in parallel, a reconciliation step, then a docs sync — repeated for as many rounds as the user asks for.

## Inputs

Parse from the user's invocation:

- **rounds** — number of loop iterations. Default `1`. Cap at `3` unless the user explicitly says `--rounds=N` with a number ≤ 5; beyond that, ask before spending the budget.
- **base** — base ref. Default `origin/main`.
- **scope** — optional path glob. Default: all changed Kotlin/Gradle/markdown files.

Print the parsed config back to the user before starting:

> Reviewing **<base>...HEAD** in **<scope>** for **<N>** round(s). Spawning agents.

## One round

A round consists of four phases. Phases 1–3 run **in parallel** in a single message with multiple `Agent` tool calls. Phase 4 runs after they all return.

### Phase 1 — Three reviewers in parallel

Spawn these three agents concurrently:

1. **kmp-pro** — repo-specific review against CLAUDE.md.
   Prompt: "Review the diff `<base>...HEAD` (scope: `<scope>`). Walk the H1–H4 + Apple-casing + Swift-interop checklists. Confidence-score each finding ≥ 80%. Group by severity. End with file:line citations. Do not edit code; report only."

2. **feature-dev:code-reviewer** (existing plugin agent — `subagent_type: feature-dev:code-reviewer`).
   Prompt: "Review the diff `<base>...HEAD` against project guidelines in CLAUDE.md. Confidence ≥ 80. Report bugs, security, code quality.

   **Swift-interop checks specific to this repo (in addition to CLAUDE.md §8):**
   - **`@Throws` audit on Swift-facing public APIs.** Every public Kotlin API likely consumed from Swift (anything `public` in `commonMain` / `appleMain` / `iosMain` / `macosMain`) that can throw must declare its **domain exceptions** in `@Throws(...)`. Missing `@Throws` on a thrower → unrecoverable iOS crash. Flag any public `suspend fun`, public init with `require`/`check`, or public function with `@throws` KDoc that lacks the matching `@Throws` annotation.
   - **`CancellationException` should NOT appear in `@Throws` on a SKIE-using project.** This repo uses SKIE, which bridges `suspend fun` as Swift `async throws` and routes coroutine cancellation through Swift's native `Task.cancel` / `CancellationError` machinery — KMP handles cancellation transparently. Adding `CancellationException::class` to the `@Throws` list pollutes the generated Swift signature and forces Swift consumers to write a meaningless catch arm. Flag any existing `@Throws(CancellationException::class, ...)` for cleanup. (Note: CLAUDE.md §8's example currently includes `CancellationException` — that example is misleading for SKIE-enabled projects and is itself a documentation finding worth raising.)"

3. **modernization-scout** — pass through the general-purpose agent. The scout's job is to web-search for modernization opportunities specific to what changed in the diff. Prompt template (fill in `<diff-summary>` from a quick `git diff --stat`):

   > You are a Kotlin Multiplatform modernization scout. The diff under review touches: `<diff-summary>`. Read `gradle/libs.versions.toml` and CLAUDE.md, then web-search for:
   >
   > 1. Latest stable Kotlin (kotlinlang.org) — and the SKIE-supported range (skie.touchlab.co or Touchlab's GitHub releases). SKIE's range is the ceiling.
   > 2. Latest stable Gradle (gradle.org/releases).
   > 3. Latest stable AGP for `com.android.kotlin.multiplatform.library` (developer.android.com).
   > 4. For each kotlinx library and AndroidX library named in the diff or in `libs.versions.toml`, current stable on Maven Central.
   > 5. Anything in the changed code that has a more idiomatic 2026-era replacement: e.g. `runCatching` patterns superseded by structured error types, callback-shaped K/N APIs that now have suspend wrappers, `kotlinx-datetime` features that replace home-grown date math.
   > 6. Sources to consult — see `references/sources.md` for a curated list. WebFetch the JetBrains roadmap (kotlinlang.org/docs/roadmap.html) and the Touchlab blog (touchlab.co/blog) at minimum.
   >
   > Report only items relevant to the diff or to existing code the diff touches. Each item: current version → proposed version → rationale → blast radius (just `:shared`? whole build? CI? consumer iOS apps?). Don't edit `libs.versions.toml`.

Each agent returns a report. **Save each one** by writing to a temp findings directory:

```bash
mkdir -p .claude/findings/round-<N>
```

The skill writes:
- `.claude/findings/round-<N>/kmp-pro.md`
- `.claude/findings/round-<N>/code-reviewer.md`
- `.claude/findings/round-<N>/modernization.md`

(Use `Write` tool. Save the agents' returned text verbatim.)

### Phase 2 — Reconcile

Read all three reports. Produce `.claude/findings/round-<N>/reconciled.md` with three sections:

1. **Consensus findings** — flagged by ≥ 2 agents. Highest priority. Group by severity.
2. **Single-source findings worth keeping** — flagged by only one agent but with strong evidence (specific file:line + clear rule violation). Demote to "consider" if speculative.
3. **Divergence notes** — places where one agent flagged something another agent's checklist missed. State the rule that should have caught it. **This is the meta-signal that improves future rounds.**

The reconciled report is the *only* artifact the user is expected to read by default. Single-agent reports are kept for audit.

### Phase 3 — Docs sync

After reconciliation, spawn:

4. **kmp-docs-sync** with prompt: "Audit docs against diff `<base>...HEAD` in scope `<scope>`. Report stale KDoc, missing KDoc on new public API, mkdocs / README staleness. Apply unambiguous fixes; flag judgement calls. **Do not delete future-work comments unless the diff proves the work is done** — see your own definition of `proven-done`."

Save its report to `.claude/findings/round-<N>/docs-sync.md`.

### Phase 4 — Round summary

Append a short markdown section to `.claude/findings/SUMMARY.md` (create if absent):

```
## Round <N> — <ISO timestamp>

- Consensus findings: <count>
- Single-source kept: <count>
- Modernization items: <count>
- Docs sync edits: <count>
- Docs flagged for human: <count>

[reconciled.md](round-<N>/reconciled.md) · [docs-sync.md](round-<N>/docs-sync.md)
```

Show the round summary inline to the user. Then either:

- If `<N>` rounds remain, ask the user whether to continue. **Do not auto-loop without confirmation past round 1** — review fatigue is real. (If the user explicitly invoked the skill with `--rounds=N>1`, they pre-confirmed.)
- If we're at the last round, conclude with: "Reports in `.claude/findings/`. Review the latest `reconciled.md` and `docs-sync.md`."

## Between-round adjustments

If a round produced **zero ≥ 80% findings**, before scheduling the next round ask the user:

> Last round was clean. Continue, broaden scope, lower the confidence floor, or stop?

If a round produced **a wave of identical findings** (same rule cited 5+ times), include in the next round's prompts:

> The previous round flagged `<rule>` <N> times. Spend more time on the *fixes* than re-finding new instances; consolidate.

## Safety rails

- The skill **never commits**, **never pushes**, and **never edits source code itself** — the agents do that. Skill role is orchestration.
- The skill writes to `.claude/findings/` only. Add `.claude/findings/` to `.gitignore` if it isn't already. (Check on first run; offer to add it.)
- Before round 1, confirm `git diff <base>...HEAD` actually has content. If empty, abort with: "No changes vs `<base>` — nothing to review."
- Pass `--no-daemon` if any agent invokes Gradle. Long-running daemons confuse parallel runs.

## Files

- `references/sources.md` — curated KMP / Kotlin / SKIE sources for the modernization scout.
- `references/round-template.md` — structure for `reconciled.md`.
