#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest

MODULE_PATH = Path(__file__).with_name('validate_orchestration.py')
SPEC = importlib.util.spec_from_file_location('validate_orchestration', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError('unable to load validate_orchestration.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class RegistryRoutingTest(unittest.TestCase):
    def test_parse_registry_packages_reads_classification(self) -> None:
        registry = """
### `ES-P02` — Blocked

| Field | Value |
| --- | --- |
| Status | `BLOCKED` |
| Classification | `PARKED_BLOCKED` |
| Priority | `20` |
"""
        packages = MODULE.parse_registry_packages(registry)
        self.assertEqual('BLOCKED', packages['ES-P02']['Status'])
        self.assertEqual(
            'PARKED_BLOCKED',
            packages['ES-P02']['Classification'],
        )

    def test_parked_blocked_is_skipped_for_ready_package(self) -> None:
        packages = {
            'ES-P02': {
                'Status': 'BLOCKED',
                'Classification': 'PARKED_BLOCKED',
                'Priority': '20',
            },
            'ES-X05': {'Status': 'READY', 'Priority': '35'},
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
            'ES-X05': {'Status': 'READY', 'Priority': '35'},
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
            'ES-X05': {'Status': 'READY', 'Priority': '35'},
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


if __name__ == '__main__':
    unittest.main()
