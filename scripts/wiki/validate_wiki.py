#!/usr/bin/env python3
"""Validate the repository-managed GitHub Wiki source."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from urllib.parse import unquote

WIKI_LINK = re.compile(r"\[\[([^\]\n]+)\]\]")
MD_LINK = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
HEADING = re.compile(r"^#\s+\S", re.MULTILINE)
PLACEHOLDER = re.compile(r"\b(?:TODO|TBD|FIXME)\b", re.IGNORECASE)

REQUIRED = {"Home.md", "_Sidebar.md", "Implementation-Status.md"}


def slug(value: str) -> str:
    value = unquote(value).strip()
    value = value.split("#", 1)[0]
    value = value.split("|")[-1]
    value = value.removesuffix(".md")
    return re.sub(r"[-_\s]+", "-", value).strip("-").casefold()


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def validate(directory: Path) -> list[str]:
    errors: list[str] = []

    if not directory.is_dir():
        return [f"Wiki page directory does not exist: {directory}"]

    nested = [p for p in directory.rglob("*") if p.is_file() and p.parent != directory]
    for path in nested:
        fail(errors, f"Publishable Wiki pages must be flat: {path}")

    files = sorted(directory.glob("*.md"))
    names = {p.name for p in files}

    for required in sorted(REQUIRED - names):
        fail(errors, f"Missing required page: {required}")

    by_slug: dict[str, Path] = {}
    for path in files:
        key = slug(path.stem)
        if key in by_slug:
            fail(errors, f"Duplicate page slug: {path.name} and {by_slug[key].name}")
        else:
            by_slug[key] = path

    for path in files:
        raw = path.read_bytes()
        if b"\x00" in raw:
            fail(errors, f"NUL byte in {path.name}")
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError as exc:
            fail(errors, f"{path.name} is not UTF-8: {exc}")
            continue

        if "\r" in text:
            fail(errors, f"{path.name} contains CR/CRLF; use LF line endings")

        if path.name != "_Sidebar.md" and not HEADING.search(text):
            fail(errors, f"{path.name} must start with an H1 heading")

        if PLACEHOLDER.search(text):
            fail(errors, f"{path.name} contains TODO/TBD/FIXME placeholder text")

        if len(raw) > 500_000:
            fail(errors, f"{path.name} exceeds 500 KB")

        for match in WIKI_LINK.finditer(text):
            target = match.group(1)
            key = slug(target)
            if not key:
                continue
            if key not in by_slug:
                fail(errors, f"{path.name}: broken Wiki link [[{target}]]")

        for match in MD_LINK.finditer(text):
            target = match.group(1).strip()
            if not target or target.startswith(("#", "http://", "https://", "mailto:")):
                continue
            target = target.split("#", 1)[0]
            if target.startswith("/"):
                continue
            resolved = (path.parent / target).resolve()
            if not resolved.exists():
                fail(errors, f"{path.name}: broken relative link ({target})")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "directory",
        nargs="?",
        default="docs/wiki/pages",
        type=Path,
        help="Directory containing flat Wiki Markdown pages",
    )
    args = parser.parse_args()

    errors = validate(args.directory)
    if errors:
        print("Wiki validation failed:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    count = len(list(args.directory.glob("*.md")))
    print(f"Wiki validation passed: {count} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
