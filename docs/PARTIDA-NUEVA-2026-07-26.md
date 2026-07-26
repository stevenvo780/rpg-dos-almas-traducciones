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

FTB Quests carga **448 misiones en 26 capítulos**, sin errores ni duplicados.
Antes de esta tanda eran 93. De las 448, **439 entregan recompensa de objeto**;
al principio solo tres misiones daban algo que no fuera experiencia.

Los capítulos nuevos cubren la progresión completa: caminos y ruinas, nacidos del
caos, fuente y aquelarre, bosque crepuscular, cuevas de Alex, sangre de dragón,
profundidades, edad de los metales, el Nether, el End, ingenio y redstone, granja
y bestias, y gestas y hazañas.

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
