#!/usr/bin/env python3
"""Valida que el repositorio no contenga binarios o secretos evidentes."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN_SUFFIXES = {
    ".7z",
    ".class",
    ".dat",
    ".dll",
    ".dylib",
    ".exe",
    ".gz",
    ".jar",
    ".jfr",
    ".jpg",
    ".jpeg",
    ".mca",
    ".mcr",
    ".mrpack",
    ".nbt",
    ".pem",
    ".png",
    ".rar",
    ".so",
    ".tar",
    ".webp",
    ".zip",
    ".zst",
}
FORBIDDEN_NAMES = {
    ".env",
    ".rcon-credentials",
    "banned-ips.json",
    "banned-players.json",
    "ops.json",
    "servers.dat",
    "usercache.json",
    "usernamecache.json",
    "whitelist.json",
}
TOKEN_PATTERNS = (
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"\bgd_pat_[A-Za-z0-9_]{20,}\b"),
    re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"),
    re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
)
SECRET_ASSIGNMENT = re.compile(
    r"(?im)^\s*[\"']?(?:rcon[._-]?password|password|passwd|secret|"
    r"api[-_]?key|access[-_]?token|auth[-_]?token|authorization)"
    r"[\"']?\s*[:=]\s*[\"']?([^\"'\s,#;}]*)"
)
SAFE_SECRET_VALUES = {
    "",
    "CONFIGURAR_LOCALMENTE",
    "false",
    "none",
    "null",
    "true",
}
ASSIGNMENT_SUFFIXES = {
    ".cfg",
    ".ini",
    ".json",
    ".json5",
    ".jsonc",
    ".properties",
    ".toml",
    ".yaml",
    ".yml",
}


def tracked_files() -> list[Path]:
    output = subprocess.check_output(
        ["git", "-C", str(ROOT), "ls-files", "-co", "--exclude-standard"],
        text=True,
    )
    return [ROOT / line for line in output.splitlines() if line]


def main() -> int:
    failures = []
    files = tracked_files()
    for path in files:
        relative = path.relative_to(ROOT)
        lowered_parts = {part.lower() for part in relative.parts}
        if path.name.lower() in FORBIDDEN_NAMES:
            failures.append(f"nombre sensible: {relative}")
        if path.suffix.lower() in FORBIDDEN_SUFFIXES:
            failures.append(f"binario prohibido: {relative}")
        if ".ssh" in lowered_parts:
            failures.append(f"ruta SSH prohibida: {relative}")
        if not path.is_file():
            continue
        data = path.read_bytes()
        if b"\x00" in data:
            failures.append(f"contenido binario: {relative}")
            continue
        text = data.decode("utf-8", errors="replace")
        for pattern in TOKEN_PATTERNS:
            if pattern.search(text):
                failures.append(f"patrón de secreto: {relative}")
        if path.suffix.lower() in ASSIGNMENT_SUFFIXES:
            for match in SECRET_ASSIGNMENT.finditer(text):
                value = match.group(1)
                if value and value not in SAFE_SECRET_VALUES:
                    failures.append(f"asignación sensible: {relative}")
                    break

    if failures:
        print("VERIFICACIÓN FALLIDA")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print(f"VERIFICACIÓN CORRECTA: {len(files)} archivos, sin hallazgos.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
