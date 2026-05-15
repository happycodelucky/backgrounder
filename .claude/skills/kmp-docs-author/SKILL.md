---
name: kmp-docs-author
description: Authors mkdocs Material pages and the repo-root AGENT_README.md for the Backgrounder Kotlin Multiplatform library, following the documentation patterns of Square (Wire/OkHttp/Retrofit/Moshi/Okio), Coil, Ktor, Koin, Dagger/Hilt, and JetBrains KMP. Delegates heavy authoring to the kmp-doc-writer agent, then hands off to kmp-docs-sync for code-truth verification. Use when the user says "write docs for X", "scaffold the docs site", "author AGENT_README", "draft a recipe page", "draft a concept page", or wants a new mkdocs page that elevates the docs voice.
---

# kmp-docs-author

Author technical documentation for the Backgrounder KMP library. The skill is the entry point; the heavy authoring runs in the `kmp-doc-writer` agent; `kmp-docs-sync` verifies the result against actual code.

## Inputs

Parse from the user's invocation:

- `--page <docs/path/page.md>` — author or refresh a single page. The most common mode.
- `--scaffold` — produce or refresh the canonical seven-section structure. Only creates *missing* canonical sections; never overwrites existing pages.
- `--agent-readme` — generate or refresh `/AGENT_README.md` at the repo root.
- `--section <overview|getting-started|installation|concepts|platforms|contributing|changelog>` — refresh one canonical section.
- `--dry-run` — emit drafts to stdout instead of writing files.
- `--force` — overwrite an existing page (default behavior is to refuse).
- An optional natural-language instruction describing the page's purpose.

If the user invokes the skill without arguments, ask which mode they want before doing any work.

## Pre-flight

Before doing anything else:

1. Confirm `mkdocs.yml` exists at the repo root. If not, refuse and explain.
2. Read `mkdocs.yml`'s `nav:` to know the canonical structure.
3. Read `gradle/libs.versions.toml` so install snippets pin a real version (mediated through the `{{ version }}` macro, not hard-coded).
4. For `--scaffold`: detect which canonical sections from `nav:` are missing on disk; only those will be created.
5. For `--page <path>`: if the file exists and `--force` is absent, refuse and tell the user to pass `--force`.

Print the parsed config back to the user before starting:

> Authoring **<mode>** with target **<path or scope>**. Spawning **kmp-doc-writer**.

## Routing

Decide whether to delegate or handle inline:

| Situation | Action |
|---|---|
| Typo fix, one-paragraph patch, single-link update | Handle inline with `Edit`. Do not spawn an agent. |
| New page from scratch | Spawn `kmp-doc-writer` with `--page <path>`. |
| Substantial refresh (>30% rewrite, voice change, restructure) | Spawn `kmp-doc-writer` with `--page <path>`. |
| `--scaffold` | Spawn `kmp-doc-writer` once per missing canonical section. |
| `--agent-readme` | Spawn `kmp-doc-writer` with `--agent-readme`. |

When you spawn `kmp-doc-writer`, pass:

- The mode and arguments parsed above.
- The natural-language instruction (if any).
- The relative path to the bundled reference directory: `.claude/skills/kmp-docs-author/references/`.

## Post-authoring

After `kmp-doc-writer` returns:

### 1. Audience-separation gate (hard fail)

For every file the agent wrote under `README.md` or `docs/**`, run:

```sh
grep -nE 'CLAUDE\.md|\.claude/|kmp-pro|kmp-doc-writer|kmp-docs-sync|kmp-docs-author|agents\.md' <file>
```

Any match in a public-doc path is a hard fail. Print the offending line and refuse to mark the run successful. Tell the user what to rewrite.

(The `AGENT_README.md` cross-link from README.md / docs/index.md uses the literal string `AGENT_README.md`, which is permitted — the forbidden-token list does not include it.)

### 2. mkdocs strict build

Run:

```sh
.venv/bin/mkdocs build --strict 2>&1
```

If the build fails, surface the error. Common causes: dead internal link, dead anchor, file in `docs/` not in `nav:`, file in `nav:` not on disk.

### 3. AGENT_README cross-link enforcement

If the agent wrote or modified `/AGENT_README.md`:

- `Grep` `README.md` and `docs/index.md` for `AGENT_README`.
- If either is missing the link, emit the snippet for it (see below) and ask the user before editing the anchor file.

**README.md cross-link snippet** (insert near the top, after the existing docs link):

```markdown
For LLMs and code-generation agents, see [`AGENT_README.md`](./AGENT_README.md) — a terse, public-surface-enumerated reference with copy-paste recipes.
```

**docs/index.md cross-link snippet** (insert at the end of the Overview body, before the first H2):

```markdown
> **Building with an LLM?** See [`AGENT_README.md`](https://github.com/happycodelucky/backgrounder/blob/main/AGENT_README.md) in the repo root — a terse, machine-targeted reference enumerating the public surface with copy-paste recipes.
```

(Absolute URL in the docs site — `AGENT_README.md` is outside `docs_dir` and `mkdocs --strict` rejects relative out-of-tree links.)

### 4. Hand off to kmp-docs-sync

Spawn the `kmp-docs-sync` agent with scope = the files `kmp-doc-writer` authored. It catches: invented claims (symbols that don't exist), missing KDoc cross-references, broken `[Symbol]` links.

If `kmp-docs-sync` reports findings, surface them to the user. Don't auto-apply — the author is `kmp-doc-writer`'s job, not `kmp-docs-sync`'s.

## Safety rails

- Never commit. The user commits.
- Never edit `mkdocs.yml`'s `nav:` silently. If a new page needs a `nav:` entry, show the diff and ask before applying.
- Never overwrite an existing page without `--force`.
- Never edit `README.md` or `docs/index.md` silently for the AGENT_README cross-link. Always show the diff and ask.
- Never run `git push`, `git commit`, `gh pr create`, or any other destructive git command.

## Files

- `references/doc-patterns.md` — pattern bible (Square, Coil, Ktor, Koin, Dagger/Hilt, JetBrains KMP). The agent always loads this.
- `references/voice.md` — tone rules (active voice, no marketing, lead with task). The agent always loads this.
- `references/page-templates.md` — five blank templates (recipe, concept, platform, contributing, changelog). The agent loads on demand by page type.
- `references/agent-readme-template.md` — the 10-section AGENT_README skeleton with section budgets.
- `references/nav-and-mkdocs.md` — mkdocs Material specifics (nav, tabs, snippets, version macro, strict mode).
- `references/sources.md` — URLs to refetch when uncertain.

## Examples

**Single recipe page:**

> /kmp-docs-author --page docs/recipes/chained-workers.md "draft a recipe for chaining workers — one runs after the other completes"

**Whole scaffold check:**

> /kmp-docs-author --scaffold

**AGENT_README:**

> /kmp-docs-author --agent-readme

**Refresh the Overview only:**

> /kmp-docs-author --section overview --force
