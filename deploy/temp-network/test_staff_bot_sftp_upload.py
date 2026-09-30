from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

MODULE_PATH = Path(__file__).with_name("staff-bot-sftp-upload.py")
SPEC = importlib.util.spec_from_file_location("staff_bot_sftp_upload", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Staff Bot uploader module could not be loaded")
uploader = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(uploader)


class FakeSftp:
    def __init__(self, *, corrupt_download: bool = False) -> None:
        self.files: dict[str, bytes] = {}
        self.corrupt_download = corrupt_download

    def stat(self, path: str):
        if path in self.files:
            return SimpleNamespace(st_size=len(self.files[path]))
        raise FileNotFoundError(path)

    def put(self, local_path: str, remote_path: str) -> None:
        self.files[remote_path] = Path(local_path).read_bytes()

    def get(self, remote_path: str, local_path: str) -> None:
        data = self.files[remote_path]
        if self.corrupt_download and ".uploading-" in remote_path:
            data = b"corrupt"
        Path(local_path).write_bytes(data)

    def rename(self, source: str, destination: str) -> None:
        if source not in self.files:
            raise FileNotFoundError(source)
        self.files[destination] = self.files.pop(source)

    def remove(self, path: str) -> None:
        if path not in self.files:
            raise FileNotFoundError(path)
        del self.files[path]


class StaffBotUploaderTest(unittest.TestCase):
    def test_failed_remote_hash_restores_existing_jar(self) -> None:
        sftp = FakeSftp(corrupt_download=True)
        sftp.files["./EnthusiaStaff-StaffBot.jar"] = b"old"
        with tempfile.TemporaryDirectory() as directory:
            candidate = Path(directory) / "candidate.jar"
            candidate.write_bytes(b"candidate")
            expected = uploader.file_sha256(candidate)
            with self.assertRaises(uploader.DeploymentError):
                uploader.deploy_jar(sftp, ".", "EnthusiaStaff-StaffBot.jar", candidate, expected)

        self.assertEqual(b"old", sftp.files["./EnthusiaStaff-StaffBot.jar"])
        self.assertFalse(any(".uploading-" in path for path in sftp.files))

    def test_remote_paths_reject_traversal(self) -> None:
        for value in ("../bot", "/bot", "bot/../other", "bot\\..\\other"):
            with self.subTest(value=value):
                with self.assertRaises(uploader.DeploymentError):
                    uploader.validate_remote_root(value)
        with self.assertRaises(uploader.DeploymentError):
            uploader.validate_filename("../m", "config name")

    def test_property_map_accepts_equals_and_colon(self) -> None:
        values = uploader.property_map(b"a=one\nb:two\n# ignored\n")
        self.assertEqual({"a": "one", "b": "two"}, values)


if __name__ == "__main__":
    unittest.main()
