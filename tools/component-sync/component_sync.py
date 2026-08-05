#!/usr/bin/env python3
"""Read-only deterministic comparison of aggregate and standalone component trees."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
from typing import Any

IGNORED_DIRS = {'.git'}
ORCHESTRATION_FILES = {'COMPONENT-METADATA.md'}
ALLOWED_BINARY_PATHS = {'gradle/wrapper/gradle-wrapper.jar'}
FORBIDDEN_DIRS = {
    '.gradle', '.idea', '.vscode', 'build', 'target', 'out', 'node_modules',
    'logs', 'log', 'cache', 'caches', 'runtime', 'server', 'servers', 'tmp', 'temp'
}
FORBIDDEN_NAMES = {
    '.git', '.env', '.env.local', '.env.production', 'credentials.json', 'secrets.json'
}
FORBIDDEN_SUFFIXES = {
    '.db', '.sqlite', '.sqlite3', '.log', '.jks', '.p12', '.pfx', '.pem',
    '.key', '.crt', '.jar', '.class', '.zip', '.tar', '.gz', '.7z'
}


class ComparisonRefused(RuntimeError):
    """Raised when a directory cannot be safely used for parity evidence."""


def _sha256(data: bytes = b'') -> Any:
    """Return SHA-256 for content identity, not a cryptographic security decision."""
    return hashlib.sha256(data, usedforsecurity=False)


def _resolved_directory(root: Path) -> Path:
    if root.is_symlink():
        raise ComparisonRefused(f'root must not be a symlink: {root}')
    resolved = root.resolve()
    if not resolved.is_dir():
        raise ComparisonRefused(f'not a directory: {resolved}')
    return resolved


def _filter_directories(
    current_path: Path,
    relative_dir: Path,
    directories: list[str],
    forbidden: list[str],
) -> list[str]:
    kept: list[str] = []
    for directory in directories:
        candidate = current_path / directory
        relative = (relative_dir / directory).as_posix()
        if directory in IGNORED_DIRS:
            continue
        if candidate.is_symlink():
            forbidden.append(relative + '/ (symlink)')
        elif directory in FORBIDDEN_DIRS:
            forbidden.append(relative + '/')
        else:
            kept.append(directory)
    return kept


def _file_is_forbidden(path: Path, relative: str) -> bool:
    lower = path.name.lower()
    if lower in FORBIDDEN_NAMES:
        return True
    if relative in ALLOWED_BINARY_PATHS:
        return False
    return any(lower.endswith(suffix) for suffix in FORBIDDEN_SUFFIXES)


def _record_file(
    root: Path,
    path: Path,
    files: dict[str, str],
    forbidden: list[str],
) -> None:
    relative = path.relative_to(root).as_posix()
    if relative in ORCHESTRATION_FILES:
        return
    if path.is_symlink():
        forbidden.append(relative + ' (symlink)')
        return
    if _file_is_forbidden(path, relative):
        forbidden.append(relative)
        return
    files[relative] = _sha256(path.read_bytes()).hexdigest()


def scan(root: Path) -> dict[str, str]:
    """Return a file digest map, refusing unsafe or non-source artifacts."""
    resolved_root = _resolved_directory(root)
    files: dict[str, str] = {}
    forbidden: list[str] = []
    for current, directories, names in os.walk(resolved_root, followlinks=False):
        current_path = Path(current)
        relative_dir = current_path.relative_to(resolved_root)
        directories[:] = _filter_directories(
            current_path, relative_dir, directories, forbidden
        )
        for name in names:
            _record_file(resolved_root, current_path / name, files, forbidden)

    if forbidden:
        shown = ', '.join(sorted(forbidden)[:50])
        raise ComparisonRefused(
            'forbidden generated/private/runtime artifacts detected: ' + shown
        )
    return files


def content_hash(root: Path, files: dict[str, str]) -> str:
    """Hash normalized relative paths and raw bytes in deterministic order."""
    digest = _sha256()
    resolved_root = _resolved_directory(root)
    for relative in sorted(files):
        data = (resolved_root / relative).read_bytes()
        encoded = relative.encode('utf-8')
        digest.update(len(encoded).to_bytes(8, 'big'))
        digest.update(encoded)
        digest.update(len(data).to_bytes(8, 'big'))
        digest.update(data)
    return digest.hexdigest()


def manifest(root: Path, revision: str | None = None) -> dict[str, object]:
    """Build a normalized manifest for one component directory."""
    resolved_root = _resolved_directory(root)
    files = scan(resolved_root)
    return {
        'root': str(resolved_root),
        'revision': revision or 'UNSPECIFIED',
        'method': 'sha256(sorted-posix-path,path-length,raw-byte-length,raw-bytes)',
        'excluded_orchestration_files': sorted(ORCHESTRATION_FILES),
        'file_count': len(files),
        'content_hash': content_hash(resolved_root, files),
        'files': files,
    }


def _manifest_files(value: dict[str, object]) -> dict[str, str]:
    files = value.get('files')
    if not isinstance(files, dict):
        raise ComparisonRefused('manifest files entry is not a dictionary')
    if not all(isinstance(path, str) and isinstance(digest, str) for path, digest in files.items()):
        raise ComparisonRefused('manifest files entry contains invalid values')
    return files


def compare_trees(
    aggregate_root: Path,
    standalone_root: Path,
    aggregate_sha: str | None = None,
    standalone_sha: str | None = None,
) -> dict[str, object]:
    """Compare aggregate and standalone source trees and report exact drift."""
    aggregate = manifest(aggregate_root, aggregate_sha)
    standalone = manifest(standalone_root, standalone_sha)
    aggregate_files = _manifest_files(aggregate)
    standalone_files = _manifest_files(standalone)
    common = set(aggregate_files) & set(standalone_files)
    added = sorted(set(aggregate_files) - set(standalone_files))
    removed = sorted(set(standalone_files) - set(aggregate_files))
    modified = sorted(
        path for path in common
        if aggregate_files[path] != standalone_files[path]
    )
    return {
        'aggregate_sha': aggregate['revision'],
        'standalone_sha': standalone['revision'],
        'aggregate_hash': aggregate['content_hash'],
        'standalone_hash': standalone['content_hash'],
        'parity': not (added or removed or modified),
        'added_to_aggregate': added,
        'missing_from_aggregate': removed,
        'modified': modified,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest='command', required=True)
    for command in ('manifest', 'hash'):
        command_parser = subparsers.add_parser(command)
        command_parser.add_argument('root')
        command_parser.add_argument('--revision')
    compare_parser = subparsers.add_parser('compare')
    compare_parser.add_argument('aggregate')
    compare_parser.add_argument('standalone')
    compare_parser.add_argument('--aggregate-sha')
    compare_parser.add_argument('--standalone-sha')
    return parser


def _run_manifest_command(args: argparse.Namespace) -> int:
    result = manifest(Path(args.root), args.revision)
    output = result['content_hash'] if args.command == 'hash' else json.dumps(
        result, indent=2, sort_keys=True
    )
    print(output)
    return 0


def _run_compare_command(args: argparse.Namespace) -> int:
    output = compare_trees(
        Path(args.aggregate),
        Path(args.standalone),
        args.aggregate_sha,
        args.standalone_sha,
    )
    print(json.dumps(output, indent=2, sort_keys=True))
    return 0 if output['parity'] else 1


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.command in {'manifest', 'hash'}:
            return _run_manifest_command(args)
        return _run_compare_command(args)
    except ComparisonRefused as exc:
        print(json.dumps({'parity': False, 'refused': True, 'reason': str(exc)}, indent=2))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
