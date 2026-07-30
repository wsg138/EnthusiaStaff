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
MAX_PAGE_BYTES = 500_000


def slug(value: str) -> str:
    """Return the normalized GitHub Wiki page slug for a link or filename."""
    value = unquote(value).strip()
    value = value.split("#", 1)[0]
    value = value.split("|")[-1]
    value = value.removesuffix(".md")
    return re.sub(r"[-_\s]+", "-", value).strip("-").casefold()


def append_error(errors: list[str], message: str) -> None:
    """Add one validation failure to the shared error collection."""
    errors.append(message)


def collect_pages(directory: Path, errors: list[str]) -> list[Path]:
    """Collect flat Markdown pages and report missing or nested content."""
    if not directory.is_dir():
        append_error(errors, f"Wiki page directory does not exist: {directory}")
        return []

    nested = [
        path
        for path in directory.rglob("*")
        if path.is_file() and path.parent != directory
    ]
    for path in nested:
        append_error(errors, f"Publishable Wiki pages must be flat: {path}")

    pages = sorted(directory.glob("*.md"))
    names = {path.name for path in pages}
    for required in sorted(REQUIRED - names):
        append_error(errors, f"Missing required page: {required}")
    return pages


def index_pages(pages: list[Path], errors: list[str]) -> dict[str, Path]:
    """Index pages by normalized slug and reject ambiguous names."""
    by_slug: dict[str, Path] = {}
    for path in pages:
        key = slug(path.stem)
        previous = by_slug.get(key)
        if previous is None:
            by_slug[key] = path
        else:
            append_error(
                errors,
                f"Duplicate page slug: {path.name} and {previous.name}",
            )
    return by_slug


def read_page(path: Path, errors: list[str]) -> tuple[bytes, str] | None:
    """Read one page, rejecting binary or non-UTF-8 content."""
    raw = path.read_bytes()
    if b"\x00" in raw:
        append_error(errors, f"NUL byte in {path.name}")
        return None

    try:
        return raw, raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        append_error(errors, f"{path.name} is not UTF-8: {exc}")
        return None


def validate_page_format(
    path: Path,
    raw: bytes,
    text: str,
    errors: list[str],
) -> None:
    """Validate encoding-independent page formatting rules."""
    if "\r" in text:
        append_error(errors, f"{path.name} contains CR/CRLF; use LF line endings")
    if path.name != "_Sidebar.md" and not HEADING.search(text):
        append_error(errors, f"{path.name} must start with an H1 heading")
    if PLACEHOLDER.search(text):
        append_error(errors, f"{path.name} contains TODO/TBD/FIXME placeholder text")
    if len(raw) > MAX_PAGE_BYTES:
        append_error(errors, f"{path.name} exceeds 500 KB")


def validate_wiki_links(
    path: Path,
    text: str,
    by_slug: dict[str, Path],
    errors: list[str],
) -> None:
    """Validate GitHub Wiki-style links such as ``[[Page Name]]``."""
    for match in WIKI_LINK.finditer(text):
        target = match.group(1)
        key = slug(target)
        if key and key not in by_slug:
            append_error(errors, f"{path.name}: broken Wiki link [[{target}]]")


def is_external_or_anchor(target: str) -> bool:
    """Return whether a Markdown link does not resolve inside the repository."""
    return target.startswith(("#", "http://", "https://", "mailto:"))


def validate_markdown_links(path: Path, text: str, errors: list[str]) -> None:
    """Validate relative Markdown links while allowing external and root links."""
    for match in MD_LINK.finditer(text):
        target = match.group(1).strip()
        if not target or is_external_or_anchor(target):
            continue

        relative_target = target.split("#", 1)[0]
        if relative_target.startswith("/"):
            continue

        resolved = (path.parent / relative_target).resolve()
        if not resolved.exists():
            append_error(errors, f"{path.name}: broken relative link ({relative_target})")


def validate_page(
    path: Path,
    by_slug: dict[str, Path],
    errors: list[str],
) -> None:
    """Apply every content rule to one Markdown page."""
    page = read_page(path, errors)
    if page is None:
        return

    raw, text = page
    validate_page_format(path, raw, text, errors)
    validate_wiki_links(path, text, by_slug, errors)
    validate_markdown_links(path, text, errors)


def validate(directory: Path) -> list[str]:
    """Validate a flat directory of publishable GitHub Wiki pages."""
    errors: list[str] = []
    pages = collect_pages(directory, errors)
    by_slug = index_pages(pages, errors)
    for path in pages:
        validate_page(path, by_slug, errors)
    return errors


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "directory",
        nargs="?",
        default="docs/wiki/pages",
        type=Path,
        help="Directory containing flat Wiki Markdown pages",
    )
    return parser.parse_args()


def report_errors(errors: list[str]) -> None:
    """Print validation failures in a stable, readable format."""
    print("Wiki validation failed:", file=sys.stderr)
    for error in errors:
        print(f"  - {error}", file=sys.stderr)


def main() -> int:
    """Run Wiki validation and return a process exit code."""
    args = parse_args()
    errors = validate(args.directory)
    if errors:
        report_errors(errors)
        return 1

    count = len(list(args.directory.glob("*.md")))
    print(f"Wiki validation passed: {count} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
