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

### Inventario y mochila

La superposición observada al abrir el inventario tenía tres orígenes
independientes:

- los botones de editor y configuración de Inventory Profiles Next ocupaban la
  misma esquina superior izquierda que FTB Teams, FTB Quests y los controles
  administrativos de FTB Library;
- el texto `NONE` era el selector de perfiles vacío de Inventory Profiles
  Next y se cruzaba con las pestañas inferiores de Blue Skies;
- las flechas de transferencia de Inventory Profiles Next se dibujaban encima
  de los controles nativos de Sophisticated Backpacks.

Se desactivaron únicamente el editor, la configuración y el selector visual de
perfiles de Inventory Profiles Next. El ordenamiento y sus atajos siguen
disponibles. Los botones FTB de equipo, misiones, modo de juego, clima, día y
noche permanecen habilitados.

Los botones de inventario de Inventory Profiles Next siguen activos en cofres
normales. Para la pantalla de Sophisticated Backpacks se añadió una pista de
integración externa que ignora sólo esta clase:

```text
net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen
```

Así la mochila conserva su búsqueda, ordenamiento y transferencias nativas sin
duplicados. La configuración quedó idéntica en la torre y en la distribución
de Isa. Se aplica al reiniciar el cliente; no requiere reiniciar el servidor.

Una segunda prueba detectó que, en el inventario normal y en el inventario de
accesorios de Aether que abre la tecla `I`, los tres botones de ordenamiento
tapaban el libro de recetas y el check de fabricación continua invadía la
cuadrícula 2x2. La misma pista externa oculta esos cuatro controles únicamente
para:

```text
net.minecraft.client.gui.screens.inventory.InventoryScreen
com.aetherteam.aether.client.gui.screen.inventory.AccessoriesScreen
```

El menú de `I` no se eliminó: no es una copia exacta de `E`, porque contiene las
ranuras especiales de Aether para guantes, anillos, capa, colgante y otros
accesorios. El libro y la cuadrícula quedan libres; el ordenamiento conserva sus
atajos y sus botones en cofres. La fabricación continua sigue disponible en las
mesas de trabajo, donde hay espacio para el control.

El historial flotante de búsquedas de JEI quedó desactivado en la torre. No
afecta la lista de objetos, la búsqueda ni la consulta de recetas; evita sumar
otra capa temporal sobre las pantallas de inventario.

## Compatibilidad FML de la lista de servidores

El aviso de servidor FML incompatible no correspondía a una diferencia de
Forge. El registro del cliente identificó tres mods presentes en el servidor
pero ausentes en los clientes:

- Collective 8.39;
- Clumps 12.0.0.4;
- Replanting Crops 5.5.

Replanting Crops declara Collective como dependencia. Se copiaron los mismos
JAR, sin modificarlos, desde el servidor a la torre y a la distribución de Isa
y se comprobaron sus SHA-256 en los tres destinos. Tras la sincronización, el
único JAR exclusivo del servidor es Spark, que está diseñado para ese lado.

El cambio no exige reiniciar el servidor. Después de reiniciar el cliente, el
registro confirmó las tres versiones nuevas, la conexión al servidor público y
la ausencia del aviso anterior de mods adicionales del servidor.

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
