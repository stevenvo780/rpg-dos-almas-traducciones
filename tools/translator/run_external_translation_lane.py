#!/usr/bin/env python3
"""Ejecuta un carril externo y acepta únicamente resultados validados."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import signal
import shutil
import subprocess
import time
from pathlib import Path
from typing import Any

import minimax_mod_translator as translator


PROVIDERS = {
    "gemini": {
        "binary": Path("/home/stev/.local/bin/agy"),
        "model": "gemini-3.5-flash-high",
    },
    "minimax": {
        "binary": Path("/home/stev/.local/bin/opencode"),
        "model": "minimax/MiniMax-M3",
    },
}
JSON_FENCE_RE = re.compile(r"```(?:json)?\s*(\{.*\})\s*```", re.DOTALL)


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


def load_json_map(path: Path) -> dict[str, str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, dict) and "translations" in payload:
        payload = payload["translations"]
    if not isinstance(payload, dict):
        raise translator.TranslatorError("el resultado no es un objeto JSON")
    if not all(isinstance(key, str) and isinstance(value, str) for key, value in payload.items()):
        raise translator.TranslatorError(
            "todas las claves y traducciones del resultado deben ser texto"
        )
    return dict(payload)


def validate_result(
    chunk: dict[str, Any],
    translations: dict[str, str],
) -> list[dict[str, Any]]:
    expected = {str(item["id"]): item for item in chunk["items"]}
    problems: list[dict[str, Any]] = []
    missing = sorted(set(expected) - set(translations))
    extras = sorted(set(translations) - set(expected))
    if missing:
        problems.append({"error": "faltan IDs", "ids": missing})
    if extras:
        problems.append({"error": "sobran IDs", "ids": extras})
    for item_id in sorted(set(expected) & set(translations)):
        errors = translator.validate_translation(
            str(expected[item_id]["source"]),
            translations[item_id],
        )
        if errors:
            problems.append({"id": item_id, "errors": errors})
    return problems


def prompt_for(chunk_name: str, result_name: str, attempt: int) -> str:
    retry_note = ""
    if attempt > 1:
        retry_note = (
            "\nEste es un reintento porque la salida anterior faltó o no pasó la "
            "validación. Comprueba el JSON antes de terminar.\n"
        )
    return f"""Actúa como traductor profesional de mods de Minecraft 1.20.1.
Tu única tarea es leer `{chunk_name}` y escribir `{result_name}`.
{retry_note}
Reglas obligatorias:
- Traduce al español neutro natural, con calidad alta y terminología propia de Minecraft.
- Usa el contexto de `namespace`, `key` y `existing_spanish`; no traduzcas literalmente si suena mal.
- Conserva EXACTAMENTE cada ID de tarea y devuelve todos, sin añadir otros.
- Conserva exactamente placeholders, códigos §/& de formato, etiquetas, escapes, URLs,
  identificadores `namespace:ruta` y cualquier token indicado como inmutable.
- Conserva nombres propios de mods/personajes cuando corresponda.
- El archivo final debe ser un único objeto JSON UTF-8: `"id": "traducción"`.
- No modifiques ningún otro archivo, no ejecutes el juego y no accedas fuera de este directorio.
- Antes de finalizar, vuelve a leer el archivo y confirma internamente que es JSON válido y completo.

Usa las herramientas de archivos para crear `{result_name}`. No te limites a mostrar el JSON
en el chat: el trabajo solo cuenta si el archivo queda escrito.
"""


def command_for(
    provider: str,
    lane_dir: Path,
    prompt: str,
    timeout_seconds: int,
) -> list[str]:
    settings = PROVIDERS[provider]
    if provider == "gemini":
        return [
            str(settings["binary"]),
            "--model",
            str(settings["model"]),
            "--effort",
            "high",
            "--mode",
            "accept-edits",
            "--dangerously-skip-permissions",
            "--add-dir",
            str(lane_dir),
            "--print-timeout",
            f"{timeout_seconds}s",
            "-p",
            prompt,
        ]
    return [
        str(settings["binary"]),
        "run",
        "--model",
        str(settings["model"]),
        "--dir",
        str(lane_dir),
        "--dangerously-skip-permissions",
        "--title",
        f"Traducción {lane_dir.name}",
        prompt,
    ]


def extract_json_from_stdout(stdout: str) -> dict[str, str] | None:
    candidates = [stdout.strip()]
    match = JSON_FENCE_RE.search(stdout)
    if match:
        candidates.append(match.group(1))
    first = stdout.find("{")
    last = stdout.rfind("}")
    if first >= 0 and last > first:
        candidates.append(stdout[first : last + 1])
    for candidate in candidates:
        if not candidate:
            continue
        try:
            payload = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        if isinstance(payload, dict) and all(
            isinstance(key, str) and isinstance(value, str)
            for key, value in payload.items()
        ):
            return dict(payload)
    return None


def save_status(
    lane_dir: Path,
    provider: str,
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
            "provider": provider,
            "model": PROVIDERS[provider]["model"],
            "state": state,
            "completed_chunks": completed,
            "total_chunks": total,
            **details,
        },
    )


def process_chunk(
    provider: str,
    lane_dir: Path,
    chunk_path: Path,
    timeout_seconds: int,
    max_attempts: int,
) -> tuple[bool, list[dict[str, Any]]]:
    chunk = json.loads(chunk_path.read_text(encoding="utf-8"))
    result_path = lane_dir / "results" / chunk_path.name
    last_problems: list[dict[str, Any]] = []
    for attempt in range(1, max_attempts + 1):
        if result_path.exists():
            try:
                translations = load_json_map(result_path)
                problems = validate_result(chunk, translations)
            except (OSError, json.JSONDecodeError, translator.TranslatorError) as exc:
                problems = [{"error": str(exc)}]
            if not problems:
                return True, []
            rejected = (
                lane_dir
                / "rejected"
                / f"{chunk_path.stem}-attempt-{attempt - 1:02d}.json"
            )
            shutil.move(result_path, rejected)
            write_json(rejected.with_suffix(".issues.json"), problems)

        prompt = prompt_for(
            f"chunks/{chunk_path.name}",
            f"results/{chunk_path.name}",
            attempt,
        )
        command = command_for(provider, lane_dir, prompt, timeout_seconds)
        started = time.monotonic()
        process = subprocess.Popen(
            command,
            cwd=lane_dir,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env={**os.environ, "NO_COLOR": "1"},
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
        if isinstance(stdout, bytes):
            stdout = stdout.decode("utf-8", errors="replace")
        if isinstance(stderr, bytes):
            stderr = stderr.decode("utf-8", errors="replace")
        log_base = lane_dir / "logs" / f"{chunk_path.stem}-attempt-{attempt:02d}"
        log_base.with_suffix(".stdout.log").write_text(stdout, encoding="utf-8")
        log_base.with_suffix(".stderr.log").write_text(stderr, encoding="utf-8")
        write_json(
            log_base.with_suffix(".meta.json"),
            {
                "attempt": attempt,
                "return_code": return_code,
                "elapsed_seconds": elapsed,
            },
        )

        if not result_path.exists():
            extracted = extract_json_from_stdout(stdout)
            if extracted is not None:
                write_json(result_path, extracted)

        try:
            translations = load_json_map(result_path)
            last_problems = validate_result(chunk, translations)
        except (OSError, json.JSONDecodeError, translator.TranslatorError) as exc:
            last_problems = [
                {
                    "error": str(exc),
                    "return_code": return_code,
                    "attempt": attempt,
                }
            ]
        if not last_problems:
            return True, []
        write_json(log_base.with_suffix(".issues.json"), last_problems)
        if attempt < max_attempts:
            time.sleep(min(15 * attempt, 45))
    return False, last_problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--provider", choices=sorted(PROVIDERS), required=True)
    parser.add_argument("--lane-dir", type=Path, required=True)
    parser.add_argument("--timeout-seconds", type=int, default=480)
    parser.add_argument("--max-attempts", type=int, default=3)
    args = parser.parse_args()

    lane_dir = args.lane_dir.resolve()
    chunks = sorted((lane_dir / "chunks").glob("chunk-*.json"))
    if not chunks:
        raise translator.TranslatorError(f"el carril no tiene lotes: {lane_dir}")
    binary = PROVIDERS[args.provider]["binary"]
    if not binary.is_file():
        raise translator.TranslatorError(f"no existe el ejecutable: {binary}")

    completed_count = 0
    failed: list[dict[str, Any]] = []
    save_status(
        lane_dir,
        args.provider,
        completed_count,
        len(chunks),
        "running",
    )
    for chunk_path in chunks:
        existing_result = lane_dir / "results" / chunk_path.name
        if existing_result.exists():
            try:
                chunk = json.loads(chunk_path.read_text(encoding="utf-8"))
                current = load_json_map(existing_result)
                if not validate_result(chunk, current):
                    completed_count += 1
                    save_status(
                        lane_dir,
                        args.provider,
                        completed_count,
                        len(chunks),
                        "running",
                        current_chunk=chunk_path.name,
                    )
                    continue
            except (OSError, json.JSONDecodeError, translator.TranslatorError):
                pass

        save_status(
            lane_dir,
            args.provider,
            completed_count,
            len(chunks),
            "running",
            current_chunk=chunk_path.name,
        )
        success, problems = process_chunk(
            args.provider,
            lane_dir,
            chunk_path,
            args.timeout_seconds,
            args.max_attempts,
        )
        if success:
            completed_count += 1
        else:
            failed.append({"chunk": chunk_path.name, "problems": problems})
        save_status(
            lane_dir,
            args.provider,
            completed_count,
            len(chunks),
            "running",
            current_chunk=chunk_path.name,
            failed_chunks=len(failed),
        )

    state = "complete" if not failed else "partial"
    save_status(
        lane_dir,
        args.provider,
        completed_count,
        len(chunks),
        state,
        failed=failed,
    )
    return 0 if not failed else 2


if __name__ == "__main__":
    raise SystemExit(main())
