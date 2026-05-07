# Round template — `reconciled.md`

Use this skeleton when writing the consolidated round report. Keep section headers verbatim so summary tooling can grep them.

```
# Reconciled review — round <N>

**Base:** <ref>
**Scope:** <glob or "all changed">
**Generated:** <ISO timestamp>
**Agents run:** kmp-pro, code-reviewer, modernization-scout

## Consensus findings

Findings flagged by ≥ 2 agents. These are the highest priority — start here.

### Critical
- **<title>** — file:line
  - Smell: <one-line description>
  - Fix: <concrete action>
  - Cite: <CLAUDE.md §X | H1/H2/H3/H4 | rule name>
  - Sources: kmp-pro, code-reviewer

### Important
- ...

### Nit
- ...

## Single-source findings worth keeping

Flagged by only one agent but with strong specific evidence (file:line + clear rule). Lower priority.

- **<title>** — file:line · source: kmp-pro
  - Why kept: <rationale — specific cite, low false-positive risk>
  - Fix: ...

## Modernization items

From the scout. Each item: current → proposed → rationale → blast radius.

- **Kotlin** 2.3.20 → 2.3.21 — SKIE 0.10.12 now supports it. Blast radius: `:shared` only; SKIE consumers may need cache invalidation.
- ...

## Divergence notes

Where one agent flagged something another agent's checklist missed. This is the meta-signal that improves future rounds.

- kmp-pro flagged `<rule>` at file:line; code-reviewer missed it. The general checklist doesn't cover `<X>` because it's repo-specific. Action: fine, expected.
- code-reviewer flagged `<rule>` at file:line; kmp-pro missed it. The kmp-pro checklist *should* have caught it because <reason>. Action: extend kmp-pro's anti-patterns list with `<rule>`.

## Stats

| | Count |
|---|---|
| Consensus critical | |
| Consensus important | |
| Consensus nit | |
| Single-source kept | |
| Single-source dropped (low confidence) | |
| Modernization items | |
| Divergence notes | |

## Raw reports

- [kmp-pro.md](kmp-pro.md)
- [code-reviewer.md](code-reviewer.md)
- [modernization.md](modernization.md)
- [docs-sync.md](docs-sync.md) — runs after this report
```

## Notes on populating the template

1. **Group findings by file** within each severity bucket if the round produced > 10 items in that bucket. Otherwise group by severity alone.
2. **Drop a finding if confidence < 80%.** The agents already filter; if you suspect a false positive in the consensus list, downgrade it to single-source and note why.
3. **Don't editorialize.** The reconciled report is a deliverable, not a discussion thread. Save discussion for the divergence notes.
4. **One line per fix.** "Pass `attempt`, not `nextAttempt`, to `delayFor`." beats "We should consider whether the index passed to `delayFor` is appropriate given the…"
5. **Cross-reference the H1–H4 IDs** if a finding matches one. They're a stable shorthand the team uses.
