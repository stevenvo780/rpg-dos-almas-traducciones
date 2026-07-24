#!/usr/bin/env python3
"""Prepara carriles aislados para agentes externos de traducción.

Solo exporta las tareas que todavía no tienen una traducción válida. No toca
mods, clientes, resource packs ni el servidor.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
from pathlib import Path
from typing import Any

import validate_agent_translations as delegated


ROOT = delegated.translator.MINECRAFT_ROOT
DEFAULT_OUTPUT_ROOT = ROOT / "Temporales/Traduccion-Externos"
MIXED_LANES = tuple(
    [f"gemini-{number}" for number in range(1, 6)]
    + [f"minimax-{number}" for number in range(1, 6)]
)
MINIMAX_LANES = tuple(f"minimax-{number}" for number in range(1, 6))


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def load_missing_tasks() -> tuple[list[dict[str, Any]], dict[str, Any]]:
    tasks, owners = delegated.load_tasks()
    supplied, file_issues = delegated.load_translations()
    report, valid = delegated.build_report(tasks, owners, supplied, file_issues)
    if file_issues or report["statistics"]["invalid"]:
        raise delegated.translator.TranslatorError(
            "hay archivos o traducciones inválidos en el conjunto actual; "
            "corrígelos antes de repartir trabajo externo"
        )

    missing: list[dict[str, Any]] = []
    for item_id, item in tasks.items():
        if item_id in valid:
            continue
        exported = dict(item)
        exported["group"] = owners[item_id]
        missing.append(exported)
    missing.sort(
        key=lambda item: (
            str(item.get("namespace", "")),
            str(item.get("key", "")),
            str(item["id"]),
        )
    )
    return missing, report


def create_run(
    output_root: Path,
    chunk_size: int,
    run_name: str | None,
    lanes: tuple[str, ...],
) -> Path:
    missing, current_report = load_missing_tasks()
    timestamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    run_dir = output_root / (run_name or f"run-{timestamp}")
    if run_dir.exists():
        raise delegated.translator.TranslatorError(
            f"la ejecución ya existe y no se sobrescribirá: {run_dir}"
        )

    chunks = [
        missing[offset : offset + chunk_size]
        for offset in range(0, len(missing), chunk_size)
    ]
    lane_counts = {lane: {"chunks": 0, "items": 0} for lane in lanes}
    for chunk_number, items in enumerate(chunks, 1):
        lane = lanes[(chunk_number - 1) % len(lanes)]
        lane_chunk = lane_counts[lane]["chunks"] + 1
        payload = {
            "schema_version": 1,
            "run": run_dir.name,
            "lane": lane,
            "global_chunk": chunk_number,
            "lane_chunk": lane_chunk,
            "instructions": {
                "target": "español neutro natural, compatible con es_MX y es_ES",
                "quality": "alta; traducir por significado y contexto de Minecraft",
                "preserve_ids": True,
                "preserve_immutable_tokens": True,
                "output_contract": "objeto JSON exacto id -> traducción",
            },
            "items": items,
        }
        chunk_path = (
            run_dir
            / "lanes"
            / lane
            / "chunks"
            / f"chunk-{lane_chunk:04d}.json"
        )
        write_json(chunk_path, payload)
        lane_counts[lane]["chunks"] += 1
        lane_counts[lane]["items"] += len(items)

    manifest = {
        "schema_version": 1,
        "created_at": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds"),
        "run": run_dir.name,
        "chunk_size": chunk_size,
        "remaining_tasks": len(missing),
        "valid_before_run": current_report["statistics"]["valid"],
        "total_tasks": current_report["statistics"]["tasks"],
        "lanes": lane_counts,
    }
    write_json(run_dir / "manifest.json", manifest)
    for lane in lanes:
        lane_dir = run_dir / "lanes" / lane
        (lane_dir / "results").mkdir(parents=True, exist_ok=True)
        (lane_dir / "rejected").mkdir(parents=True, exist_ok=True)
        (lane_dir / "logs").mkdir(parents=True, exist_ok=True)
        os.chmod(lane_dir, 0o700)

    write_json(
        output_root / "current-run.json",
        {"run_dir": str(run_dir.resolve()), "manifest": manifest},
    )
    return run_dir


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--chunk-size", type=int, default=60)
    parser.add_argument("--run-name")
    parser.add_argument(
        "--layout",
        choices=("mixed-10", "minimax-5"),
        default="mixed-10",
    )
    args = parser.parse_args()
    if not 10 <= args.chunk_size <= 500:
        raise delegated.translator.TranslatorError(
            "--chunk-size debe estar entre 10 y 500"
        )
    lanes = MIXED_LANES if args.layout == "mixed-10" else MINIMAX_LANES
    run_dir = create_run(
        args.output_root.resolve(),
        args.chunk_size,
        args.run_name,
        lanes,
    )
    print(run_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
