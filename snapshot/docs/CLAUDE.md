# Contexto de RPG Dos Almas para Claude

Este archivo es la entrada rápida para Claude Code. Antes de actuar, leer
completo `AGENTS.md`; sus reglas prevalecen. Después leer:

1. `README.md`
2. `Documentacion/ESTADO-Y-TRASPASO.md`
3. `Documentacion/DOCKER-ACCESOS.md`
4. `Servidor/RPG-Dos-Almas/BACKUPS.md`
5. `Documentacion/CREDENCIALES-Y-ACCESOS.md`

## Objetivo del entorno

RPG Dos Almas es un modpack y servidor privado de Minecraft 1.20.1 con Forge
47.4.20. La torre ejecuta el servidor y un cliente potente; la distribución de
Isa contiene un cliente equivalente, hiperoptimizado para un portátil con GPU
integrada, además de un instalador amigable de Windows.

Todo vive bajo `/home/stev/Minecraft`. No recrear copias operativas dispersas en
`/home/stev`.

## Estado actual

- Servidor público: `minecraft.stevenvallejo.com:25565`.
- Unidad principal: `rpg-dos-almas-server.service`.
- Túnel: `rpg-dos-almas-tunnel.service`.
- Respaldos: `rpg-dos-almas-backup.timer`.
- Máximo de jugadores: 20.
- Cliente y distribución de Isa sincronizados.
- Idioma del juego: `es_es`.
- Instalador Windows: versión 1.2.0, probado como NSIS y con SHA-256.
- Traducción profunda instalada y validada; su contenido restrictivo no se
  publica.
- Compatibilidad propia `dos_almas_compat` 1.0.2 integra sed con mochilas y
  corrige la reflexión de MapSyncer.

## Rutas

```text
Cliente:      /home/stev/Minecraft/Cliente/RPG-Dos-Almas
Servidor:     /home/stev/Minecraft/Servidor/RPG-Dos-Almas
Mundo:        /home/stev/Minecraft/Servidor/RPG-Dos-Almas/Dos-Almas
Backups:      /home/stev/Minecraft/Respaldos/Mundos
Isa:          /home/stev/Minecraft/Distribucion/Isa-Windows/pack
Instalador:   /home/stev/Descargas/RPG-Dos-Almas-Instalador-Windows.exe
Git público:  /home/stev/Minecraft/Distribucion/GitHub/RPG-Dos-Almas-Traducciones
```

## Operación segura

- Hablar con el usuario en español.
- Usar siempre `systemctl --user` y `journalctl --user`.
- Antes de detener o reiniciar:

```bash
ss -Htn state established '( sport = :25565 )'
```

- Antes de cambiar mods, crear una copia coherente y sincronizar servidor,
  torre e Isa.
- No controlar teclado, ratón ni ventanas mientras el usuario use el equipo.
- No limpiar Docker ni tocar `/var/lib/docker`.
- No eliminar temporales, herramientas, snapshots o reparaciones sin auditoría.

## UI y cliente

Inventory Profiles Next conserva ordenamiento y atajos, pero sus controles
flotantes se ocultan donde se superponían:

- inventario normal de `E`;
- accesorios de Aether de `I`;
- Sophisticated Backpacks, que usa sus controles nativos.

No eliminar el menú de `I`: contiene ranuras distintas para anillos, guantes,
capa, colgantes y otros accesorios. El historial flotante de JEI permanece
desactivado.

## Traducciones y licencias

El resource pack privado contiene 4.800 archivos y el datapack 154. La
validación combinada parseó 4.422 JSON y `mcmeta` sin faltantes de prosa visible.
Los textos profundos derivados de mods restrictivos solo pueden vivir en los
clientes, instalador y respaldos privados. No copiarlos al repositorio público.

## Secretos

Los valores compartidos para el proyecto están en
`/home/stev/Minecraft/.env`, modo `0600`. Nunca mostrarlos en comandos, logs,
documentación, commits o respuestas. Usar las referencias descritas en
`Documentacion/CREDENCIALES-Y-ACCESOS.md`.

RCON, claves SSH y GitHub conservan sus almacenes propios; no duplicarlos en
Git ni en archivos de documentación.

## Publicación

Desde el repositorio público:

```bash
python3 tools/project_snapshot.py
python3 tools/verify_repository.py
python3 -m unittest discover -s tools/tests -v
python3 -m unittest discover -s tools/translator/tests -v
```

Después inspeccionar `git status`, el diff y la lista de archivos staged. El
repositorio solo admite trabajo propio, configuración sanitizada y manifiestos;
no admite mundos, jugadores, credenciales, JAR, EXE, ZIP, logs ni recursos
restrictivos.
