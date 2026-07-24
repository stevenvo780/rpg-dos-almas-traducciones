# Estado y traspaso — RPG Dos Almas

Última validación integral: `2026-07-24 12:30 COT`.

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
- Jugadores máximos: 20.
- Modo de autenticación actual: `online-mode=false`.
- Lista blanca: activada y forzada; contiene únicamente los tres perfiles con
  datos de jugador existentes.
- Distancia del servidor: 24 chunks visibles y 8 de simulación.
- RCON está habilitado en `127.0.0.1:25575`; sus datos están en
  `.rcon-credentials`, modo `0600`.

Prueba funcional realizada por RCON:

```text
0/20 jugadores conectados
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

El servidor carga `dos_almas_compat` 1.0.2, módulo propio que integra el consumo
automático de bebidas de Thirst Was Taken con el `Feeding Upgrade` de
Sophisticated Backpacks. Respeta activación, filtro, modo de consumo, pureza y
recipientes.

El mismo módulo corrige el `NoSuchFieldException: AIR` de MapSyncer 1.0.3
proporcionando referencias remapeadas por Forge sin modificar el JAR ajeno. Se
repitió una parada controlada: MapSyncer limpió sus cachés sin excepción y el
servidor volvió a `Done`.

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

## Acceso público

- Unidad local: `rpg-dos-almas-tunnel.service`, servicio de usuario activo.
- VPS puente: `76.13.118.239`, puerto público `25565/tcp`.
- El VPS entrega el socket público a un forward SSH restringido en
  `127.0.0.1:25566`.
- La cuenta remota no tiene shell y su clave sólo permite ese forward.
- La definición reproducible está en
  `Servidor/RPG-Dos-Almas/infra`.

El acceso público confirmado es `minecraft.stevenvallejo.com:25565`. La zona
DNS usa los nameservers de Vercel y contiene un registro `A` exacto para
`minecraft` hacia el VPS. El ping de estado de Minecraft responde por el
dominio, anuncia 20 plazas y entrega el icono del servidor. No hace falta un
registro SRV mientras se conserve el puerto 25565.

El modo offline es necesario para los clientes actuales. Aunque la lista blanca
reduce la exposición, un tercero que conozca el nombre exacto de un jugador
autorizado podría suplantarlo; es el riesgo residual de publicar un servidor sin
autenticación oficial.

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

Antes de instalar la integración 1.0.2 se creó y verificó además
`Dos-Almas-2026-07-24_101609.tar.zst` tanto localmente como en el NAS.

Antes de instalar la traducción profunda se creó y verificó
`Dos-Almas-2026-07-24_120156.tar.zst` tanto localmente como en el NAS.

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
- Idioma del juego: `es_es`; interfaz de TLauncher: español.
- Valores actuales del cliente: render 32, simulación 32,
  `entityDistanceScaling=2.0`, mezcla de biomas 3.
- La UI tiene zonas separadas para Xaero, Jade, Iron's Spells, Thirst, Combat
  Roll e Inventory HUD. El detalle está en
  `Documentacion/INTEGRACIONES-Y-UI-2026-07-24.md`.
- Inventory Profiles Next oculta su selector vacío, editor y configuración. En
  el inventario normal y en el de accesorios de Aether (`I`) también oculta sus
  controles flotantes de orden y fabricación continua para no tapar el libro de
  recetas ni la cuadrícula; los atajos siguen activos y los botones permanecen
  en cofres.
- Sophisticated Backpacks usa únicamente sus controles nativos, sin flechas ni
  ordenamiento duplicados de Inventory Profiles Next.

El cliente y la distribución de Isa contienen las mismas versiones de
Collective 8.39, Clumps 12.0.0.4 y Replanting Crops 5.5 que el servidor. La
prueba posterior confirmó que desapareció el aviso FML de mods adicionales del
servidor.

El paquete `RPG-Dos-Almas-Espanol` instalado contiene 4.800 archivos. La
validación combinada de la traducción profunda parseó 4.422 JSON y `mcmeta`,
encontró cero faltantes de prosa visible y preservó 208 valores vacíos,
símbolos o controles técnicos. El contenido derivado de mods con licencias
restrictivas permanece privado y no se publica en GitHub.

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
- El perfil portátil usa `guiScale:2`, conserva iluminación dinámica y contiene
  la misma versión 1.0.2 del módulo de compatibilidad.
- Tanto el cliente de la torre como el perfil portátil tienen
  `minecraft.stevenvallejo.com` preconfigurado en `options.txt` y
  `servers.dat`.
- El instalador Windows 1.2.0 se recompiló y verificó como archivo NSIS el
  2026-07-24. Está en
  `/home/stev/Descargas/RPG-Dos-Almas-Instalador-Windows.exe`, acompañado de su
  suma SHA-256. La prueba integral de 7-Zip validó sus 5.153 archivos y la
  extracción de control confirmó `es_es`, `guiScale:2` y las reglas nuevas de
  Inventory Profiles Next.

El icono activo está en
`Servidor/RPG-Dos-Almas/server-icon.png`; su fuente original generada para este
proyecto se conserva en
`Recursos/Identidad/RPG-Dos-Almas-server-icon-source.png`.

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
