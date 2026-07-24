# Incidente de instalación y arranque en portátil Windows

Fecha: 2026-07-23
Equipo: portátil Windows de la red local
Estado: cliente iniciado; esperando confirmación de acceso al servidor

## Alcance

Este documento registra únicamente hechos observados en el sistema de archivos,
procesos y capturas autorizadas. No contiene contraseñas ni otros secretos.

## Resumen

El instalador inicial no pudo completarse en `C:` porque la unidad tenía
aproximadamente 0,48 GB libres. El modpack se instaló después en
`F:\RPG-Dos-Almas`, donde se verificaron 115 mods y 156 archivos de
configuración.

TLauncher empezó a preparar Minecraft y Forge. La primera selección
`Forge 1.20.1` dejó una carpeta de versión vacía. Un intento posterior descargó
Minecraft 1.20.1 y creó correctamente la versión
`1.20.1-forge-47.4.10`. Forge inició después, cargó los mods y alcanzó la
pantalla de confirmación para servidores de terceros. No se generó ningún
informe de crash.

## Línea temporal observada

- 23:11: TLauncher creó perfiles e índice de recursos.
- 23:11: se creó `versions\Forge 1.20.1`, pero quedó sin archivos.
- 23:21–23:26: se descargaron el cliente base 1.20.1, librerías y Forge
  47.4.10.
- 23:26: quedaron creados:
  - `versions\1.20.1\1.20.1.jar`
  - `versions\1.20.1\1.20.1.json`
  - `versions\1.20.1-forge-47.4.10\1.20.1-forge-47.4.10.json`
  - `versions\1.20.1-forge-47.4.10\TLauncherAdditional.json`
- 23:32: el puente temporal de asistencia registró el clic
  `CLICK 1140 704`.
- 23:36: permanecía un proceso Java activo, pero no había registro de inicio
  de Minecraft.
- 23:37: el puente registró un segundo clic sobre el botón de inicio.
- 23:38: la captura mostró TLauncher en estado `Ejecución de inicialización`,
  con la versión `Oficial 1.20.1-forge-47.4.10` seleccionada y el botón
  `Cancelar` activo. En ese momento aún no existía `latest.log`.
- 23:39: el proceso Java de TLauncher mantenía varias conexiones HTTPS
  establecidas y una conexión pendiente. Esto indica actividad de red durante
  la inicialización; no debe clasificarse todavía como bloqueo.
- 23:41–23:42: la descarga avanzó de 2.523 a 2.628 archivos de recursos
  (427.368.837 a 462.418.733 bytes) y de 72 a 137 librerías
  (80.789.523 a 141.723.219 bytes) en unos 30 segundos. La inicialización
  seguía progresando y todavía no correspondía intervenir.
- 23:44: se creó `logs\latest.log` y Forge comenzó a descubrir los 115 mods.
- 23:46:32: el registro confirmó `Game took 111.221 seconds to start`.
- 23:46–23:47: la captura mostró la ventana `Minecraft Forge 1.20.1` abierta
  en la advertencia inicial de juego en servidores de terceros, con los botones
  `Proceed` y `Back`.

## Errores y condiciones confirmadas

### 1. Espacio insuficiente en `C:`

- Espacio libre observado: aproximadamente 0,48 GB.
- El instalador original usaba `%APPDATA%` y el temporal de Windows en `C:`.
- Efecto: el paquete no podía descomprimirse ni copiarse de forma completa.
- Mitigación aplicada: juego y temporales dirigidos a `F:`.

### 2. Primera versión de TLauncher incompleta

- Ruta: `F:\RPG-Dos-Almas\versions\Forge 1.20.1`.
- Resultado: carpeta y subcarpeta `natives` vacías.
- Efecto: esa entrada no podía ejecutar Minecraft.

### 3. El juego todavía no alcanzó Forge

A las 23:36 no existían:

- `F:\RPG-Dos-Almas\logs\latest.log`
- `F:\RPG-Dos-Almas\logs\debug.log`
- archivos en `F:\RPG-Dos-Almas\crash-reports`

Esto descarta, por ahora, un fallo dentro de un mod: la JVM de Minecraft aún no
había iniciado la fase en la que Forge carga y registra mods.

Esta condición quedó resuelta a las 23:44, cuando apareció `latest.log`.

## Incidencias no fatales observadas durante el arranque

### Autenticación de Mojang: HTTP 401

El registro indica `Failed to verify authentication` y
`InvalidCredentialsException: Status: 401`, seguido inmediatamente por
`Setting user: Jkov`. El cliente continuó cargando y alcanzó el menú. Es
compatible con una sesión offline/no oficial de TLauncher y no fue causa de
caída.

### Modelo inválido de Born in Chaos

`born_in_chaos_v1:models/custom/icysweetness.json` referencia
`minecraft:Icysweetness`. La mayúscula no es válida en un
`ResourceLocation`, por lo que ese modelo no se cargó. El fallo es visual y no
impidió iniciar el cliente.

### Comprobaciones de actualización

La consulta de actualización de `configured` devolvió un contenido que no era
el objeto JSON esperado. Otra consulta de versión agotó su tiempo de conexión.
Ambos sucesos ocurrieron en el hilo `Forge Version Check` después de completar
la carga y no afectaron el juego.

### Clases opcionales ausentes

Se registraron varios `ClassNotFoundException` en nivel `WARN` para
integraciones opcionales con Quark, Create, Tough As Nails, Botania y otros
mods no presentes. No hubo `ModLoadingException`, `LoadingFailedException` ni
error fatal asociado.

### Configuración corregida automáticamente

Twilight Forest detectó claves ausentes y escribió sus valores predeterminados.
Estos avisos son de migración/configuración, no fallos de carga.

## Estado de la instalación válida

- Carpeta del juego: `F:\RPG-Dos-Almas`
- Minecraft base: 1.20.1
- Forge preparado: 47.4.10
- Mods: 115
- Archivos de configuración: 156
- Servidor preconfigurado: `192.168.78.210:25565`

## Observación del operador en pantalla

La captura de las 23:36 muestra otra instancia de asistencia trabajando con
TLauncher. Allí se indica que el launcher volvió al botón sin crear el proceso
del juego y que se estaba investigando un clic no recibido por la ventana.
Esto se registra como observación del operador, no como causa confirmada por un
log.

Una captura posterior, a las 23:38, confirma que el segundo intento sí fue
aceptado por TLauncher y alcanzó la etapa de inicialización.

## Próxima evidencia necesaria

Para cerrar completamente el incidente falta confirmar:

1. aceptación de la advertencia `Third-Party Online Play`;
2. conexión al servidor `192.168.78.210:25565`;
3. ausencia de desconexión o crash durante los primeros minutos dentro del
   mundo.
