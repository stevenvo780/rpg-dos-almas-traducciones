#!/usr/bin/env python3
"""Valida y, solo cuando esté completo, empaqueta traducciones delegadas."""

from __future__ import annotations

import argparse
import collections
import datetime as dt
import json
from pathlib import Path
from typing import Any

import minimax_mod_translator as translator


TASKS_ROOT = translator.MINECRAFT_ROOT / "Temporales/Traduccion-Agentes/tasks"
TRANSLATIONS_ROOT = (
    translator.MINECRAFT_ROOT / "Temporales/Traduccion-Agentes/translations"
)
DEFAULT_REPORT = (
    translator.MINECRAFT_ROOT
    / "Temporales/Traduccion-Agentes/validation-report.json"
)
DEFAULT_OUTPUT = (
    translator.TOOL_DIR
    / "output/RPG-Dos-Almas-Traduccion-Completa-GPT"
)


def load_tasks() -> tuple[dict[str, dict[str, Any]], dict[str, str]]:
    tasks: dict[str, dict[str, Any]] = {}
    owners: dict[str, str] = {}
    for path in sorted(TASKS_ROOT.glob("*.json")):
        if path.name == "summary.json":
            continue
        payload = json.loads(path.read_text(encoding="utf-8"))
        group = str(payload["group"])
        for item in payload["items"]:
            item_id = str(item["id"])
            if item_id in tasks:
                raise translator.TranslatorError(f"ID de tarea repetido: {item_id}")
            tasks[item_id] = item
            owners[item_id] = group
    return tasks, owners


def unwrap_map(payload: object, path: Path) -> dict[str, str]:
    if isinstance(payload, dict) and "translations" in payload:
        payload = payload["translations"]
    if not isinstance(payload, dict):
        raise translator.TranslatorError(f"{path}: se esperaba un objeto JSON")
    if not all(isinstance(key, str) and isinstance(value, str) for key, value in payload.items()):
        raise translator.TranslatorError(f"{path}: todas las entradas deben ser texto")
    return dict(payload)


def load_translations() -> tuple[dict[str, str], list[dict[str, str]]]:
    merged: dict[str, str] = {}
    issues: list[dict[str, str]] = []
    if not TRANSLATIONS_ROOT.is_dir():
        return merged, issues
    for path in sorted(TRANSLATIONS_ROOT.rglob("*.json")):
        if path.name.startswith("progress") or path.name == "validation-report.json":
            continue
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            values = unwrap_map(payload, path)
        except (OSError, json.JSONDecodeError, translator.TranslatorError) as exc:
            issues.append({"path": str(path), "error": str(exc)})
            continue
        for item_id, value in values.items():
            if item_id in merged and merged[item_id] != value:
                issues.append(
                    {
                        "path": str(path),
                        "id": item_id,
                        "error": "traducción duplicada y diferente",
                    }
                )
                continue
            merged[item_id] = value
    return merged, issues


def rebuild_catalog() -> tuple[
    dict[tuple[str, str], translator.CatalogEntry],
    dict[str, translator.WorkItem],
]:
    mods_dirs = [path.resolve() for path in translator.DEFAULT_MODS_DIRS]
    disabled_dirs = translator.discover_disabled_dirs(mods_dirs)
    occurrences = translator.collect_occurrences(mods_dirs, disabled_dirs)
    scans = translator.scan_inventory(occurrences)
    catalog = translator.build_catalog(scans, include_disabled=False)
    work_items = {item.item_id: item for item in translator.make_work_items(catalog)}
    return catalog, work_items


def build_report(
    tasks: dict[str, dict[str, Any]],
    owners: dict[str, str],
    supplied: dict[str, str],
    file_issues: list[dict[str, str]],
) -> tuple[dict[str, Any], dict[str, str]]:
    valid: dict[str, str] = {}
    invalid: list[dict[str, Any]] = []
    extras = sorted(set(supplied) - set(tasks))
    missing = sorted(set(tasks) - set(supplied))
    by_group: dict[str, collections.Counter[str]] = collections.defaultdict(
        collections.Counter
    )

    for item_id, item in tasks.items():
        group = owners[item_id]
        by_group[group]["total"] += 1
        if item_id not in supplied:
            by_group[group]["missing"] += 1
            continue
        errors = translator.validate_translation(
            str(item["source"]),
            supplied[item_id],
        )
        if errors:
            by_group[group]["invalid"] += 1
            invalid.append({"id": item_id, "group": group, "errors": errors})
            continue
        by_group[group]["valid"] += 1
        valid[item_id] = supplied[item_id]

    complete = not file_issues and not extras and not missing and not invalid
    report = {
        "schema_version": 1,
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "status": "complete" if complete else "incomplete",
        "statistics": {
            "tasks": len(tasks),
            "supplied_ids": len(supplied),
            "valid": len(valid),
            "missing": len(missing),
            "invalid": len(invalid),
            "extras": len(extras),
            "file_issues": len(file_issues),
        },
        "groups": {group: dict(counter) for group, counter in sorted(by_group.items())},
        "missing_ids": missing,
        "invalid": invalid,
        "extra_ids": extras,
        "file_issues": file_issues,
    }
    return report, valid


def publish_staging(valid: dict[str, str], report: dict[str, Any]) -> Path:
    catalog, work_items = rebuild_catalog()
    if set(valid) != set(work_items):
        raise translator.TranslatorError(
            "el catálogo actual ya no coincide con los lotes; vuelve a exportarlos"
        )
    translations = {
        (work_items[item_id].namespace, work_items[item_id].key): text
        for item_id, text in valid.items()
    }
    rendered, untranslated = translator.render_language_maps(
        catalog,
        translations,
        translator.DEFAULT_SPANISH_VARIANTS,
    )
    if untranslated:
        raise translator.TranslatorError(
            f"quedaron {len(untranslated)} claves sin traducción"
        )
    translator.write_resource_pack(
        output=DEFAULT_OUTPUT,
        rendered=rendered,
        embedded_manifest=report,
        mods_dirs=[path.resolve() for path in translator.DEFAULT_MODS_DIRS],
        overwrite=False,
    )
    return DEFAULT_OUTPUT


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--publish-staging", action="store_true")
    args = parser.parse_args()

    tasks, owners = load_tasks()
    supplied, file_issues = load_translations()
    report, valid = build_report(tasks, owners, supplied, file_issues)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report["statistics"], ensure_ascii=False))
    print(f"Informe: {args.report.resolve()}")
    if args.publish_staging:
        if report["status"] != "complete":
            raise translator.TranslatorError(
                "no se publica: la traducción delegada está incompleta o es inválida"
            )
        output = publish_staging(valid, report)
        print(f"Resource pack de staging: {output}")
    return 0 if report["status"] == "complete" else 1


if __name__ == "__main__":
    raise SystemExit(main())
