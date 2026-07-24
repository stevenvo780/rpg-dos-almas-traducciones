# RPG Dos Almas

Respaldo reproducible y privado del proyecto **RPG Dos Almas**, basado en
Minecraft 1.20.1 y Forge 47.4.20. Guarda la configuración operativa, los
perfiles de cliente, la automatización del servidor, las fuentes del instalador,
los manifiestos de dependencias y el proyecto completo de traducción.

El repositorio no intenta sustituir los respaldos del mundo. Su objetivo es
evitar que se pierda el trabajo propio y permitir reconstruir el entorno sin
redistribuir Minecraft, Forge, mods, launchers ni recursos de terceros.

## Qué contiene

- `snapshot/server`: configuración, parámetros de Java, scripts y unidades
  systemd del servidor.
- `snapshot/client/tower`: configuración y opciones del cliente potente.
- `snapshot/client/portable`: perfil hiperoptimizado para GPU integrada.
- `snapshot/distribution`: fuentes del instalador de Windows y guías de uso.
- `snapshot/modpack`: manifiestos Modrinth/Packwiz y constructor reproducible.
- `snapshot/manifests`: inventarios SHA-256 de mods y contenido deliberadamente
  excluido.
- `resourcepack`: overlay de traducción para uso privado.
- `tools/translator`: auditor, validadores y constructor de traducciones.
- `tools/project_snapshot.py`: regenera el snapshot desde el entorno canónico.
- `tools/verify_repository.py`: comprueba que no entren binarios prohibidos ni
  secretos evidentes.
- `docs`: estado, recuperación, auditorías y decisiones operativas.

## Lo que no contiene

No se suben mundos, backups, JAR, instaladores de Forge, Minecraft, TLauncher,
shaders, resource packs de terceros, bibliotecas descargadas, cachés, logs,
capturas, datos de jugadores ni credenciales. Los manifiestos conservan nombres
y hashes para poder auditar qué debe obtenerse de su fuente legítima.

Consulta [`docs/COPYRIGHT-Y-EXCLUSIONES.md`](docs/COPYRIGHT-Y-EXCLUSIONES.md) y
[`docs/RECUPERACION.md`](docs/RECUPERACION.md) antes de reconstruir o compartir
el proyecto.

## Traducción

- 16.036 tareas validadas y 0 faltantes.
- 102 namespaces.
- Variantes `es_es`, `es_mx`, `es_ar`, `es_cl`, `es_ec`, `es_uy` y `es_ve`.
- Los JAR originales nunca se modifican.

Para aplicarla, copia `resourcepack/RPG-Dos-Almas-Espanol` a `resourcepacks`,
actívala y pulsa `F3 + T` o reinicia el cliente.

## Actualizar el respaldo

Desde la raíz del repositorio:

```bash
python3 tools/project_snapshot.py
python3 tools/verify_repository.py
python3 -m unittest discover -s tools/tests -v
python3 -m unittest discover -s tools/translator/tests -v
```

El generador solo lee `/home/stev/Minecraft` y vuelve a crear `snapshot`.
