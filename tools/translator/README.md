# Traductor seguro de mods con MiniMax-M3

Esta herramienta audita la unión de mods activos de la torre y del paquete de
Isa, y puede crear un **resource pack** español sin modificar ningún JAR, mod,
cliente ni archivo del servidor.

El modo predeterminado es `audit`: no usa red, no requiere credenciales y no
crea un resource pack. Genera un manifiesto que incluye todos los JAR,
incluidos los que no tienen `assets/*/lang`, y separa los encontrados en
`mods-disabled`.

## Seguridad

- Solo lee los JAR como archivos ZIP.
- Nunca modifica JAR, `mods`, clientes ni `Servidor`.
- Nunca detiene o recarga el servidor.
- Los JAR de `mods-disabled` se auditan por separado y no entran en el pack,
  salvo que se indique expresamente `--include-disabled`.
- La única fuente admitida para el secreto es `MINIMAX_API_KEY`. No se leen
  `.env`, archivos de credenciales ni otras variables.
- La clave solo se consulta al usar simultáneamente `--mode translate
  --execute`; no se guarda ni se imprime.
- Toda respuesta se rechaza si cambia placeholders, códigos de formato,
  etiquetas, URLs o IDs `namespace:ruta`.
- Los aciertos validados se guardan en SQLite para reanudar; la caché no
  contiene la clave.
- Si falla una sola parte, no se publica un pack parcial.

## Auditoría recomendada

Desde este directorio:

```bash
python3 minimax_mod_translator.py
```

`--dry-run` es un alias explícito de este comportamiento seguro:

```bash
python3 minimax_mod_translator.py --dry-run
```

Los directorios activos predeterminados son:

```text
/home/stev/Minecraft/Cliente/RPG-Dos-Almas/mods
/home/stev/Minecraft/Distribucion/Isa-Windows/pack/mods
```

La herramienta descubre los `mods-disabled` hermanos para inventariarlos. El
manifiesto predeterminado queda en:

```text
reports/coverage-manifest.json
```

Contiene deduplicación y colisiones por:

- SHA-256: copias idénticas en torre e Isa se inspeccionan una vez.
- Nombre: se señalan nombres iguales con contenidos distintos.
- Namespace: se enumeran todos los JAR que aportan cada namespace.

Para auditar otra unión activa se repite `--mods-dir`:

```bash
python3 minimax_mod_translator.py \
  --mods-dir /ruta/cliente/mods \
  --mods-dir /ruta/distribucion/mods
```

También puede añadirse un directorio deshabilitado explícito:

```bash
python3 minimax_mod_translator.py \
  --mods-dir /ruta/mods \
  --disabled-dir /ruta/mods-disabled
```

## Traducción y reanudación

No se debe pegar la clave en el comando, el README ni archivos del mod. Debe
inyectarse en el entorno del proceso mediante un mecanismo de secretos. La
ejecución real exige las dos opciones deliberadas:

```bash
python3 minimax_mod_translator.py \
  --mode translate \
  --execute \
  --workers 4 \
  --batch-size 40
```

El endpoint está fijado en el código a la API oficial OpenAI-compatible de
MiniMax y el modelo predeterminado es `MiniMax-M3`. Cada petición fija
`thinking.type=disabled` para que el contenido sea JSON puro y comprobable.
`--workers` controla los lotes simultáneos y `--batch-size` su tamaño. En caso
de interrupción basta repetir el mismo comando: las respuestas ya validadas
salen de `cache/translations.sqlite3`.

No se acepta `--mode translate` sin `--execute`; así una orden incompleta nunca
consume API accidentalmente. No se ejecuta ninguna llamada durante pruebas
unitarias.

### Alternativa con agentes GPT delegados

Si no se usa la API de MiniMax, los lotes para agentes GPT se generan con:

```bash
python3 export_agent_tasks.py
```

Las tareas quedan separadas bajo
`Temporales/Traduccion-Agentes/tasks`. Los agentes escriben únicamente en
`Temporales/Traduccion-Agentes/translations`. Para medir el avance y rechazar
IDs, JSON o placeholders incorrectos:

```bash
python3 validate_agent_translations.py
```

El validador no publica nada mientras falte una sola tarea válida. Cuando el
informe alcance `complete`, la publicación todavía aislada se solicita con
`--publish-staging`. Ninguno de estos comandos modifica el cliente o el
servidor.

## Salida

Cuando todas las claves que lo requieren tienen traducción validada se crea:

```text
output/RPG-Dos-Almas-Traduccion-Completa/
```

Incluye archivos JSON para las variantes oficiales usadas por Minecraft:

```text
es_es, es_mx, es_ar, es_cl, es_ec, es_uy, es_ve
```

Puede repetirse `--spanish-variant` para seleccionar otras variantes `es_*`.
Los textos españoles existentes se conservan cuando son válidos y las claves
faltantes se completan desde el catálogo canónico, cuya prioridad es `en_us`,
`en_gb`, otros idiomas y, por último, español.

La salida es deliberadamente externa al cliente. Después de revisar el
manifiesto se puede copiar el directorio completo a `resourcepacks` y activar
o recargar recursos con `F3 + T`; eso no reinicia el servidor. La distribución
a los dos clientes se hace como una operación separada y respaldable.

Si la salida ya existe, la herramienta se detiene. `--overwrite` solo autoriza
reemplazar un pack hijo de
`Herramientas/Traductor-Mods-MiniMax/output`; nunca una ruta arbitraria. El
reemplazo es transaccional y conserva temporalmente el anterior hasta publicar
el nuevo. Se rechazan salidas dentro de `mods`, con extensión `.jar` o dentro
de `/home/stev/Minecraft/Servidor`.

## Pruebas

No requieren red ni paquetes externos:

```bash
python3 -m unittest discover -s tests -v
```

Las pruebas cubren extracción JSON/`.lang`, JAR sin idiomas, deduplicación de
directorios activos y deshabilitados, validación de tokens, caché y generación
de todas las variantes.
