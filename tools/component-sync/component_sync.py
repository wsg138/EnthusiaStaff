#!/usr/bin/env python3
"""Read-only deterministic comparison of aggregate and standalone component trees."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path

IGNORED_DIRS = {'.git'}
ORCHESTRATION_FILES = {'COMPONENT-METADATA.md'}
FORBIDDEN_DIRS = {
    '.gradle', '.idea', '.vscode', 'build', 'target', 'out', 'node_modules',
    'logs', 'log', 'cache', 'caches', 'runtime', 'server', 'servers', 'tmp', 'temp'
}
FORBIDDEN_NAMES = {
    '.env', '.env.local', '.env.production', 'credentials.json', 'secrets.json'
}
FORBIDDEN_SUFFIXES = {
    '.db', '.sqlite', '.sqlite3', '.log', '.jks', '.p12', '.pfx', '.pem',
    '.key', '.crt', '.jar', '.class', '.zip', '.tar', '.gz', '.7z'
}


class ComparisonRefused(RuntimeError):
    """Raised when a directory cannot be safely used for parity evidence."""


def scan(root: Path) -> dict[str, str]:
    root = root.resolve()
    if not root.is_dir():
        raise ComparisonRefused(f'not a directory: {root}')

    files: dict[str, str] = {}
    forbidden: list[str] = []
    for current, dirs, names in os.walk(root):
        current_path = Path(current)
        relative_dir = current_path.relative_to(root)
        kept_dirs: list[str] = []
        for directory in dirs:
            relative = (relative_dir / directory).as_posix()
            if directory in IGNORED_DIRS:
                continue
            if directory in FORBIDDEN_DIRS:
                forbidden.append(relative + '/')
                continue
            kept_dirs.append(directory)
        dirs[:] = kept_dirs

        for name in names:
            path = current_path / name
            relative = path.relative_to(root).as_posix()
            if relative in ORCHESTRATION_FILES:
                continue
            if path.is_symlink():
                forbidden.append(relative + ' (symlink)')
                continue
            lower = name.lower()
            if lower in FORBIDDEN_NAMES or any(lower.endswith(suffix) for suffix in FORBIDDEN_SUFFIXES):
                forbidden.append(relative)
                continue
            files[relative] = hashlib.sha256(path.read_bytes()).hexdigest()

    if forbidden:
        shown = ', '.join(sorted(forbidden)[:50])
        raise ComparisonRefused('forbidden generated/private/runtime artifacts detected: ' + shown)
    return files


def content_hash(root: Path, files: dict[str, str]) -> str:
    digest = hashlib.sha256()
    root = root.resolve()
    for relative in sorted(files):
        data = (root / relative).read_bytes()
        encoded = relative.encode('utf-8')
        digest.update(len(encoded).to_bytes(8, 'big'))
        digest.update(encoded)
        digest.update(len(data).to_bytes(8, 'big'))
        digest.update(data)
    return digest.hexdigest()


def manifest(root: Path, revision: str | None) -> dict[str, object]:
    files = scan(root)
    return {
        'root': str(root.resolve()),
        'revision': revision or 'UNSPECIFIED',
        'method': 'sha256(sorted-posix-path,path-length,raw-byte-length,raw-bytes)',
        'excluded_orchestration_files': sorted(ORCHESTRATION_FILES),
        'file_count': len(files),
        'content_hash': content_hash(root, files),
        'files': files,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest='command', required=True)
    manifest_parser = subparsers.add_parser('manifest')
    manifest_parser.add_argument('root')
    manifest_parser.add_argument('--revision')
    hash_parser = subparsers.add_parser('hash')
    hash_parser.add_argument('root')
    hash_parser.add_argument('--revision')
    compare_parser = subparsers.add_parser('compare')
    compare_parser.add_argument('aggregate')
    compare_parser.add_argument('standalone')
    compare_parser.add_argument('--aggregate-sha')
    compare_parser.add_argument('--standalone-sha')
    args = parser.parse_args()

    try:
        if args.command in {'manifest', 'hash'}:
            result = manifest(Path(args.root), args.revision)
            print(result['content_hash'] if args.command == 'hash' else json.dumps(result, indent=2, sort_keys=True))
            return 0

        aggregate = manifest(Path(args.aggregate), args.aggregate_sha)
        standalone = manifest(Path(args.standalone), args.standalone_sha)
        aggregate_files = aggregate['files']
        standalone_files = standalone['files']
        assert isinstance(aggregate_files, dict) and isinstance(standalone_files, dict)
        added = sorted(set(aggregate_files) - set(standalone_files))
        removed = sorted(set(standalone_files) - set(aggregate_files))
        modified = sorted(
            path for path in set(aggregate_files) & set(standalone_files)
            if aggregate_files[path] != standalone_files[path]
        )
        output = {
            'aggregate_sha': aggregate['revision'],
            'standalone_sha': standalone['revision'],
            'aggregate_hash': aggregate['content_hash'],
            'standalone_hash': standalone['content_hash'],
            'parity': not (added or removed or modified),
            'added_to_aggregate': added,
            'missing_from_aggregate': removed,
            'modified': modified,
        }
        print(json.dumps(output, indent=2, sort_keys=True))
        return 0 if output['parity'] else 1
    except ComparisonRefused as exc:
        print(json.dumps({'parity': False, 'refused': True, 'reason': str(exc)}, indent=2))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
