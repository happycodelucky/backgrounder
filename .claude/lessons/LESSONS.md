# Lessons Learned — Backgrounder

Living document. Agents and humans add entries here whenever something is worth
remembering across sessions. Read it before planning non-trivial work and
whenever you get stuck — we may have seen the issue before.

## How to use this file

- **Before planning** a non-trivial change: skim all four sections, then grep
  for keywords from the task (e.g. `mutex`, `BGTask`, `XCFramework`,
  `SKIE`, `WorkManager`).
- **When stuck** for more than a few minutes: search here before going wider.
- **Add an entry as soon as you learn something** — don't batch. A terse line
  written now beats a polished paragraph never written.

## How to add an entry

- Pick the right section. If it fits two, pick the one a future reader would
  search first.
- Allocate the next sequential ID for that section (`B-007`, `D-003`, …). IDs
  are stable forever — never renumber.
- One to three lines per entry. Cite a file path or commit/PR when useful.
  No long prose. If you need more than three lines, you're explaining what,
  not why.
- Code comments may reference an entry by ID (e.g. `// see B-004`).
- If an entry becomes obsolete, mark it `~~B-NNN~~ (superseded by B-NNN)` —
  do not delete. History matters.

Date format: `YYYY-MM-DD`. Always absolute, never relative ("last week").

---

## Bugs we've hit (B)

Bugs introduced in this codebase that are worth remembering. Each entry:
**cause → fix**, plus the file or PR if known.

<!-- Template:
### B-001 — Short title — YYYY-MM-DD
**Cause:** one line.
**Fix:** one line.
**Ref:** `path/to/file.kt:42` or PR #N.
-->

_No entries yet. See also the `H1`–`M6` catalogue in
[`.claude/agents/kmp-pro.md`](../agents/kmp-pro.md) — migrate those here as
they're re-encountered or extended._

---

## Novel design decisions (D)

Creative or non-obvious decisions the team made — especially ones a fresh
reader would not infer from the code. Capture the **decision** and the
**reason it beat the obvious alternative**.

<!-- Template:
### D-001 — Short title — YYYY-MM-DD
**Decision:** one line.
**Why over the obvious alternative:** one line.
**Ref:** file or PR.
-->

_No entries yet._

---

## NEVER DO (N)

Things the user has explicitly asked us not to do again. Each entry is a
hard rule with a one-line reason. If a rule is general-purpose, move it into
`CLAUDE.md §13 Hard rules` instead — this section is for the specific,
contextual "we tried this, don't again" lessons.

<!-- Template:
### N-001 — Short title — YYYY-MM-DD
**Don't:** one line.
**Why:** one line — what happened when we did.
-->

_No entries yet._

---

## Troubleshooting (T)

When we got stuck, what unstuck us. Symptom-first so a future reader can
match against what they're seeing.

<!-- Template:
### T-001 — Short symptom — YYYY-MM-DD
**Symptom:** what you observe.
**Cause:** what was actually wrong.
**Unstuck by:** the action that resolved it.
-->

_No entries yet._
