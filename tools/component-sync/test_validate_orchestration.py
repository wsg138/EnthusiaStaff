#!/usr/bin/env python3
import importlib.util
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

MODULE_PATH = Path(__file__).with_name('validate_orchestration.py')
SPEC = importlib.util.spec_from_file_location('validate_orchestration', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError('unable to load validate_orchestration.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class RegistryRoutingTest(unittest.TestCase):
    def test_parse_registry_packages_reads_classification(self) -> None:
        registry = """
| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-P02` | Runtime recovery | `BLOCKED` | `PARKED_BLOCKED` | 20 | `ES-P01` | waiting |
"""
        packages = MODULE.parse_registry_packages(registry)
        self.assertEqual('BLOCKED', packages['ES-P02']['Status'])
        self.assertEqual(
            'PARKED_BLOCKED',
            packages['ES-P02']['Classification'],
        )

    def test_parse_registry_packages_rejects_duplicate_id(self) -> None:
        registry = """
| ID | Status | Priority |
| --- | --- | ---: |
| `ES-P01` | `COMPLETE` | 10 |
| `ES-P01` | `READY` | 20 |
"""
        with self.assertRaisesRegex(ValueError, 'duplicate registry IDs: ES-P01'):
            MODULE.parse_registry_packages(registry)

    def test_package_inventory_reports_conflicting_duplicate_id(self) -> None:
        registry = """
| ID | Status | Priority |
| --- | --- | ---: |
| `ES-P01` | `COMPLETE` | 10 |
| `ES-P01` | `READY` | 20 |
"""
        with TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            package_root = root / 'ai-agents' / 'work-packages'
            package_directory = package_root / 'packages'
            package_directory.mkdir(parents=True)
            (package_directory / 'ES-P01.md').write_text('', encoding='utf-8')
            (package_root / 'PACKAGE-REGISTRY.md').write_text(
                registry,
                encoding='utf-8',
            )

            errors, _, _, packages = MODULE._package_inventory(root)

        self.assertIn('duplicate registry entry ES-P01', errors)
        self.assertEqual('COMPLETE', packages['ES-P01']['Status'])

    def test_parked_blocked_is_skipped_for_ready_package(self) -> None:
        packages = {
            'ES-P02': {
                'Status': 'BLOCKED',
                'Classification': 'PARKED_BLOCKED',
                'Priority': '20',
            },
            'ES-X05': {'Status': 'READY', 'Classification': 'READY', 'Priority': '35'},
            'ES-P01': {'Status': 'COMPLETE', 'Priority': '10'},
        }
        errors, selected = MODULE.validate_registry_routing(
            packages,
            {'ES-P02': ['ES-P01'], 'ES-X05': ['ES-P01'], 'ES-P01': []},
        )
        self.assertEqual([], errors)
        self.assertEqual('ES-X05', selected)

    def test_actionable_continuation_precedes_ready_package(self) -> None:
        packages = {
            'ES-P02': {
                'Status': 'BLOCKED',
                'Classification': 'ACTIONABLE_CONTINUATION',
                'Priority': '20',
            },
            'ES-X05': {'Status': 'READY', 'Classification': 'READY', 'Priority': '35'},
            'ES-P01': {'Status': 'COMPLETE', 'Priority': '10'},
        }
        errors, selected = MODULE.validate_registry_routing(
            packages,
            {'ES-P02': ['ES-P01'], 'ES-X05': ['ES-P01'], 'ES-P01': []},
        )
        self.assertEqual([], errors)
        self.assertEqual('ES-P02', selected)

    def test_persistent_status_requires_classification(self) -> None:
        errors, selected = MODULE.validate_registry_routing(
            {'ES-P02': {'Status': 'BLOCKED', 'Priority': '20'}},
            {'ES-P02': []},
        )
        self.assertIsNone(selected)
        self.assertTrue(
            any('requires routing classification' in error for error in errors)
        )

    def test_ready_package_requires_complete_dependencies(self) -> None:
        packages = {
            'ES-X05': {'Status': 'READY', 'Classification': 'READY', 'Priority': '35'},
            'ES-P01': {
                'Status': 'PARTIAL',
                'Priority': '10',
                'Classification': 'ACTIONABLE_CONTINUATION',
            },
        }
        errors, selected = MODULE.validate_registry_routing(
            packages,
            {'ES-X05': ['ES-P01'], 'ES-P01': []},
        )
        self.assertEqual('ES-P01', selected)
        self.assertTrue(
            any('READY with incomplete dependencies' in error for error in errors)
        )

    def test_legacy_scan_ignores_local_analyzer_outputs(self) -> None:
        with TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            analyzer_log = root / '.codacy' / 'logs' / 'analysis.txt'
            analyzer_log.parent.mkdir(parents=True)
            legacy_marker = 'MIRROR' + '_PENDING'
            analyzer_log.write_bytes(f'{legacy_marker} local analyzer output'.encode('utf-16'))

            errors = MODULE._legacy_policy_errors(root)

        self.assertEqual([], errors)


if __name__ == '__main__':
    unittest.main()
