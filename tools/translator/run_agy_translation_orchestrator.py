#!/usr/bin/env python3
"""Traduce cinco carriles Gemini mediante subagentes de una sola sesión Agy.

El perfil OAuth se comparte en un único proceso principal. Cada oleada pide al
agente principal que invoque subagentes concurrentes, evitando cinco procesos
CLI que compitan por el keyring, el token y el actualizador.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import signal
import subprocess
import time
from pathlib import Path
from typing import Any

import minimax_mod_translator as translator


AGY_BIN = Path("/datos/agents/shared/.local/bin/agy")
AGY_HOME = Path("/datos/agents/shared")
MODEL = "gemini-3.5-flash-high"


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def read_map(path: Path) -> dict[str, str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or not all(
        isinstance(key, str) and isinstance(value, str)
        for key, value in payload.items()
    ):
        raise translator.TranslatorError("el resultado no es un mapa JSON de textos")
    return dict(payload)


def validate_pair(chunk_path: Path, result_path: Path) -> list[dict[str, Any]]:
    try:
        chunk = json.loads(chunk_path.read_text(encoding="utf-8"))
        values = read_map(result_path)
    except (OSError, json.JSONDecodeError, translator.TranslatorError) as exc:
        return [{"error": str(exc)}]
    expected = {str(item["id"]): item for item in chunk["items"]}
    problems: list[dict[str, Any]] = []
    missing = sorted(set(expected) - set(values))
    extras = sorted(set(values) - set(expected))
    if missing:
        problems.append({"error": "faltan IDs", "ids": missing})
    if extras:
        problems.append({"error": "sobran IDs", "ids": extras})
    for item_id in sorted(set(expected) & set(values)):
        errors = translator.validate_translation(
            str(expected[item_id]["source"]),
            values[item_id],
        )
        if errors:
            problems.append({"id": item_id, "errors": errors})
    return problems


def save_lane_status(
    lane_dir: Path,
    completed: int,
    total: int,
    state: str,
    **details: object,
) -> None:
    write_json(
        lane_dir / "status.json",
        {
            "updated_at": utc_now(),
            "lane": lane_dir.name,
            "provider": "gemini",
            "model": MODEL,
            "execution": "agy-primary-with-parallel-subagents",
            "state": state,
            "completed_chunks": completed,
            "total_chunks": total,
            **details,
        },
    )


def build_prompt(assignments: list[tuple[Path, Path]], attempt: int) -> str:
    lines: list[str] = []
    for number, (chunk_path, result_path) in enumerate(assignments, 1):
        lines.append(
            f"{number}. Entrada `{chunk_path.as_posix()}` -> "
            f"salida `{result_path.as_posix()}`"
        )
    retry = ""
    if attempt > 1:
        retry = (
            "\nEs un reintento: alguna salida anterior faltó o no conservó sus "
            "tokens. Revisa con especial cuidado todos los IDs y placeholders.\n"
        )
    return f"""Coordina una oleada profesional de traducción de mods de Minecraft 1.20.1.
{retry}
Debes usar `invoke_subagent`: inicia exactamente {len(assignments)} subagentes
Gemini concurrentes, uno por asignación. No traduzcas las asignaciones
secuencialmente en el agente principal. Espera a que todos terminen y verifica sus
archivos antes de finalizar.

Asignaciones:
{chr(10).join(lines)}

Instrucciones idénticas para cada subagente:
- Leer únicamente su archivo de entrada y escribir únicamente su archivo de salida.
- Español neutro natural de alta calidad, compatible con es_MX y es_ES.
- Usar `namespace`, `key` y `existing_spanish` como contexto.
- Conservar exactamente todos los IDs, placeholders, códigos §/&, etiquetas,
  escapes, URLs, IDs `namespace:ruta` y tokens inmutables.
- Conservar nombres propios cuando corresponda.
- La salida es un único objeto JSON UTF-8 `"id": "traducción"`, completo y sin extras.
- No ejecutar el juego, no modificar mods y no acceder fuera de este directorio.

El agente principal debe esperar a los subagentes, comprobar que existen las
{len(assignments)} salidas y que cada una es JSON válido. No pegues miles de textos
en la respuesta final: deja el trabajo en los archivos indicados.
"""


def run_wave(
    run_dir: Path,
    assignments: list[tuple[Path, Path]],
    wave: int,
    attempt: int,
    timeout_seconds: int,
) -> tuple[int, float]:
    prompt = build_prompt(assignments, attempt)
    command = [
        str(AGY_BIN),
        "--model",
        MODEL,
        "--effort",
        "high",
        "--mode",
        "accept-edits",
        "--dangerously-skip-permissions",
        "--add-dir",
        str(run_dir),
        "--print-timeout",
        f"{timeout_seconds}s",
        "-p",
        prompt,
    ]
    environment = {
        **os.environ,
        "HOME": str(AGY_HOME),
        "NO_COLOR": "1",
        "AGY_CLI_DISABLE_AUTO_UPDATE": "true",
    }
    started = time.monotonic()
    process = subprocess.Popen(
        command,
        cwd=run_dir,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=environment,
        start_new_session=True,
    )
    try:
        stdout, stderr = process.communicate(timeout=timeout_seconds + 30)
        return_code = process.returncode
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            stdout, stderr = process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            stdout, stderr = process.communicate()
        return_code = 124
        stderr += "\nTiempo máximo agotado."
    elapsed = round(time.monotonic() - started, 2)
    log_base = run_dir / "agy-logs" / f"wave-{wave:04d}-attempt-{attempt:02d}"
    log_base.parent.mkdir(parents=True, exist_ok=True)
    log_base.with_suffix(".stdout.log").write_text(stdout, encoding="utf-8")
    log_base.with_suffix(".stderr.log").write_text(stderr, encoding="utf-8")
    write_json(
        log_base.with_suffix(".meta.json"),
        {
            "return_code": return_code,
            "elapsed_seconds": elapsed,
            "assignments": len(assignments),
        },
    )
    return return_code, elapsed


def completed_count(lane_dir: Path) -> int:
    completed = 0
    for chunk_path in sorted((lane_dir / "chunks").glob("chunk-*.json")):
        result_path = lane_dir / "results" / chunk_path.name
        if not validate_pair(chunk_path, result_path):
            completed += 1
    return completed


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--timeout-seconds", type=int, default=600)
    parser.add_argument("--max-attempts", type=int, default=2)
    args = parser.parse_args()
    run_dir = args.run_dir.resolve()
    lanes = sorted((run_dir / "lanes").glob("gemini-*"))
    if len(lanes) != 5:
        raise translator.TranslatorError(
            f"se esperaban cinco carriles Gemini y se encontraron {len(lanes)}"
        )
    if not AGY_BIN.is_file():
        raise translator.TranslatorError(f"no existe Agy compartido: {AGY_BIN}")

    lane_chunks = {
        lane: sorted((lane / "chunks").glob("chunk-*.json"))
        for lane in lanes
    }
    total_waves = max(len(chunks) for chunks in lane_chunks.values())
    failed: list[dict[str, Any]] = []
    for lane, chunks in lane_chunks.items():
        save_lane_status(
            lane,
            completed_count(lane),
            len(chunks),
            "running",
        )

    for wave_index in range(total_waves):
        pending: list[tuple[Path, Path]] = []
        for lane, chunks in lane_chunks.items():
            if wave_index >= len(chunks):
                continue
            chunk_path = chunks[wave_index]
            result_path = lane / "results" / chunk_path.name
            if validate_pair(chunk_path, result_path):
                pending.append(
                    (
                        chunk_path.relative_to(run_dir),
                        result_path.relative_to(run_dir),
                    )
                )
        if not pending:
            continue

        remaining = pending
        wave_problems: list[dict[str, Any]] = []
        for attempt in range(1, args.max_attempts + 1):
            run_wave(
                run_dir,
                remaining,
                wave_index + 1,
                attempt,
                args.timeout_seconds,
            )
            wave_problems = []
            retry: list[tuple[Path, Path]] = []
            for relative_chunk, relative_result in remaining:
                problems = validate_pair(
                    run_dir / relative_chunk,
                    run_dir / relative_result,
                )
                if problems:
                    wave_problems.append(
                        {
                            "chunk": str(relative_chunk),
                            "result": str(relative_result),
                            "problems": problems,
                        }
                    )
                    retry.append((relative_chunk, relative_result))
            if not retry:
                break
            remaining = retry
        if wave_problems:
            failed.extend(wave_problems)
        for lane, chunks in lane_chunks.items():
            save_lane_status(
                lane,
                completed_count(lane),
                len(chunks),
                "running",
                current_wave=wave_index + 1,
                failed_assignments=len(failed),
            )

    for lane, chunks in lane_chunks.items():
        complete = completed_count(lane)
        save_lane_status(
            lane,
            complete,
            len(chunks),
            "complete" if complete == len(chunks) else "partial",
            failed_assignments=len(failed),
        )
    write_json(run_dir / "agy-summary.json", {"failed": failed})
    return 0 if not failed else 2


if __name__ == "__main__":
    raise SystemExit(main())
