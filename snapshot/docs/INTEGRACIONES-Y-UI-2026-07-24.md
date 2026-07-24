# Integraciones y distribución de la interfaz

Fecha de aplicación: 2026-07-24.

## Mochilas y sed

El `Feeding Upgrade` de Sophisticated Backpacks sólo evalúa el hambre y los
objetos con propiedades de comida. Thirst Was Taken mantiene la sed y las
bebidas en una API independiente, por lo que ambos mods no pueden cooperar sin
un adaptador.

Se creó el módulo propio y MIT `dos_almas_compat` para resolver ese hueco sin
modificar ni redistribuir código de los mods:

- busca mochilas Sophisticated Backpacks en el inventario y en ranuras Curios;
- actúa únicamente si la mochila lleva un `Feeding Upgrade` activado;
- respeta el filtro de lista blanca o negra configurado en el propio upgrade;
- aplica los modos `ANY`, `HALF` y `FULL` del upgrade a la sed;
- consume solamente objetos reconocidos como bebida por Thirst Was Taken;
- prioriza bebidas seguras y agua de pureza aceptable o purificada;
- permite agua insegura como último recurso sólo con sed crítica;
- devuelve la botella o recipiente primero a la mochila y, si no cabe, al
  inventario del jugador;
- permite excluir objetos mediante el tag
  `dos_almas_compat:auto_drink_blacklist`.

La configuración común se genera en
`config/dos_almas_compat-common.toml`. El código fuente canónico está en
`Herramientas/RPG-Dos-Almas-Compat`.

El mismo JAR compilado quedó instalado en servidor, cliente de la torre y
paquete portátil de Isa. Sus hashes se comprobaron antes del reinicio. El
servidor lo detectó y cargó correctamente.

### Prueba dentro del juego

1. Poner una bebida válida y un `Feeding Upgrade` habilitado dentro de la misma
   mochila.
2. Llevar la mochila en el inventario o en su ranura Curios.
3. Reducir la sed lo suficiente para el modo elegido en el upgrade.
4. Esperar aproximadamente un segundo.
5. Verificar que sube la sed, baja una unidad de bebida y el recipiente vacío
   regresa a la mochila.

La compilación y carga están verificadas; esta prueba de jugabilidad necesita
un jugador conectado y sigue siendo la comprobación funcional final.

## MapSyncer y Forge

MapSyncer 1.0.3 intenta resolver por reflexión los campos de desarrollo
`Blocks.AIR` y `Fluids.EMPTY`. En un servidor Forge productivo esos nombres
están remapeados, por lo que el mod producía un `NoSuchFieldException` al
inicializar su lector de bloques, incluido durante la limpieza al apagar.

El módulo `dos_almas_compat` ahora entrega esas referencias mediante código que
ForgeGradle remapea. No modifica el JAR de MapSyncer y la integración se omite
silenciosamente si el mod no está instalado.

## Distribución de la UI

La interfaz queda repartida por zonas para que cada mod tenga un espacio
estable:

- Xaero: esquina superior derecha.
- Jade: parte superior central.
- avisos de recarga de Iron's Spells: esquina superior izquierda.
- mana de Iron's Spells: esquina inferior izquierda.
- barra de hechizos: junto a la barra rápida.
- sed: posición nativa inferior derecha.
- Combat Roll: parte inferior, a la izquierda de la barra rápida.
- estado de armadura de Inventory HUD: centro izquierdo, sólo cuando la
  durabilidad baja del 75 %.
- inventario adicional de Inventory HUD: desactivado por defecto; si se activa,
  aparece en la zona superior izquierda a partir de `x=8`, `y=64`.
- efectos duplicados de Inventory HUD: desactivados.

El portátil usa `guiScale:2` para conservar legibilidad y espacio con una
pantalla pequeña. Los siete archivos de UI relevantes se sincronizaron entre
la torre y el paquete portátil y se validó que las parejas fueran idénticas.

## Integraciones auditadas

- Open Parties and Claims usa FTB Teams como sistema principal de equipos.
- FTB Quests entrega por equipo donde corresponde.
- Better Combat ya posee registro para las armas modificadas del paquete.
- RunicSkills mantiene activas sus integraciones de magia, progresión y
  requisitos con Iron's Spells, Ars, Apotheosis y FTB.
- Thirst Was Taken ya reconoce bebidas y alimentos de Farmer's Delight y
  dispone de reglas por tipo de bebida, sopa y fruta.
- Xaero, MapSyncer, Server Waypoint y Open Parties and Claims ya cooperan para
  mapas, dimensiones, marcadores y territorios.

No se introdujeron cambios de balance adicionales sin una incompatibilidad
reproducible. El hueco funcional confirmado era el consumo automático de
bebidas desde la mochila.

## Mensajes conocidos del arranque

Relics 0.8.0.13 intenta registrar un `ScreenMixin` en el servidor dedicado y
produce un aviso de lado inválido. Es un defecto de separación cliente/servidor
del propio mod; no impide que el servidor termine el arranque. Los avisos de
mixins de Thirst, Oreganized y Apotheosis corresponden a compatibilidades
opcionales con mods que no están instalados. El error de reflexión de MapSyncer
sí quedó corregido mediante el adaptador propio.

No se modificaron JAR de terceros para ocultar estos avisos. El servidor llega
a `Done`, mantiene el puerto local y responde también a través del puente
público.

## Respaldo previo

Antes de instalar el módulo se creó un respaldo coherente del mundo mediante
el procedimiento oficial y se verificaron la copia local, la copia NAS y sus
sumas. Antes de cada parada o reinicio se comprobó que no hubiera conexiones
establecidas al puerto 25565.
