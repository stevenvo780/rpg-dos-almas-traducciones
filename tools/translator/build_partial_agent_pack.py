#!/usr/bin/env python3
"""Construye un resource pack parcial solo con traducciones ya validadas.

Incluye traducciones españolas oficiales reutilizables, resultados válidos de
los agentes delegados y los overrides manuales del paquete activo. No publica
ni modifica clientes por sí mismo.
"""

from __future__ import annotations

import collections
import json
from pathlib import Path

import minimax_mod_translator as translator
import validate_agent_translations as delegated


OUTPUT = translator.TOOL_DIR / "output/RPG-Dos-Almas-Traduccion-Completa-GPT"
ACTIVE_CUSTOM_PACK = (
    translator.MINECRAFT_ROOT
    / "Cliente/RPG-Dos-Almas/resourcepacks/RPG-Dos-Almas-Espanol"
)
ORIGINAL_CUSTOM_PACK = (
    translator.MINECRAFT_ROOT
    / "Archivo/Reparaciones/Traduccion-Espanol-pre-instalacion-20260724"
    / "Torre-RPG-Dos-Almas-Espanol"
)


def load_custom_overrides() -> dict[tuple[str, str], dict[str, str]]:
    result: dict[tuple[str, str], dict[str, str]] = {}
    source_pack = ORIGINAL_CUSTOM_PACK if ORIGINAL_CUSTOM_PACK.is_dir() else ACTIVE_CUSTOM_PACK
    assets = source_pack / "assets"
    if not assets.is_dir():
        return result
    for path in sorted(assets.glob("*/lang/es_*.json")):
        namespace = path.parts[-3]
        locale = path.stem.lower()
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
        if not isinstance(payload, dict) or not all(
            isinstance(key, str) and isinstance(value, str)
            for key, value in payload.items()
        ):
            raise translator.TranslatorError(f"override inválido: {path}")
        result[(namespace, locale)] = dict(payload)
    return result


def main() -> int:
    tasks, owners = delegated.load_tasks()
    supplied, file_issues = delegated.load_translations()
    report, valid = delegated.build_report(tasks, owners, supplied, file_issues)
    catalog, work_items = delegated.rebuild_catalog()

    task_translations = {
        (work_items[item_id].namespace, work_items[item_id].key): value
        for item_id, value in valid.items()
        if item_id in work_items
    }
    rendered: dict[str, dict[str, dict[str, str]]] = collections.defaultdict(
        lambda: collections.defaultdict(dict)
    )
    official_keys: set[tuple[str, str]] = set()
    delegated_keys: set[tuple[str, str]] = set()

    for identity in sorted(catalog):
        entry = catalog[identity]
        generic = translator.select_existing_spanish(entry)
        delegated_value = task_translations.get(identity)
        for target in translator.DEFAULT_SPANISH_VARIANTS:
            target_existing = translator.existing_for_target(entry, target)
            if target_existing is not None and target_existing.value != entry.source.value:
                value = target_existing.value
                official_keys.add(identity)
            elif delegated_value is not None:
                value = delegated_value
                delegated_keys.add(identity)
            elif generic is not None and generic.value != entry.source.value:
                value = generic.value
                official_keys.add(identity)
            else:
                continue
            errors = translator.validate_translation(entry.source.value, value)
            if errors:
                raise translator.TranslatorError(
                    f"{entry.namespace}:{entry.key}: " + ", ".join(errors)
                )
            rendered[entry.namespace][target][entry.key] = value

    custom_overrides = load_custom_overrides()
    custom_keys = 0
    for (namespace, locale), values in custom_overrides.items():
        targets = (
            translator.DEFAULT_SPANISH_VARIANTS
            if locale == "es_es"
            else (locale,)
        )
        for target in targets:
            rendered[namespace][target].update(values)
        custom_keys += len(values)

    complete = (
        report["statistics"]["missing"] == 0
        and report["statistics"]["invalid"] == 0
        and report["statistics"]["file_issues"] == 0
    )
    embedded = {
        **report,
        "status": "complete_validated" if complete else "partial_validated",
        "publication": {
            "official_spanish_keys": len(official_keys),
            "delegated_gpt_keys": len(delegated_keys),
            "custom_override_keys": custom_keys,
            "missing_agent_tasks": report["statistics"]["missing"],
            "invalid_agent_tasks": report["statistics"]["invalid"],
            "server_restart_required": False,
        },
    }
    translator.write_resource_pack(
        output=OUTPUT,
        rendered=rendered,
        embedded_manifest=embedded,
        mods_dirs=[path.resolve() for path in translator.DEFAULT_MODS_DIRS],
        overwrite=OUTPUT.exists(),
        pack_description=(
            "Traducción española completa y validada de RPG Dos Almas"
            if complete
            else "Traducción española validada de RPG Dos Almas (parcial)"
        ),
    )
    print(
        json.dumps(
            embedded["publication"],
            ensure_ascii=False,
            sort_keys=True,
        )
    )
    print(f"Pack {'completo' if complete else 'parcial'} validado: {OUTPUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
