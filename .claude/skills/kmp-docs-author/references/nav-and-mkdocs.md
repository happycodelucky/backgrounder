# Mkdocs Material specifics

The mechanical rules for editing the docs site without breaking it. The repo's `mkdocs.yml` is configured the way Wire / OkHttp configure theirs — explicit `nav:`, `strict: true`, mkdocs-material theme, content-tabs linked, mkdocs-macros for version substitution.

---

## Canonical nav order

The `nav:` in `mkdocs.yml` is the source of truth. Section order is deliberate and matches the reader's likely path:

```
Overview
  ↓ what is this?
Getting started
  ↓ first call running in 5 minutes
Installation
  ↓ wire up the build
Concepts
  ↓ mental model for the second-pass reader
Recipes
  ↓ task-oriented how-tos
Platforms
  ↓ per-platform deltas
Contributing
  ↓ file a bug, propose a change
Changelog
  ↓ what changed
```

When adding a page, place it inside the section it most belongs to. Don't invent new top-level sections without discussion — there are seven canonical sections and each one earned its slot.

---

## Adding a page

1. Create the markdown file under the appropriate `docs/` subdirectory.
2. Add it to `mkdocs.yml` under the matching `nav:` section, in the order it should appear.
3. If the page has no obvious slot, the page is probably misnamed or misscoped — reconsider before inventing a section.
4. Run `mkdocs build --strict`. If it fails, the cause is one of: dead internal link, dead anchor, file in `docs/` not in `nav:`, file in `nav:` not on disk.

---

## `strict: true` is the contract

The repo's `mkdocs.yml` sets `strict: true`. Every CI build catches:

- Broken internal links (`[Foo](../foo.md)` where `foo.md` doesn't exist).
- Dead anchors (`[Foo](#bar)` where `## Bar` doesn't exist on the page).
- Files in `docs/` that aren't in `nav:` (orphan pages — fail).
- `nav:` entries pointing at files that don't exist.

**Implication for authors:** before referencing a page or anchor, verify it exists. Before adding a `nav:` entry, create the file in the same change. Before deleting a page, grep the docs for inbound links and update or remove them.

---

## Content tabs (`pymdownx.tabbed`)

Used for platform-specific snippets. The repo enables `content.tabs.link` so a reader's tab choice persists across pages — pick `iOS` on the install page and the iOS tab stays selected on the recipes page.

**Casing rules.** Tab labels are exact strings:

- `Common` — the shared/Kotlin source set
- `Android` — Android-specific
- `iOS` — Apple iOS (lowercase `i`, uppercase `OS`)
- `macOS` — Apple macOS (lowercase `m`, uppercase `OS`)
- `Kotlin` — Kotlin call site (when paired with `Swift`)
- `Swift` — Swift call site (when paired with `Kotlin`)

**Do not use:** `Ios`, `Macos`, `KMP`, `KotlinJS`, `Native`. The first two are wrong casing for Apple brands; the rest are ambiguous.

**Syntax:**

```markdown
=== "Common"
    ```kotlin
    val backgrounder = Backgrounder.start(...)
    ```

=== "Android"
    ```kotlin
    // Android-specific construction
    ```

=== "iOS"
    ```swift
    let backgrounder = try Backgrounder.start(...)
    ```
```

---

## Snippet inclusion (`pymdownx.snippets`)

Use `--8<--` to include a shared snippet file. Required for install snippets that appear on multiple pages so they don't drift.

**Syntax:**

```markdown
--8<-- "snippets/install.md"
```

**Convention:** snippets live under `docs/snippets/` (not in `nav:` — they're includes, not pages). The mkdocs `pymdownx.snippets: check_paths: true` setting fails the build if the snippet path doesn't resolve.

---

## Version substitution (`mkdocs-macros`)

The repo uses `mkdocs-macros` (configured via `main.py` at the repo root) to compute `version` from the latest published GitHub release at site-build time.

**Use in markdown:**

```markdown
implementation("com.happycodelucky.backgrounder:backgrounder:{{ version }}")
```

**Never hard-code a version pin in install snippets.** Hard-coded versions go stale within weeks and need to be hand-updated on every release — a process bug waiting to happen.

**Local dev behavior:** when building the site outside a release context, `main.py` falls back to `main` so the placeholder still resolves to a meaningful string (not an exception).

---

## Admonitions (`!!! type "title"`)

Use sparingly. The four useful types:

| Type | When to use |
|---|---|
| `!!! note` | Side information that's relevant but not blocking. Use rarely. |
| `!!! warning` | Something that will surprise a reader and may break their build or runtime. |
| `!!! danger` | Something that will definitely break their build or runtime if ignored. |
| `!!! info` | A pointer to other documentation. Use almost never — prefer inline links. |

**Platform-specific gotchas use `!!! warning "iOS"` (or `Android` / `macOS`).** The title is the platform name; the body is the gotcha. Title casing follows the same rules as tab labels.

**Example:**

```markdown
!!! warning "iOS"
    Every `TaskId` you schedule must appear in `BGTaskSchedulerPermittedIdentifiers`
    in your app's Info.plist. Missing entries fail at `schedule()` time with
    `ScheduleOutcome.Rejected`.
```

---

## Code blocks

- Always specify the language: ` ```kotlin `, ` ```swift `, ` ```kotlin `, ` ```sh `, ` ```xml `, ` ```yaml `.
- The repo enables `pymdownx.highlight` with line numbers off by default (turn on with `linenums="1"` for tutorial-style snippets only).
- Copy button is automatic via `content.code.copy`.
- Annotations (`# (1)!`) are enabled via `content.code.annotate` — use sparingly for complex snippets.

---

## Mermaid diagrams

Enabled via `pymdownx.superfences` with the mermaid custom fence. Use for architecture diagrams in concept pages. Keep diagrams under 8 nodes — bigger ones don't render legibly on mobile.

**Syntax:**

```markdown
\```mermaid
graph LR
  A[Caller] --> B[Scheduler]
  B --> C[Runtime]
  C --> D[Worker]
\```
```

---

## Building locally

```sh
# One-time virtualenv setup (the repo expects .venv/)
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt

# Live preview at http://localhost:8000
.venv/bin/mkdocs serve

# Strict build — exactly what CI runs
.venv/bin/mkdocs build --strict
```

The `--strict` flag is the verification gate. A page that builds locally without `--strict` may still break CI.

---

## Forbidden mkdocs patterns

| Anti-pattern | Why | Use instead |
|---|---|---|
| Auto-discovery for `nav:` | Page order non-deterministic; new files leak to site without review | Explicit `nav:` |
| Hard-coded version in install snippet | Stale within weeks | `{{ version }}` macro |
| Page outside `nav:` but in `docs/` | Orphan — fails strict build | Add to `nav:` or move out of `docs/` |
| Page in `nav:` not on disk | Broken — fails strict build | Create the file |
| `[Foo](#missing-anchor)` | Dead anchor — fails strict build | Verify the anchor exists |
| HTML embeds for what markdown can do | Unreadable raw, harder to edit | Markdown |
| External CDN for assets | Fragile, privacy implications | Vendor under `docs/assets/` |
| `nav:` deeper than 2 levels | Hides content below the fold | Promote to a top-level section or split |
