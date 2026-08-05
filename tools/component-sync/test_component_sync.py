import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name('component_sync.py')


class ComponentSyncTests(unittest.TestCase):
    def run_tool(self, *args: object) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), *map(str, args)],
            text=True,
            capture_output=True,
            check=False,
        )

    def test_equal_ignores_only_aggregate_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            aggregate = Path(temp) / 'aggregate'
            standalone = Path(temp) / 'standalone'
            aggregate.mkdir()
            standalone.mkdir()
            (aggregate / 'src.txt').write_text('same\n', encoding='utf-8')
            (standalone / 'src.txt').write_text('same\n', encoding='utf-8')
            (aggregate / 'COMPONENT-METADATA.md').write_text('orchestration\n', encoding='utf-8')
            result = self.run_tool(
                'compare', aggregate, standalone,
                '--aggregate-sha', 'a', '--standalone-sha', 'b'
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            data = json.loads(result.stdout)
            self.assertTrue(data['parity'])
            self.assertEqual('a', data['aggregate_sha'])
            self.assertEqual('b', data['standalone_sha'])

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
            result = self.run_tool('compare', aggregate, standalone)
            self.assertEqual(1, result.returncode)
            data = json.loads(result.stdout)
            self.assertEqual(['added.txt'], data['added_to_aggregate'])
            self.assertEqual(['missing.txt'], data['missing_from_aggregate'])
            self.assertEqual(['changed.txt'], data['modified'])

    def test_forbidden_artifact_refuses_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'component'
            root.mkdir()
            (root / 'private.db').write_bytes(b'x')
            result = self.run_tool('manifest', root)
            self.assertEqual(2, result.returncode)
            self.assertTrue(json.loads(result.stdout)['refused'])

    def test_symlink_refuses_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'component'
            root.mkdir()
            target = root / 'target.txt'
            target.write_text('x', encoding='utf-8')
            try:
                (root / 'link.txt').symlink_to(target)
            except OSError:
                self.skipTest('symlinks unavailable')
            result = self.run_tool('manifest', root)
            self.assertEqual(2, result.returncode)

    def test_gradle_wrapper_jar_is_compared(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            aggregate = Path(temp) / 'aggregate'
            standalone = Path(temp) / 'standalone'
            for root in (aggregate, standalone):
                wrapper = root / 'gradle' / 'wrapper'
                wrapper.mkdir(parents=True)
                (wrapper / 'gradle-wrapper.jar').write_bytes(b'wrapper')
            result = self.run_tool('compare', aggregate, standalone)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_nested_git_file_refuses_parity(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'component'
            root.mkdir()
            (root / '.git').write_text('gitdir: elsewhere', encoding='utf-8')
            result = self.run_tool('manifest', root)
            self.assertEqual(2, result.returncode)
            self.assertTrue(json.loads(result.stdout)['refused'])


if __name__ == '__main__':
    unittest.main()
