#!/usr/bin/env python3
"""Audita y traduce recursos de idioma de mods de Minecraft sin alterar los JAR.

La ejecución predeterminada es una auditoría local. Las llamadas a MiniMax
requieren simultáneamente ``--mode translate`` y ``--execute``.
"""

from __future__ import annotations

import argparse
import collections
import concurrent.futures
import dataclasses
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import sqlite3
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from pathlib import Path
from typing import Any, Iterable, Iterator, Mapping, Sequence


TOOL_DIR = Path(__file__).resolve().parent
MINECRAFT_ROOT = TOOL_DIR.parent.parent
DEFAULT_MODS_DIRS = (
    MINECRAFT_ROOT / "Cliente/RPG-Dos-Almas/mods",
    MINECRAFT_ROOT / "Distribucion/Isa-Windows/pack/mods",
)
DEFAULT_REPORT = TOOL_DIR / "reports/coverage-manifest.json"
DEFAULT_OUTPUT = TOOL_DIR / "output/RPG-Dos-Almas-Traduccion-Completa"
DEFAULT_CACHE = TOOL_DIR / "cache/translations.sqlite3"
DEFAULT_SPANISH_VARIANTS = (
    "es_es",
    "es_mx",
    "es_ar",
    "es_cl",
    "es_ec",
    "es_uy",
    "es_ve",
)
MINIMAX_ENDPOINT = "https://api.minimax.io/v1/chat/completions"
DEFAULT_MODEL = "MiniMax-M3"
PROMPT_VERSION = "rpg-dos-almas-es-v1"
MAX_LANG_FILE_BYTES = 8 * 1024 * 1024

LANG_PATH_RE = re.compile(
    r"^assets/(?P<namespace>[a-z0-9_.-]+)/lang/"
    r"(?P<locale>[a-z0-9_.-]+)\.(?P<format>json|lang)$",
    re.IGNORECASE,
)
PRINTF_RE = re.compile(
    r"%(?:\d+\$)?(?:[-+#0,(<]*)?(?:\d+|\*)?(?:\.\d+|\.\*)?"
    r"(?:[tT][a-zA-Z]|[a-zA-Z%])"
)
MESSAGE_FORMAT_RE = re.compile(r"\{\d+(?:,[^{}]+)?\}")
BRACE_PLACEHOLDER_RE = re.compile(r"(?<!\{)\{\}(?!\})")
NAMED_BRACE_RE = re.compile(r"\{[A-Za-z_][A-Za-z0-9_.-]*\}")
MINECRAFT_FORMAT_RE = re.compile(r"§(?:[0-9a-fk-or]|#[0-9a-f]{6})", re.IGNORECASE)
AMPERSAND_FORMAT_RE = re.compile(r"(?<!&)&(?:[0-9a-fk-or]|#[0-9a-f]{6})", re.IGNORECASE)
RESOURCE_ID_RE = re.compile(
    r"(?<![A-Za-z0-9_.-])[a-z0-9_.-]+:[a-z0-9_./-]+"
    r"(?![A-Za-z0-9_./-])"
)
URL_RE = re.compile(r"https?://[^\s<>\"]+")
ANGLE_TAG_RE = re.compile(r"</?[a-zA-Z][^<>\n]*>")
ESCAPED_NEWLINE_RE = re.compile(r"\\[nrt]")
CONTROL_CHARACTER_RE = re.compile(r"[\n\r\t]")
TRANSLATABLE_LETTER_RE = re.compile(r"[A-Za-z]")


class TranslatorError(RuntimeError):
    """Error operativo mostrado de forma segura al usuario."""


@dataclasses.dataclass(frozen=True)
class JarOccurrence:
    path: Path
    source_dir: Path
    scope: str
    sha256: str
    filename: str
    order: int


@dataclasses.dataclass(frozen=True)
class LangAsset:
    namespace: str
    locale: str
    format: str
    archive_path: str
    entries: Mapping[str, str]
    parse_errors: tuple[str, ...] = ()


@dataclasses.dataclass
class JarScan:
    sha256: str
    occurrences: list[JarOccurrence]
    assets: list[LangAsset]
    scan_errors: list[str]

    @property
    def namespaces(self) -> list[str]:
        return sorted({asset.namespace for asset in self.assets})


@dataclasses.dataclass(frozen=True)
class ValueCandidate:
    value: str
    namespace: str
    key: str
    locale: str
    jar_sha256: str
    jar_name: str
    archive_path: str
    provider_order: int


@dataclasses.dataclass
class CatalogEntry:
    namespace: str
    key: str
    source: ValueCandidate
    spanish: dict[str, ValueCandidate]
    source_conflicts: list[ValueCandidate]
    spanish_conflicts: dict[str, list[ValueCandidate]]


@dataclasses.dataclass(frozen=True)
class WorkItem:
    item_id: str
    namespace: str
    key: str
    source: str
    tokens: Mapping[str, tuple[str, ...]]


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_relative_to(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def normalized_locale(value: str) -> str:
    return value.strip().lower().replace("-", "_")


def is_spanish_locale(locale: str) -> bool:
    locale = normalized_locale(locale)
    return locale == "es" or locale.startswith("es_")


def is_english_locale(locale: str) -> bool:
    locale = normalized_locale(locale)
    return locale == "en" or locale.startswith("en_")


def iter_jar_files(directory: Path) -> Iterator[Path]:
    if not directory.is_dir():
        raise TranslatorError(f"No existe el directorio de mods: {directory}")
    yield from sorted(
        (path for path in directory.iterdir() if path.is_file() and path.suffix.lower() == ".jar"),
        key=lambda path: path.name.casefold(),
    )


def discover_disabled_dirs(mods_dirs: Sequence[Path]) -> list[Path]:
    discovered: list[Path] = []
    seen: set[Path] = set()
    for mods_dir in mods_dirs:
        sibling = mods_dir.parent / "mods-disabled"
        resolved = sibling.resolve()
        if sibling.is_dir() and resolved not in seen:
            seen.add(resolved)
            discovered.append(sibling)
    return discovered


def collect_occurrences(
    mods_dirs: Sequence[Path],
    disabled_dirs: Sequence[Path],
) -> list[JarOccurrence]:
    occurrences: list[JarOccurrence] = []
    order = 0
    for scope, directories in (("active", mods_dirs), ("disabled", disabled_dirs)):
        for directory in directories:
            directory = directory.expanduser().resolve()
            for path in iter_jar_files(directory):
                occurrences.append(
                    JarOccurrence(
                        path=path.resolve(),
                        source_dir=directory,
                        scope=scope,
                        sha256=sha256_file(path),
                        filename=path.name,
                        order=order,
                    )
                )
                order += 1
    return occurrences


def parse_json_lang(raw: bytes, archive_path: str) -> tuple[dict[str, str], list[str]]:
    try:
        decoded = raw.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        return {}, [f"{archive_path}: UTF-8 inválido ({exc.reason})"]
    try:
        value = json.loads(decoded)
    except json.JSONDecodeError as exc:
        return {}, [f"{archive_path}: JSON inválido en línea {exc.lineno}, columna {exc.colno}"]
    if not isinstance(value, dict):
        return {}, [f"{archive_path}: la raíz JSON no es un objeto"]
    entries: dict[str, str] = {}
    errors: list[str] = []
    for key, text in value.items():
        if not isinstance(key, str) or not isinstance(text, str):
            errors.append(f"{archive_path}: entrada no textual omitida: {key!r}")
            continue
        entries[key] = text
    return entries, errors


def _unescape_properties(text: str) -> str:
    def unicode_replacer(match: re.Match[str]) -> str:
        try:
            return chr(int(match.group(1), 16))
        except ValueError:
            return match.group(0)

    text = re.sub(r"\\u([0-9a-fA-F]{4})", unicode_replacer, text)
    replacements = {
        r"\n": "\n",
        r"\r": "\r",
        r"\t": "\t",
        r"\=": "=",
        r"\:": ":",
        r"\\": "\\",
    }
    for encoded, decoded in replacements.items():
        text = text.replace(encoded, decoded)
    return text


def parse_legacy_lang(raw: bytes, archive_path: str) -> tuple[dict[str, str], list[str]]:
    try:
        decoded = raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        decoded = raw.decode("latin-1")
    logical_lines: list[str] = []
    pending = ""
    for physical in decoded.splitlines():
        line = pending + physical
        slash_count = len(line) - len(line.rstrip("\\"))
        if slash_count % 2:
            pending = line[:-1]
            continue
        logical_lines.append(line)
        pending = ""
    if pending:
        logical_lines.append(pending)

    entries: dict[str, str] = {}
    errors: list[str] = []
    for number, line in enumerate(logical_lines, 1):
        stripped = line.lstrip()
        if not stripped or stripped.startswith("#") or stripped.startswith("!"):
            continue
        separator = None
        escaped = False
        for index, character in enumerate(line):
            if escaped:
                escaped = False
                continue
            if character == "\\":
                escaped = True
                continue
            if character in "=:" or character.isspace():
                separator = index
                break
        if separator is None:
            key, value = line, ""
        else:
            key = line[:separator]
            value_start = separator
            while value_start < len(line) and (
                line[value_start].isspace() or line[value_start] in "=:"
            ):
                value_start += 1
            value = line[value_start:]
        key = _unescape_properties(key.strip())
        if not key:
            errors.append(f"{archive_path}: clave vacía en línea {number}")
            continue
        entries[key] = _unescape_properties(value)
    return entries, errors


def scan_jar(path: Path, sha256: str, occurrences: list[JarOccurrence]) -> JarScan:
    assets: list[LangAsset] = []
    errors: list[str] = []
    try:
        with zipfile.ZipFile(path) as archive:
            for info in sorted(archive.infolist(), key=lambda item: item.filename.casefold()):
                normalized_path = info.filename.replace("\\", "/")
                match = LANG_PATH_RE.fullmatch(normalized_path)
                if not match or info.is_dir():
                    continue
                if info.file_size > MAX_LANG_FILE_BYTES:
                    errors.append(
                        f"{normalized_path}: supera el límite seguro de "
                        f"{MAX_LANG_FILE_BYTES} bytes"
                    )
                    continue
                try:
                    raw = archive.read(info)
                except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
                    errors.append(f"{normalized_path}: no se pudo leer ({type(exc).__name__})")
                    continue
                file_format = match.group("format").lower()
                if file_format == "json":
                    entries, parse_errors = parse_json_lang(raw, normalized_path)
                else:
                    entries, parse_errors = parse_legacy_lang(raw, normalized_path)
                assets.append(
                    LangAsset(
                        namespace=match.group("namespace").lower(),
                        locale=normalized_locale(match.group("locale")),
                        format=file_format,
                        archive_path=normalized_path,
                        entries=entries,
                        parse_errors=tuple(parse_errors),
                    )
                )
                errors.extend(parse_errors)
    except (OSError, zipfile.BadZipFile) as exc:
        errors.append(f"JAR/ZIP ilegible ({type(exc).__name__})")
    return JarScan(sha256=sha256, occurrences=occurrences, assets=assets, scan_errors=errors)


def scan_inventory(occurrences: Sequence[JarOccurrence]) -> dict[str, JarScan]:
    groups: dict[str, list[JarOccurrence]] = collections.defaultdict(list)
    for occurrence in occurrences:
        groups[occurrence.sha256].append(occurrence)
    scans: dict[str, JarScan] = {}
    for sha256, group in groups.items():
        representative = min(group, key=lambda item: item.order)
        scans[sha256] = scan_jar(representative.path, sha256, list(group))
    return scans


def source_rank(locale: str) -> tuple[int, str]:
    locale = normalized_locale(locale)
    if locale == "en_us":
        return (0, locale)
    if locale == "en_gb":
        return (1, locale)
    if locale == "en" or locale.startswith("en_"):
        return (2, locale)
    if not is_spanish_locale(locale):
        return (10, locale)
    if locale == "es_mx":
        return (20, locale)
    if locale == "es_es":
        return (21, locale)
    return (22, locale)


def spanish_rank(locale: str, target: str | None = None) -> tuple[int, str]:
    locale = normalized_locale(locale)
    if target and locale == target:
        return (0, locale)
    if locale == "es_mx":
        return (1, locale)
    if locale == "es_es":
        return (2, locale)
    return (3, locale)


def candidate_sort_key(candidate: ValueCandidate, spanish: bool = False) -> tuple[Any, ...]:
    locale_rank = spanish_rank(candidate.locale) if spanish else source_rank(candidate.locale)
    format_rank = 0 if candidate.archive_path.lower().endswith(".json") else 1
    return (
        locale_rank,
        candidate.provider_order,
        format_rank,
        candidate.jar_name.casefold(),
        candidate.archive_path.casefold(),
    )


def included_hashes(
    scans: Mapping[str, JarScan],
    include_disabled: bool,
) -> set[str]:
    result: set[str] = set()
    for sha256, scan in scans.items():
        scopes = {occurrence.scope for occurrence in scan.occurrences}
        if "active" in scopes or (include_disabled and "disabled" in scopes):
            result.add(sha256)
    return result


def build_catalog(
    scans: Mapping[str, JarScan],
    include_disabled: bool = False,
) -> dict[tuple[str, str], CatalogEntry]:
    candidates: dict[tuple[str, str], list[ValueCandidate]] = collections.defaultdict(list)
    allowed = included_hashes(scans, include_disabled)
    for sha256, scan in scans.items():
        if sha256 not in allowed:
            continue
        provider = min(
            (
                occurrence
                for occurrence in scan.occurrences
                if occurrence.scope == "active" or include_disabled
            ),
            key=lambda occurrence: occurrence.order,
        )
        for asset in scan.assets:
            for key, value in asset.entries.items():
                candidates[(asset.namespace, key)].append(
                    ValueCandidate(
                        value=value,
                        namespace=asset.namespace,
                        key=key,
                        locale=asset.locale,
                        jar_sha256=sha256,
                        jar_name=provider.filename,
                        archive_path=asset.archive_path,
                        provider_order=provider.order,
                    )
                )

    catalog: dict[tuple[str, str], CatalogEntry] = {}
    for identity, values in candidates.items():
        source = min(values, key=lambda item: candidate_sort_key(item, spanish=False))
        # Traducciones normales de otros idiomas no son conflictos. Solo lo es
        # que dos proveedores discrepen para la misma clave y locale canónico.
        source_conflicts = [
            candidate
            for candidate in values
            if candidate.locale == source.locale and candidate.value != source.value
        ]
        spanish_candidates = [candidate for candidate in values if is_spanish_locale(candidate.locale)]
        chosen_spanish: dict[str, ValueCandidate] = {}
        spanish_conflicts: dict[str, list[ValueCandidate]] = {}
        for locale in sorted({candidate.locale for candidate in spanish_candidates}):
            local = [candidate for candidate in spanish_candidates if candidate.locale == locale]
            chosen = min(local, key=lambda item: candidate_sort_key(item, spanish=True))
            chosen_spanish[locale] = chosen
            conflicts = [candidate for candidate in local if candidate.value != chosen.value]
            if conflicts:
                spanish_conflicts[locale] = conflicts
        catalog[identity] = CatalogEntry(
            namespace=identity[0],
            key=identity[1],
            source=source,
            spanish=chosen_spanish,
            source_conflicts=source_conflicts,
            spanish_conflicts=spanish_conflicts,
        )
    return catalog


def immutable_tokens(text: str) -> dict[str, tuple[str, ...]]:
    """Extrae tokens que nunca se aceptan alterados por el modelo."""

    return {
        "printf": tuple(PRINTF_RE.findall(text)),
        "message_format": tuple(MESSAGE_FORMAT_RE.findall(text)),
        "brace": tuple(BRACE_PLACEHOLDER_RE.findall(text)),
        "named_brace": tuple(NAMED_BRACE_RE.findall(text)),
        "minecraft_format": tuple(MINECRAFT_FORMAT_RE.findall(text)),
        "ampersand_format": tuple(AMPERSAND_FORMAT_RE.findall(text)),
        "resource_ids": tuple(RESOURCE_ID_RE.findall(text)),
        "urls": tuple(URL_RE.findall(text)),
        "tags": tuple(ANGLE_TAG_RE.findall(text)),
        "escapes": tuple(ESCAPED_NEWLINE_RE.findall(text)),
        "control_characters": tuple(CONTROL_CHARACTER_RE.findall(text)),
    }


def validate_translation(source: str, translated: str) -> list[str]:
    if not isinstance(translated, str):
        return ["la traducción no es texto"]
    if source and not translated.strip():
        return ["la traducción quedó vacía"]
    before = immutable_tokens(source)
    after = immutable_tokens(translated)
    errors: list[str] = []
    order_sensitive = {
        "minecraft_format",
        "ampersand_format",
        "tags",
        "escapes",
        "control_characters",
    }
    for category in before:
        if category in order_sensitive:
            equal = before[category] == after[category]
        else:
            equal = collections.Counter(before[category]) == collections.Counter(after[category])
        if not equal:
            errors.append(f"tokens alterados: {category}")
    return errors


def select_existing_spanish(entry: CatalogEntry) -> ValueCandidate | None:
    valid = [
        candidate
        for candidate in entry.spanish.values()
        if not validate_translation(entry.source.value, candidate.value)
    ]
    if not valid:
        return None
    return min(valid, key=lambda item: candidate_sort_key(item, spanish=True))


def text_needs_translation(source: str, existing: ValueCandidate | None) -> bool:
    if not TRANSLATABLE_LETTER_RE.search(source):
        return False
    if existing is None:
        return True
    return existing.value.strip() == source.strip()


def entry_needs_translation(entry: CatalogEntry) -> bool:
    """Solo genera desde una fuente inglesa canónica.

    Claves presentes únicamente en español u otros idiomas se conservan, pero
    no se envían a un traductor como si su valor fuera inglés.
    """

    if not is_english_locale(entry.source.locale):
        return False
    return text_needs_translation(
        entry.source.value,
        select_existing_spanish(entry),
    )


def make_work_items(catalog: Mapping[tuple[str, str], CatalogEntry]) -> list[WorkItem]:
    items: list[WorkItem] = []
    for index, identity in enumerate(sorted(catalog), 1):
        entry = catalog[identity]
        if not entry_needs_translation(entry):
            continue
        items.append(
            WorkItem(
                item_id=f"t{index:08d}",
                namespace=entry.namespace,
                key=entry.key,
                source=entry.source.value,
                tokens=immutable_tokens(entry.source.value),
            )
        )
    return items


def cache_fingerprint(item: WorkItem, model: str) -> str:
    payload = json.dumps(
        {
            "prompt_version": PROMPT_VERSION,
            "model": model,
            "namespace": item.namespace,
            "key": item.key,
            "source": item.source,
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


class TranslationCache:
    """Caché SQLite local; nunca contiene credenciales."""

    def __init__(self, path: Path) -> None:
        self.path = path
        path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(path, check_same_thread=False)
        self._connection.execute("PRAGMA journal_mode=WAL")
        self._connection.execute(
            """
            CREATE TABLE IF NOT EXISTS translations (
                fingerprint TEXT PRIMARY KEY,
                model TEXT NOT NULL,
                namespace TEXT NOT NULL,
                lang_key TEXT NOT NULL,
                source TEXT NOT NULL,
                translated TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )
        self._connection.commit()
        self._lock = threading.Lock()

    def get(self, item: WorkItem, model: str) -> str | None:
        fingerprint = cache_fingerprint(item, model)
        with self._lock:
            row = self._connection.execute(
                "SELECT translated FROM translations WHERE fingerprint = ?",
                (fingerprint,),
            ).fetchone()
        if row is None:
            return None
        translated = str(row[0])
        if validate_translation(item.source, translated):
            return None
        return translated

    def put(self, item: WorkItem, model: str, translated: str) -> None:
        errors = validate_translation(item.source, translated)
        if errors:
            raise TranslatorError(
                f"No se guardó una respuesta inválida para {item.namespace}:{item.key}: "
                + ", ".join(errors)
            )
        with self._lock:
            self._connection.execute(
                """
                INSERT OR REPLACE INTO translations
                (fingerprint, model, namespace, lang_key, source, translated, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    cache_fingerprint(item, model),
                    model,
                    item.namespace,
                    item.key,
                    item.source,
                    translated,
                    utc_now(),
                ),
            )
            self._connection.commit()

    def close(self) -> None:
        with self._lock:
            self._connection.close()

    def __enter__(self) -> "TranslationCache":
        return self

    def __exit__(self, *_: Any) -> None:
        self.close()


class MiniMaxClient:
    """Cliente mínimo de la API OpenAI-compatible, sin dependencias externas."""

    SYSTEM_PROMPT = """\
Eres un traductor profesional de mods de Minecraft 1.20.1.
Traduce al español neutro y natural todos los valores recibidos.
No traduzcas claves, IDs, nombres de namespaces, URLs ni tokens.
Conserva EXACTAMENTE cada placeholder printf, MessageFormat, {}, código §,
código &, etiqueta, secuencia escapada e identificador namespace:ruta.
Devuelve únicamente un objeto JSON cuyas claves sean los IDs opacos recibidos
y cuyos valores sean las traducciones. No agregues ni omitas IDs."""

    def __init__(
        self,
        api_key: str,
        model: str,
        timeout: float,
        retries: int,
    ) -> None:
        if not api_key:
            raise TranslatorError("MINIMAX_API_KEY no está definida")
        self._api_key = api_key
        self.model = model
        self.timeout = timeout
        self.retries = retries

    def build_payload(self, batch: Sequence[WorkItem]) -> dict[str, Any]:
        items = [
            {
                "id": item.item_id,
                "namespace": item.namespace,
                "key": item.key,
                "text": item.source,
                "immutable_tokens": {key: list(value) for key, value in item.tokens.items()},
            }
            for item in batch
        ]
        return {
            "model": self.model,
            # MiniMax-M3 incluye trazas <think> en content si no se desactiva.
            # Eso impediría validar la respuesta como un objeto JSON puro.
            "thinking": {"type": "disabled"},
            "messages": [
                {"role": "system", "content": self.SYSTEM_PROMPT},
                {
                    "role": "user",
                    "content": json.dumps(
                        {"target_locale": "es_MX/es_ES", "items": items},
                        ensure_ascii=False,
                        separators=(",", ":"),
                    ),
                },
            ],
            "temperature": 0.1,
        }

    def _request_once(self, batch: Sequence[WorkItem]) -> dict[str, str]:
        payload = self.build_payload(batch)
        request = urllib.request.Request(
            MINIMAX_ENDPOINT,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self._api_key}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
        except urllib.error.HTTPError as exc:
            raise TranslatorError(f"MiniMax respondió HTTP {exc.code}") from None
        except urllib.error.URLError as exc:
            reason = getattr(exc, "reason", None)
            reason_name = type(reason).__name__ if reason is not None else "red"
            raise TranslatorError(f"falló la conexión con MiniMax ({reason_name})") from None
        try:
            envelope = json.loads(raw.decode("utf-8"))
            content = envelope["choices"][0]["message"]["content"]
        except (UnicodeDecodeError, json.JSONDecodeError, KeyError, IndexError, TypeError):
            raise TranslatorError("MiniMax devolvió una respuesta incompatible") from None
        if not isinstance(content, str):
            raise TranslatorError("MiniMax no devolvió contenido textual")
        content = content.strip()
        if content.startswith("```") and content.endswith("```"):
            content = re.sub(r"^```(?:json)?\s*", "", content, flags=re.IGNORECASE)
            content = re.sub(r"\s*```$", "", content)
        try:
            translated = json.loads(content)
        except json.JSONDecodeError:
            raise TranslatorError("MiniMax no devolvió el objeto JSON solicitado") from None
        if not isinstance(translated, dict) or not all(
            isinstance(key, str) and isinstance(value, str)
            for key, value in translated.items()
        ):
            raise TranslatorError("el JSON de MiniMax no es un mapa de textos")
        expected = {item.item_id for item in batch}
        if set(translated) != expected:
            raise TranslatorError("MiniMax agregó, omitió o cambió IDs del lote")
        return translated

    def translate(self, batch: Sequence[WorkItem]) -> dict[str, str]:
        last_error: TranslatorError | None = None
        for attempt in range(self.retries + 1):
            try:
                translated = self._request_once(batch)
                by_id = {item.item_id: item for item in batch}
                validation_errors: list[str] = []
                for item_id, value in translated.items():
                    errors = validate_translation(by_id[item_id].source, value)
                    if errors:
                        validation_errors.append(
                            f"{item_id} ({', '.join(errors)})"
                        )
                if validation_errors:
                    raise TranslatorError(
                        "MiniMax alteró tokens inmutables: " + "; ".join(validation_errors)
                    )
                return translated
            except TranslatorError as exc:
                last_error = exc
                if attempt < self.retries:
                    time.sleep(min(2**attempt, 8))
        assert last_error is not None
        raise last_error


def chunked(items: Sequence[WorkItem], size: int) -> Iterator[list[WorkItem]]:
    for start in range(0, len(items), size):
        yield list(items[start : start + size])


def translate_with_cache(
    work_items: Sequence[WorkItem],
    cache: TranslationCache,
    client: MiniMaxClient,
    model: str,
    batch_size: int,
    workers: int,
) -> tuple[dict[tuple[str, str], str], list[dict[str, Any]], int]:
    translations: dict[tuple[str, str], str] = {}
    pending: list[WorkItem] = []
    cache_hits = 0
    for item in work_items:
        cached = cache.get(item, model)
        if cached is None:
            pending.append(item)
        else:
            translations[(item.namespace, item.key)] = cached
            cache_hits += 1

    failures: list[dict[str, Any]] = []
    batches = list(chunked(pending, batch_size))
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        future_to_batch = {
            executor.submit(client.translate, batch): batch for batch in batches
        }
        for future in concurrent.futures.as_completed(future_to_batch):
            batch = future_to_batch[future]
            try:
                result = future.result()
            except TranslatorError as exc:
                failures.append(
                    {
                        "item_ids": [item.item_id for item in batch],
                        "keys": [f"{item.namespace}:{item.key}" for item in batch],
                        "error": str(exc),
                    }
                )
                continue
            by_id = {item.item_id: item for item in batch}
            for item_id, translated in result.items():
                item = by_id[item_id]
                cache.put(item, model, translated)
                translations[(item.namespace, item.key)] = translated
    return translations, failures, cache_hits


def existing_for_target(entry: CatalogEntry, target: str) -> ValueCandidate | None:
    candidates = [
        candidate
        for candidate in entry.spanish.values()
        if not validate_translation(entry.source.value, candidate.value)
    ]
    if not candidates:
        return None
    return min(candidates, key=lambda item: spanish_rank(item.locale, target))


def render_language_maps(
    catalog: Mapping[tuple[str, str], CatalogEntry],
    translations: Mapping[tuple[str, str], str],
    variants: Sequence[str],
) -> tuple[dict[str, dict[str, dict[str, str]]], list[str]]:
    rendered: dict[str, dict[str, dict[str, str]]] = collections.defaultdict(
        lambda: collections.defaultdict(dict)
    )
    untranslated: list[str] = []
    for identity in sorted(catalog):
        entry = catalog[identity]
        generic_existing = select_existing_spanish(entry)
        needs_translation = entry_needs_translation(entry)
        translated = translations.get(identity)
        if needs_translation and translated is None:
            untranslated.append(f"{entry.namespace}:{entry.key}")
        for target in variants:
            target_existing = existing_for_target(entry, target)
            if target_existing is not None and target_existing.value != entry.source.value:
                value = target_existing.value
            elif translated is not None:
                value = translated
            elif generic_existing is not None:
                value = generic_existing.value
            else:
                value = entry.source.value
            errors = validate_translation(entry.source.value, value)
            if errors:
                raise TranslatorError(
                    f"Validación interna fallida para {entry.namespace}:{entry.key}: "
                    + ", ".join(errors)
                )
            rendered[entry.namespace][target][entry.key] = value
    return rendered, untranslated


def safe_output_path(output: Path, mods_dirs: Sequence[Path]) -> Path:
    output = output.expanduser().resolve()
    if output.suffix.lower() == ".jar":
        raise TranslatorError("la salida debe ser un directorio, nunca un JAR")
    for mods_dir in mods_dirs:
        if is_relative_to(output, mods_dir.expanduser().resolve()):
            raise TranslatorError("la salida no puede estar dentro de un directorio de mods")
    server_root = (MINECRAFT_ROOT / "Servidor").resolve()
    if is_relative_to(output, server_root):
        raise TranslatorError("la herramienta nunca escribe dentro de Servidor")
    return output


def validate_overwrite_target(output: Path) -> None:
    allowed_root = (TOOL_DIR / "output").resolve()
    if output == allowed_root or not is_relative_to(output, allowed_root):
        raise TranslatorError(
            "--overwrite solo puede reemplazar un pack hijo de "
            f"{allowed_root}"
        )
    if output.is_symlink():
        raise TranslatorError("--overwrite rechaza salidas que sean enlaces simbólicos")
    if output.exists() and not output.is_dir():
        raise TranslatorError("--overwrite requiere que la salida existente sea un directorio")


def write_resource_pack(
    output: Path,
    rendered: Mapping[str, Mapping[str, Mapping[str, str]]],
    embedded_manifest: Mapping[str, Any],
    mods_dirs: Sequence[Path],
    overwrite: bool,
    pack_description: str = "Traducción completa de mods de RPG Dos Almas",
) -> None:
    output = safe_output_path(output, mods_dirs)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists() and not overwrite:
        raise TranslatorError(
            f"La salida ya existe: {output}. Usa --overwrite para reemplazar solo esa salida."
        )
    if output.exists() and overwrite:
        validate_overwrite_target(output)
    temporary = output.parent / f".{output.name}.tmp-{uuid.uuid4().hex}"
    temporary.mkdir()
    try:
        pack_meta = {
            "pack": {
                "pack_format": 15,
                "description": pack_description,
            }
        }
        (temporary / "pack.mcmeta").write_text(
            json.dumps(pack_meta, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        for namespace in sorted(rendered):
            lang_dir = temporary / "assets" / namespace / "lang"
            lang_dir.mkdir(parents=True, exist_ok=True)
            for locale in sorted(rendered[namespace]):
                target = lang_dir / f"{locale}.json"
                target.write_text(
                    json.dumps(
                        dict(sorted(rendered[namespace][locale].items())),
                        ensure_ascii=False,
                        indent=2,
                    )
                    + "\n",
                    encoding="utf-8",
                )
        (temporary / "coverage-manifest.json").write_text(
            json.dumps(embedded_manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        if output.exists():
            previous = output.parent / f".{output.name}.previous-{uuid.uuid4().hex}"
            output.replace(previous)
            try:
                temporary.replace(output)
            except BaseException:
                previous.replace(output)
                raise
            shutil.rmtree(previous)
        else:
            temporary.replace(output)
    except BaseException:
        if temporary.exists():
            shutil.rmtree(temporary)
        raise


def describe_scan(scan: JarScan) -> dict[str, Any]:
    active_paths = sorted(
        str(item.path) for item in scan.occurrences if item.scope == "active"
    )
    disabled_paths = sorted(
        str(item.path) for item in scan.occurrences if item.scope == "disabled"
    )
    if active_paths and disabled_paths:
        scope = "active_and_disabled"
    elif active_paths:
        scope = "active"
    else:
        scope = "disabled_only"
    return {
        "sha256": scan.sha256,
        "scope": scope,
        "filenames": sorted({item.filename for item in scan.occurrences}),
        "active_paths": active_paths,
        "disabled_paths": disabled_paths,
        "language_status": "has_lang" if scan.assets else "no_lang",
        "namespaces": scan.namespaces,
        "language_files": [
            {
                "path": asset.archive_path,
                "namespace": asset.namespace,
                "locale": asset.locale,
                "format": asset.format,
                "keys": len(asset.entries),
                "parse_errors": list(asset.parse_errors),
            }
            for asset in scan.assets
        ],
        "scan_errors": scan.scan_errors,
    }


def build_manifest(
    args: argparse.Namespace,
    occurrences: Sequence[JarOccurrence],
    scans: Mapping[str, JarScan],
    catalog: Mapping[tuple[str, str], CatalogEntry],
    work_items: Sequence[WorkItem],
    translations: Mapping[tuple[str, str], str] | None = None,
    failures: Sequence[Mapping[str, Any]] = (),
    cache_hits: int = 0,
    final_status: str = "audit_only",
) -> dict[str, Any]:
    active_occurrences = [item for item in occurrences if item.scope == "active"]
    disabled_occurrences = [item for item in occurrences if item.scope == "disabled"]
    active_hashes = {item.sha256 for item in active_occurrences}
    disabled_hashes = {item.sha256 for item in disabled_occurrences}
    filename_hashes: dict[str, set[str]] = collections.defaultdict(set)
    namespace_hashes: dict[str, set[str]] = collections.defaultdict(set)
    for item in occurrences:
        filename_hashes[item.filename.casefold()].add(item.sha256)
    for sha256, scan in scans.items():
        for namespace in scan.namespaces:
            namespace_hashes[namespace].add(sha256)
    duplicate_hashes = {
        sha256: sorted(str(item.path) for item in scan.occurrences)
        for sha256, scan in scans.items()
        if len(scan.occurrences) > 1
    }
    name_collisions = {
        name: sorted(hashes)
        for name, hashes in filename_hashes.items()
        if len(hashes) > 1
    }
    namespace_providers = {
        namespace: sorted(hashes) for namespace, hashes in sorted(namespace_hashes.items())
    }
    source_conflicts = sum(bool(entry.source_conflicts) for entry in catalog.values())
    spanish_conflicts = sum(bool(entry.spanish_conflicts) for entry in catalog.values())
    valid_existing = sum(
        select_existing_spanish(entry) is not None for entry in catalog.values()
    )
    translations = translations or {}
    jar_rows = [describe_scan(scan) for _, scan in sorted(scans.items())]
    manifest = {
        "schema_version": 1,
        "generated_at": utc_now(),
        "tool": "Traductor-Mods-MiniMax",
        "prompt_version": PROMPT_VERSION,
        "mode": args.mode,
        "executed_api": bool(args.mode == "translate" and args.execute),
        "model": args.model,
        "status": final_status,
        "safety": {
            "jars_modified": False,
            "server_modified": False,
            "disabled_included_in_pack": bool(args.include_disabled),
            "secret_source": "MINIMAX_API_KEY only when API execution is explicit",
        },
        "inputs": {
            "active_mods_dirs": [str(path) for path in args.mods_dir],
            "disabled_mods_dirs": [str(path) for path in args.disabled_dir],
            "spanish_variants": list(args.spanish_variant),
        },
        "statistics": {
            "active_jar_occurrences": len(active_occurrences),
            "disabled_jar_occurrences": len(disabled_occurrences),
            "unique_active_jars_by_sha256": len(active_hashes),
            "unique_disabled_jars_by_sha256": len(disabled_hashes),
            "unique_disabled_only_jars_by_sha256": len(disabled_hashes - active_hashes),
            "unique_jars_total_by_sha256": len(scans),
            "active_jars_without_lang": sum(
                1
                for sha256 in active_hashes
                if sha256 in scans and not scans[sha256].assets
            ),
            "disabled_only_jars_without_lang": sum(
                1
                for sha256 in disabled_hashes - active_hashes
                if sha256 in scans and not scans[sha256].assets
            ),
            "catalog_namespaces": len({entry.namespace for entry in catalog.values()}),
            "catalog_keys": len(catalog),
            "valid_existing_spanish_keys": valid_existing,
            "keys_requiring_model": len(work_items),
            "translated_or_cache_keys_this_run": len(translations),
            "cache_hits": cache_hits,
            "failed_batches": len(failures),
            "source_conflicting_keys": source_conflicts,
            "spanish_conflicting_keys": spanish_conflicts,
        },
        "deduplication": {
            "hash_duplicates": duplicate_hashes,
            "same_filename_different_hash": name_collisions,
            "namespace_providers": namespace_providers,
        },
        "jars": jar_rows,
        "namespaces": [],
        "failures": list(failures),
    }
    for namespace in sorted({identity[0] for identity in catalog}):
        entries = [entry for identity, entry in catalog.items() if identity[0] == namespace]
        manifest["namespaces"].append(
            {
                "namespace": namespace,
                "keys": len(entries),
                "existing_spanish": sum(
                    select_existing_spanish(entry) is not None for entry in entries
                ),
                "requires_model": sum(
                    entry_needs_translation(entry)
                    for entry in entries
                ),
                "source_conflicts": sum(bool(entry.source_conflicts) for entry in entries),
                "spanish_conflicts": sum(bool(entry.spanish_conflicts) for entry in entries),
            }
        )
    return manifest


def write_manifest(path: Path, manifest: Mapping[str, Any]) -> None:
    path = path.expanduser().resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Audita todos los idiomas de mods y, con confirmación explícita, "
            "crea un resource pack español mediante MiniMax-M3."
        )
    )
    parser.add_argument(
        "--mode",
        choices=("audit", "translate"),
        default="audit",
        help="audit es el modo seguro y predeterminado; translate prepara traducciones",
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="autoriza llamadas API; solo es válido junto con --mode translate",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="fuerza auditoría sin red; es equivalente al comportamiento predeterminado",
    )
    parser.add_argument(
        "--mods-dir",
        action="append",
        type=Path,
        help="directorio activo; puede repetirse (por defecto: torre e Isa)",
    )
    parser.add_argument(
        "--disabled-dir",
        action="append",
        type=Path,
        help="directorio mods-disabled adicional; puede repetirse",
    )
    parser.add_argument(
        "--no-discover-disabled",
        action="store_true",
        help="no descubre automáticamente el mods-disabled hermano",
    )
    parser.add_argument(
        "--include-disabled",
        action="store_true",
        help="incluye idiomas deshabilitados en el pack (nunca por defecto)",
    )
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--batch-size", type=int, default=40)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--timeout", type=float, default=120.0)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument(
        "--spanish-variant",
        action="append",
        help="variante a generar; puede repetirse",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="reemplaza únicamente --output-dir si la traducción termina completa",
    )
    return parser


def normalize_args(args: argparse.Namespace, parser: argparse.ArgumentParser) -> argparse.Namespace:
    if args.dry_run and args.execute:
        parser.error("--dry-run y --execute son incompatibles")
    if args.dry_run:
        args.mode = "audit"
    if args.execute and args.mode != "translate":
        parser.error("--execute solo se permite con --mode translate")
    if args.mode == "translate" and not args.execute:
        parser.error("--mode translate requiere --execute; sin ambos se conserva el dry-run")
    if args.batch_size < 1 or args.batch_size > 200:
        parser.error("--batch-size debe estar entre 1 y 200")
    if args.workers < 1 or args.workers > 32:
        parser.error("--workers debe estar entre 1 y 32")
    if args.timeout <= 0:
        parser.error("--timeout debe ser positivo")
    if args.retries < 0 or args.retries > 10:
        parser.error("--retries debe estar entre 0 y 10")
    args.mods_dir = [
        path.expanduser().resolve() for path in (args.mods_dir or DEFAULT_MODS_DIRS)
    ]
    explicit_disabled = [
        path.expanduser().resolve() for path in (args.disabled_dir or [])
    ]
    discovered = [] if args.no_discover_disabled else discover_disabled_dirs(args.mods_dir)
    args.disabled_dir = list(dict.fromkeys([*explicit_disabled, *discovered]))
    variants = args.spanish_variant or list(DEFAULT_SPANISH_VARIANTS)
    args.spanish_variant = list(
        dict.fromkeys(normalized_locale(locale) for locale in variants)
    )
    invalid_variants = [
        locale for locale in args.spanish_variant if not is_spanish_locale(locale)
    ]
    if invalid_variants:
        parser.error(
            "--spanish-variant solo admite variantes es_*: " + ", ".join(invalid_variants)
        )
    return args


def run(args: argparse.Namespace) -> int:
    occurrences = collect_occurrences(args.mods_dir, args.disabled_dir)
    if not any(item.scope == "active" for item in occurrences):
        raise TranslatorError("no se encontraron JAR activos")
    scans = scan_inventory(occurrences)
    catalog = build_catalog(scans, include_disabled=args.include_disabled)
    work_items = make_work_items(catalog)

    if args.mode == "audit":
        manifest = build_manifest(
            args,
            occurrences,
            scans,
            catalog,
            work_items,
            final_status="audit_only",
        )
        write_manifest(args.report, manifest)
        stats = manifest["statistics"]
        print(
            "Auditoría terminada: "
            f"{stats['active_jar_occurrences']} JAR activos, "
            f"{stats['unique_active_jars_by_sha256']} únicos por SHA-256, "
            f"{stats['catalog_keys']} claves y "
            f"{stats['keys_requiring_model']} pendientes de MiniMax."
        )
        print(f"Manifiesto: {Path(args.report).expanduser().resolve()}")
        return 0

    # La clave se consulta solamente después de inventariar y solo en ejecución explícita.
    api_key = os.environ.get("MINIMAX_API_KEY")
    if not api_key:
        raise TranslatorError(
            "Falta MINIMAX_API_KEY. No se leen archivos .env ni otras variables."
        )
    client = MiniMaxClient(
        api_key=api_key,
        model=args.model,
        timeout=args.timeout,
        retries=args.retries,
    )
    with TranslationCache(args.cache.expanduser().resolve()) as cache:
        translations, failures, cache_hits = translate_with_cache(
            work_items=work_items,
            cache=cache,
            client=client,
            model=args.model,
            batch_size=args.batch_size,
            workers=args.workers,
        )
    rendered, untranslated = render_language_maps(
        catalog,
        translations,
        args.spanish_variant,
    )
    if untranslated:
        failures = [
            *failures,
            {
                "error": "quedaron claves sin traducción validada",
                "count": len(untranslated),
                "keys": untranslated,
            },
        ]
    status = "complete" if not failures and not untranslated else "incomplete"
    manifest = build_manifest(
        args,
        occurrences,
        scans,
        catalog,
        work_items,
        translations=translations,
        failures=failures,
        cache_hits=cache_hits,
        final_status=status,
    )
    write_manifest(args.report, manifest)
    if status != "complete":
        print(
            "Traducción incompleta; los aciertos quedaron en caché para reanudar. "
            "No se publicó un resource pack parcial.",
            file=sys.stderr,
        )
        print(f"Manifiesto: {Path(args.report).expanduser().resolve()}", file=sys.stderr)
        return 2
    write_resource_pack(
        output=args.output_dir,
        rendered=rendered,
        embedded_manifest=manifest,
        mods_dirs=[*args.mods_dir, *args.disabled_dir],
        overwrite=args.overwrite,
    )
    print(f"Resource pack completo: {Path(args.output_dir).expanduser().resolve()}")
    print(f"Manifiesto: {Path(args.report).expanduser().resolve()}")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = normalize_args(parser.parse_args(argv), parser)
    try:
        return run(args)
    except TranslatorError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("Interrumpido; las traducciones ya validadas permanecen en caché.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
