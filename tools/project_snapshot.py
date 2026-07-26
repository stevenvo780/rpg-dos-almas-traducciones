#!/usr/bin/env python3
"""Genera un snapshot reconstruible sin copiar binarios de terceros ni secretos."""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import stat
import struct
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = Path(
    os.environ.get("RPG_DOS_ALMAS_ROOT", "/home/stev/Minecraft")
).resolve()
SNAPSHOT_ROOT = REPO_ROOT / "snapshot"

TEXT_SUFFIXES = {
    "",
    ".bat",
    ".cfg",
    ".cmd",
    ".desktop",
    ".ini",
    ".java",
    ".json",
    ".json5",
    ".jsonc",
    ".md",
    ".mcfunction",
    ".mcmeta",
    ".nsi",
    ".properties",
    ".ps1",
    ".service",
    ".sh",
    ".snbt",
    ".socket",
    ".timer",
    ".toml",
    ".txt",
    ".yaml",
    ".yml",
    ".gradle",
}
SKIP_NAMES = {
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
SKIP_RELATIVE_PREFIXES = {
    (".git",),
    (".gradle",),
    ("build",),
    ("jei", "world"),
    ("spark", "tmp"),
}
SENSITIVE_KEY = re.compile(
    r"(?i)^(?:rcon[._-]?password|password|passwd|contraseña|secret|"
    r"api[-_]?key|access[-_]?token|auth[-_]?token|credential|authorization)$"
)
JSON_SECRET = re.compile(
    r'(?im)^(\s*"(?:rcon[._-]?password|password|passwd|contraseña|secret|'
    r'api[-_]?key|access[-_]?token|auth[-_]?token|credential|authorization)"'
    r'\s*:\s*)("[^"]*"|[^,\r\n]+)'
)
ASSIGNMENT_SECRET = re.compile(
    r"(?im)^(\s*(?:rcon[._-]?password|password|passwd|contraseña|secret|"
    r"api[-_]?key|access[-_]?token|auth[-_]?token|credential|authorization)"
    r"\s*[=:]\s*)([^\r\n#;]*)"
)

CONFIG_TREES = (
    (
        "Servidor/RPG-Dos-Almas/config",
        "server/config",
    ),
    (
        "Servidor/RPG-Dos-Almas/defaultconfigs",
        "server/defaultconfigs",
    ),
    (
        "Cliente/RPG-Dos-Almas/config",
        "client/tower/config",
    ),
    (
        "Cliente/RPG-Dos-Almas/defaultconfigs",
        "client/tower/defaultconfigs",
    ),
    (
        "Distribucion/Isa-Windows/pack/config",
        "client/portable/config",
    ),
    (
        "Distribucion/Isa-Windows/pack/defaultconfigs",
        "client/portable/defaultconfigs",
    ),
    (
        "Distribucion/Isa-Windows/pack/mods/.index",
        "modpack/packwiz-index",
    ),
    (
        "Servidor/RPG-Dos-Almas/infra",
        "server/infra",
    ),
    (
        "Herramientas/RPG-Dos-Almas-Compat",
        "custom/dos-almas-compat",
    ),
    # Datapack propio del mundo: solo el espacio de nombres dos_almas, que es
    # trabajo original. El resto de dos-almas-fixes son parches derivados de
    # datos de mods y se quedan fuera por licencia.
    (
        "Servidor/RPG-Dos-Almas/datapacks-plantilla/dos-almas-fixes/data/dos_almas",
        "server/datapacks/dos-almas/data/dos_almas",
    ),
    # Launcher propio: solo fuentes, documentación y empaquetado. El JRE, los
    # JAR de dependencias y las salidas de build/ y dist/ quedan fuera.
    (
        "Launcher-Propio/src",
        "custom/launcher-propio/src",
    ),
    (
        "Launcher-Propio/docs",
        "custom/launcher-propio/docs",
    ),
    (
        "Launcher-Propio/packaging",
        "custom/launcher-propio/packaging",
    ),
)

FILES = (
    ("README.md", "docs/README-ENTORNO.md"),
    ("AGENTS.md", "docs/AGENTS.md"),
    ("CLAUDE.md", "docs/CLAUDE.md"),
    (
        "Documentacion/CREDENCIALES-Y-ACCESOS.md",
        "docs/CREDENCIALES-Y-ACCESOS.md",
    ),
    (
        "Documentacion/ESTADO-Y-TRASPASO.md",
        "docs/ESTADO-Y-TRASPASO.md",
    ),
    (
        "Documentacion/AUDITORIA-TEXTOS-FUERA-DE-LANG-2026-07-24.md",
        "docs/AUDITORIA-TEXTOS-FUERA-DE-LANG-2026-07-24.md",
    ),
    (
        "Documentacion/AUDITORIA-TRADUCCIONES-MODS-2026-07-24.md",
        "docs/AUDITORIA-TRADUCCIONES-MODS-2026-07-24.md",
    ),
    (
        "Documentacion/INCIDENTE-PORTATIL-WINDOWS-2026-07-23.md",
        "docs/INCIDENTE-PORTATIL-WINDOWS-2026-07-23.md",
    ),
    (
        "Documentacion/REPARACION-LOGS-2026-07-24.md",
        "docs/REPARACION-LOGS-2026-07-24.md",
    ),
    (
        "Documentacion/INTEGRACIONES-Y-UI-2026-07-24.md",
        "docs/INTEGRACIONES-Y-UI-2026-07-24.md",
    ),
    ("Servidor/RPG-Dos-Almas/BACKUPS.md", "server/docs/BACKUPS.md"),
    (
        "Servidor/RPG-Dos-Almas/MEJORAS-PROXIMA-PARTIDA.md",
        "server/docs/MEJORAS-PROXIMA-PARTIDA.md",
    ),
    (
        "Servidor/RPG-Dos-Almas/PUBLICACION.md",
        "server/docs/PUBLICACION.md",
    ),
    (
        "Servidor/RPG-Dos-Almas/iniciar-servidor.sh",
        "server/scripts/iniciar-servidor.sh",
    ),
    (
        "Servidor/RPG-Dos-Almas/ajustar-dh.sh",
        "server/scripts/ajustar-dh.sh",
    ),
    (
        "Servidor/RPG-Dos-Almas/copia-seguridad.sh",
        "server/scripts/copia-seguridad.sh",
    ),
    (
        "Servidor/RPG-Dos-Almas/respaldar-mundo.sh",
        "server/scripts/respaldar-mundo.sh",
    ),
    (
        "Servidor/RPG-Dos-Almas/reiniciar-mundo.sh",
        "server/scripts/reiniciar-mundo.sh",
    ),
    (
        "Launcher-Propio/build.sh",
        "custom/launcher-propio/build.sh",
    ),
    (
        "Launcher-Propio/empaquetar.sh",
        "custom/launcher-propio/empaquetar.sh",
    ),
    (
        "Servidor/RPG-Dos-Almas/user_jvm_args.txt",
        "server/user_jvm_args.txt",
    ),
    (
        "Cliente/RPG-Dos-Almas/options.txt",
        "client/tower/options.txt",
    ),
    (
        "Distribucion/Isa-Windows/pack/options.txt",
        "client/portable/options.txt",
    ),
    (
        "Distribucion/Modpack/build_pack.py",
        "modpack/build_pack.py",
    ),
    (
        "Distribucion/Modpack/build/modrinth.index.json",
        "modpack/modrinth.index.json",
    ),
    (
        "Distribucion/Modpack/build/overrides/README-RPG-DOS-ALMAS.txt",
        "modpack/overrides/README-RPG-DOS-ALMAS.txt",
    ),
    (
        "Distribucion/Modpack/build/overrides/options.txt",
        "modpack/overrides/options.txt",
    ),
    (
        "Distribucion/Isa-Windows/INSTALAR-LINUX.sh",
        "distribution/isa-windows/INSTALAR-LINUX.sh",
    ),
    (
        "Distribucion/Isa-Windows/INSTALAR-WINDOWS.bat",
        "distribution/isa-windows/INSTALAR-WINDOWS.bat",
    ),
    (
        "Distribucion/Isa-Windows/LANZAR-LINUX-OPTIMIZADO.sh",
        "distribution/isa-windows/LANZAR-LINUX-OPTIMIZADO.sh",
    ),
    (
        "Distribucion/Isa-Windows/LEEME.txt",
        "distribution/isa-windows/LEEME.txt",
    ),
    (
        "Distribucion/Isa-Windows/SERVIDOR.txt",
        "distribution/isa-windows/SERVIDOR.txt",
    ),
    (
        "Distribucion/Isa-Windows/TLauncher-RPG.desktop",
        "distribution/isa-windows/TLauncher-RPG.desktop",
    ),
    (
        "Distribucion/Isa-Windows/instalar-windows.ps1",
        "distribution/isa-windows/instalar-windows.ps1",
    ),
    (
        "Distribucion/Isa-Windows/Instalador-Windows/COMO-JUGAR.txt",
        "distribution/isa-windows/Instalador-Windows/COMO-JUGAR.txt",
    ),
    (
        "Distribucion/Isa-Windows/Instalador-Windows/RPG-Dos-Almas.nsi",
        "distribution/isa-windows/Instalador-Windows/RPG-Dos-Almas.nsi",
    ),
    (
        "Distribucion/Isa-Windows/Instalador-Windows/abrir-rpg-dos-almas.ps1",
        "distribution/isa-windows/Instalador-Windows/abrir-rpg-dos-almas.ps1",
    ),
    (
        "Distribucion/Isa-Windows/Instalador-Windows/compilar-instalador.sh",
        "distribution/isa-windows/Instalador-Windows/compilar-instalador.sh",
    ),
    (
        "Distribucion/Isa-Windows/Instalador-Windows/instalar-paquete.ps1",
        "distribution/isa-windows/Instalador-Windows/instalar-paquete.ps1",
    ),
)

BINARY_FILES = (
    (
        "Servidor/RPG-Dos-Almas/server-icon.png",
        "server/server-icon.png",
    ),
)

SYSTEMD_FILES = (
    "rpg-dos-almas-server.service",
    "rpg-dos-almas-backup.service",
    "rpg-dos-almas-backup.timer",
    "rpg-dos-almas-dh.service",
    "rpg-dos-almas-dh.timer",
    "rpg-dos-almas-tunnel.service",
)

MOD_COLLECTIONS = (
    ("server/active", "Servidor/RPG-Dos-Almas/mods"),
    ("server/disabled", "Servidor/RPG-Dos-Almas/mods-disabled"),
    ("server/staging-final", "Servidor/RPG-Dos-Almas/mods-staging-final"),
    (
        "server/staging-loot-mining",
        "Servidor/RPG-Dos-Almas/mods-staging-loot-mining",
    ),
    (
        "server/staging-ui-qol",
        "Servidor/RPG-Dos-Almas/mods-staging-ui-qol",
    ),
    (
        "server/staging-world-expansion",
        "Servidor/RPG-Dos-Almas/mods-staging-world-expansion",
    ),
    ("client/tower/active", "Cliente/RPG-Dos-Almas/mods"),
    ("client/tower/disabled", "Cliente/RPG-Dos-Almas/mods-disabled"),
    ("client/portable/active", "Distribucion/Isa-Windows/pack/mods"),
    (
        "client/portable/disabled",
        "Distribucion/Isa-Windows/pack/mods-disabled",
    ),
)

RESOURCE_COLLECTIONS = (
    ("client/tower/resourcepacks", "Cliente/RPG-Dos-Almas/resourcepacks"),
    ("client/tower/shaderpacks", "Cliente/RPG-Dos-Almas/shaderpacks"),
    (
        "client/tower/shaderpacks-disabled",
        "Cliente/RPG-Dos-Almas/shaderpacks-disabled",
    ),
    (
        "client/portable/resourcepacks",
        "Distribucion/Isa-Windows/pack/resourcepacks",
    ),
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sanitize_text(text: str) -> str:
    text = JSON_SECRET.sub(r'\1"CONFIGURAR_LOCALMENTE"', text)
    text = ASSIGNMENT_SECRET.sub(r"\1CONFIGURAR_LOCALMENTE", text)
    text = re.sub(
        r"(?i)(https?://[^:/\s]+:)[^@\s/]+@",
        r"\1CONFIGURAR_LOCALMENTE@",
        text,
    )
    text = re.sub(
        r"-----BEGIN [A-Z ]*PRIVATE KEY-----.*?"
        r"-----END [A-Z ]*PRIVATE KEY-----",
        "CLAVE_PRIVADA_OMITIDA",
        text,
        flags=re.DOTALL,
    )
    lines = [line.rstrip() for line in text.splitlines()]
    normalized = "\n".join(lines).rstrip()
    return f"{normalized}\n" if normalized else ""


def copy_text(source: Path, destination: Path) -> None:
    data = source.read_bytes()
    if b"\x00" in data:
        raise ValueError(f"Archivo binario rechazado: {source}")
    try:
        text = data.decode("utf-8-sig")
    except UnicodeDecodeError:
        text = data.decode("latin-1")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(sanitize_text(text), encoding="utf-8")
    source_mode = stat.S_IMODE(source.stat().st_mode)
    executable = bool(source_mode & 0o111) and (
        source.suffix.lower() in {".py", ".sh"} or source.name == "gradlew"
    )
    destination.chmod(0o755 if executable else 0o644)


def copy_project_png(source: Path, destination: Path) -> None:
    data = source.read_bytes()
    if len(data) > 256 * 1024:
        raise ValueError(f"PNG propio demasiado grande: {source}")
    if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError(f"PNG propio inválido: {source}")
    width, height = struct.unpack(">II", data[16:24])
    if (width, height) != (64, 64):
        raise ValueError(
            f"El icono debe ser 64x64, no {width}x{height}: {source}"
        )
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
    destination.chmod(0o644)


def is_sensitive_filename(name: str) -> bool:
    return name in SKIP_NAMES or name.startswith(".env.")


def copy_tree(source: Path, destination: Path) -> None:
    if not source.is_dir():
        return
    for item in sorted(source.rglob("*")):
        if not item.is_file() or item.is_symlink():
            continue
        relative = item.relative_to(source)
        if any(
            relative.parts[: len(prefix)] == prefix
            for prefix in SKIP_RELATIVE_PREFIXES
        ):
            continue
        if (
            is_sensitive_filename(item.name)
            or item.suffix.lower() not in TEXT_SUFFIXES
        ):
            continue
        copy_text(item, destination / relative)


def write_server_properties() -> None:
    source = SOURCE_ROOT / "Servidor/RPG-Dos-Almas/server.properties"
    destination = SNAPSHOT_ROOT / "server/server.properties.example"
    lines = []
    for raw in source.read_text(encoding="utf-8").splitlines():
        if "=" not in raw or raw.lstrip().startswith("#"):
            lines.append(raw)
            continue
        key, value = raw.split("=", 1)
        if SENSITIVE_KEY.search(key):
            value = "CONFIGURAR_LOCALMENTE"
        lines.append(f"{key}={value}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text("\n".join(lines) + "\n", encoding="utf-8")


def collect_files(collections: tuple[tuple[str, str], ...], suffix: str | None):
    result = {}
    for label, relative in collections:
        root = SOURCE_ROOT / relative
        entries = []
        if root.is_dir():
            for path in sorted(root.rglob("*")):
                if not path.is_file() or path.is_symlink():
                    continue
                if suffix is not None and path.suffix.lower() != suffix:
                    continue
                entries.append(
                    {
                        "path": path.relative_to(root).as_posix(),
                        "bytes": path.stat().st_size,
                        "sha256": sha256(path),
                    }
                )
        result[label] = entries
    return result


def write_json(relative: str, payload) -> None:
    destination = SNAPSHOT_ROOT / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def write_manifests() -> None:
    write_json(
        "manifests/mods.json",
        {
            "minecraft": "1.20.1",
            "forge": "47.4.20",
            "nota": (
                "Inventario de verificación; los JAR no se incluyen ni se "
                "autoriza su redistribución."
            ),
            "collections": collect_files(MOD_COLLECTIONS, ".jar"),
        },
    )
    write_json(
        "manifests/third-party-resources.json",
        {
            "nota": (
                "Inventario de recursos excluidos. Deben recuperarse desde "
                "su fuente y conforme a su licencia."
            ),
            "collections": collect_files(RESOURCE_COLLECTIONS, None),
        },
    )

    backups = []
    backup_root = SOURCE_ROOT / "Respaldos/Mundos"
    for archive in sorted(backup_root.glob("*.tar.zst")):
        sidecar = archive.with_name(f"{archive.name}.sha256")
        declared = None
        if sidecar.is_file():
            match = re.search(r"\b([0-9a-fA-F]{64})\b", sidecar.read_text())
            if match:
                declared = match.group(1).lower()
        backups.append(
            {
                "file": archive.name,
                "bytes": archive.stat().st_size,
                "declared_sha256": declared,
                "sidecar_present": sidecar.is_file(),
            }
        )
    write_json(
        "manifests/world-backups.json",
        {
            "nota": "Catálogo; los mundos y sus archivos no se guardan en Git.",
            "backups": backups,
        },
    )
    write_json(
        "manifests/excluded-content.json",
        {
            "excluded": [
                {
                    "category": "minecraft-y-dependencias",
                    "examples": [
                        "Minecraft",
                        "Forge",
                        "Java",
                        "libraries",
                        "assets",
                        "versions",
                    ],
                    "reason": "contenido descargable de terceros",
                },
                {
                    "category": "mods-y-recursos",
                    "examples": ["JAR", "shaders", "resource packs de terceros"],
                    "reason": "licencias individuales y tamaño",
                },
                {
                    "category": "estado-de-juego",
                    "examples": [
                        "mundo",
                        "saves",
                        "backups",
                        "datos de jugadores",
                        "historial JEI por mundo",
                    ],
                    "reason": "estado binario, privacidad y uso de backups dedicados",
                },
                {
                    "category": "estado-efimero",
                    "examples": ["logs", "crash reports", "cachés", "capturas"],
                    "reason": "no necesario para reconstrucción",
                },
                {
                    "category": "secretos",
                    "examples": [
                        ".rcon-credentials",
                        ".env",
                        "claves SSH",
                        "contraseñas",
                        "tokens",
                    ],
                    "reason": "seguridad",
                },
            ]
        },
    )


def write_snapshot_manifest() -> None:
    entries = []
    manifest = SNAPSHOT_ROOT / "manifests/snapshot-files.json"
    for path in sorted(SNAPSHOT_ROOT.rglob("*")):
        if not path.is_file() or path == manifest:
            continue
        entries.append(
            {
                "path": path.relative_to(SNAPSHOT_ROOT).as_posix(),
                "bytes": path.stat().st_size,
                "sha256": sha256(path),
            }
        )
    write_json(
        "manifests/snapshot-files.json",
        {"files": entries, "count": len(entries)},
    )


def main() -> int:
    expected = SOURCE_ROOT / "Servidor/RPG-Dos-Almas"
    if not expected.is_dir():
        raise SystemExit(f"No existe el servidor canónico: {expected}")
    if REPO_ROOT == SOURCE_ROOT:
        raise SystemExit("El repositorio no puede ser la raíz fuente.")

    shutil.rmtree(SNAPSHOT_ROOT, ignore_errors=True)
    SNAPSHOT_ROOT.mkdir(parents=True)

    for source_relative, destination_relative in CONFIG_TREES:
        copy_tree(
            SOURCE_ROOT / source_relative,
            SNAPSHOT_ROOT / destination_relative,
        )
    for source_relative, destination_relative in FILES:
        source = SOURCE_ROOT / source_relative
        if source.is_file():
            copy_text(source, SNAPSHOT_ROOT / destination_relative)
    for source_relative, destination_relative in BINARY_FILES:
        source = SOURCE_ROOT / source_relative
        if source.is_file():
            copy_project_png(source, SNAPSHOT_ROOT / destination_relative)

    systemd_root = Path.home() / ".config/systemd/user"
    for name in SYSTEMD_FILES:
        source = systemd_root / name
        if source.is_file():
            copy_text(
                source,
                SNAPSHOT_ROOT / "server/systemd-user" / name,
            )

    write_server_properties()
    write_manifests()
    write_snapshot_manifest()
    print(f"Snapshot generado en {SNAPSHOT_ROOT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
