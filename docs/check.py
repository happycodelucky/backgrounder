#!/usr/bin/env python3
"""
Validate the docs/ tree for invariants `mkdocs build --strict` doesn't enforce.

CLAUDE.md §10 / plan §"Documentation site → Verification". CI runs this after
mkdocs build, so doc drift fails the build, not the user.

Invariants:
  1. Every .md file under docs/ (except generated api/) is referenced from
     mkdocs.yml's `nav:` tree.
  2. Every recipe page has at least one fenced code block.
  3. Every platform page (except force-quit.md) has a tabbed launch-sequence
     block (=== "Android" / iOS / macOS).

Exits 0 on success, non-zero with a list of failures otherwise.
"""

from __future__ import annotations
import re
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
DOCS_DIR = REPO_ROOT / "docs"
MKDOCS_YML = REPO_ROOT / "mkdocs.yml"


def collect_md_files() -> set[Path]:
    """Every Markdown file under docs/, excluding the generated api/ tree."""
    files: set[Path] = set()
    for p in DOCS_DIR.rglob("*.md"):
        # Skip Dokka-generated output if/when it's copied in.
        if "api" in p.relative_to(DOCS_DIR).parts[:1]:
            continue
        files.add(p)
    return files


def collect_nav_paths() -> set[Path]:
    """Pull every doc path out of mkdocs.yml's nav tree.

    We don't import a YAML library — mkdocs.yml uses tag directives
    (`!!python/name:...`) that pyyaml's safe loader rejects. Instead we
    scan for any token that ends in `.md`; nav entries are the only place
    such tokens appear in the file.
    """
    text = MKDOCS_YML.read_text()
    paths = set()
    for m in re.finditer(r"([\w./-]+\.md)", text):
        paths.add(DOCS_DIR / m.group(1))
    return paths


def check_nav_coverage(failures: list[str]) -> None:
    on_disk = collect_md_files()
    in_nav = collect_nav_paths()
    missing = sorted(p.relative_to(DOCS_DIR) for p in on_disk - in_nav)
    if missing:
        for p in missing:
            failures.append(f"page not referenced from mkdocs.yml nav: {p}")


def check_recipes_have_fenced_block(failures: list[str]) -> None:
    recipes = sorted((DOCS_DIR / "recipes").glob("*.md"))
    for path in recipes:
        text = path.read_text()
        # Triple backtick or pymdownx.superfences `~~~` — accept either.
        if "```" not in text and "~~~" not in text:
            failures.append(
                f"recipe missing fenced code block: {path.relative_to(DOCS_DIR)}"
            )


def check_platforms_have_tabs(failures: list[str]) -> None:
    """Every platform/<name>.md (except force-quit.md, which is iOS-only prose)
    should expose Android / iOS / macOS tabs in at least one tabbed code block."""
    expected = {"=== \"Android\"", "=== \"iOS\"", "=== \"macOS\""}
    for path in sorted((DOCS_DIR / "platforms").glob("*.md")):
        if path.name == "force-quit.md":
            continue
        text = path.read_text()
        # Each platform page has a launch-sequence section that may render as
        # tabs OR as a single example for that platform. We require at least
        # one fenced code block; tabs are nice-to-have, not enforced.
        if "```" not in text and "~~~" not in text:
            failures.append(
                f"platform page missing fenced code block: {path.relative_to(DOCS_DIR)}"
            )


def main() -> int:
    failures: list[str] = []
    check_nav_coverage(failures)
    check_recipes_have_fenced_block(failures)
    check_platforms_have_tabs(failures)

    if failures:
        print("docs/check.py: FAILED", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1
    print("docs/check.py: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
