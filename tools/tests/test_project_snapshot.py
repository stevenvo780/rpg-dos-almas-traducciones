import importlib.util
import json
import sys
import tomllib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "tools/project_snapshot.py"
SPEC = importlib.util.spec_from_file_location("project_snapshot", MODULE_PATH)
PROJECT_SNAPSHOT = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = PROJECT_SNAPSHOT
SPEC.loader.exec_module(PROJECT_SNAPSHOT)


class SanitizerTests(unittest.TestCase):
    def test_redacts_exact_secret_assignments(self):
        source = '"password": "valor-local",\nrcon.password=valor-local\n'
        result = PROJECT_SNAPSHOT.sanitize_text(source)
        self.assertNotIn("valor-local", result)
        self.assertEqual(result.count("CONFIGURAR_LOCALMENTE"), 2)

    def test_preserves_innocent_setting_names(self):
        source = "superSecretSettings = false\nvalidator.if.password = true\n"
        self.assertEqual(PROJECT_SNAPSHOT.sanitize_text(source), source)

    def test_normalizes_trailing_whitespace(self):
        source = "linea   \n\n\n"
        self.assertEqual(PROJECT_SNAPSHOT.sanitize_text(source), "linea\n")

    def test_removes_private_key_blocks(self):
        begin = "-----BEGIN " + "PRIVATE KEY-----"
        end = "-----END " + "PRIVATE KEY-----"
        source = (
            f"antes\n{begin}\n"
            f"material\n{end}\ndespues\n"
        )
        result = PROJECT_SNAPSHOT.sanitize_text(source)
        self.assertNotIn("material", result)
        self.assertIn("CLAVE_PRIVADA_OMITIDA", result)

    def test_env_filenames_are_always_excluded(self):
        for name in (".env", ".env.local", ".env.production", ".env.backup"):
            with self.subTest(name=name):
                self.assertTrue(PROJECT_SNAPSHOT.is_sensitive_filename(name))
        self.assertFalse(
            PROJECT_SNAPSHOT.is_sensitive_filename("environment.md")
        )


class GeneratedSnapshotTests(unittest.TestCase):
    def test_server_icon_is_valid_64px_png(self):
        data = (ROOT / "snapshot/server/server-icon.png").read_bytes()
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
        self.assertEqual(data[12:16], b"IHDR")
        self.assertEqual(data[16:24], b"\x00\x00\x00@\x00\x00\x00@")
        self.assertLessEqual(len(data), 256 * 1024)

    def test_generated_json_is_valid(self):
        files = sorted((ROOT / "snapshot").rglob("*.json"))
        self.assertTrue(files)
        for path in files:
            with self.subTest(path=path):
                json.loads(path.read_text(encoding="utf-8"))

    def test_generated_toml_is_valid(self):
        files = sorted((ROOT / "snapshot").rglob("*.toml"))
        self.assertTrue(files)
        for path in files:
            with self.subTest(path=path):
                text = path.read_text(encoding="utf-8")
                relative = path.relative_to(ROOT / "snapshot")
                is_gradle_resource_template = (
                    relative.parts[:1] == ("custom",)
                    and relative.name == "mods.toml"
                    and "${" in text
                )
                if not is_gradle_resource_template:
                    tomllib.loads(text)


if __name__ == "__main__":
    unittest.main()
