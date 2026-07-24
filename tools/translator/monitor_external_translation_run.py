#!/usr/bin/env python3
"""Importa periódicamente resultados externos válidos mientras corren los carriles."""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import import_external_translation_results as external
import validate_agent_translations as delegated


def write_json(path: Path, payload: object) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def publish_accepted(run_dir: Path, accepted: dict[str, dict[str, str]]) -> None:
    destination = external.DEFAULT_DESTINATION / run_dir.name
    destination.mkdir(parents=True, exist_ok=True)
    for lane, values in accepted.items():
        write_json(destination / f"{lane}.json", values)


def refresh_reports(run_dir: Path) -> tuple[int, int]:
    accepted, external_report = external.validate_run(run_dir)
    write_json(run_dir / "validation-report.json", external_report)
    publish_accepted(run_dir, accepted)

    tasks, owners = delegated.load_tasks()
    supplied, file_issues = delegated.load_translations()
    main_report, _ = delegated.build_report(tasks, owners, supplied, file_issues)
    write_json(delegated.DEFAULT_REPORT, main_report)
    return (
        int(external_report["accepted_items"]),
        int(main_report["statistics"]["missing"]),
    )


def all_lanes_finished(run_dir: Path) -> bool:
    manifest_path = run_dir / "manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        expected_lanes = len(manifest["lanes"])
    except (OSError, json.JSONDecodeError, KeyError, TypeError):
        return False
    statuses = sorted((run_dir / "lanes").glob("*/status.json"))
    if len(statuses) != expected_lanes:
        return False
    terminal = {"complete", "partial"}
    for path in statuses:
        try:
            status = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return False
        if status.get("state") not in terminal:
            return False
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--interval-seconds", type=int, default=30)
    args = parser.parse_args()
    run_dir = args.run_dir.resolve()
    last_accepted = -1
    while True:
        accepted, missing = refresh_reports(run_dir)
        if accepted != last_accepted:
            print(
                json.dumps(
                    {"accepted_external": accepted, "missing_global": missing},
                    ensure_ascii=False,
                ),
                flush=True,
            )
            last_accepted = accepted
        if all_lanes_finished(run_dir):
            return 0 if missing == 0 else 2
        time.sleep(max(10, args.interval_seconds))


if __name__ == "__main__":
    raise SystemExit(main())
