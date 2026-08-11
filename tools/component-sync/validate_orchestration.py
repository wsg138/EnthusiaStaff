#!/usr/bin/env python3
import re
import sys
from pathlib import Path
from typing import Mapping

MINIMUM_PYTHON = (3, 10)
if sys.version_info < MINIMUM_PYTHON:
    raise RuntimeError('validate_orchestration.py requires Python 3.10 or newer')

ROOT = Path(__file__).resolve().parents[2]
PACKAGE_ROOT = ROOT / 'ai-agents' / 'work-packages'
PERSISTENT_STATUSES = {
    'ACTIVE',
    'PARTIAL',
    'BLOCKED',
    'REVIEW',
    'MERGE_PENDING',
    'SYNC_PENDING',
}
ROUTING_CLASSIFICATIONS = {
    'ACTIONABLE_CONTINUATION',
    'PARKED_BLOCKED',
}
VALID_STATUSES = PERSISTENT_STATUSES | {
    'READY',
    'COMPLETE',
    'PLANNED',
    'DEFERRED',
    'SUPERSEDED',
}


def _plain_field(value: str) -> str:
    """Return a Markdown table value without one enclosing code span."""
    value = value.strip()
    if len(value) >= 2 and value.startswith('`') and value.endswith('`'):
        return value[1:-1]
    return value


def parse_registry_packages(registry: str) -> dict[str, dict[str, str]]:
    """Parse the canonical package-index Markdown table."""
    packages, duplicate_ids = _parse_registry_rows(registry)
    if duplicate_ids:
        raise ValueError(
            'duplicate registry IDs: ' + ', '.join(sorted(set(duplicate_ids)))
        )
    return packages


def _parse_registry_rows(
    registry: str,
) -> tuple[dict[str, dict[str, str]], list[str]]:
    """Parse package rows while preserving duplicate-ID evidence."""
    headers: list[str] | None = None
    packages: dict[str, dict[str, str]] = {}
    duplicate_ids: list[str] = []
    for line in registry.splitlines():
        cells = _table_cells(line)
        if cells is None:
            continue
        if cells and cells[0] == 'ID':
            headers = cells
            continue
        if headers is None or not _is_package_row(cells):
            continue
        fields = {
            header: value
            for header, value in zip(headers, cells, strict=False)
            if value not in {'', '-', '—'}
        }
        package_id = cells[0]
        if package_id in packages:
            duplicate_ids.append(package_id)
            continue
        packages[package_id] = fields
    return packages, duplicate_ids


def _table_cells(line: str) -> list[str] | None:
    if not line.startswith('|'):
        return None
    return [_plain_field(value) for value in line.strip().strip('|').split('|')]


def _is_package_row(cells: list[str]) -> bool:
    return bool(cells) and re.fullmatch(r'ES-[A-Z]+\d+', cells[0]) is not None


def validate_registry_routing(
    packages: Mapping[str, Mapping[str, str]],
    dependencies: Mapping[str, list[str]],
) -> tuple[list[str], str | None]:
    """Validate classification-first routing and return the selected package."""
    errors: list[str] = []
    priorities: dict[str, int] = {}

    for package_id, fields in packages.items():
        status = fields.get('Status')
        if status not in VALID_STATUSES:
            errors.append(f'{package_id} has invalid or missing status {status!r}')

        priority_value = fields.get('Priority')
        try:
            priorities[package_id] = int(priority_value or '')
        except ValueError:
            errors.append(f'{package_id} has invalid or missing priority {priority_value!r}')

        classification = fields.get('Classification')
        if status in PERSISTENT_STATUSES:
            if classification not in ROUTING_CLASSIFICATIONS:
                errors.append(
                    f'{package_id} status {status} requires routing classification '
                    'ACTIONABLE_CONTINUATION or PARKED_BLOCKED'
                )
        elif status == 'READY' and classification != 'READY':
            errors.append(f'{package_id} READY status requires READY classification')
        elif status in {'PLANNED', 'DEFERRED'} and classification != 'PARKED_BLOCKED':
            errors.append(f'{package_id} status {status} requires PARKED_BLOCKED classification')
        elif status in {'COMPLETE', 'SUPERSEDED'} and classification is not None:
            errors.append(
                f'{package_id} status {status} must not declare persistent '
                f'classification {classification}'
            )

        if status == 'READY':
            incomplete = [
                dependency
                for dependency in dependencies.get(package_id, [])
                if packages.get(dependency, {}).get('Status') != 'COMPLETE'
            ]
            if incomplete:
                errors.append(
                    f'{package_id} is READY with incomplete dependencies: '
                    + ', '.join(incomplete)
                )

    actionable = [
        package_id
        for package_id, fields in packages.items()
        if fields.get('Classification') == 'ACTIONABLE_CONTINUATION'
        and package_id in priorities
    ]
    ready = [
        package_id
        for package_id, fields in packages.items()
        if fields.get('Status') == 'READY' and package_id in priorities
    ]
    candidates = actionable or ready
    selected = min(candidates, key=lambda package_id: priorities[package_id]) if candidates else None
    return errors, selected


def _package_inventory(
    root: Path,
) -> tuple[list[str], list[Path], list[str], dict[str, dict[str, str]]]:
    """Read the package registry and validate its file inventory."""
    errors: list[str] = []
    package_root = root / 'ai-agents' / 'work-packages'
    package_files = sorted((package_root / 'packages').glob('ES-*.md'))
    package_ids = [path.stem for path in package_files]
    if len(package_ids) != len(set(package_ids)):
        errors.append('duplicate package IDs')

    registry = (package_root / 'PACKAGE-REGISTRY.md').read_text(encoding='utf-8')
    registry_packages, duplicate_registry_ids = _parse_registry_rows(registry)
    errors.extend(
        f'duplicate registry entry {package_id}'
        for package_id in sorted(set(duplicate_registry_ids))
    )
    for package_id in package_ids:
        if package_id not in registry_packages:
            errors.append(f'{package_id} missing registry entry')
    unexpected_packages = sorted(set(registry_packages) - set(package_ids))
    if unexpected_packages:
        errors.append('registry entries without package files: ' + ', '.join(unexpected_packages))
    return errors, package_files, package_ids, registry_packages


def _audit_coverage(root: Path) -> tuple[list[str], int]:
    """Validate the canonical audit-ID inventory."""
    errors: list[str] = []
    package_root = root / 'ai-agents' / 'work-packages'
    coverage = (package_root / 'AUDIT-COVERAGE.md').read_text(encoding='utf-8')
    audit_ids = re.findall(r'\| `(AUD-[A-Z]+-\d{3})` \|', coverage)
    if len(audit_ids) != 99 or len(set(audit_ids)) != 99:
        errors.append(
            f'audit coverage must have 99 unique IDs, got '
            f'{len(audit_ids)}/{len(set(audit_ids))}'
        )
    return errors, len(set(audit_ids))


def _read_dependencies(
    package_files: list[Path],
    registry_packages: Mapping[str, Mapping[str, str]],
) -> tuple[list[str], dict[str, list[str]]]:
    """Read package dependencies and report malformed package sections."""
    errors: list[str] = []
    dependencies = {
        package_id: re.findall(r'ES-[A-Z]+\d+', fields.get('Dependencies', ''))
        for package_id, fields in registry_packages.items()
    }
    for path in package_files:
        text = path.read_text(encoding='utf-8')
        status = registry_packages.get(path.stem, {}).get('Status')
        if status != 'COMPLETE':
            for section in range(1, 29):
                if f'## {section}.' not in text:
                    errors.append(f'{path.stem} missing section {section}')
    return errors, dependencies


def _dependency_errors(dependencies: Mapping[str, list[str]]) -> list[str]:
    """Report unknown dependencies and cycles."""
    errors: list[str] = []
    for package_id, values in dependencies.items():
        for value in values:
            if value not in dependencies:
                errors.append(f'{package_id} unknown dependency {value}')

    visit: dict[str, int] = {}

    def dfs(node: str, stack: list[str]) -> None:
        """Record dependency cycles without stopping other validation checks."""
        if visit.get(node) == 1:
            errors.append('dependency cycle: ' + ' -> '.join(stack + [node]))
            return
        if visit.get(node) == 2:
            return
        visit[node] = 1
        for dependency in dependencies.get(node, []):
            dfs(dependency, stack + [node])
        visit[node] = 2

    for package_id in dependencies:
        dfs(package_id, [])
    return errors


def _required_file_errors(root: Path) -> list[str]:
    """Report missing files required by the orchestration contract."""
    required = [
        'components/README.md',
        'tools/component-sync/component_sync.py',
        'ai-agents/work-packages/COMPONENT-REGISTRY.md',
    ]
    return [f'missing {relative}' for relative in required if not (root / relative).exists()]


def _legacy_policy_errors(root: Path) -> list[str]:
    """Report files that reintroduce the abandoned component-branch design."""
    errors: list[str] = []
    negative_policy_docs = {
        'WORKSPACE-MANIFEST.md',
        'components/README.md',
        'ai-agents/AGENTS.md',
        'ai-agents/README.md',
        'ai-agents/UNIVERSAL-AGENT-PROMPT.md',
        'ai-agents/WORKSPACE-STATE.md',
        'ai-agents/work-packages/README.md',
        'ai-agents/work-packages/BRANCH-AND-MIRROR-POLICY.md',
        'ai-agents/work-packages/COMPONENT-REGISTRY.md',
        'ai-agents/work-packages/templates/PACKAGE-TEMPLATE.md',
        'ai-agents/reports/package-handoffs/2026-08-05-package-planning-setup.md',
        'docs/wiki/pages/Development-Blueprint.md',
        'docs/wiki/pages/Implementation-Status.md',
        'tools/component-sync/README.md',
    }
    legacy_patterns = [
        r'component/enthusia-(?:staff|site|rosechat|currency|market|commend)',
        r'MIRROR_PENDING',
        r'core-allowlist',
        r'matching isolated component PR',
        r'Isolated PR \|',
    ]
    for path in root.rglob('*'):
        errors.extend(_legacy_file_errors(path, root, negative_policy_docs, legacy_patterns))

    if (root / 'tools/component-sync/core-allowlist.txt').exists():
        errors.append('component-only core allowlist must not exist')
    if (root / 'COMPONENT-METADATA.md').exists():
        errors.append('root component metadata from abandoned branch design must not exist')
    return errors


def _legacy_file_errors(
    path: Path,
    root: Path,
    negative_policy_docs: set[str],
    legacy_patterns: list[str],
) -> list[str]:
    if not path.is_file() or path.suffix not in {'.md', '.txt', '.py'}:
        return []
    relative = path.relative_to(root).as_posix()
    if relative == 'tools/component-sync/validate_orchestration.py' or relative in negative_policy_docs:
        return []
    text = path.read_text(encoding='utf-8')
    return [
        f'legacy component-branch requirement in {relative}: {pattern}'
        for pattern in legacy_patterns
        if re.search(pattern, text, flags=re.I)
    ]


def validate(root: Path = ROOT) -> tuple[list[str], str | None, int, int]:
    """Validate package orchestration files under one repository root."""
    errors, package_files, package_ids, registry_packages = _package_inventory(root)
    audit_errors, audit_count = _audit_coverage(root)
    dependency_section_errors, dependencies = _read_dependencies(
        package_files,
        registry_packages,
    )
    routing_errors, selected_package = validate_registry_routing(
        registry_packages,
        dependencies,
    )
    errors.extend(audit_errors)
    errors.extend(dependency_section_errors)
    errors.extend(_dependency_errors(dependencies))
    errors.extend(routing_errors)
    errors.extend(_required_file_errors(root))
    errors.extend(_legacy_policy_errors(root))

    return errors, selected_package, len(package_ids), audit_count


def main() -> int:
    """Run validation and return a process exit code."""
    errors, selected_package, package_count, audit_count = validate()
    if errors:
        print('\n'.join('ERROR: ' + error for error in errors))
        return 1
    print(
        f'OK: {package_count} packages, {audit_count} audit IDs, '
        'acyclic dependencies, classification-first routing selects '
        f'{selected_package or "NONE"}, simplified aggregate/standalone model'
    )
    return 0


if __name__ == '__main__':
    sys.exit(main())
