# Contrato de UI — Launcher RPG Dos Almas

Este documento fija dos cosas para que 5 agentes puedan escribir un panel cada
uno **en paralelo, sin verse entre sí**, y el integrador solo tenga que
instanciarlos:

1. El sistema de diseño (`Tema.java`, ya escrito y compilado).
2. El contrato exacto de cada panel: constructor, métodos públicos y quién es
   dueño de qué.

Si tu panel necesita algo que no está aquí, **no inventes una llamada a otro
panel**: descríbelo en tu informe final y el integrador decide.

## 1. Dirección visual (resumen)

Fondo casi negro cálido (`Tema.FONDO` / `FONDO_ALTO`, degradado + viñeta + grano
sutil generado por código) con un único acento dorado (`Tema.ACENTO`,
`RGB(198,155,71)`, la marca ya establecida del modpack). El dorado se reserva
para la acción principal y los estados; todo lo demás vive en grises cálidos
(`SUPERFICIE`, `SUPERFICIE_ELEVADA`, `BORDE`) para que ese dorado destaque.
Tipografía: solo fuentes lógicas (`Font.SANS_SERIF`, `Font.MONOSPACED`), para
que Windows y Linux vean el mismo layout. Radios de 8/14/20 px, espaciado en
múltiplos de 4 (`ESPACIO_XS/PEQUENO/MEDIO/GRANDE/XL`). Sin el foco azul feo de
Swing: todos los componentes propios pintan su propio estado con Java2D.

`Tema.java` ya está en
`/home/stev/Minecraft/Launcher-Propio/src/com/dosalmas/launcher/Tema.java`,
compila solo y no depende de ningún panel. Import estático recomendado:

```java
import com.dosalmas.launcher.Tema;
```

(no hay que importar nada más: todo vive en clases anidadas de `Tema`, ver
sección 3).

## 2. Navegación

Hoy existe un `JSplitPane` con "modo simple" (Isa) / "modo avanzado" (Steven).
Con 5 secciones eso ya no alcanza. Propuesta:

- **Barra lateral izquierda fija**, ancho ~208 px, pintada con
  `Tema.SUPERFICIE` (un tono más oscura que el contenido para que se lea como
  un panel de navegación aparte). Arriba, el nombre del modpack en
  `Tema.titulo()` color `Tema.ACENTO`. Debajo, 5 botones de navegación propios
  (uno por panel), apilados verticalmente, cada uno con:
  - texto de la sección;
  - una franja vertical de 3 px en `Tema.ACENTO` a la izquierda cuando está
    seleccionado;
  - fondo `Tema.SUPERFICIE_ELEVADA` cuando está seleccionado u hover, fondo
    transparente en reposo.
  Esto **no es parte del contrato de los paneles**: lo construye el
  integrador en la ventana (`LauncherWindow`), no cada panel.
- **Área de contenido a la derecha**, un `CardLayout` que muestra el panel de
  la sección activa. Cada panel ya trae su propio scroll/estructura interna;
  el integrador solo lo añade a una carta.
- **Modo simple vs. modo avanzado** (`Config.advancedMode`):
  - **Simple** (Isa, valor por defecto en una instalación nueva): la barra
    lateral se oculta por completo. La ventana se reduce a una sola tarjeta
    centrada que contiene **exactamente** `PanelJugar` — nombre, botón JUGAR,
    insignia de estado. Nada de pestañas, nada de consola. Ventana pequeña
    (como hoy: ~480×) .
  - **Avanzado** (Steven, se activa desde `PanelAjustes`): aparece la barra
    lateral completa con las 5 secciones y la ventana crece (~900×640).
    `PanelJugar` es **el mismo panel**, reutilizado dentro del `CardLayout`;
    no hay dos implementaciones.
  - El cambio de modo lo decide el usuario dentro de `PanelAjustes` (una
    casilla "Modo avanzado"). `PanelAjustes` no reconstruye la ventana él
    mismo: llama a su callback `alCambiarModo` (ver contrato abajo) y es
    `LauncherWindow` quien decide mostrar/ocultar la barra lateral y
    redimensionar. Así ningún panel manipula la ventana directamente.
- Isa **nunca** tiene que ver la barra lateral para poder jugar: con
  `advancedMode == false` (el valor por defecto en `Config`), lo primero que
  abre ya es la tarjeta simple.

## 3. Componentes disponibles en `Tema`

Todos son clases anidadas públicas de `Tema`, pintadas con Java2D,
antialias activado, sin depender de ningún panel:

| Clase | Qué es | Constructor |
|---|---|---|
| `Tema.PanelFondo` | fondo de ventana (degradado + viñeta + grano) | `new Tema.PanelFondo()` o `new Tema.PanelFondo(LayoutManager)` |
| `Tema.BotonPrimario` | botón de acción principal, relleno dorado | `new Tema.BotonPrimario(String texto)` |
| `Tema.BotonSecundario` | botón de acompañamiento, contorno | `new Tema.BotonSecundario(String texto)` |
| `Tema.Tarjeta` | panel de superficie con esquinas redondeadas | `new Tema.Tarjeta()` o `new Tema.Tarjeta(LayoutManager)` |
| `Tema.CampoTexto` | `JTextField` con borde redondeado, placeholder y error | `new Tema.CampoTexto()` / `new Tema.CampoTexto(int columnas)` — `setSugerencia(String)`, `setError(boolean)` |
| `Tema.Separador` | línea divisoria, con etiqueta de sección opcional | `new Tema.Separador()` / `new Tema.Separador(String tituloSeccion)` |
| `Tema.Insignia` | chip de estado (punto de color + texto) | `new Tema.Insignia(String texto, Tema.Estado estado)` — `setTexto`, `setEstado` |
| `Tema.Estado` | enum `EXITO, AVISO, PELIGRO, NEUTRO` | usado por `Insignia` y recomendado para traducir `Verificador.Nivel` a color |

Colores, fuentes y espaciados relevantes para los paneles: `Tema.FONDO`,
`Tema.SUPERFICIE`, `Tema.SUPERFICIE_ELEVADA`, `Tema.BORDE`, `Tema.BORDE_SUAVE`,
`Tema.TEXTO`, `Tema.TEXTO_SUAVE`, `Tema.TEXTO_DEBIL`, `Tema.ACENTO`,
`Tema.ACENTO_FUERTE`, `Tema.ACENTO_APAGADO`, `Tema.ACENTO_TEXTO`, `Tema.EXITO`,
`Tema.AVISO`, `Tema.PELIGRO`; `Tema.titulo()`, `Tema.subtitulo()`,
`Tema.cuerpo()`, `Tema.cuerpoNegrita()`, `Tema.etiqueta()`, `Tema.mono()`;
`Tema.ESPACIO_XS/PEQUENO/MEDIO/GRANDE/XL`; `Tema.RADIO_PEQUENO/MEDIO/GRANDE`.

`Tema.aplicar()` se llama **una sola vez**, en el arranque de la ventana,
antes de crear ningún componente (ya ajusta `UIManager` para que
`JOptionPane`, tooltips, scrollbars, listas y combos por defecto hereden el
aspecto). Ningún panel debe volver a llamarlo.

## 4. Reglas del contrato

- Todos los paneles **extienden `JPanel`** y reciben lo que necesiten por
  constructor (nunca variables globales ni singletons propios).
- **Ningún panel llama a otro panel directamente** ni guarda una referencia a
  otro panel. Si un panel necesita avisar a la ventana de un cambio (por
  ejemplo, "cambié la instalación, revisa si sigue arrancando"), recibe un
  **callback funcional** (`Runnable` o `Consumer<T>`) por constructor y lo
  invoca. Es la ventana (`LauncherWindow`, el integrador) quien decide qué
  otros paneles refrescar.
- **Dueño de `Config`**: `LauncherWindow` carga `Config.load()` una vez al
  abrir y pasa **la misma instancia** (no una copia) a todos los paneles que
  la necesiten. Los paneles leen y escriben campos de esa instancia
  directamente. Cuando un panel termina un cambio que debe persistir, **el
  propio panel llama a `config.save()`** (ya es la operación atómica que
  expone `Config`) y despues invoca su callback de cambio para que la
  ventana decida qué más `refrescar()`. La ventana nunca copia el `Config` de
  vuelta a los paneles: como es la misma referencia, ya está actualizado en
  todos.
- **Propagación de un cambio**: cada panel expone un método público
  `refrescar()` sin argumentos que recarga su estado leyendo de nuevo disco y
  `Config`. La ventana mantiene una lista de los 5 paneles instanciados y, al
  recibir cualquier callback de "algo cambió", decide a cuáles de los otros 4
  llamarles `refrescar()` (normalmente: cambios en `PanelAjustes` o
  `PanelMods` deben refrescar `PanelJugar`, que es quien muestra si la
  instalación está lista para jugar).
- Ningún panel abre un `JDialog`/`JOptionPane` que bloquee para pedirle algo a
  otro panel: si hace falta cambiar de sección (p. ej. "abre Ajustes"), se usa
  un callback de navegación (`Runnable`) que la ventana traduce en "selecciona
  esta carta del `CardLayout`".
- Ningún panel inventa una función que no puede funcionar de verdad (regla 9
  del proyecto). En concreto, **no hay ningún mod de skins instalado en el
  pack**: `PanelSkins` debe ser honesto sobre esa limitación (ver su
  contrato) en vez de simular un cambio de skin que no hace nada.

## 5. Contrato por panel

Firmas **literales**: cópialas tal cual, no las reinterpretes.

### `PanelJugar`

Responsabilidad: nombre de jugador, botón JUGAR, insignia de estado de la
instalación, barra de progreso mientras lanza, y el propio lanzamiento del
juego (reutiliza `MinecraftLauncher`, `Verificador`, `Registro`, igual que hoy
hace `LauncherWindow.LaunchWorker`). Es el único panel visible en modo simple.

```java
public final class PanelJugar extends JPanel {
    public PanelJugar(Config config,
                       Runnable alGuardarConfig,
                       Runnable alIrAAjustes,
                       Consumer<String> alLineaDeConsola);

    /** Vuelve a leer config y a verificar la instalacion (Verificador.revisar). */
    public void refrescar();
}
```

- `alGuardarConfig`: se invoca despues de que el panel cambie
  `config.playerName` y llame `config.save()`. La ventana no tiene por que
  reaccionar (nadie mas depende del nombre), pero se expone por
  consistencia y porque un futuro panel podria necesitarlo.
- `alIrAAjustes`: se invoca cuando el jugador pulsa "Abrir Ajustes" en el
  dialogo de problemas bloqueantes. La ventana cambia la carta activa del
  `CardLayout` a `PanelAjustes` (en modo simple, antes fuerza el cambio a modo
  avanzado para que Ajustes sea alcanzable).
- `alLineaDeConsola`: se invoca con cada linea de salida del proceso del
  juego mientras esta lanzando (una linea por llamada). La ventana la
  reenvia a `PanelRegistros.anexarLinea(linea)`. `PanelJugar` NUNCA importa
  `PanelRegistros`.

### `PanelMods`

Responsabilidad: listar los mods de `mods/` y `mods-disabled/` dentro del
`gameDir` del `Config`, buscar por nombre y activar/desactivar moviendo el
jar entre esas dos carpetas (la convencion ya establecida en el pack; ver
`CLAUDE.md` del proyecto). No decide nunca añadir o quitar mods del pack, solo
alternar los que ya existen en disco.

```java
public final class PanelMods extends JPanel {
    public PanelMods(Config config, Runnable alCambiarMods);

    /** Relee mods/ y mods-disabled/ desde disco. */
    public void refrescar();
}
```

- `alCambiarMods`: se invoca cada vez que el panel mueve un jar de carpeta.
  La ventana debe llamar `PanelJugar.refrescar()` (activar/desactivar un mod
  puede cambiar si la instalacion arranca) tras recibir este callback.

### `PanelSkins`

Responsabilidad: gestionar la apariencia del jugador **dentro de lo que
realmente existe**. Hoy el pack **no trae ningun mod de skins** (ni
CustomSkinLoader ni equivalente) y el lanzamiento es offline, sin servidor de
skins de Mojang. Por eso este panel debe:

- explicar con claridad esa limitacion (no fingir un selector que no hace
  nada);
- mostrar el UUID offline del jugador actual (`MinecraftLauncher.offlineUuid`)
  y explicar que Minecraft asigna el modelo clasico/slim segun ese UUID, sin
  que el launcher pueda elegirlo;
- si quiere ofrecer algo accionable de verdad: un boton que abra en el
  explorador de archivos la carpeta `resourcepacks/` del `gameDir` (via
  `Desktop.getDesktop().open(...)`), que es el mecanismo real y ya soportado
  por Minecraft/Forge para paquetes de skin en 1.20.1.
- Si en el futuro se instala un mod de skins, este panel sera el que gane
  funcionalidad; hasta entonces, un panel honesto que explica el limite es
  preferible a uno que simula un cambio que no persiste en ningun sitio.

```java
public final class PanelSkins extends JPanel {
    public PanelSkins(Config config);

    /** Vuelve a leer el nombre/UUID actual y si existe resourcepacks/. */
    public void refrescar();
}
```

Este panel no cambia `Config` y por tanto no recibe ningun callback: es de
solo lectura sobre el estado actual del jugador y del disco.

### `PanelAjustes`

Responsabilidad: memoria (min/max), rutas (gameDir, assetsRoot, javaBin),
argumentos JVM extra, resolucion de ventana y el interruptor de modo
avanzado/simple. Sustituye a `SettingsDialog` como panel embebido (deja de
ser un dialogo modal aparte).

```java
public final class PanelAjustes extends JPanel {
    public PanelAjustes(Config config,
                         Runnable alGuardarConfig,
                         Runnable alCambiarModo);

    /** Vuelve a leer config y a detectar rutas (Config.completarConDeteccion). */
    public void refrescar();
}
```

- `alGuardarConfig`: se invoca despues de que el panel llame `config.save()`
  tras cualquier cambio de memoria/rutas/argumentos. La ventana debe llamar
  `refrescar()` en `PanelJugar` y `PanelMods` (una ruta nueva puede arreglar
  o romper la instalacion, o apuntar a otra carpeta de mods).
- `alCambiarModo`: se invoca especificamente cuando cambia
  `config.advancedMode` (ya guardado). La ventana reconstruye la
  presentacion: muestra u oculta la barra lateral y redimensiona, sin volver
  a crear ningun panel.

### `PanelRegistros`

Responsabilidad: consola en vivo (alimentada por la ventana, que reenvia lo
que recibe de `PanelJugar` via `alLineaDeConsola`) y el listado de registros
guardados en `Rutas.carpetaLogs()` (los que hoy escribe `Registro`), con
posibilidad de abrirlos, copiarlos o guardarlos aparte.

```java
public final class PanelRegistros extends JPanel {
    public PanelRegistros(Config config);

    /** Vuelve a listar los archivos de Rutas.carpetaLogs() desde disco. */
    public void refrescar();

    /** Añade una linea a la consola en vivo (llamada por la ventana). */
    public void anexarLinea(String linea);

    /** Limpia la consola en vivo; la ventana la llama al empezar cada partida. */
    public void limpiarConsola();
}
```

Este panel nunca lanza el juego ni sabe que `PanelJugar` existe: solo recibe
lineas de texto que la ventana le entrega.

## 6. Quien instancia que (resumen para el integrador)

```java
Config config = Config.load();

PanelRegistros registros = new PanelRegistros(config);

PanelJugar jugar = new PanelJugar(config,
        () -> {},                          // nada mas que refrescar depende del nombre
        () -> mostrarSeccion("ajustes"),
        registros::anexarLinea);

PanelMods mods = new PanelMods(config, () -> jugar.refrescar());

PanelSkins skins = new PanelSkins(config);

PanelAjustes ajustes = new PanelAjustes(config,
        () -> { jugar.refrescar(); mods.refrescar(); },
        () -> aplicarModo(config.advancedMode));
```

`aplicarModo` y `mostrarSeccion` son metodos privados de `LauncherWindow`, no
de ningun panel.
