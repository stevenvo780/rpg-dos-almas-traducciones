# Instrucciones para continuar este entorno

Antes de realizar cambios, leer completos:

1. `README.md`
2. `Documentacion/ESTADO-Y-TRASPASO.md`
3. `Documentacion/DOCKER-ACCESOS.md`
4. `Servidor/RPG-Dos-Almas/BACKUPS.md`

Reglas operativas:

- Comunicarse con el usuario en español.
- Minecraft vive bajo `/home/stev/Minecraft`; no recrear las rutas antiguas en
  la raíz de `/home/stev`.
- El servidor y sus timers son unidades systemd de usuario. Usar siempre
  `systemctl --user` y `journalctl --user`.
- Antes de parar o reiniciar el servidor, comprobar conexiones al puerto 25565.
- Antes de cambiar mods, crear una copia coherente y mantener sincronizados
  servidor, cliente de la torre y distribución de Isa.
- No imprimir ni copiar contraseñas, `.rcon-credentials`, `.env`, claves SSH o
  volúmenes de secretos.
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
