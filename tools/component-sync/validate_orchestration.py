#!/usr/bin/env python3
from pathlib import Path
import re
import sys
from typing import Mapping

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
    """Parse package tables from the canonical registry."""
    headings = list(
        re.finditer(r'^### `(ES-[A-Z]+\d+)`.*$', registry, flags=re.M)
    )
    packages: dict[str, dict[str, str]] = {}
    for index, heading in enumerate(headings):
        package_id = heading.group(1)
        end = headings[index + 1].start() if index + 1 < len(headings) else len(registry)
        block = registry[heading.end():end]
        fields = {
            match.group(1).strip(): _plain_field(match.group(2))
            for match in re.finditer(r'^\| ([^|]+?) \| (.*?) \|$', block, flags=re.M)
        }
        packages[package_id] = fields
    return packages


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
        elif classification is not None:
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


def validate(root: Path = ROOT) -> tuple[list[str], str | None, int, int]:
    """Validate package orchestration files under one repository root."""
    errors: list[str] = []
    package_root = root / 'ai-agents' / 'work-packages'
    package_files = sorted((package_root / 'packages').glob('ES-*.md'))
    package_ids = [path.stem for path in package_files]
    if len(package_ids) != 21:
        errors.append(f'expected 21 package files, found {len(package_ids)}')
    if len(package_ids) != len(set(package_ids)):
        errors.append('duplicate package IDs')

    registry = (package_root / 'PACKAGE-REGISTRY.md').read_text(encoding='utf-8')
    registry_packages = parse_registry_packages(registry)
    for package_id in package_ids:
        if package_id not in registry_packages:
            errors.append(f'{package_id} missing registry entry')
    unexpected_packages = sorted(set(registry_packages) - set(package_ids))
    if unexpected_packages:
        errors.append('registry entries without package files: ' + ', '.join(unexpected_packages))

    coverage = (package_root / 'AUDIT-COVERAGE.md').read_text(encoding='utf-8')
    audit_ids = re.findall(r'\| `(AUD-[A-Z]+-\d{3})` \|', coverage)
    if len(audit_ids) != 99 or len(set(audit_ids)) != 99:
        errors.append(
            f'audit coverage must have 99 unique IDs, got '
            f'{len(audit_ids)}/{len(set(audit_ids))}'
        )

    dependencies: dict[str, list[str]] = {}
    for path in package_files:
        text = path.read_text(encoding='utf-8')
        for section in range(1, 29):
            if f'## {section}.' not in text:
                errors.append(f'{path.stem} missing section {section}')
        dependency_parts = text.split('## 8. Dependencies', 1)
        if len(dependency_parts) != 2:
            dependencies[path.stem] = []
            continue
        dependency_section = dependency_parts[1].split('## 9.', 1)[0]
        dependencies[path.stem] = [
            value
            for value in re.findall(r'`(ES-[A-Z]+\d+)`', dependency_section)
            if value != path.stem
        ]

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

    routing_errors, selected_package = validate_registry_routing(
        registry_packages,
        dependencies,
    )
    errors.extend(routing_errors)

    required = [
        'components/README.md',
        'tools/component-sync/component_sync.py',
        'ai-agents/work-packages/COMPONENT-REGISTRY.md',
    ]
    for relative in required:
        if not (root / relative).exists():
            errors.append(f'missing {relative}')

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
        if not path.is_file() or path.suffix not in {'.md', '.txt', '.py'}:
            continue
        relative = path.relative_to(root).as_posix()
        if relative == 'tools/component-sync/validate_orchestration.py':
            continue
        text = path.read_text(encoding='utf-8')
        for pattern in legacy_patterns:
            if re.search(pattern, text, flags=re.I) and relative not in negative_policy_docs:
                errors.append(f'legacy component-branch requirement in {relative}: {pattern}')

    if (root / 'tools/component-sync/core-allowlist.txt').exists():
        errors.append('component-only core allowlist must not exist')
    if (root / 'COMPONENT-METADATA.md').exists():
        errors.append('root component metadata from abandoned branch design must not exist')

    return errors, selected_package, len(package_ids), len(set(audit_ids))


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
