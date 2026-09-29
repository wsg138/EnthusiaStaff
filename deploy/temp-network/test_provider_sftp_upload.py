from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

MODULE_PATH = Path(__file__).with_name("provider-sftp-upload.py")
SPEC = importlib.util.spec_from_file_location("provider_sftp_upload", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
uploader = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(uploader)


class FakeSftp:
    def __init__(self, *, corrupt_download: bool = False) -> None:
        self.files: dict[str, bytes] = {}
        self.directories = {"plugins"}
        self.corrupt_download = corrupt_download

    def stat(self, path: str):
        if path in self.directories:
            return SimpleNamespace(st_size=0)
        if path in self.files:
            return SimpleNamespace(st_size=len(self.files[path]))
        raise FileNotFoundError(path)

    def mkdir(self, path: str) -> None:
        self.directories.add(path)

    def listdir(self, path: str) -> list[str]:
        prefix = path.rstrip("/") + "/"
        return [name[len(prefix):] for name in self.files if name.startswith(prefix) and "/" not in name[len(prefix):]]

    def rename(self, source: str, destination: str) -> None:
        if source not in self.files:
            raise FileNotFoundError(source)
        self.files[destination] = self.files.pop(source)

    def put(self, local_path: str, remote_path: str) -> None:
        self.files[remote_path] = Path(local_path).read_bytes()

    def get(self, remote_path: str, local_path: str) -> None:
        data = self.files[remote_path]
        if self.corrupt_download and ".uploading-" in remote_path:
            data = b"corrupt"
        Path(local_path).write_bytes(data)

    def remove(self, path: str) -> None:
        if path not in self.files:
            raise FileNotFoundError(path)
        del self.files[path]


class ProviderUploaderTest(unittest.TestCase):
    def test_matching_plugin_names_is_bounded_to_jar_prefix(self) -> None:
        names = [
            "EnthusiaCommend-1.jar",
            "enthusiacommend-old.JAR",
            "EnthusiaCommend.yml",
            "Other.jar",
        ]
        self.assertEqual(
            ["EnthusiaCommend-1.jar", "enthusiacommend-old.JAR"],
            uploader.matching_plugin_names(names, "EnthusiaCommend"),
        )

    def test_stage_provider_backs_up_old_jars_and_installs_verified_candidate(self) -> None:
        sftp = FakeSftp()
        sftp.files["plugins/EnthusiaCommend-old.jar"] = b"old"
        with tempfile.TemporaryDirectory() as directory:
            candidate = Path(directory) / "candidate.jar"
            candidate.write_bytes(b"candidate")
            expected = uploader.file_sha256(candidate)

            replaced, backup_dir = uploader.stage_provider(
                sftp,
                "plugins",
                candidate,
                expected,
                "EnthusiaCommend.jar",
                "EnthusiaCommend",
            )

        self.assertEqual(["EnthusiaCommend-old.jar"], replaced)
        self.assertEqual(b"candidate", sftp.files["plugins/EnthusiaCommend.jar"])
        self.assertNotIn("plugins/EnthusiaCommend-old.jar", sftp.files)
        self.assertEqual(b"old", sftp.files[f"{backup_dir}/EnthusiaCommend-old.jar"])

    def test_failed_remote_hash_restores_previous_plugin(self) -> None:
        sftp = FakeSftp(corrupt_download=True)
        sftp.files["plugins/EnthusiaCommend-old.jar"] = b"old"
        with tempfile.TemporaryDirectory() as directory:
            candidate = Path(directory) / "candidate.jar"
            candidate.write_bytes(b"candidate")
            expected = uploader.file_sha256(candidate)

            with self.assertRaises(SystemExit):
                uploader.stage_provider(
                    sftp,
                    "plugins",
                    candidate,
                    expected,
                    "EnthusiaCommend.jar",
                    "EnthusiaCommend",
                )

        self.assertEqual(b"old", sftp.files["plugins/EnthusiaCommend-old.jar"])
        self.assertNotIn("plugins/EnthusiaCommend.jar", sftp.files)
        self.assertFalse(any(".uploading-" in path for path in sftp.files))

    def test_filename_and_sha_validation_reject_unsafe_input(self) -> None:
        with self.assertRaises(SystemExit):
            uploader.validate_filename("../plugin.jar", "remote name")
        with self.assertRaises(SystemExit):
            uploader.validate_filename("plugins/plugin.jar", "remote name")
        with self.assertRaises(SystemExit):
            uploader.validate_sha256("not-a-sha")


if __name__ == "__main__":
    unittest.main()
