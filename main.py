"""
mkdocs-macros entry point for the Backgrounder docs site.

Exposes a single `version` variable that markdown can reference as
`{{ version }}` for install snippets, version-pinning notes, etc.

Resolution order:
  1. BACKGROUNDER_VERSION env var (CI sets this from `gh release view`).
  2. The `tagName` of the latest GitHub release (queried via `gh` if
     available on PATH, e.g. in a local dev shell that has it installed).
  3. `main` — local fallback. The rendered docs say
     `implementation("...:main")` which is a clear "you're viewing a
     development build" signal, not a misleading hard-coded version.

The fallback chain means CI builds always render the real version,
local builds work without setup, and no one needs to edit markdown when
cutting a release.
"""

from __future__ import annotations

import os
import subprocess


def _latest_release_tag() -> str | None:
    """Return the `tagName` of the latest GitHub release, or None on failure.

    Uses the `gh` CLI. Returns None for any failure mode (gh not installed,
    not authenticated, network unreachable, no releases yet, etc.) — the
    caller falls back to the 'main' placeholder.
    """
    try:
        result = subprocess.run(
            ["gh", "release", "view", "--json", "tagName", "-q", ".tagName"],
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return None

    if result.returncode != 0:
        return None

    tag = result.stdout.strip()
    return tag or None


def _resolve_version() -> str:
    """Compute the version string the docs should render."""
    # CI passes BACKGROUNDER_VERSION explicitly. Honour it before shelling out.
    env_version = os.environ.get("BACKGROUNDER_VERSION", "").strip()
    if env_version:
        return env_version.lstrip("v")

    tag = _latest_release_tag()
    if tag:
        return tag.lstrip("v")

    # Local dev fallback. A reader who sees "main" in a copy-paste install
    # snippet immediately knows they're looking at unreleased docs.
    return "main"


def define_env(env):  # noqa: ANN001 (mkdocs-macros API)
    """mkdocs-macros entry point — register variables and filters."""
    env.variables["version"] = _resolve_version()
