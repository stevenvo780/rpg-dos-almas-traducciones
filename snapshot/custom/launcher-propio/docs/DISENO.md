# Launcher Dos Almas — Documento de diseño

Proyecto propio para lanzar el modpack **RPG Dos Almas** sin depender de TLauncher,
con cuentas offline, consola de depuración y distribución fácil a Windows y Linux.

## 1. Por qué un launcher propio

| Launcher | Offline sin Microsoft | Control/consola | Veredicto |
|---|---|---|---|
| TLauncher | sí | poco control, cerrado, dependencia externa | lo que usamos hoy |
| Prism Launcher | **no** (exige login Microsoft una vez) | excelente | descartado |
| HMCL | **no** (idem) | bueno | descartado |
| **Propio** | **sí** | **total** | **elegido** |

## 2. Decisiones de arquitectura

- **Lenguaje: Java (Swing)**, un solo `.jar`, **sin dependencias externas**.
  - Minecraft ya requiere Java, así que no añade requisitos.
  - El mismo `.jar` corre igual en Windows y Linux.
  - Compilable con `javac` directo (hay javac 26); no hace falta Maven/Gradle.
- **El launcher NO instala Forge.** El pack ya trae su versión instalada; el
  launcher **lee el JSON de versión y lanza**. Esto evita el punto frágil que
  hizo fallar a portablemc (post-procesado del instalador de Forge).
- **Verifica antes de lanzar, pero NO descarga.** Decisión revisada el
  2026-07-24: lo único que una PC nueva no puede heredar del pack de Linux son
  **1,5 MB** de natives de Windows; escribir y mantener un descargador con
  reintentos y verificación en tres sistemas no se justifica por ese tamaño.
  En su lugar, el `Verificador` dice **exactamente qué falta y qué hacer**, y
  las piezas se copian al preparar la distribución.
- **Detección automática de rutas.** El launcher no contiene ninguna ruta de una
  máquina concreta: busca el pack, los assets y el Java junto a su propio jar y
  en las ubicaciones habituales del usuario. El mismo jar funciona sin
  argumentos en la torre, en el portátil y en Windows.

## 3. Hechos del entorno (verificados 2026-07-24)

- Pack: `/home/stev/Minecraft/Cliente/RPG-Dos-Almas` — MC 1.20.1, Forge 47.4.20, ~134 mods.
- Version JSON: `versions/Forge 1.20.1/Forge 1.20.1.json`
  - **autocontenido** (`inheritsFrom: null`) — todo lo necesario está ahí
  - `mainClass`: `cpw.mods.bootstraplauncher.BootstrapLauncher`
  - 117 librerías declaradas · 28 args JVM · 38 args de juego
  - `assetIndex` id **5** · `javaVersion`: java-runtime-gamma **17**
- Natives ya extraídos en `versions/Forge 1.20.1/natives/` (libglfw, liblwjgl…).
- `libraries/`: 139 MB, **94 jars** presentes vs 117 declaradas → verificar faltantes
  (algunas pueden estar excluidas por *rules* de SO).
- `assets/` del pack **vacía**; `~/.minecraft/assets` (466 MB) tiene index 32 pero
  **no el index 5** → hay que localizar o descargar los assets de 1.20.1.
- JRE disponible y adecuado: `Launcher-Propio/data/jvm/java-runtime-gamma/bin/java`
  → **OpenJDK 17.0.15 (Microsoft build), 96 MB** — es justo el que pide el JSON.

## 4. Los 19 placeholders a resolver

`assets_index_name`, `assets_root`, `auth_access_token`, `auth_player_name`,
`auth_uuid`, `auth_xuid`, `classpath`, `classpath_separator`, `clientid`,
`game_directory`, `launcher_name`, `launcher_version`, `library_directory`,
`natives_directory`, `resolution_height`, `resolution_width`, `user_type`,
`version_name`, `version_type`.

Clave para offline: `auth_uuid` = UUID v3 de `OfflinePlayer:<nombre>` (debe ser
**estable** para que el jugador conserve inventario y progreso).

## 5. Estructura del proyecto

```
Launcher-Propio/
├── src/com/dosalmas/launcher/   código fuente Java
│   ├── Main.java                línea de comandos (--check, --print, --cp, --launch)
│   ├── Cli.java                 verbos en JSON para agentes y para MCP
│   ├── LauncherWindow.java      ventana principal: navegación y los dos modos
│   ├── Tema.java                paleta, tipografía y componentes propios
│   ├── PanelJugar.java          pantalla de jugar (única en modo simple)
│   ├── PanelMods.java           activar/desactivar mods del pack
│   ├── ModsServicio.java        lectura de mods.toml y movimiento de jars
│   ├── PanelSkins.java          apariencia y limitaciones honestas
│   ├── SkinsServicio.java       biblioteca local de PNG
│   ├── PanelAjustes.java        ajustes embebidos (sustituye a SettingsDialog)
│   ├── PanelRegistros.java      consola en vivo y registros guardados
│   ├── Config.java              configuración persistente
│   ├── Rutas.java               detección de pack, assets, versión y Java
│   ├── Verificador.java         comprobaciones previas al lanzamiento
│   ├── Mensajes.java            errores técnicos -> frases accionables
│   ├── Registro.java            log de cada partida, en disco
│   ├── MinecraftLauncher.java   núcleo: classpath, argumentos, arranque
│   └── Json.java                parser JSON sin dependencias
├── build/                       clases compiladas
├── dist/                        el .jar final distribuible
├── data/jvm/java-runtime-gamma/ JRE 17 para empaquetar
└── docs/                        este diseño y referencias
```

## 6. Objetivos de producto

- **Modo simple** (para Isa, Windows): escribir nombre → botón JUGAR. Nada más.
- **Modo avanzado** (para Steven): consola en vivo, RAM, argumentos JVM, ruta de
  Java, selector de instancia.
- **Estabilidad**: verificar integridad antes de lanzar; mensajes de error claros
  en vez de crashes mudos.
- **Distribución**: un paquete con el launcher + JRE para Windows, sin instalar nada.

## 7. Hallazgos críticos de la Fase 1 (verificados en disco)

1. **El JSON NO es autocontenido para el classpath.** Los 9 jars de Forge/FML
   (`fmlcore`, `forge-…-client`, `forge-…-universal`, `client-…-slim/extra/srg`,
   `javafmllanguage`, `mclanguage`, `lowcodelanguage`) viven **solo** en
   `TLauncherAdditional.json` → `additionalFiles[]`. Sin fusionarlos, Forge
   arranca sin FML y revienta con `ClassNotFoundException`.
   *El `.txt` de mappings de esa lista NO va al classpath.*
2. **En Linux no falta ninguna librería**: de las 117 declaradas, 81 aplican tras
   evaluar *rules* y las 81 están presentes. (Las "23 faltantes" eran de otros SO.)
   En Windows harían falta las natives-windows de LWJGL.
3. **`downloads.artifact.path` es inconsistente** (36 de 117 ya traen el prefijo
   `libraries/`). La ruta se recalcula desde el nombre Maven; `path` es respaldo.
4. **El jar de versión es el `client.jar` vanilla** (SHA-1 idéntico a
   `downloads.client`) y no aparece en `libraries` → se añade por convención.
5. **Los assets ya existen completos** en el store de Prism
   (`~/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/assets`,
   index 5 + 625 MB) → no hay que descargar 640 MB.
6. **Reglas**: sin reglas = permitido; con reglas el veredicto parte de *denegado*
   y gana la última que coincide.
7. Falta `liblwjgl_tinyfd.so` en natives (carga perezosa; solo afecta diálogos
   nativos de archivo). Extraíble de `lwjgl-tinyfd-3.3.1-natives-linux.jar`.
8. **Los jars `client-…-slim.jar` y `client-…-srg.jar` NO van al classpath.**
   Descubierto lanzando de verdad: incluirlos provoca
   `ResolutionException: Modules minecraft and client export package …`.
   FML los localiza solo, vía `-DlibraryDirectory`.
   **Cómo saber qué sí va:** el propio `-DignoreList` del JSON enumera lo que
   Forge espera en el classpath pero no quiere como módulo
   (`client-extra`, `fmlcore`, `javafmllanguage`, `lowcodelanguage`,
   `mclanguage`, `forge-`, `<version>.jar`). Lo que no aparece ahí —como
   `slim` y `srg`— simplemente no debe estar.

## 8. Estado

- [x] Fase 0 — decisiones de arquitectura y auditoría inicial
- [x] Fase 1 — spec técnico, auditoría del pack, diseño UI, plan de distribución
- [x] **Fase 2 — núcleo funcionando y VALIDADO EN VIVO** (`Json`, `MinecraftLauncher`, `Main`)
  - compila con `--release 17`; `dist/DosAlmasLauncher.jar` (20 KB)
  - `--check` pasa todas las verificaciones · classpath de **89 entradas**
  - `--print` genera 62 argumentos con **0 placeholders sin resolver**
  - UUID offline estable (`OfflinePlayer:<nombre>`)
  - **2026-07-24: lanzó el juego real.** Cargó los 134 mods, inicializó LWJGL y
    OpenAL, y **conectó a `minecraft.stevenvallejo.com:25565`**. Sin cuenta
    Microsoft. Cerrado limpiamente por timeout de la prueba.
- [x] **Fase 3 — interfaz Swing + consola en vivo** (2026-07-24)
  - modo simple (nombre + JUGAR) y modo avanzado (consola, RAM, argumentos JVM)
  - **verificación previa**: el botón JUGAR no arranca nada si falta una pieza;
    en su lugar se explica qué falta y se ofrece abrir Ajustes
  - **registro persistente** en `<config>/logs/launcher-<fecha>.log`, con el
    comando exacto y el entorno; se conservan las 10 últimas partidas. Es la
    única forma de diagnosticar un fallo de la máquina virtual, que ocurre
    antes de que el juego escriba su propio `latest.log`
  - cerrar el launcher con el juego abierto pregunta antes; no se puede lanzar
    dos veces sobre la misma carpeta
- [x] **Fase 3b — portabilidad real** (2026-07-24)
  - `Rutas`: detección de pack, assets, versión y Java. **Cero rutas fijas**
    (`grep -rn "/home/stev" src/` da 0 resultados)
  - `Verificador`: comprueba carpeta escribible, versión, natives, Java (que la
    versión mayor sea la que pide el pack), assets, librerías que faltan y los
    jars de `bootstraplauncher`/`securejarhandler`
  - filtro de **arquitectura** en los clasificadores Maven: sin él, en Windows
    entrarían también las natives de x86 y arm64 (21 en vez de 7)
  - `artifact` se lee en la raíz de cada librería, que es donde lo pone este
    pack, además de bajo `downloads` (formato de Mojang)
  - **recolector de basura concurrente** (Shenandoah) cuando el Java lo admite:
    Distant Horizons lo pide expresamente para que la imagen no dé tirones
- [ ] Fase 4 — empaquetado Windows y entrega a Isa

### Lo que falta para Windows (medido, no estimado)

| Pieza | Tamaño | De dónde sale |
|---|---|---|
| natives de LWJGL para Windows x64 (7 jars) | **1,5 MB** | URL oficial ya presente en el JSON |
| JRE 17 de Windows | ~45 MB | Adoptium/Temurin |
| librerías comunes | 139 MB | se copian del pack de Linux |
| assets | 625 MB | se copian del pack de Linux |

El pack de Isa hoy solo trae `mods` y `config` porque TLauncher bajaba el resto.

### Cómo probar

```bash
cd /home/stev/Minecraft/Launcher-Propio
./build.sh                                         # compila
java -jar dist/DosAlmasLauncher.jar --check        # verifica (autodetecta todo)
java -jar dist/DosAlmasLauncher.jar --print        # ver el comando
java -jar dist/DosAlmasLauncher.jar               # abrir la ventana
java -jar dist/DosAlmasLauncher.jar --launch --name Stev   # lanzar de verdad
```

Comprobado el 2026-07-24 en las dos máquinas, con el mismo jar y sin argumentos:

- **torre** (CachyOS): detecta `Cliente/RPG-Dos-Almas`, los assets del almacén
  de Prism y el JRE 17 de `data/jvm`. 88 entradas de classpath.
- **portátil** (Fedora 42, Intel Iris Xe): detecta `~/Minecraft/RPG-Dos-Almas`,
  sus assets propios y **su `jre17` en lugar del Java 21 del sistema**. Lanzó el
  juego real: 143 mods y entrada al mundo en 88 s.
