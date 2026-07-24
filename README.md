# RPG Dos Almas — traducción española de mods

Proyecto reproducible para auditar y completar las traducciones de los mods del
modpack **RPG Dos Almas**, basado en Minecraft 1.20.1 y Forge 47.4.20.

## Estado

- 16.036 tareas de traducción validadas.
- 0 tareas faltantes.
- 0 traducciones con tokens incompatibles.
- 102 namespaces incluidos en el resource pack.
- Variantes generadas: `es_es`, `es_mx`, `es_ar`, `es_cl`, `es_ec`, `es_uy`
  y `es_ve`.
- Los JAR originales nunca se modifican.

El informe final está en
[`data/validation-report.json`](data/validation-report.json). El resource pack
instalable está en
[`resourcepack/RPG-Dos-Almas-Espanol`](resourcepack/RPG-Dos-Almas-Espanol).

## Instalación

1. Copiar `resourcepack/RPG-Dos-Almas-Espanol` dentro de la carpeta
   `resourcepacks` de la instancia.
2. Activar **RPG Dos Almas Español** en el menú de paquetes de recursos.
3. Presionar `F3 + T` o reiniciar el cliente.

No se necesita reiniciar el servidor: los textos traducidos son recursos del
cliente.

## Contenido

- `tools/translator`: auditor, validadores, constructor del pack y
  orquestadores para MiniMax M3 y Gemini 3.5 Flash mediante Agy.
- `data/task-catalog.json`: catálogo portable con texto fuente, contexto y
  tokens inmutables.
- `data/validated-translations.json`: mapa final `id -> traducción`.
- `data/validation-report.json`: resultado de la validación integral.
- `resourcepack`: paquete final listo para Minecraft.
- `docs`: auditorías y notas operativas de la reparación.

## Validación

Desde `tools/translator`:

```bash
python3 -m unittest discover -s tests -v
```

Las pruebas cubren la lectura de idiomas JSON y `.lang`, deduplicación de JAR,
preservación de placeholders y códigos de formato, caché y generación de
variantes españolas.

## Seguridad

El repositorio no incluye mods JAR, mundos, respaldos, credenciales, tokens,
claves SSH, datos RCON ni logs de sesiones de los agentes.
