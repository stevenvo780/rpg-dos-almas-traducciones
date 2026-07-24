# Estado y traspaso — RPG Dos Almas

Última validación integral: `2026-07-23 11:48 COT`.

Este documento permite continuar el trabajo iniciando una nueva sesión desde
`/home/stev/Minecraft`, sin depender del historial del chat anterior.

## Inicio recomendado para una nueva sesión

```bash
cd /home/stev/Minecraft
```

Pedir al nuevo agente que lea primero:

1. `/home/stev/Minecraft/README.md`
2. `/home/stev/Minecraft/Documentacion/ESTADO-Y-TRASPASO.md`
3. `/home/stev/Minecraft/Documentacion/DOCKER-ACCESOS.md`
4. `/home/stev/Minecraft/Servidor/RPG-Dos-Almas/BACKUPS.md`

Nunca pegar en el chat el contenido de `.rcon-credentials`, `.env`, claves SSH
o volúmenes cuyo nombre incluya `secrets`.

## Estado confirmado del servidor

- Unidad: `rpg-dos-almas-server.service`, servicio de **usuario**.
- Estado al validar: habilitado, activo y escuchando en `*:25565`.
- IP LAN de la torre: `192.168.78.210`.
- Ruta: `/home/stev/Minecraft/Servidor/RPG-Dos-Almas`.
- Mundo: `/home/stev/Minecraft/Servidor/RPG-Dos-Almas/Dos-Almas`.
- Forge/Minecraft: `1.20.1`, Forge `47.4.20`.
- Dificultad: `hard`.
- Jugadores máximos: 4.
- Modo de autenticación actual: `online-mode=false`.
- Distancia del servidor: 24 chunks visibles y 8 de simulación.
- RCON está habilitado en `127.0.0.1:25575`; sus datos están en
  `.rcon-credentials`, modo `0600`.

Prueba funcional realizada por RCON:

```text
0/4 jugadores conectados
TPS total: 20.000
Tiempo medio de tick: 0.245 ms
```

Las diez dimensiones cargadas reportaron 20 TPS. El proceso no tenía reinicios
automáticos ni errores de memoria.

Comandos operativos:

```bash
systemctl --user status rpg-dos-almas-server.service
systemctl --user restart rpg-dos-almas-server.service
journalctl --user -u rpg-dos-almas-server.service -n 200 --no-pager
ss -Hltnp '( sport = :25565 )'
```

Una parada controlada anterior guardó todos los chunks y dimensiones. MapSyncer
emitió `NoSuchFieldException: AIR` durante su limpieza final, después del
guardado; no impidió el nuevo arranque ni el funcionamiento a 20 TPS. Si vuelve
a investigarse, tratarlo como advertencia de cierre y comprobar primero si hay
una versión compatible antes de cambiar el mod.

Pendientes de contenido no bloqueantes observados en `logs/debug.log`:

- ThirstWasTaken intenta cargar integraciones/loot tables de bloques o bebidas
  ausentes (`sand_filter`, Brewin' and Chewin y Farmer's Respite) y varios
  modificadores con serializer `thirst:add_loot_table`.
- Iron's Spells no decodifica dos tablas que usan
  `minecraft:set_written_book_pages`.
- Relics intenta resolver un `ScreenMixin` de cliente en el servidor dedicado.

No provocaron crash, pérdida de TPS ni mensajes `Can't keep up`, pero esos
botines concretos pueden no generarse. Conviene resolverlos en una sesión
separada con copia previa y prueba cliente/servidor; no mezclar esa reparación
con la reorganización de rutas ya validada.

## Respaldos

- Timer: `rpg-dos-almas-backup.timer`, habilitado y activo.
- Servicio: `rpg-dos-almas-backup.service`.
- Copias locales: `/home/stev/Minecraft/Respaldos/Mundos`.
- Destino inmediato: NAS
  `nas@192.168.78.144:/mnt/pool/backups/kratos/minecraft/RPG-Dos-Almas`.
- Drive se alimenta después mediante el trabajo Restic del NAS de las 05:00.

El ciclo automático ejecutado después de la reorganización terminó con código
0 y verificó en el NAS estas tres copias:

```text
Dos-Almas-2026-07-22_1629.tar.zst
Dos-Almas-2026-07-23_014419.tar.zst
Dos-Almas-pre-mods-2026-07-22_184127.tar.zst
```

No creó otra copia porque todavía no habían transcurrido las 20 horas
configuradas. Verificar el NAS no equivale por sí solo a confirmar que el
snapshot cifrado ya llegó a Drive; consultar `BACKUPS.md` antes de afirmarlo.

El timer de ajuste automático de Distant Horizons
`rpg-dos-almas-dh.timer` permanece deshabilitado e inactivo.

## Cliente de la torre

- Instancia activa: `/home/stev/Minecraft/Cliente/RPG-Dos-Almas`.
- TLauncher: `/home/stev/Minecraft/Launcher/TLauncher.jar`.
- Wrapper estable: `/home/stev/.local/bin/tlauncher`.
- Acceso de Escritorio:
  `/home/stev/Escritorio/RPG Dos Almas.desktop`.
- Entradas del menú:
  `/home/stev/.local/share/applications/rpg-dos-almas.desktop` y
  `/home/stev/.local/share/applications/tlauncher.desktop`.
- Perfil seleccionado: `Forge 1.20.1`.
- Memoria configurada en TLauncher: 24 GiB.
- Idioma del juego: `es_mx`; interfaz de TLauncher: español.
- Valores actuales del cliente: render 32, simulación 32,
  `entityDistanceScaling=2.0`, mezcla de biomas 3.

El JAR pasó validación ZIP completa y todos los accesos ejecutan el mismo
wrapper. `.tlauncher` y `.minecraft` permanecen ocultos en `/home/stev` por
compatibilidad; dentro de `Minecraft/Launcher` existen enlaces hacia ambos.

## Isa y distribución

- Instalador fuente para Windows:
  `/home/stev/Minecraft/Distribucion/Isa-Windows`.
- Constructor del modpack:
  `/home/stev/Minecraft/Distribucion/Modpack`.
- Los ZIP finales para entregar permanecen intencionalmente en
  `/home/stev/Descargas`.

No almacenar en este documento las contraseñas de la torre o del portátil.

## Organización y almacenamiento

El árbol `/home/stev/Minecraft` ocupa aproximadamente 19 GB:

| Categoría | Tamaño aproximado |
|---|---:|
| `Herramientas` | 12 GB |
| `Servidor` | 2.2 GB |
| `Cliente` | 1.9 GB |
| `Respaldos` | 1.9 GB |
| `Temporales` | 619 MB |
| `Distribucion` | 373 MB |
| `Evidencias` | 50 MB |

Todo pertenece a `stev:stev`; se verificaron más de 2.000 enlaces y ninguno
estaba roto. `/home` tiene aproximadamente 532 GB libres. No quedan directorios
Minecraft visibles dispersos en `/home/stev`.

No borrar `Herramientas`, `Temporales`, snapshots ni reparaciones sin revisar su
contenido: son los candidatos futuros de limpieza, pero algunos contienen
staging y rollback todavía útiles.

## Docker

La auditoría completa está en
`/home/stev/Minecraft/Documentacion/DOCKER-ACCESOS.md`.

Resumen crítico:

- Docker funciona y tenía 29/35 contenedores activos, sin OOM ni unhealthy.
- `stev` tiene acceso administrativo mediante `/var/run/docker.sock`.
- Docker usa `/var/lib/docker` en la partición raíz, no `/home`.
- La raíz estaba al 88 %, con unos 37 GB libres.
- Las capas grabables sumaban cerca de 181 GB; `ws-humanizar`,
  `ws-personal`, `ws-prizma` y `ctrl-infra` eran las mayores.

No ejecutar `docker system prune`, borrar volúmenes ni mover
`/var/lib/docker` como parte de una tarea de Minecraft.

## Comprobación rápida después de reiniciar la sesión

```bash
systemctl --user is-active rpg-dos-almas-server.service
systemctl --user is-active rpg-dos-almas-backup.timer
ss -Hltn '( sport = :25565 )'
rg '^minecraft.gamedir=' /home/stev/.tlauncher/tlauncher-2.0.properties
docker ps --format 'table {{.Names}}\t{{.Status}}'
df -h / /home
```

Resultados esperados: servidor `active`, timer `active`, puerto 25565 en
escucha, `minecraft.gamedir` apuntando a
`/home/stev/Minecraft/Cliente/RPG-Dos-Almas`, y ningún contenedor `unhealthy`.
