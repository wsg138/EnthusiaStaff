import importlib.util
import tempfile
import unittest
from pathlib import Path
from types import ModuleType

MODULE_PATH = Path(__file__).with_name('component_sync.py')


def load_component_sync() -> ModuleType:
    spec = importlib.util.spec_from_file_location('component_sync', MODULE_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError('unable to load component_sync module')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


component_sync = load_component_sync()
ComparisonRefused = component_sync.ComparisonRefused


class ComponentSyncTests(unittest.TestCase):
    def test_equal_ignores_only_aggregate_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            aggregate = Path(temp) / 'aggregate'
            standalone = Path(temp) / 'standalone'
            aggregate.mkdir()
            standalone.mkdir()
            (aggregate / 'src.txt').write_text('same\n', encoding='utf-8')
            (standalone / 'src.txt').write_text('same\n', encoding='utf-8')
            (aggregate / 'COMPONENT-METADATA.md').write_text(
                'orchestration\n', encoding='utf-8'
            )
            result = component_sync.compare_trees(
                aggregate, standalone, aggregate_sha='a', standalone_sha='b'
            )
            self.assertTrue(result['parity'])
            self.assertEqual('a', result['aggregate_sha'])
            self.assertEqual('b', result['standalone_sha'])

    def test_reports_added_removed_and_modified(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            aggregate = Path(temp) / 'aggregate'
            standalone = Path(temp) / 'standalone'
            aggregate.mkdir()
            standalone.mkdir()
            (aggregate / 'added.txt').write_text('x', encoding='utf-8')
            (standalone / 'missing.txt').write_text('x', encoding='utf-8')
            (aggregate / 'changed.txt').write_text('aggregate', encoding='utf-8')
            (standalone / 'changed.txt').write_text('standalone', encoding='utf-8')
            result = component_sync.compare_trees(aggregate, standalone)
            self.assertFalse(result['parity'])
            self.assertEqual(['added.txt'], result['added_to_aggregate'])
            self.assertEqual(['missing.txt'], result['missing_from_aggregate'])
            self.assertEqual(['changed.txt'], result['modified'])

    def test_forbidden_artifact_refuses_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'component'
            root.mkdir()
            (root / 'private.db').write_bytes(b'x')
            with self.assertRaises(ComparisonRefused):
                component_sync.manifest(root)

    def test_file_symlink_refuses_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'component'
            root.mkdir()
            target = root / 'target.txt'
            target.write_text('x', encoding='utf-8')
            try:
                (root / 'link.txt').symlink_to(target)
            except OSError:
                self.skipTest('symlinks unavailable')
            with self.assertRaises(ComparisonRefused):
                component_sync.manifest(root)

    def test_directory_symlink_refuses_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'component'
            target = Path(temp) / 'target'
            root.mkdir()
            target.mkdir()
            try:
                (root / 'linked').symlink_to(target, target_is_directory=True)
            except OSError:
                self.skipTest('directory symlinks unavailable')
            with self.assertRaises(ComparisonRefused):
                component_sync.manifest(root)

    def test_root_symlink_refuses_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / 'target'
            link = Path(temp) / 'link'
            target.mkdir()
            try:
                link.symlink_to(target, target_is_directory=True)
            except OSError:
                self.skipTest('directory symlinks unavailable')
            with self.assertRaises(ComparisonRefused):
                component_sync.manifest(link)

    def test_gradle_wrapper_jar_is_compared(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            aggregate = Path(temp) / 'aggregate'
            standalone = Path(temp) / 'standalone'
            for root in (aggregate, standalone):
                wrapper = root / 'gradle' / 'wrapper'
                wrapper.mkdir(parents=True)
                (wrapper / 'gradle-wrapper.jar').write_bytes(b'wrapper')
            result = component_sync.compare_trees(aggregate, standalone)
            self.assertTrue(result['parity'])

    def test_nested_git_file_refuses_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'component'
            root.mkdir()
            (root / '.git').write_text('gitdir: elsewhere', encoding='utf-8')
            with self.assertRaises(ComparisonRefused):
                component_sync.manifest(root)


if __name__ == '__main__':
    unittest.main()
