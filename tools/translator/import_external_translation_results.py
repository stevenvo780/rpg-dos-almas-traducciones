#!/usr/bin/env python3
"""Valida una ejecución externa y publica solo sus resultados aceptados."""

from __future__ import annotations

import argparse
import datetime as dt
import json
from pathlib import Path
from typing import Any

import minimax_mod_translator as translator


ROOT = translator.MINECRAFT_ROOT
DEFAULT_DESTINATION = (
    ROOT
    / "Temporales/Traduccion-Agentes/translations/external-validated"
)


def read_map(path: Path) -> dict[str, str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or not all(
        isinstance(key, str) and isinstance(value, str)
        for key, value in payload.items()
    ):
        raise translator.TranslatorError(f"resultado JSON inválido: {path}")
    return dict(payload)


def validate_run(run_dir: Path) -> tuple[dict[str, dict[str, str]], dict[str, Any]]:
    accepted: dict[str, dict[str, str]] = {}
    all_ids: dict[str, str] = {}
    issues: list[dict[str, Any]] = []
    total_items = 0
    for lane_dir in sorted((run_dir / "lanes").iterdir()):
        if not lane_dir.is_dir():
            continue
        lane_values: dict[str, str] = {}
        for chunk_path in sorted((lane_dir / "chunks").glob("chunk-*.json")):
            chunk = json.loads(chunk_path.read_text(encoding="utf-8"))
            expected = {str(item["id"]): item for item in chunk["items"]}
            total_items += len(expected)
            result_path = lane_dir / "results" / chunk_path.name
            if not result_path.is_file():
                issues.append(
                    {
                        "lane": lane_dir.name,
                        "chunk": chunk_path.name,
                        "error": "falta resultado",
                    }
                )
                continue
            try:
                values = read_map(result_path)
            except (OSError, json.JSONDecodeError, translator.TranslatorError) as exc:
                issues.append(
                    {
                        "lane": lane_dir.name,
                        "chunk": chunk_path.name,
                        "error": str(exc),
                    }
                )
                continue
            missing = sorted(set(expected) - set(values))
            extras = sorted(set(values) - set(expected))
            if missing or extras:
                issues.append(
                    {
                        "lane": lane_dir.name,
                        "chunk": chunk_path.name,
                        "missing_ids": missing,
                        "extra_ids": extras,
                    }
                )
            chunk_invalid: list[dict[str, Any]] = []
            chunk_valid: dict[str, str] = {}
            for item_id in sorted(set(expected) & set(values)):
                value = values[item_id]
                errors = translator.validate_translation(
                    str(expected[item_id]["source"]),
                    value,
                )
                if errors:
                    chunk_invalid.append({"id": item_id, "errors": errors})
                    continue
                if item_id in all_ids:
                    chunk_invalid.append(
                        {
                            "id": item_id,
                            "errors": [
                                f"ID repetido también en el carril {all_ids[item_id]}"
                            ],
                        }
                    )
                    continue
                chunk_valid[item_id] = value
            if chunk_invalid:
                issues.append(
                    {
                        "lane": lane_dir.name,
                        "chunk": chunk_path.name,
                        "invalid": chunk_invalid,
                    }
                )
            for item_id, value in chunk_valid.items():
                all_ids[item_id] = lane_dir.name
                lane_values[item_id] = value
        if lane_values:
            accepted[lane_dir.name] = lane_values

    accepted_items = sum(len(values) for values in accepted.values())
    report = {
        "schema_version": 1,
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "run": run_dir.name,
        "total_items": total_items,
        "accepted_items": accepted_items,
        "pending_or_invalid_items": total_items - accepted_items,
        "accepted_lanes": {lane: len(values) for lane, values in accepted.items()},
        "issues": issues,
    }
    return accepted, report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--destination", type=Path, default=DEFAULT_DESTINATION)
    parser.add_argument("--report", type=Path)
    parser.add_argument(
        "--publish",
        action="store_true",
        help="copia las traducciones válidas al árbol leído por el validador principal",
    )
    args = parser.parse_args()
    run_dir = args.run_dir.resolve()
    accepted, report = validate_run(run_dir)
    report_path = args.report or (run_dir / "validation-report.json")
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    if args.publish:
        destination = args.destination.resolve() / run_dir.name
        destination.mkdir(parents=True, exist_ok=True)
        for lane, values in accepted.items():
            path = destination / f"{lane}.json"
            path.write_text(
                json.dumps(values, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
    print(
        json.dumps(
            {
                "total": report["total_items"],
                "accepted": report["accepted_items"],
                "pending_or_invalid": report["pending_or_invalid_items"],
                "issue_groups": len(report["issues"]),
            },
            ensure_ascii=False,
        )
    )
    print(report_path)
    return 0 if not report["issues"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
