#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
PACKAGE_ROOT = ROOT / 'ai-agents' / 'work-packages'
errors: list[str] = []

package_files = sorted((PACKAGE_ROOT / 'packages').glob('ES-*.md'))
package_ids = [path.stem for path in package_files]
if len(package_ids) != 21:
    errors.append(f'expected 21 package files, found {len(package_ids)}')
if len(package_ids) != len(set(package_ids)):
    errors.append('duplicate package IDs')

registry = (PACKAGE_ROOT / 'PACKAGE-REGISTRY.md').read_text(encoding='utf-8')
for package_id in package_ids:
    if f'### `{package_id}`' not in registry:
        errors.append(f'{package_id} missing registry entry')
ready_packages = re.findall(
    r'### `(ES-[A-Z]+\d+)`.*?\| Status \| `READY` \|',
    registry,
    flags=re.S,
)
if ready_packages != ['ES-P01']:
    errors.append(f'exactly ES-P01 must be READY, got {ready_packages}')

coverage = (PACKAGE_ROOT / 'AUDIT-COVERAGE.md').read_text(encoding='utf-8')
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

required = [
    'components/README.md',
    'tools/component-sync/component_sync.py',
    'ai-agents/work-packages/COMPONENT-REGISTRY.md',
]
for relative in required:
    if not (ROOT / relative).exists():
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
for path in ROOT.rglob('*'):
    if not path.is_file() or path.suffix not in {'.md', '.txt', '.py'}:
        continue
    relative = path.relative_to(ROOT).as_posix()
    if relative == 'tools/component-sync/validate_orchestration.py':
        continue
    text = path.read_text(encoding='utf-8')
    for pattern in legacy_patterns:
        if re.search(pattern, text, flags=re.I) and relative not in negative_policy_docs:
            errors.append(f'legacy component-branch requirement in {relative}: {pattern}')

if (ROOT / 'tools/component-sync/core-allowlist.txt').exists():
    errors.append('component-only core allowlist must not exist')
if (ROOT / 'COMPONENT-METADATA.md').exists():
    errors.append('root component metadata from abandoned branch design must not exist')

if errors:
    print('\n'.join('ERROR: ' + error for error in errors))
    sys.exit(1)
print(
    f'OK: {len(package_ids)} packages, 99 audit IDs, acyclic dependencies, '
    'only ES-P01 READY, simplified aggregate/standalone model'
)
