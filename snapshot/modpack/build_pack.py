#!/usr/bin/env python3
"""Build the reproducible RPG Dos Almas Modrinth pack."""

import hashlib
import json
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

MC = "1.20.1"
FORGE = "47.4.20"
ROOT = Path(__file__).resolve().parent
BUILD = ROOT / "build"
OUTPUT = ROOT / "RPG-Dos-Almas-1.0.0.mrpack"
API = "https://api.modrinth.com/v2"
UA = "RPG-Dos-Almas/1.0 (local personal modpack)"

# Every entry is gameplay-relevant or a well-established performance/QoL mod.
PROJECTS = [
    # Performance and stability
    "embeddium", "modernfix", "ferrite-core",
    "entityculling", "dynamic-fps", "memoryleakfix", "oculus", "distanthorizons",
    # RPG progression, combat and magic
    "apotheosis", "irons-spells-n-spellbooks", "irons-lib", "better-combat",
    "simply-swords", "majruszs-progressive-difficulty", "runic-skills", "yacl",
    # Worlds, dungeons and bosses
    "when-dungeons-arise", "dungeons-and-taverns", "yungs-better-dungeons",
    "yungs-better-strongholds", "aether", "blue-skies",
    "alexs-mobs", "mowzies-mobs", "l_enders-cataclysm", "biomes-o-plenty",
    # Adventure quality of life
    "jei", "jade", "xaeros-minimap", "xaeros-world-map", "waystones",
    "sophisticated-backpacks", "lootr", "corpse", "carry-on",
    "farmers-delight", "comforts", "natures-compass", "artifacts",
]


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def project(ref):
    return get_json(f"{API}/project/{urllib.parse.quote(ref)}")


def versions(project_id):
    params = urllib.parse.urlencode({
        "loaders": json.dumps(["forge"]),
        "game_versions": json.dumps([MC]),
        "featured": "false",
    })
    return get_json(f"{API}/project/{project_id}/version?{params}")


def choose_file(version):
    return next((item for item in version["files"] if item.get("primary")), version["files"][0])


def main():
    shutil.rmtree(BUILD, ignore_errors=True)
    (BUILD / "overrides").mkdir(parents=True)
    selected = {}
    queue = list(PROJECTS)
    missing = []

    while queue:
        ref = queue.pop(0)
        try:
            info = project(ref)
        except urllib.error.HTTPError as exc:
            missing.append(f"{ref}: proyecto no encontrado ({exc.code})")
            continue
        pid = info["id"]
        if pid in selected:
            continue
        compatible = versions(pid)
        if not compatible:
            missing.append(f"{info['title']}: sin versión Forge {MC}")
            continue
        version = compatible[0]
        selected[pid] = (info, version, choose_file(version))
        for dep in version.get("dependencies", []):
            if dep.get("dependency_type") == "required":
                if dep.get("project_id"):
                    queue.append(dep["project_id"])
                elif dep.get("version_id"):
                    dep_version = get_json(f"{API}/version/{dep['version_id']}")
                    queue.append(dep_version["project_id"])

    files = []
    for info, version, item in sorted(selected.values(), key=lambda value: value[0]["title"].lower()):
        hashes = item["hashes"]
        files.append({
            "path": f"mods/{item['filename']}",
            "hashes": {key: hashes[key] for key in ("sha1", "sha512") if key in hashes},
            "env": {"client": "required", "server": "required"},
            "downloads": [item["url"]],
            "fileSize": item.get("size", 0),
        })

    options = """# Perfil equilibrado para el portatil; el PC potente puede subir estos valores.\nrenderDistance:10\nsimulationDistance:8\nentityDistanceScaling:0.8\nmaxFps:120\nenableVsync:true\nparticles:1\ngraphicsMode:1\nbiomeBlendRadius:2\n"""
    (BUILD / "overrides" / "options.txt").write_text(options, encoding="utf-8")
    (BUILD / "overrides" / "README-RPG-DOS-ALMAS.txt").write_text(
        "RPG Dos Almas 1.0.0\nPerfil base compartido y optimizado.\n"
        "RAM portatil: 6-8 GB. RAM PC potente: 10-12 GB.\n"
        "Los shaders son opcionales y solo deben activarse en el PC potente.\n",
        encoding="utf-8",
    )
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": "1.0.0",
        "name": "RPG Dos Almas",
        "summary": "Aventura RPG cooperativa, magia, jefes, dimensiones y progresión.",
        "files": files,
        "dependencies": {"minecraft": MC, "forge": FORGE},
    }
    (BUILD / "modrinth.index.json").write_text(json.dumps(index, indent=2), encoding="utf-8")
    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in BUILD.rglob("*"):
            if path.is_file():
                archive.write(path, path.relative_to(BUILD))

    print(f"Creado: {OUTPUT}")
    print(f"Mods y dependencias: {len(files)}")
    if missing:
        print("Omitidos:")
        for message in missing:
            print(f"- {message}")
    return 0 if len(files) >= 30 else 1


if __name__ == "__main__":
    sys.exit(main())
