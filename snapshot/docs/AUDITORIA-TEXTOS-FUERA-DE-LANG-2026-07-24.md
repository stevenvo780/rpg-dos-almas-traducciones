# Auditoría de textos fuera de `assets/*/lang`

Fecha: `2026-07-24`

Esta auditoría identifica textos que el jugador puede ver pero que no están en
los archivos de idioma convencionales de los mods. Se realizó en modo de solo
lectura sobre:

- Cliente canónico: `/home/stev/Minecraft/Cliente/RPG-Dos-Almas`
- Servidor canónico: `/home/stev/Minecraft/Servidor/RPG-Dos-Almas`
- Distribución de Isa:
  `/home/stev/Minecraft/Distribucion/Isa-Windows/pack`

Cobertura:

- 118 JAR en el cliente.
- 91 JAR en el servidor, incluidos cuatro JAR exclusivos del servidor.
- 115 JAR en la distribución de Isa, incluidos dos JAR exclusivos de esa
  distribución.
- 7.752 plantillas de estructuras NBT.
- FTB Quests, Patchouli y otros libros, configuraciones JSON/JSON5/TOML/CFG,
  datapacks, funciones y posibles directorios de scripts.

No se modificó ningún JAR, archivo de configuración, mundo ni distribución
durante la auditoría.

## Resumen de aplicación

| Grupo | Aplicación | Reinicio |
|---|---|---|
| Idiomas normales y libros alojados en `assets` | Paquete de recursos y `F3+T` | No |
| Datos JSON, libros alojados en `data` y estructuras NBT | Datapack y `/reload` | No, con las limitaciones indicadas |
| FTB Quests | `/ftbquests reload` | Normalmente no |
| `config/apotheosis/names.cfg` | Recarga del servidor no confirmada | Sí, recomendado |
| Metadata `META-INF/mods.toml` | No admite override por resource pack/datapack | Sí, si se alterara el JAR; no recomendado |
| Texto ya generado dentro del mundo | Edición del mundo existente | No se corrige con una recarga |

## 1. Libros Patchouli

Se encontraron cuatro libros Patchouli sin contenido en español. Todos usan
contenido inglés alojado en `assets`, por lo que sus árboles localizados
`es_mx` se pueden distribuir mediante el paquete de recursos
`RPG-Dos-Almas-Espanol`.

| Mod y libro | Ruta interna inglesa | JSON | Campos traducibles | Caracteres |
|---|---|---:|---:|---:|
| Apotheosis — Chronicle of Shadows | `assets/apotheosis/patchouli_books/apoth_chronicle/en_us` | 133 | 510 | 52.108 |
| Ars Nouveau — Worn Notebook | `assets/ars_nouveau/patchouli_books/worn_notebook/en_us` | 281 | 659 | 21.091 |
| Iron's Spells — Guide Book | `assets/irons_spellbooks/patchouli_books/iss_guide_book/en_us` | 10 | 93 | 8.762 |
| Simply Swords — Runic Grimoire | `assets/simplyswords/patchouli_books/runic_grimoire/en_us` | 81 | 228 | 31.310 |
| **Total** |  | **505** | **1.490** | **113.271** |

Hay 292 campos con marcado especial. La traducción debe preservar exactamente:

- Marcadores Patchouli como `$(...)`, enlaces y saltos `$(br)`/`$(br2)`.
- Identificadores `namespace:id`.
- Variables de formato y cantidades.
- Nombres de archivos y estructura JSON.

Aplicación:

1. Crear los mismos árboles bajo `es_mx` dentro del paquete de recursos.
2. Activar o actualizar el paquete en cada cliente.
3. Pulsar `F3+T`.
4. Cerrar y volver a abrir el libro.

No hace falta reiniciar el servidor para esos 505 JSON.

### Metadata literal de dos libros

Además del contenido localizado, estos archivos bajo `data` contienen cuatro
literales ingleses: nombre y texto de portada de cada libro.

- `data/irons_spellbooks/patchouli_books/iss_guide_book/book.json`
- `data/simplyswords/patchouli_books/runic_grimoire/book.json`

No se deben modificar los JAR. Se pueden sobreescribir con un datapack que
mantenga intactos todos los campos técnicos y traduzca únicamente `name` y
`landing_text`. Se aplican con `/reload`; conviene reabrir el libro y, si el
cliente conserva el contenido anterior, reconectar.

Los nombres y portada de Apotheosis y Ars Nouveau en sus `book.json` son claves
de idioma, no texto directo.

## 2. Libros de Alex's Mobs y Alex's Caves

### Alex's Mobs

El diccionario de animales ya incluye 91 de 91 archivos en español de México:

```text
assets/alexsmobs/book/animal_dictionary/es_mx
```

No debe retraducirse. Sus 91 JSON estructurales usan claves de idioma o
identificadores técnicos y no contienen texto directo que requiera traducción.

### Alex's Caves

El compendio de Alex's Caves contiene:

- 68 TXT ingleses.
- 76.409 caracteres en inglés.
- 52 TXT ya existentes en `es_es`.
- 0 TXT en `es_mx`.
- 16 TXT sin ninguna versión española, que suman 21.230 caracteres en inglés.

Ruta inglesa:

```text
assets/alexscaves/books/en_us
```

Los 16 archivos faltantes en `es_es` son:

```text
candy_cavity/candicorn.txt
candy_cavity/caniac.txt
candy_cavity/caramel_cube.txt
candy_cavity/chapter.txt
candy_cavity/general.txt
candy_cavity/gingerbread_man.txt
candy_cavity/gum_worm.txt
candy_cavity/gumbeeper.txt
candy_cavity/gummy_bear.txt
candy_cavity/licowitch.txt
candy_cavity/resources.txt
candy_cavity/sweetish_fish.txt
candy_cavity/utilities.txt
primordial_caves/atlatitan.txt
primordial_caves/luxtructosaurus.txt
toxic_caves/tremorzilla.txt
```

Para `es_mx` deben prepararse los 68 TXT: adaptar los 52 `es_es` existentes y
traducir los 16 faltantes desde `en_us`. Los 87 JSON estructurales del libro no
contienen literales visibles y no deben traducirse.

Aplicación: paquete de recursos, `F3+T` y reapertura del libro. No requiere
reinicio.

## 3. Guía propia de Hexerei

Hexerei no usa Patchouli para esta guía. Su contenido estructural está bajo:

```text
data/hexerei/book
```

Resultados:

- 6 nombres literales de capítulos en `book_entries.json`.
- 287 páginas referenciadas y existentes.
- 518 campos de texto que ya son claves de idioma; deben resolverse en
  `assets/hexerei/lang`, no modificarse en los JSON del libro.
- 27 tooltips ingleses directos dentro de las páginas.
- Total fuera de `lang` que sí debe traducirse: 33 campos.

Los valores `showTitle: "none"` son controles y no son texto traducible.
`showTitle: "Hexerei"` es un nombre propio.

Los 33 campos se pueden sobreescribir mediante datapack. Aplicación:
`/reload` y reapertura del libro; puede ser necesaria una reconexión si el
cliente mantiene páginas en caché.

## 4. Otros recursos del cliente

### Loot Journal

En:

```text
assets/loot_journal/pickups/themes/*.json
```

hay 11 literales:

- 4 `display_name`.
- 7 `description`.

Se pueden sobreescribir en el paquete de recursos. Aplicación: `F3+T` y volver
a abrir la pantalla del mod.

### Libro de ejemplo de Citadel

Se detectaron dos literales bajo:

```text
assets/citadel/book/citadel_book
```

Son contenido de ejemplo y probablemente no aparecen durante una partida
normal. Si se decide cubrirlos de todos modos, se pueden sobreescribir con el
paquete de recursos y `F3+T`.

## 5. Avances y otros datos JSON

### Avances con texto directo

Se inspeccionaron las definiciones de avances de los JAR. Se encontraron 90
literales ingleses que no usan claves de idioma:

- Simply Swords: 88 campos, correspondientes a título y descripción de 44
  avances.
- Dungeons & Taverns: 2 campos, título y descripción de un avance.

Los demás avances inspeccionados usan objetos `translate` y deben resolverse
mediante los archivos de idioma normales.

Los 90 campos se pueden sobreescribir mediante datapack conservando sin cambios
criterios, requisitos, padres, iconos, identificadores y recompensas. Aplicación:
`/reload`. Los avances ya concedidos conservan su progreso; la presentación de
la interfaz debe usar la definición recargada.

### Nombres directos en datos

Se encontraron 51 nombres directos:

| Origen | Cantidad | Tipo |
|---|---:|---|
| Ars Nouveau | 23 | Nombres de tomos/hechizos predefinidos |
| Dungeons & Taverns | 23 | Nombres de mapas generados en botín |
| Apotheosis | 4 | Nombres de minibosses |
| YUNG's Better Strongholds | 1 | Nombre de espada en botín |

Se pueden sobreescribir mediante datapack y aplicar con `/reload`. La recarga
solo afecta las generaciones posteriores. Los nombres ya guardados en NBT de
ítems o entidades existentes no cambian.

## 6. FTB Quests

Las misiones canónicas están en:

```text
/home/stev/Minecraft/Servidor/RPG-Dos-Almas/config/ftbquests/quests
```

Se inspeccionaron:

- 13 capítulos.
- 201 campos visibles entre `title`, `description` y `lock_message`.

Estos 201 campos ya están en español. No deben enviarse a traducción automática.
Tampoco deben traducirse:

- IDs de misiones, tareas o recompensas.
- Dependencias.
- Nombres de archivo.
- Tipos, identificadores de ítems y entidades.
- Coordenadas, condiciones o lógica.

Si se editan en el futuro, la recarga específica recomendada es:

```text
/ftbquests reload
```

Si la versión instalada no acepta el comando, habrá que usar su mecanismo de
edición/sincronización o reiniciar de forma controlada. Los SNBT de progreso de
jugadores en el mundo contienen estado e IDs, no traducciones, y no deben
alterarse.

## 7. Estructuras NBT

Se auditaron 7.752 archivos `.nbt`. En 144 archivos se detectaron 258
componentes de texto literales:

| Mod | Componentes |
|---|---:|
| When Dungeons Arise | 197 |
| L'Ender's Cataclysm | 18 |
| Hexerei | 18 |
| Dungeons & Taverns | 14 |
| Born in Chaos | 9 |
| Iron's Spells | 1 |
| The Aether | 1 |
| **Total** | **258** |

Los componentes aparecen en:

- Letreros.
- Libros escritos.
- `CustomName` y `BossName`.
- Nombres y lore de ítems.

Una parte corresponde a nombres propios, créditos o bromas del autor y debe
preservarse o revisarse manualmente. Las frases, rótulos, nombres comunes y
descripciones sí se pueden traducir.

Técnicamente es posible colocar copias traducidas de las plantillas NBT en un
datapack, conservando toda la estructura y cambiando únicamente los componentes
de texto. Se aplican con `/reload`, pero hay una limitación importante:

> Solo cambiarán las estructuras generadas después de la recarga.

Las estructuras que ya existen en el mundo contienen copias de esos textos. No
cambiarán con `F3+T`, `/reload`, reconexión ni reinicio. Corregirlas exigiría
editar el mundo existente, operación de mayor riesgo que no forma parte de esta
traducción.

## 8. Configuración de nombres de Apotheosis

Estos archivos contienen grupos de nombres visibles generados para bosses y
equipo:

```text
Cliente/RPG-Dos-Almas/config/apotheosis/names.cfg
Servidor/RPG-Dos-Almas/config/apotheosis/names.cfg
Distribucion/Isa-Windows/pack/config/apotheosis/names.cfg
```

Cada uno contiene 597 entradas:

| Grupo | Cantidad | Tratamiento |
|---|---:|---|
| Nombres completos | 335 | Nombres propios; preservar |
| Fragmentos combinables | 74 | Preservar salvo revisión humana |
| Prefijos | 29 | Traducibles |
| Sufijos | 79 | Traducibles |
| Raíces de objetos | 80 | Traducibles |

También existen dos formatos con `%s`, que deben conservar exactamente sus
variables:

- `Suffix Format`
- `Ownership Format`

Los tres archivos tienen contenido y hash diferentes aunque coinciden en
cantidad. No se debe copiar uno sobre otro. La traducción debe aplicar la misma
terminología a cada variante sin alterar grupos, claves, delimitadores,
variables ni listas técnicas.

Esta configuración no tiene una recarga en caliente confirmada. El procedimiento
seguro es:

1. Crear una copia coherente.
2. Aplicar y validar las tres variantes.
3. Reiniciar el servidor de forma controlada, comprobando antes conexiones a
   `25565`.
4. Reiniciar los clientes cuando se distribuya la configuración.

El cambio solo afectará nombres generados después; ítems y entidades existentes
con nombre guardado conservarán el anterior.

## 9. Campos que parecen texto pero son lógica

Los siguientes valores no deben traducirse:

- `TitleId` y condiciones de
  `config/RunicSkills/runicskills.titles.json5`.
- Nombres de habilidades dentro de condiciones de Runic Skills.
- Categorías de Xaero como `gui.xaero_entity_category_*`; son claves de idioma.
- `messages[].id` de Majrusz's Progressive Difficulty; son claves de idioma.
- `Freshah` en `custom_names`; es un nombre propio.
- Nombres de perfiles como `Default` cuando actúan como identificadores.
- Comandos de teletransporte y variables `{x}`, `{y}`, `{z}`, `{yaw}`, `{d}`.
- Identificadores `namespace:id`.
- Nombres de biomas, recetas, loot tables, tags y rutas.
- Claves JSON/SNBT/TOML/CFG.
- Tipos, valores de enumeración y formatos de color.

En los recursos de animación se detectaron 79 valores `author`/`description`
de Better Combat, Simply Swords y Combat Roll. Son metadata técnica de las
animaciones, no texto mostrado al jugador, y no deben traducirse.

También se detectaron 91 nombres ingleses de piezas internas de modelos. No se
muestran al jugador y no deben traducirse.

## 10. Metadata de los mods

En los JAR del cliente se detectaron:

- 117 `displayName`.
- 113 `authors`.
- 35 `description`.
- 265 campos de metadata en total.

Los cuatro JAR exclusivos del servidor —Clumps, Collective, Replanting Crops y
spark— solo aportan metadata fuera de `lang`; no contienen libros, avances,
funciones visibles ni estructuras NBT adicionales. Los dos JAR exclusivos de
la distribución —ObsidianUI y RyoamicLights— también solo aportan metadata.

Los autores y nombres propios no deben traducirse. Las descripciones sí son
texto visible en la lista de mods, pero `META-INF/mods.toml` no se puede
sobreescribir mediante un paquete de recursos ni un datapack. Modificar los JAR:

- Rompería su integridad.
- Complicaría las actualizaciones.
- Exigiría reiniciar el cliente.
- Tendría que repetirse después de cada actualización.

Por esas razones, las descripciones del listado de mods quedan documentadas como
excepción técnica y no se recomienda alterarlas.

## 11. Scripts, funciones y datapacks existentes

No se encontraron directorios activos de KubeJS, CraftTweaker ni otros scripts
de contenido en el cliente o servidor canónicos.

No se encontraron funciones `.mcfunction` con comandos visibles como `say`,
`tellraw`, `title` o equivalentes dentro de los 118 JAR del cliente.

El datapack activo:

```text
Servidor/RPG-Dos-Almas/Dos-Almas/datapacks/dos-almas-fixes
```

solo contiene una receta y no aporta texto visible.

## 12. Destinos de sincronización

El paquete de recursos español actual solo contiene una traducción de
Server Waypoint. Los recursos nuevos deben incorporarse primero a:

```text
/home/stev/Minecraft/Cliente/RPG-Dos-Almas/resourcepacks/RPG-Dos-Almas-Espanol
```

Después deben sincronizarse en:

```text
/home/stev/Minecraft/Distribucion/Isa-Windows/pack/resourcepacks/RPG-Dos-Almas-Espanol
```

Los overrides de contenido bajo `data` deben vivir en un datapack del mundo o
en otro mecanismo de datos del servidor, no dentro del resource pack.

Antes de cambiar mods o configuraciones se debe crear una copia coherente y
mantener sincronizados:

- Servidor.
- Cliente de la torre.
- Distribución de Isa.

## 13. Reglas para traducción masiva

Una traducción automática por lotes debe operar únicamente sobre una lista
blanca de campos. Para MiniMax u otro modelo:

1. Nunca traducir claves.
2. Nunca traducir IDs, rutas, namespaces, nombres de archivo ni comandos.
3. Preservar exactamente `%s`, formatos printf, `{variables}` y marcado
   Patchouli `$(...)`.
4. Preservar componentes JSON, colores, estilos y saltos.
5. Mantener nombres propios cuando no sean títulos comunes.
6. Validar que el número de archivos, claves y variables sea idéntico antes y
   después.
7. Validar todos los JSON y SNBT generados.
8. Verificar que el resource pack cargue sin errores con `F3+T`.
9. Probar el datapack con `/reload` y revisar el registro del servidor.
10. No prometer que textos ya grabados dentro del mundo cambiarán.

## Conclusión

La mayor parte de la traducción pendiente puede aplicarse sin reiniciar el
servidor:

- Resource pack y `F3+T` para Patchouli, Alex's Caves, Loot Journal y recursos
  del cliente.
- Datapack y `/reload` para avances, nombres de datos, Hexerei y futuras
  estructuras.

Las excepciones son la configuración generativa de nombres de Apotheosis, para
la que se recomienda reinicio controlado, la metadata interna de los JAR, que no
se recomienda modificar, y los textos ya generados en el mundo, que no cambian
con ninguna recarga.
