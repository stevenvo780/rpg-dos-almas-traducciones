#!/usr/bin/env python3
"""Exporta lotes deterministas para traductores delegados.

No llama a ninguna API ni modifica clientes, servidor o JAR. Los lotes se
escriben en Temporales para que cada agente trabaje en una ruta independiente.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

import minimax_mod_translator as translator


OUTPUT_ROOT = (
    translator.MINECRAFT_ROOT / "Temporales/Traduccion-Agentes/tasks"
)
GROUPS = ("terra-a", "terra-b", "sol-c")


def group_for(namespace: str) -> str:
    digest = hashlib.sha256(namespace.encode("utf-8")).digest()
    return GROUPS[int.from_bytes(digest[:4], "big") % len(GROUPS)]


def main() -> int:
    mods_dirs = [path.resolve() for path in translator.DEFAULT_MODS_DIRS]
    disabled_dirs = translator.discover_disabled_dirs(mods_dirs)
    occurrences = translator.collect_occurrences(mods_dirs, disabled_dirs)
    scans = translator.scan_inventory(occurrences)
    catalog = translator.build_catalog(scans, include_disabled=False)
    work_items = translator.make_work_items(catalog)

    grouped: dict[str, list[dict[str, object]]] = {group: [] for group in GROUPS}
    for item in work_items:
        entry = catalog[(item.namespace, item.key)]
        existing = {
            locale: candidate.value
            for locale, candidate in sorted(entry.spanish.items())
        }
        grouped[group_for(item.namespace)].append(
            {
                "id": item.item_id,
                "namespace": item.namespace,
                "key": item.key,
                "source": item.source,
                "immutable_tokens": {
                    name: list(values) for name, values in item.tokens.items()
                },
                "existing_spanish": existing,
            }
        )

    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
    summary: dict[str, object] = {
        "schema_version": 1,
        "source_catalog_keys": len(catalog),
        "total_tasks": len(work_items),
        "groups": {},
    }
    for group in GROUPS:
        path = OUTPUT_ROOT / f"{group}.json"
        payload = {
            "schema_version": 1,
            "group": group,
            "instructions": {
                "target": "español neutro natural, compatible con es_mx",
                "preserve_keys": True,
                "preserve_immutable_tokens": True,
                "output_contract": "mapa JSON id -> traducción",
            },
            "items": grouped[group],
        }
        path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        namespaces = sorted({str(item["namespace"]) for item in grouped[group]})
        summary["groups"][group] = {
            "path": str(path),
            "items": len(grouped[group]),
            "namespaces": len(namespaces),
            "namespace_names": namespaces,
        }

    (OUTPUT_ROOT / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary["groups"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
