# Auditoría de traducciones de mods — 2026-07-24

Auditoría de solo lectura de los mods activos de la torre y de la distribución
de Isa-Windows. Los JAR no se modificaron. Las futuras traducciones deben
publicarse mediante el paquete de recursos externo, de modo que puedan
recargarse en el cliente con `F3 + T` sin reiniciar el servidor.

## Alcance

Rutas activas examinadas:

- Torre: `/home/stev/Minecraft/Cliente/RPG-Dos-Almas/mods`
- Isa-Windows:
  `/home/stev/Minecraft/Distribucion/Isa-Windows/pack/mods`

Resultados del inventario:

- 118 JAR activos en la torre.
- 115 JAR activos en Isa-Windows.
- 113 nombres compartidos; sus archivos son byte-idénticos.
- 120 nombres de JAR únicos en la unión activa.
- 99 JAR contienen recursos de idioma, repartidos en 102 namespaces.
- 21 JAR no contienen recursos bajo `/lang/`.
- Los 120 JAR superaron la comprobación ZIP/CRC.
- Los directorios `mods-disabled` se auditaron por separado y no se mezclaron
  con las cifras activas.

Mods exclusivos de Isa-Windows:

- `ObsidianUI-forge-0.2.3+mc1.20.1.jar`
- `RyoamicLights-forge-0.2.3+mc1.20.1.jar`

Mods exclusivos de la torre:

- `entity_model_features-3.2.4-1.20.1-forge.jar`
- `entity_texture_features_1.20.1-forge-7.1.jar`
- `oculus-mc1.20.1-1.8.0.jar`
- `sound-physics-remastered-forge-1.20.1-1.5.1.jar`
- `tl_skin_cape_forge_1.20-1.38.jar`

## Cobertura global

Los conteos usan claves únicas por archivo después de aplicar la semántica
normal de JSON: si una clave está repetida, prevalece su última aparición.

| Idioma | Namespaces válidos | Claves presentes | Faltantes frente a EN | Extras | Idénticas a EN |
|---|---:|---:|---:|---:|---:|
| `en_us` | 102 | 30.360 | — | — | — |
| `es_es` | 54 | 15.401 | 16.189 | 1.230 | 931 |
| `es_mx` | 34 | 9.154 | 21.463 | 257 | 650 |
| `es_ar` | 22 | 6.024 | 24.690 | 354 | 1.909 |
| `es_cl` | 11 | 3.271 | 27.189 | 100 | 404 |
| `es_ec` | 8 | 2.180 | 28.270 | 90 | 234 |
| `es_uy` | 5 | 865 | 29.504 | 9 | 204 |
| `es_ve` | 5 | 865 | 29.504 | 9 | 204 |

La torre usa `es_mx`. Su cobertura efectiva actual es solo de
aproximadamente 29,3 %. No se debe asumir un fallback de `es_mx` a `es_es`:
las claves ausentes pueden terminar mostrándose desde `en_us`. El paquete debe
generar explícitamente los códigos de idioma que se quieran soportar.

## Volumen candidato para traducción

Reutilizando primero cualquier traducción no idéntica disponible en
`es_es`, `es_mx`, `es_ar`, `es_cl`, `es_ec`, `es_uy` o `es_ve`:

- 14.849 claves no tienen ninguna traducción española.
- 977 claves solo tienen valores exactamente iguales al inglés.
- Quedan 15.826 claves candidatas para generación o revisión.
- Esas claves representan 14.408 textos fuente ingleses únicos.

Para completar directamente `es_mx`, sin aprovechar antes las demás
variantes:

- 21.463 claves están ausentes.
- 650 valores son exactamente iguales al inglés.
- Total: 22.113 claves candidatas, equivalentes a 20.049 textos fuente únicos.

Un valor idéntico es un candidato de revisión, no un error automático:
nombres propios, siglas y ciertos nombres de objetos pueden ser correctos sin
cambios.

## Prioridades para `es_mx`

`Candidatos` es la suma de claves faltantes y valores idénticos al inglés.

| Prioridad | Namespace | Claves EN | Faltantes | Idénticas | Candidatos |
|---:|---|---:|---:|---:|---:|
| 1 | `alexscaves` | 1.712 | 1.712 | 0 | 1.712 |
| 2 | `runicskills` | 1.989 | 1.414 | 197 | 1.611 |
| 3 | `ars_nouveau` | 1.532 | 1.532 | 0 | 1.532 |
| 4 | `simplyswords` | 1.531 | 1.531 | 0 | 1.531 |
| 5 | `twilightforest` | 1.488 | 1.488 | 0 | 1.488 |
| 6 | `hexerei` | 1.053 | 1.053 | 0 | 1.053 |
| 7 | `born_in_chaos_v1` | 1.022 | 1.022 | 0 | 1.022 |
| 8 | `apotheosis` | 1.014 | 1.014 | 0 | 1.014 |
| 9 | `regions_unexplored` | 836 | 836 | 0 | 836 |
| 10 | `meed` | 619 | 619 | 0 | 619 |
| 11 | `cataclysm` | 735 | 406 | 143 | 549 |
| 12 | `undergarden` | 548 | 548 | 0 | 548 |
| 13 | `inventoryprofilesnext` | 519 | 519 | 0 | 519 |
| 14 | `xaerominimap` | 646 | 513 | 3 | 516 |
| 15 | `openpartiesandclaims` | 501 | 501 | 0 | 501 |
| 16 | `irons_spellbooks` | 1.320 | 480 | 17 | 497 |
| 17 | `relics` | 403 | 403 | 0 | 403 |
| 18 | `mowziesmobs` | 440 | 390 | 6 | 396 |
| 19 | `xaeroworldmap` | 317 | 317 | 0 | 317 |
| 20 | `deeperdarker` | 295 | 295 | 0 | 295 |

Los dos namespaces exclusivos de Isa-Windows también están incluidos:

- `obsidianui`: 10 claves EN; falta 1 en `es_mx`.
- `ryoamiclights`: 21 claves EN; faltan 5 en `es_mx`.

## Mods activos sin recursos `/lang/`

Estos 21 JAR están contabilizados, pero no ofrecen un `en_us.json` desde el
que generar traducciones:

1. `amber-forge-1.20.1-1.3.0-beta.2+1.20.1.jar`
2. `appleskin-forge-mc1.20.1-2.5.1.jar`
3. `architectury-9.2.14-forge.jar`
4. `BetterThirdPerson-Forge-1.20-1.9.0.jar`
5. `dungeons-and-taverns-3.0.3.jar`
6. `ferritecore-6.0.1-forge.jar`
7. `GlitchCore-forge-1.20.1-0.0.1.1.jar`
8. `Iceberg-1.20.1-forge-1.1.25.jar`
9. `ImmediatelyFast-Forge-1.5.5+1.20.4.jar`
10. `Kambrik-6.1.1+1.20.1-forge.jar`
11. `kotlinforforge-4.12.0-all.jar`
12. `LegendaryTooltips-1.20.1-forge-1.4.5.jar`
13. `memoryleakfix-forge-1.17+-1.1.4.jar`
14. `OctoLib-FORGE-0.5.0.1+1.20.1.jar`
15. `player-animation-lib-forge-1.0.2-rc1+1.20.jar`
16. `Prism-1.20.1-forge-1.0.5.jar`
17. `resourcefulconfig-forge-1.20.1-2.1.3.jar`
18. `resourcefullib-forge-1.20.1-2.1.29.jar`
19. `terrablenderfix-forge-1.20.1-0.0.1.jar`
20. `tl_skin_cape_forge_1.20-1.38.jar`
21. `YungsApi-1.20-Forge-4.0.6.jar`

Muchos son bibliotecas o mejoras de rendimiento y posiblemente no muestran
texto. Para mantener cobertura sin excepciones deben constar en el manifiesto
como `NO_LANG` y someterse a una prueba visual para detectar cadenas creadas
directamente desde código, configuraciones o contenido distinto de
`assets/*/lang/*.json`.

## JSON de idioma inválidos

Se encontraron 12 archivos sintácticamente inválidos:

| JAR o namespace | Archivo inválido |
|---|---|
| `alexsmobs` | `assets/alexsmobs/lang/sv_se.json` |
| `bettercombat` | `assets/bettercombat/lang/ja_jp.json` |
| `entity_texture_features` | `assets/entity_texture_features/lang/fr_fr.json` |
| `jeresources` | `assets/jeresources/lang/zh_tw.json` |
| `cataclysm` | `assets/cataclysm/lang/fr_fr.json` |
| `mowziesmobs` | `assets/mowziesmobs/lang/pt_br.json` |
| `mowziesmobs` | `assets/mowziesmobs/lang/ru_ru.json` |
| `twilightforest` | `assets/twilightforest/lang/es_ar.json` |
| `twilightforest` | `assets/twilightforest/lang/es_cl.json` |
| `twilightforest` | `assets/twilightforest/lang/es_ec.json` |
| `twilightforest` | `assets/twilightforest/lang/es_mx.json` |
| `twilightforest` | `assets/twilightforest/lang/ja_jp.json` |

Ningún `en_us` ni `es_es` está afectado. El `es_mx` inválido de
`twilightforest` se trató como ausente. Además, 104 archivos de idioma
contienen claves JSON repetidas; no son errores sintácticos, pero el generador
debe emitir cada clave una sola vez.

## `mods-disabled`, separados

Hay seis JAR en cada directorio `mods-disabled`, con ocho nombres únicos.
Cinco están inactivos en ambas distribuciones:

| JAR | Estado de idioma |
|---|---|
| `DistantHorizons-3.2.0-b-1.20.1-fabric-forge.jar` | 471 claves EN; sin `es_mx` ni `es_es` |
| `enlightend-5.0.14-1.20.1.jar` | 547 claves EN; sin `es_mx` ni `es_es` |
| `questmod-1.0.0.jar` | 31 EN; 18 en `es_es`, todas idénticas; sin `es_mx` |
| `entitymodelshaderfix-forge-1.1.0+mc1.20.1.jar` | Sin recursos `/lang/` |
| `xaeromapsync-0.0.1.jar` | Sin recursos `/lang/` |

No se deben agregar sus idiomas al paquete mientras continúen inactivos.

Otros tres nombres aparecen deshabilitados en una distribución pero activos
en la otra: `ImmediatelyFast`, `Oculus` y `Sound Physics`. Sus copias
deshabilitadas son byte-idénticas a las activas, por lo que sus namespaces ya
se contabilizaron una sola vez dentro de la unión activa.

## Método reproducible

Inventario de nombres activos:

```bash
find Cliente/RPG-Dos-Almas/mods \
     Distribucion/Isa-Windows/pack/mods \
     -maxdepth 1 -type f -iname '*.jar' -printf '%f\n' |
sort -u
```

Proceso aplicado a cada nombre único:

1. Comparar el SHA-256 de nombres compartidos y auditar por separado cualquier
   archivo cuyo contenido difiera.
2. Validar el JAR con `zipfile.ZipFile(...).testzip()`.
3. Localizar rutas que coincidan exactamente con
   `^assets/([^/]+)/lang/([^/]+)\.json$`.
4. Decodificar como UTF-8 con BOM opcional y validar con `json.loads`.
5. Exigir un objeto JSON con claves y valores de texto.
6. Comparar las claves de cada idioma con `en_us`:

```python
faltantes = en_us.keys() - idioma.keys()
extras = idioma.keys() - en_us.keys()
identicas = {
    clave
    for clave in en_us.keys() & idioma.keys()
    if en_us[clave] == idioma[clave]
}
```

7. Tratar un JSON inválido como no disponible.
8. Auditar `mods-disabled` en una colección independiente.
9. Antes de publicar, validar que se conservaron sin cambios marcadores como
   `%s`, `%1$s`, `%d`, llaves, códigos de formato, saltos de línea y escapes.

## MiniMax M3 y credenciales

Esta auditoría no ejecutó traducciones ni realizó llamadas externas. La fase
masiva con MiniMax M3 no debe comenzar hasta:

1. Usar el identificador oficial confirmado `MiniMax-M3` mediante la API
   compatible con OpenAI en `https://api.minimax.io/v1/chat/completions`.
2. Proporcionar una clave válida mediante el almacén de secretos o una variable
   de entorno del proceso; nunca escribirla en este documento, el repositorio,
   los JAR, los registros ni el paquete final.
3. Ejecutar primero un lote piloto con validación automática de claves,
   marcadores y JSON, y solo después ampliar la concurrencia.

MiniMax documenta M3 y su acceso compatible con OpenAI en:

- <https://www.minimax.io/models/text/m3>
- <https://platform.minimax.io/docs/api-reference/text-chat-openai>

El delegador integrado de esta sesión no ofrece MiniMax entre sus modelos.
Sin una credencial disponible de forma segura, puede preparar lotes y
validaciones, pero no completar la traducción remota con M3.

## Estado de publicación posterior a la auditoría

Actualización local del 24 de julio de 2026:

- Idioma configurado en torre e Isa: `es_es`.
- Catálogo activo: 33.694 claves en 102 namespaces.
- Paquete publicado: 85 namespaces con `es_es`.
- Textos españoles distintos del inglés: 20.687 (61,40 % del catálogo).
- Claves todavía ausentes: 11.547.
- Valores iguales al inglés que requieren revisión humana: 1.460.
- Tareas delegadas válidas incorporadas: 5.661.
- Tareas delegadas inválidas: 0.
- Tareas que todavía necesitan traducción: 10.375.

Runic Skills tiene 1.998 claves cubiertas: 1.978 son traducciones distintas del
inglés y 20 conservan formatos, abreviaturas o nombres propios como `CON`,
`INT`, `MAG`, `Gourmet`, `Ender` y nombres de mods. No le faltan claves.

El paquete validado se instaló y quedó sincronizado en:

- cliente de la torre;
- distribución de Isa-Windows;
- portátil Windows en `F:\RPG-Dos-Almas`.

Esta publicación sigue siendo parcial. No debe describirse como traducción
profunda completa de todos los mods. Los clientes pueden cargarla reiniciando
el juego o mediante `F3+T`; el servidor no necesita reiniciarse por los
archivos de idioma.
