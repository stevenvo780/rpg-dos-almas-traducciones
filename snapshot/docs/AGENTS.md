# Instrucciones para continuar este entorno

Antes de realizar cambios, leer completos:

1. `README.md`
2. `Documentacion/ESTADO-Y-TRASPASO.md`
3. `Documentacion/DOCKER-ACCESOS.md`
4. `Servidor/RPG-Dos-Almas/BACKUPS.md`
5. `Documentacion/CREDENCIALES-Y-ACCESOS.md`

`CLAUDE.md` contiene un resumen equivalente para Claude Code y otros modelos,
pero estas instrucciones son la autoridad operativa del proyecto.

Reglas operativas:

- Comunicarse con el usuario en español.
- Minecraft vive bajo `/home/stev/Minecraft`; no recrear las rutas antiguas en
  la raíz de `/home/stev`.
- El servidor y sus timers son unidades systemd de usuario. Usar siempre
  `systemctl --user` y `journalctl --user`.
- Antes de parar o reiniciar el servidor, comprobar conexiones al puerto 25565.
- Antes de cambiar mods, crear una copia coherente y mantener sincronizados
  servidor, cliente de la torre y distribución de Isa.
- No controlar el teclado, ratón o ventanas del usuario mientras esté usando el
  equipo, salvo petición explícita.
- Los secretos compartidos para este proyecto viven únicamente en
  `/home/stev/Minecraft/.env`, modo `0600`. Nunca imprimir, registrar, adjuntar,
  versionar ni copiar sus valores a documentación.
- No ejecutar la carga del `.env` con `set -x`, no pasarlo como argumento de
  proceso y no duplicar los secretos que ya viven en `.rcon-credentials`, el
  keyring de GitHub, `~/.ssh` o almacenes de secretos.
- No ejecutar limpiezas Docker, borrar volúmenes ni mover `/var/lib/docker` sin
  una tarea explícita y una auditoría independiente: la raíz está cerca del
  límite y varias capas contienen decenas de GB de estado.
- Preservar `Herramientas`, `Temporales`, `Respaldos`, `Snapshots` y
  `Archivo/Reparaciones` hasta identificar qué es prescindible.

Rutas canónicas:

- Cliente: `/home/stev/Minecraft/Cliente/RPG-Dos-Almas`
- Servidor: `/home/stev/Minecraft/Servidor/RPG-Dos-Almas`
- Mundo: `/home/stev/Minecraft/Servidor/RPG-Dos-Almas/Dos-Almas`
- Backups: `/home/stev/Minecraft/Respaldos/Mundos`
- TLauncher: `/home/stev/Minecraft/Launcher/TLauncher.jar`
- Wrapper: `/home/stev/.local/bin/tlauncher`
- Distribución de Isa:
  `/home/stev/Minecraft/Distribucion/Isa-Windows/pack`
- Instalador final:
  `/home/stev/Descargas/RPG-Dos-Almas-Instalador-Windows.exe`
- Repositorio público:
  `/home/stev/Minecraft/Distribucion/GitHub/RPG-Dos-Almas-Traducciones`

Estado funcional confirmado el 2026-07-24:

- Minecraft 1.20.1 con Forge 47.4.20.
- Servidor público: `minecraft.stevenvallejo.com:25565`.
- Servicio, túnel y timer de respaldos administrados como unidades de usuario.
- Máximo de 20 jugadores, lista blanca forzada y modo offline por compatibilidad
  con los clientes actuales.
- Cliente de la torre y distribución de Isa sincronizados en mods obligatorios,
  traducciones y reglas de UI.
- Traducción profunda instalada mediante el resource pack
  `RPG-Dos-Almas-Espanol` y el datapack
  `RPG-Dos-Almas-Traduccion-Profunda`.
- Instalador Windows 1.2.0 validado por SHA-256 y prueba integral de 7-Zip sobre
  5.153 archivos.

Reglas de Git y licencias:

- El repositorio de GitHub es público.
- Nunca versionar `.env`, credenciales, claves, mundos, backups, datos de
  jugadores, logs, capturas, JAR, ZIP, EXE ni otros binarios de terceros.
- Las traducciones profundas derivadas de mods con licencias restrictivas
  permanecen privadas. Git conserva solo documentación, herramientas propias,
  configuraciones permitidas y manifiestos de rutas, tamaños y hashes.
- Regenerar el snapshot con `python3 tools/project_snapshot.py`.
- Antes de publicar ejecutar:
  `python3 tools/verify_repository.py`,
  `python3 -m unittest discover -s tools/tests -v` y
  `python3 -m unittest discover -s tools/translator/tests -v`.
- Inspeccionar `git status`, el diff y el índice; publicar únicamente archivos
  intencionales.

Protocolo para cambios de servidor o mods:

1. Auditar el estado y las conexiones activas.
2. Crear una copia coherente siguiendo `Servidor/RPG-Dos-Almas/BACKUPS.md`.
3. Sincronizar servidor, torre e Isa cuando corresponda.
4. Validar hashes, JSON/TOML, arranque hasta `Done` y registros.
5. Recompilar y probar el instalador si cambia el cliente portátil.
6. Actualizar documentación y snapshot público sanitizado.
