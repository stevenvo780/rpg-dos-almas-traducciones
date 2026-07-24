# Minecraft — estructura principal

Todo el entorno de Minecraft de esta torre quedó centralizado aquí el
2026-07-23. Las rutas importantes son:

- `Cliente/RPG-Dos-Almas`: instancia activa que abre TLauncher.
- `Servidor/RPG-Dos-Almas`: servidor Forge y mundo `Dos-Almas`.
- `Respaldos/Mundos`: copias locales verificables del mundo.
- `Respaldos/Snapshots`: estados completos conservados antes de cambios grandes.
- `Distribucion/Isa-Windows`: instalador preparado para el portátil de Isa.
- `Distribucion/Modpack`: fuente y construcción del paquete.
- `Launcher/TLauncher.jar`: ejecutable del launcher.
- `Herramientas/Render-Lab`: laboratorio y pruebas de rendimiento.
- `Herramientas/RPG-Dos-Almas-Compat`: módulo Forge propio para integrar sed,
  mochilas y compatibilidades del servidor.
- `Recursos/Shaders`: paquetes de shaders conservados.
- `Recursos/Identidad`: fuentes visuales originales del proyecto.
- `Evidencias`: capturas, configuraciones y accesos antiguos de diagnóstico.
- `Archivo/Reparaciones`: respaldos pequeños creados durante reparaciones.
- `Temporales`: staging que aún puede ser útil; no se eliminó.
- `Documentacion`: inventarios operativos y traspaso para continuar el trabajo.

Documentación principal:

- [Accesos Docker](Documentacion/DOCKER-ACCESOS.md)
- [Estado y traspaso](Documentacion/ESTADO-Y-TRASPASO.md)
- [Integraciones y UI](Documentacion/INTEGRACIONES-Y-UI-2026-07-24.md)

## Cómo iniciar

Usar el acceso **RPG Dos Almas (TLauncher)** del Escritorio o del menú de
aplicaciones. Ambos llaman al wrapper estable:

```text
/home/stev/.local/bin/tlauncher
```

No hace falta abrir el JAR directamente. TLauncher está configurado para usar:

```text
/home/stev/Minecraft/Cliente/RPG-Dos-Almas
```

## Servidor y respaldos

El servicio de usuario sigue llamándose `rpg-dos-almas-server.service` y el
temporizador de usuario para copias `rpg-dos-almas-backup.timer`. Deben
administrarse siempre con `systemctl --user` y `journalctl --user`. El mundo
real está en:

```text
/home/stev/Minecraft/Servidor/RPG-Dos-Almas/Dos-Almas
```

La dirección pública del servidor es:

```text
minecraft.stevenvallejo.com
```

Usa el puerto estándar de Minecraft Java (`25565`), por lo que no hace falta
escribir el puerto en el cliente.

El mundo y sus copias permanecen bajo `/home`, en el RAID Btrfs con espacio
suficiente; no se trasladó nada a `/root`.

## Directorios convencionales

`.tlauncher` y `.minecraft` permanecen ocultos en `/home/stev` porque varias
aplicaciones esperan esos nombres convencionales. Dentro de `Launcher` hay
enlaces cómodos hacia ambos; no son copias adicionales.

La configuración anterior a esta reorganización se conservó en
`Archivo/Reorganizacion-20260723T1134`.
