from __future__ import annotations

import importlib.util
import json
import sqlite3
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "minimax_mod_translator.py"
SPEC = importlib.util.spec_from_file_location("minimax_mod_translator", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
translator = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = translator
SPEC.loader.exec_module(translator)


def make_jar(path: Path, entries: dict[str, str | bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        for name, value in entries.items():
            archive.writestr(name, value)


class TokenValidationTests(unittest.TestCase):
    def test_accepts_translation_with_reordered_numbered_placeholders(self) -> None:
        source = "Give %1$s to %2$s: minecraft:diamond §a<green>\\n"
        translated = "Entrega %2$s a %1$s: minecraft:diamond §a<green>\\n"
        self.assertEqual([], translator.validate_translation(source, translated))

    def test_rejects_changed_placeholder_format_and_id(self) -> None:
        source = "Value: %1$.2f for mod:item &a"
        translated = "Valor: %s para mod:objeto &b"
        errors = translator.validate_translation(source, translated)
        self.assertIn("tokens alterados: printf", errors)
        self.assertIn("tokens alterados: resource_ids", errors)
        self.assertIn("tokens alterados: ampersand_format", errors)

    def test_rejects_changed_named_brace_and_line_break(self) -> None:
        source = "Hello {player}\nNext"
        translated = "Hola {jugador} Siguiente"
        errors = translator.validate_translation(source, translated)
        self.assertIn("tokens alterados: named_brace", errors)
        self.assertIn("tokens alterados: control_characters", errors)

    def test_literal_percentage_before_word_is_not_printf(self) -> None:
        source = "you move 5% slower and take 15% less damage"
        translated = "te mueves un 5% más lento y recibes un 15% menos de daño"
        self.assertEqual((), translator.immutable_tokens(source)["printf"])
        self.assertEqual([], translator.validate_translation(source, translated))


class InventoryTests(unittest.TestCase):
    def test_scans_json_legacy_and_no_lang_jar(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            mods = Path(temp) / "mods"
            mods.mkdir()
            make_jar(
                mods / "languages.jar",
                {
                    "assets/demo/lang/en_us.json": json.dumps(
                        {"demo.hello": "Hello %s", "demo.item": "Item"}
                    ),
                    "assets/demo/lang/es_mx.lang": (
                        "# comment\n"
                        "demo.hello=Hola %s\n"
                        "demo.multiline=Línea\\nsegunda\n"
                    ),
                },
            )
            make_jar(mods / "without-lang.jar", {"META-INF/MANIFEST.MF": "ok"})
            occurrences = translator.collect_occurrences([mods], [])
            scans = translator.scan_inventory(occurrences)
            self.assertEqual(2, len(scans))
            statuses = sorted(bool(scan.assets) for scan in scans.values())
            self.assertEqual([False, True], statuses)
            language_scan = next(scan for scan in scans.values() if scan.assets)
            self.assertEqual(["demo"], language_scan.namespaces)
            legacy = next(asset for asset in language_scan.assets if asset.format == "lang")
            self.assertEqual("Hola %s", legacy.entries["demo.hello"])
            self.assertEqual("Línea\nsegunda", legacy.entries["demo.multiline"])

    def test_multi_dir_hash_dedup_and_disabled_exclusion(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            tower = root / "tower/mods"
            isa = root / "isa/mods"
            disabled = root / "tower/mods-disabled"
            for directory in (tower, isa, disabled):
                directory.mkdir(parents=True)
            content = {
                "assets/common/lang/en_us.json": json.dumps({"common.key": "Common"})
            }
            make_jar(tower / "same.jar", content)
            make_jar(isa / "same-copy.jar", content)
            make_jar(
                isa / "isa-only.jar",
                {"assets/isa/lang/en_us.json": json.dumps({"isa.key": "Isa"})},
            )
            make_jar(
                disabled / "off.jar",
                {"assets/off/lang/en_us.json": json.dumps({"off.key": "Disabled"})},
            )
            occurrences = translator.collect_occurrences([tower, isa], [disabled])
            scans = translator.scan_inventory(occurrences)
            self.assertEqual(3, len(scans))
            catalog = translator.build_catalog(scans, include_disabled=False)
            self.assertIn(("common", "common.key"), catalog)
            self.assertIn(("isa", "isa.key"), catalog)
            self.assertNotIn(("off", "off.key"), catalog)
            catalog_with_disabled = translator.build_catalog(scans, include_disabled=True)
            self.assertIn(("off", "off.key"), catalog_with_disabled)

    def test_spanish_only_key_is_preserved_without_model_task(self) -> None:
        source = translator.ValueCandidate(
            value="Texto ya español",
            namespace="demo",
            key="demo.spanish_only",
            locale="es_mx",
            jar_sha256="abc",
            jar_name="demo.jar",
            archive_path="assets/demo/lang/es_mx.json",
            provider_order=0,
        )
        entry = translator.CatalogEntry(
            namespace="demo",
            key="demo.spanish_only",
            source=source,
            spanish={"es_mx": source},
            source_conflicts=[],
            spanish_conflicts={},
        )
        self.assertFalse(translator.entry_needs_translation(entry))
        self.assertEqual([], translator.make_work_items({("demo", entry.key): entry}))


class CacheAndOutputTests(unittest.TestCase):
    def test_m3_payload_disables_thinking(self) -> None:
        client = translator.MiniMaxClient(
            api_key="test-only-not-a-real-secret",
            model="MiniMax-M3",
            timeout=1,
            retries=0,
        )
        item = translator.WorkItem(
            item_id="t1",
            namespace="demo",
            key="demo.key",
            source="Hello",
            tokens=translator.immutable_tokens("Hello"),
        )
        payload = client.build_payload([item])
        self.assertEqual({"type": "disabled"}, payload["thinking"])
        self.assertEqual("MiniMax-M3", payload["model"])

    def test_cache_only_returns_strictly_valid_value(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            cache_path = Path(temp) / "cache.sqlite3"
            item = translator.WorkItem(
                item_id="t1",
                namespace="demo",
                key="demo.key",
                source="Hello %1$s",
                tokens=translator.immutable_tokens("Hello %1$s"),
            )
            with translator.TranslationCache(cache_path) as cache:
                cache.put(item, "MiniMax-M3", "Hola %1$s")
                self.assertEqual("Hola %1$s", cache.get(item, "MiniMax-M3"))
                with self.assertRaises(translator.TranslatorError):
                    cache.put(item, "MiniMax-M3", "Hola %s")
            with sqlite3.connect(cache_path) as connection:
                columns = {
                    row[1] for row in connection.execute("PRAGMA table_info(translations)")
                }
            self.assertNotIn("api_key", columns)

    def test_resource_pack_has_all_requested_variants(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output = root / "output/pack"
            rendered = {
                "demo": {
                    "es_mx": {"demo.hello": "Hola"},
                    "es_es": {"demo.hello": "Hola"},
                }
            }
            translator.write_resource_pack(
                output=output,
                rendered=rendered,
                embedded_manifest={"jars": [{"language_status": "no_lang"}]},
                mods_dirs=[root / "mods"],
                overwrite=False,
            )
            self.assertTrue((output / "pack.mcmeta").is_file())
            self.assertEqual(
                {"demo.hello": "Hola"},
                json.loads(
                    (output / "assets/demo/lang/es_mx.json").read_text(encoding="utf-8")
                ),
            )
            self.assertTrue((output / "assets/demo/lang/es_es.json").is_file())
            with self.assertRaises(translator.TranslatorError):
                translator.write_resource_pack(
                    output=output,
                    rendered=rendered,
                    embedded_manifest={},
                    mods_dirs=[],
                    overwrite=False,
                )

    def test_overwrite_rejects_arbitrary_existing_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            output = root / "arbitrary-existing"
            output.mkdir()
            with self.assertRaises(translator.TranslatorError):
                translator.write_resource_pack(
                    output=output,
                    rendered={"demo": {"es_mx": {"demo.key": "Hola"}}},
                    embedded_manifest={},
                    mods_dirs=[],
                    overwrite=True,
                )
            self.assertTrue(output.is_dir())

    def test_audit_parser_never_requires_api_key(self) -> None:
        parser = translator.build_parser()
        args = translator.normalize_args(parser.parse_args([]), parser)
        self.assertEqual("audit", args.mode)
        self.assertFalse(args.execute)
        self.assertEqual(2, len(args.mods_dir))

    def test_explicit_dry_run_forces_audit(self) -> None:
        parser = translator.build_parser()
        args = translator.normalize_args(
            parser.parse_args(["--mode", "translate", "--dry-run"]),
            parser,
        )
        self.assertEqual("audit", args.mode)
        self.assertFalse(args.execute)


if __name__ == "__main__":
    unittest.main()
