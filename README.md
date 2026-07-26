# RPG Dos Almas

Respaldo reproducible público del trabajo propio de **RPG Dos Almas**, basado en
Minecraft 1.20.1 y Forge 47.4.20. Guarda la configuración operativa, los
perfiles de cliente, la automatización del servidor, las fuentes del instalador,
los manifiestos de dependencias y las herramientas publicables de traducción.

El repositorio no intenta sustituir los respaldos del mundo. Su objetivo es
evitar que se pierda el trabajo propio y permitir reconstruir el entorno sin
redistribuir Minecraft, Forge, mods, launchers ni recursos de terceros. La
única imagen incluida es el icono 64×64 creado específicamente para este
proyecto.

## Qué contiene

- `snapshot/server`: configuración, icono original, parámetros de Java, scripts
  y unidades systemd del servidor.
- `snapshot/client/tower`: configuración y opciones del cliente potente.
- `snapshot/client/portable`: perfil hiperoptimizado para GPU integrada.
- `snapshot/distribution`: fuentes del instalador de Windows y guías de uso.
- `snapshot/modpack`: manifiestos Modrinth/Packwiz y constructor reproducible.
- `snapshot/custom`: código fuente de los módulos propios de compatibilidad y del
  launcher propio, sin JRE, dependencias ni salidas de compilación.
- `snapshot/server/infra`: definición reproducible del puente público.
- `snapshot/manifests`: inventarios SHA-256 de mods y contenido deliberadamente
  excluido.
- `snapshot/docs`: traspaso operativo, reglas para agentes, contexto para
  Claude y mapa de credenciales sin valores sensibles.
- `resourcepack`: capa publicable del overlay de traducción.
- `tools/translator`: auditor, validadores y constructor de traducciones.
- `tools/project_snapshot.py`: regenera el snapshot desde el entorno canónico.
- `tools/verify_repository.py`: comprueba que no entren binarios prohibidos ni
  secretos evidentes.
- `docs`: estado, recuperación, auditorías y decisiones operativas.

## Lo que no contiene

No se suben mundos, backups, JAR, instaladores de Forge, Minecraft, TLauncher,
shaders, resource packs de terceros, bibliotecas descargadas, cachés, logs,
capturas, datos de jugadores, `.env` ni credenciales. Los manifiestos conservan
nombres y hashes para poder auditar qué debe obtenerse de su fuente legítima.

Consulta [`docs/COPYRIGHT-Y-EXCLUSIONES.md`](docs/COPYRIGHT-Y-EXCLUSIONES.md) y
[`docs/RECUPERACION.md`](docs/RECUPERACION.md) antes de reconstruir o compartir
el proyecto.

Las instrucciones actuales están en
[`snapshot/docs/AGENTS.md`](snapshot/docs/AGENTS.md) y
[`snapshot/docs/CLAUDE.md`](snapshot/docs/CLAUDE.md). El mapa seguro de accesos
está en
[`snapshot/docs/CREDENCIALES-Y-ACCESOS.md`](snapshot/docs/CREDENCIALES-Y-ACCESOS.md).

## Traducción

- 16.036 tareas validadas y 0 faltantes.
- 102 namespaces.
- Variantes `es_es`, `es_mx`, `es_ar`, `es_cl`, `es_ec`, `es_uy` y `es_ve`.
- Los JAR originales nunca se modifican.

La instalación privada contiene además una capa profunda para libros y otros
textos alojados fuera de los diccionarios normales. Ese contenido derivado no
se publica cuando la licencia del mod no permite redistribuirlo; el repositorio
solo conserva su inventario de rutas y hashes.

Para aplicarla, copia `resourcepack/RPG-Dos-Almas-Espanol` a `resourcepacks`,
actívala y pulsa `F3 + T` o reinicia el cliente.

## Misiones

`snapshot/server/config/ftbquests` conserva el árbol de FTB Quests: **661
misiones en 34 capítulos**, casi todas con recompensa de objeto.

FTB Quests no detecta identificadores duplicados —gana el último cargado, sin
avisar— y los reasigna en cada recarga. Cualquier tanda nueva se valida antes de
copiarla al servidor y se respalda el árbol anterior. El procedimiento está en
[`docs/PARTIDA-NUEVA-2026-07-26.md`](docs/PARTIDA-NUEVA-2026-07-26.md).

## Actualizar el respaldo

Desde la raíz del repositorio:

```bash
python3 tools/project_snapshot.py
python3 tools/verify_repository.py
python3 -m unittest discover -s tools/tests -v
python3 -m unittest discover -s tools/translator/tests -v
```

El generador solo lee `/home/stev/Minecraft` y vuelve a crear `snapshot`.
