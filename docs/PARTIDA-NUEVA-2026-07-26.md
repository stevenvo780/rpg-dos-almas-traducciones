# Partida nueva, árbol de misiones y launcher — 25 y 26 de julio de 2026

Trabajo previo y posterior al reinicio total del mundo. El servidor quedó con un
mapa generado desde cero, un árbol de misiones diez veces mayor y un launcher
propio capaz de reparar la instalación de cada jugador.

## Reinicio del mundo

`server/scripts/reiniciar-mundo.sh` regenera el mundo en ocho pasos: respaldo,
parada, retirada del mundo anterior con marca de tiempo, semilla nueva, arranque
y comprobación. Conserva `ops.json` y `whitelist.json`, y salta el respaldo si ya
existe uno de menos de 45 minutos.

Dos detalles que costaron un intento fallido y quedan fijados en el script:

- El servidor lo gobierna **Docker**, no systemd. La unidad `systemctl --user`
  es una ruta heredada e inactiva; usar `docker stop` y `docker start`.
- Forge vuelca la configuración de `Dos-Almas/serverconfig/` **al apagarse**.
  Editar esos `.toml` con el servidor encendido no sirve: el apagado escribe
  encima el valor que tenía en memoria. Hay que parar primero y comprobar el
  valor después de arrancar.

El mundo anterior no se borra: se aparta como `Dos-Almas-retirado-<fecha>`.

## Árbol de misiones

FTB Quests carga **661 misiones en 34 capítulos**, sin errores ni duplicados.
Antes de esta tanda eran 93, y solo tres daban algo que no fuera experiencia;
ahora casi todas entregan recompensa de objeto.

La primera ronda añadió trece capítulos de progresión: caminos y ruinas, nacidos
del caos, fuente y aquelarre, bosque crepuscular, cuevas de Alex, sangre de
dragón, profundidades, edad de los metales, el Nether, el End, ingenio y
redstone, granja y bestias, y gestas y hazañas.

### Segunda ronda: profundidad por mod

La sospecha de partida era que muchos mods aportaban una sola misión testimonial.
Se confirmó. Dieciséis diagnósticos independientes recorrieron mod por mod lo que
el árbol cubría de verdad y encontraron **351 pasos de progresión reales** frente
a lo que había, más 108 piezas de contenido que ninguna misión mencionaba.

El caso de las mochilas ilustra el patrón: de las 448 misiones, solo **cinco**
nombraban Sophisticated Backpacks. Cuatro pedían fabricar algo —mochila base,
mochila de hierro, mejora de recogida y mejora de compactado— y la quinta
regalaba la mejora de vacío como premio de una misión de Lootr que no tenía nada
que ver. De los seis niveles de mochila solo dos aparecían, y de las treinta y
cuatro mejoras con receta, dos. Fluidos, energía, cocina portátil, alquimia y
atrapamobs no existían para el árbol.

De ahí salieron ocho capítulos nuevos con 218 misiones: el arte de la mochila,
atlas del errante, del diccionario al vacío, más allá de la puerta, botín sin
mapa, el oficio del viajero, secretos del Aether, y sed y sustento.

El diagnóstico también descartó contenido que parece existir y no sirve:

- Las siete mejoras «Chipped» tienen nombre en `en_us.json`, pero su receta
  depende de un mod que **no está instalado**. Son contenido muerto.
- `infinity_upgrade` y `survival_infinity_upgrade` no tienen receta en ningún
  dato del mod: no se obtienen en supervivencia.
- `netherite_backpack` no se fabrica en mesa, es una mejora de herrería y exige
  la plantilla de un bastión.
- La configuración limita a una mejora de la familia horno por mochila, así que
  pedir horno, ahumador y alto horno en la misma es imposible.

### Cómo validar antes de desplegar

`BaseQuestFile.refreshIDMap()` **no detecta identificadores duplicados**: si dos
misiones comparten identificador gana la última cargada, en silencio y sin aviso
en el registro. Además FTB reasigna identificadores al recargar, así que
intentar conservarlos a mano no aporta nada.

Por eso cada tanda se valida antes de copiarla al servidor, comprobando lo que ya
ha fallado antes en este proyecto:

- objetos inventados que no existen en ningún mod;
- escapes `\uXXXX`, que el parser de SNBT descarta sin avisar;
- identificadores duplicados;
- entidades, dimensiones, biomas, estructuras y avances inexistentes;
- misiones y textos anteriores alterados.

El catálogo de objetos válidos se construye desde los **nombres** de
`en_us.json` de cada JAR, no desde los modelos: la lista sacada de modelos daba
falsos positivos y llegaron cuatro objetos inexistentes a producción. El JAR de
la versión cuelga de `versions/<nombre>/<nombre>.jar`, un nivel más abajo de lo
que parece; buscarlo solo en `versions/*.jar` deja fuera las entidades de vanilla.

Antes de tocar el árbol se copia `chapters` a `chapters.antes-de-<motivo>-<fecha>`.
Esa costumbre permitió detectar que una tanda había cambiado dos identificadores
sin declararlo, y distinguir cuál era de tarea y cuál de recompensa.

## Ajustes de juego

| Ajuste | Antes | Ahora |
|---|---|---|
| Tomo de experiencia (`max_xp`) | 1 395 | 9 240 576 |
| MapSyncer (`maxConcurrentRegions`) | 4 | 12 |
| Resistencia a la infección | 0,25 | 0,7 |
| Dormir durante una horda | prohibido | permitido |
| Una horda bloquea el sueño ajeno | sí | no |

El tomo pasaba de 30 niveles a unos 1 450. MapSyncer congelaba el hilo del
servidor y con cuatro regiones simultáneas se acercaba al límite de
`max-tick-time`; con doce reparte mejor el trabajo.

### Mochilas

Ranuras de inventario y de mejora por nivel, de cuero a netherita:

| Nivel | Antes | Ahora |
|---|---|---|
| Cuero | 27 / 1 | 48 / 3 |
| Hierro | 45 / 1 | 64 / 3 |
| Oro | 54 / 2 | 80 / 6 |
| Diamante | 81 / 3 | 112 / 9 |
| Netherita | 108 / 5 | 144 / 9 |
| Superior | 120 / 7 | 144 / 10 |

El tope de mejoras de apilado sube de 3 a 10. Los 144 del final no son
arbitrarios: la rejilla de Sophisticated Backpacks usa `maxLineWidth = 16` y el
mod no admite más de 144 ranuras, así que 144 es a la vez el máximo y una fila
exacta. Los números intermedios se eligieron múltiplos de 16 para que la ventana
quede simétrica, redondeando hacia arriba.

## Launcher propio

El código fuente entra al repositorio en `snapshot/custom/launcher-propio`:
veintiuna clases Java, la documentación de diseño y el empaquetado para Linux.
Quedan fuera el JRE, los JAR de dependencias y las salidas de `build/` y `dist/`.

Correcciones de esta tanda:

- **`options.txt` sin `version`.** Minecraft 1.20.1 espera `version:3465`. Sin
  ese campo `OptionsKeyLwjgl3Fix` lanza una excepción y el juego **descarta el
  archivo entero**: el idioma volvía a inglés y los resource packs desaparecían
  en cada instalación nueva. `Sincronizador` ahora escribe la clave solo si falta,
  sin pisar la que el jugador ya tenga.
- **Resource packs fantasma.** `depurarPacks()` descarta las referencias
  `file/<x>` cuyo archivo no existe y añade al final los packs propios del
  jugador, en vez de imponer la lista a ciegas.
- **Perfiles de shaders.** El perfil alto usa Complementary Reimagined y el bajo
  Shrimple, en lugar de quedarse sin shaders.
- **Recolector de basura.** `-XX:+DisableExplicitGC` se sustituye por
  `-XX:+ExplicitGCInvokesConcurrent`: Distant Horizons llama a `System.gc()` y
  conviene atender esa llamada de forma concurrente, no ignorarla.

## Shrimple y Oculus

Shrimple 0.12 no arranca con Oculus 1.8.0. `BlockMaterialMapping` interpreta cada
token de `block.properties` como un `ResourceLocation`, que solo admite
`[a-z0-9/._-]`; la sintaxis `%tag` del shader lo hace fallar. La copia
distribuida lleva 168 tokens retirados, conservando las 792 definiciones y el
mismo juego de claves, sin ninguna quedar vacía.

Oculus solo analiza `block.properties`, `item.properties` y `entity.properties`.
El resto de `.properties` de un shader no pasa por ese código y no hace falta
tocarlo.

## El recetario nativo estaba medio vacío

El librito verde de la mesa **solo enseña lo que el jugador tiene desbloqueado**,
y cada mod suelta sus recetas con su propio avance. Con 136 mods eso deja el
recetario a medias aunque todo sea fabricable, porque `doLimitedCrafting` está en
`false` y nunca hubo nada bloqueado de verdad.

Lo que no es evidente: el libro **no** clasifica por el `type` del JSON, sino por
si la clase implementa `CraftingRecipe`. Por eso 172 recetas con serializer propio
—las mejoras de mochila, las herramientas de Blue Skies, las velas de Hexerei—
siempre fueron elegibles; lo único que las escondía era el desbloqueo.

Y hay un agujero que no se arregla jugando: **1.513 recetas de mesa no las concede
ningún avance de ningún mod**. Alex's Caves y Simply Swords se dejan 343 cada uno,
Hexerei 152, Iron's Spellbooks 136. Esas no aparecerían jamás.

La solución es el datapack `snapshot/server/datapacks/dos-almas`: un avance sin
`display` —así no saca notificación ni cuenta como logro— que dispara una función
con `recipe give @s *`. Salta una vez por jugador al entrar. Se comprobó antes de
aplicarlo que **ninguno de los 215 avances que usan las misiones es de receta**,
así que no completa nada solo.

Quedan fuera unas 2.485 recetas de estaciones propias (alambique, altar, aparato
de encantamiento). Eso es imposible por diseño: `CraftingMenu` solo consulta
`RecipeType.CRAFTING`, así que aunque se pintaran en el libro la mesa nunca las
produciría. Para esas está JEI.

## Shaders: en gráfica integrada, Oculus se cuelga

El perfil bajo llegó a salir con los shaders encendidos. En una Iris Xe eso no da
pocos fotogramas: **el juego no abre**. El proceso queda vivo quemando CPU y el
registro se congela en `Creating pipeline for dimension overworld` seguido de
`Type is VERTEX / GEOMETRY / FRAGMENT`. Sin excepción, sin crash report y con la
ventana sin crear, es indistinguible de «no me arranca».

Queda fijado en tres sitios que hay que mantener en línea:

1. `PerfilesServicio.aplicarShaders()` → `conShaders = (p == Perfil.ALTO)`.
2. `client/portable/config/oculus.properties` → `enableShaders=false`.
3. El `oculus.properties` de cada equipo ya instalado.

El perfil solo se aplica al pulsarlo en Ajustes, **no en cada arranque**: por eso
corregir el archivo a mano aguanta, pero hay que desplegar el launcher corregido
para que nadie lo vuelva a romper desde la interfaz.

## Misiones imposibles retiradas

Cinco misiones del capítulo XXVIII pedían los biomas del End de Biomes O' Plenty
(`end_wilds`, `end_reef`, `end_corruption`) y bloqueaban por dependencia la misión
final del capítulo. **Esos tres biomas no se generan nunca**: BoP solo registra
regiones de Overworld y Nether, TerraBlender no tiene un tipo de región para el
End, y el propio mod generó su `biome_toggles.json` con 66 biomas mientras el JAR
trae 69 — los tres que faltan son exactamente esos. Es contenido inacabado del mod.

Se quitaron las cinco y se limpiaron las dos dependencias de la misión final, que
por sí sola sí es completable.

En la misma pasada se corrigieron dos recompensas que se habrían entregado vacías:
`minecraft:ominous_banner` no es un objeto registrado (es un estandarte con NBT) y
`farmersdelight:tomatoes_on_rope` es un bloque de cultivo sin `BlockItem`.

## Pendiente

- Probar el ciclo desinstalar → reinstalar → lanzar en cada sistema operativo.
- Que el launcher garantice el **contenido** por hashes, no solo las claves de
  configuración.
- `view-distance: 18` provoca tirones cuando varios jugadores cargan terreno a la
  vez. Diagnosticado y dejado así a propósito.
- Decidir la hora del escaneo programado de MapSyncer (04:00) si alguien juega de
  madrugada.
- No hay ningún mod de carcaj dedicado instalado. `relics:arrow_quiver`, de
  Relics, es el único carcaj disponible en el modpack.
- El equipo de Isabel es también Iris Xe y salió de la misma distribución, así que
  le toca el mismo arreglo de shaders y el launcher corregido. Estaba apagado.
- El repositorio está **público** mientras `docs/COPYRIGHT-Y-EXCLUSIONES.md` pide
  mantenerlo privado hasta revisar una por una las licencias de los mods cuyos
  textos adapta el overlay de traducción.
