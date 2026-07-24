# Reparación de logs y desconexiones — 24 de julio de 2026

## Diagnóstico

- El servidor no se cayó ni se reinició durante las desconexiones:
  `rpg-dos-almas-server.service` mantuvo el mismo proceso y `NRestarts=0`.
- Los clientes de la torre y del portátil cerraron su sesión de juego de forma
  normal. El intento posterior del portátil quedó detenido durante la conexión.
- La red local y el puerto `25565` estaban disponibles. En el portátil la señal
  Wi-Fi era del 92 %.
- La salida HTTPS del portátil era irregular: algunas direcciones de
  `api.minecraftservices.com` respondían en menos de 300 ms y otras agotaban el
  tiempo de espera. La ruta IPv6 tampoco respondía.
- El lanzador del portátil forzaba Forge `47.4.10`, mientras el servidor y la
  torre usan Forge `47.4.20`.

## Correcciones aplicadas

### Datapack del mundo

Se corrigió y se dejó con prioridad máxima:

`Servidor/RPG-Dos-Almas/Dos-Almas/datapacks/dos-almas-fixes`

Incluye:

- tablas de botín opcionales ausentes de Thirst;
- modificadores globales de botín de Thirst con un codec disponible;
- libros y referencias de botín incompatibles de Iron's Spells;
- pools ausentes de Dungeons Arise;
- la tabla del bisonte de Alex's Mobs, que intentaba fundir piel;
- alias de texturas antiguas que Runic Skills solicita a Apotheosis.

La última recarga terminó con 55 recetas y 6832 avances, sin errores de parseo,
excepciones ni modificadores de botín rechazados.

### Clientes

- Se instalaron los alias de texturas en la torre, la distribución de Isa y el
  portátil Windows.
- El portátil recibió Forge `47.4.20` sin retirar la instalación anterior.
- Su acceso directo ahora elige `Forge 1.20.1` y fuerza IPv4 para Java.
- El perfil Forge de la torre también fuerza IPv4.
- El lanzador incluido en la distribución de Isa hereda la misma preferencia.

### Servidor

Se añadió `-Djava.net.preferIPv4Stack=true` a `user_jvm_args.txt`. Entrará en
vigor en el próximo reinicio planificado; no se interrumpió al jugador
conectado para aplicarlo.

## Estado verificado

- Servicio: activo.
- Reinicios inesperados: 0.
- Jugadores durante la validación: 1 (`stev`).
- Rendimiento: 20 TPS; tiempo medio total aproximado de 15 ms por tick.
- Errores posteriores a la recarga limpia: ninguno.

## Avisos que no requieren parche

- Apotheosis avisa que algunas combinaciones `mythic` y `ancient` tienen menos
  afijos disponibles que los solicitados. Es un aviso de balance, no una caída.
- Placebo/Apotheosis puede avisar del tipo de bloque de un generador de jefe al
  entrar un jugador. No desconecta la sesión.
- MapSyncer puede intentar consultar Xaero antes de que Xaero termine de
  inicializarse; la sincronización continúa.
- Better Combat puede recibir un paquete sin mano de ataque durante una muerte
  o cambio de estado. No es una desconexión.

## Copias de reversión

- `Respaldos/Correcciones/dos-almas-fixes-pre-logs-20260724-0205`
- `Respaldos/Correcciones/network-before-20260724-0230`
- En el portátil:
  `F:\RPG-Dos-Almas\backup\correcciones-20260724`

No se reinició ni detuvo el servidor y no se modificaron mundos, inventarios ni
volúmenes Docker.

## Reinicio autorizado posterior

Después de recibir autorización explícita del usuario:

1. Se avisó dentro del juego.
2. Se ejecutó `save-all flush` y se confirmó `Saved the game`.
3. Se volvió a comprobar la conexión al puerto `25565`.
4. Se reinició `rpg-dos-almas-server.service` con `systemctl --user`.

El servidor volvió a estar listo a las 02:36:26, mantiene Forge `47.4.20` y
20 TPS. La JVM confirmó:

```text
java.net.preferIPv4Stack=true
java.net.preferIPv6Addresses=false
```

El comprobador externo de versiones de Forge agotó el tiempo de espera sin
afectar el arranque. Se desactivaron para el siguiente inicio los chequeos de
actualizaciones de Forge y Collective; las descargas de traducciones de
Collective permanecen habilitadas.

MapSyncer emitió durante el apagado una excepción de reflexión al limpiar su
caché (`NoSuchFieldException: AIR`). Ocurrió después de guardar todas las
dimensiones y no afectó el mundo. El mod se conservó porque su función de
sincronización de mapas es útil y retirar o modificar su JAR sería un cambio
desproporcionado.

A las 02:50, el timer normal `rpg-dos-almas-backup.timer` comprobó que no había
jugadores y ejecutó su respaldo consistente programado. No fue una caída:
detuvo el servicio, guardó todas las dimensiones, validó
`Dos-Almas-2026-07-24_025015.tar.zst` y volvió a iniciar el servidor. Este
segundo inicio confirmó que el comprobador de Forge estaba desactivado y no
repitió sus timeouts. Estado posterior: servidor activo, puerto `25565`
escuchando y 20 TPS.
