package com.dosalmas.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verbos de linea de comandos pensados para que un agente automatizado (no
 * una persona delante de una pantalla) controle el launcher. Cada verbo
 * imprime un unico documento JSON por stdout y termina con un codigo de
 * salida que se puede interpretar sin parsear texto:
 *
 *   0 = todo correcto
 *   1 = problema de la instalacion o de la operacion pedida
 *   2 = error de uso (verbo o argumento invalido)
 *
 * Esta clase no abre ninguna ventana ni depende de Swing: es deliberadamente
 * la misma logica que ya usan la GUI y el modo --check/--launch de Main,
 * expuesta de forma que un programa (no una persona) pueda leerla.
 *
 * Main.java debe reconocer estos verbos y despachar aqui antes de tratarlos
 * como los suyos propios. Ver el comentario de integracion al final del
 * archivo.
 */
public final class Cli {

    private Cli() {}

    private static final List<String> VERBOS = List.of(
            "estado", "mods", "activar-mod", "desactivar-mod",
            "registros", "registro", "config", "config-set", "jugar",
            "generar-perfil", "sincronizar");

    /** true si args[0] es uno de los verbos que atiende esta clase. */
    public static boolean reconoce(String verbo) {
        return verbo != null && VERBOS.contains(verbo);
    }

    /** Punto de entrada: args[0] ya es un verbo reconocido por {@link #reconoce}. */
    public static void despachar(String[] args) {
        String verbo = args[0];
        String[] resto = Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (verbo) {
                case "estado":         cmdEstado();               break;
                case "mods":           cmdMods();                 break;
                case "activar-mod":    cmdActivarMod(resto, true);  break;
                case "desactivar-mod": cmdActivarMod(resto, false); break;
                case "registros":      cmdRegistros();            break;
                case "registro":       cmdRegistro(resto);        break;
                case "config":         cmdConfig();               break;
                case "config-set":     cmdConfigSet(resto);       break;
                case "jugar":          cmdJugar(resto);           break;
                case "generar-perfil": cmdGenerarPerfil();        break;
                case "sincronizar":    cmdSincronizar();          break;
                default:               error(2, "Verbo desconocido: " + verbo);
            }
        } catch (Exception e) {
            error(1, Mensajes.explicar(e));
        }
    }

    // ---------------------------------------------------------------- estado

    private static void cmdEstado() throws IOException {
        Config cfg = Config.load();
        MinecraftLauncher.Profile p = cfg.toProfile();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("gameDir", strOf(p.gameDir));
        out.put("gameDirExiste", p.gameDir != null && Files.isDirectory(p.gameDir));
        out.put("versionId", p.versionId);
        out.put("assetsRoot", strOf(p.assetsRoot));
        out.put("assetsExiste", p.assetsRoot != null && Files.isDirectory(p.assetsRoot));
        out.put("javaBin", strOf(p.javaBin));
        out.put("javaVersionMayor", p.javaBin != null ? Rutas.versionMayorDe(p.javaBin) : null);

        MinecraftLauncher ml = null;
        if (p.gameDir != null && Files.isRegularFile(p.versionJson())) {
            try {
                ml = new MinecraftLauncher(p);
            } catch (IOException e) {
                // se refleja mas abajo como problema de instalacion, no aqui
            }
        }
        if (ml != null) {
            try {
                out.put("classpathEntradas", ml.buildClasspath().size());
                out.put("javaRequerido", ml.requiredJavaMajor());
            } catch (IOException e) {
                out.put("classpathEntradas", null);
            }
        } else {
            out.put("classpathEntradas", null);
        }

        EstadoJuego ej = EstadoJuego.leer();
        boolean jugando = ej != null && ej.vivo();
        out.put("jugando", jugando);
        out.put("pid", jugando ? ej.pid : null);
        out.put("registroActual", jugando ? ej.registro : null);

        List<Verificador.Problema> problemas = Verificador.revisar(p, ml);
        out.put("problemas", problemasAJson(problemas));

        imprimir(out);
        if (Verificador.hayBloqueantes(problemas)) System.exit(1);
    }

    // ------------------------------------------------------------------ mods

    private static void cmdMods() {
        Config cfg = Config.load();
        Path gameDir = cfg.toProfile().gameDir;
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            error(1, "No se encontro la carpeta del juego; revisa 'estado' o la configuracion.");
            return;
        }
        ModsServicio servicio = new ModsServicio(gameDir);
        ModsServicio.Listado listado = servicio.listar();

        List<Object> activos = new ArrayList<>();
        List<Object> desactivados = new ArrayList<>();
        for (ModsServicio.ModInfo m : listado.mods) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("archivo", m.archivo);
            mm.put("nombre", m.nombre);
            mm.put("version", m.version);
            mm.put("tamanoBytes", m.tamanoBytes);
            mm.put("tamano", m.tamanoFormateado());
            (m.activo ? activos : desactivados).add(mm);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activos", activos);
        out.put("desactivados", desactivados);
        out.put("errores", listado.errores);
        imprimir(out);
        if (!listado.errores.isEmpty()) System.exit(1);
    }

    private static void cmdActivarMod(String[] resto, boolean activar) {
        if (resto.length < 1) {
            error(2, "Uso: " + (activar ? "activar-mod" : "desactivar-mod") + " <archivo.jar>");
            return;
        }
        String archivo = resto[0];
        Config cfg = Config.load();
        Path gameDir = cfg.toProfile().gameDir;
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            error(1, "No se encontro la carpeta del juego; revisa 'estado' o la configuracion.");
            return;
        }
        ModsServicio servicio = new ModsServicio(gameDir);
        ModsServicio.Resultado r = activar ? servicio.activar(archivo) : servicio.desactivar(archivo);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exito", r.exito);
        out.put("mensaje", r.mensaje);
        imprimir(out);
        if (!r.exito) System.exit(1);
    }

    // -------------------------------------------------------------- registros

    private static void cmdRegistros() throws IOException {
        Path dir = Rutas.carpetaLogs();
        List<Map<String, Object>> registros = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.log")) {
                for (Path f : ds) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nombre", f.getFileName().toString());
                    m.put("fecha", Files.getLastModifiedTime(f).toInstant().toString());
                    m.put("tamanoBytes", Files.size(f));
                    registros.add(m);
                }
            }
        }
        // Mas reciente primero: el nombre lleva el sello de fecha en el propio texto.
        registros.sort((a, b) -> ((String) b.get("nombre")).compareTo((String) a.get("nombre")));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("carpeta", dir.toString());
        out.put("registros", registros);
        imprimir(out);
    }

    private static void cmdRegistro(String[] resto) throws IOException {
        if (resto.length < 1) {
            error(2, "Uso: registro <nombre> [--lineas N]");
            return;
        }
        String nombre = resto[0];
        // Solo un nombre de archivo suelto: nada que permita salir de la carpeta de registros.
        if (nombre.contains("/") || nombre.contains("\\") || nombre.contains("..")) {
            error(2, "Nombre de registro invalido: " + nombre);
            return;
        }
        Path f = Rutas.carpetaLogs().resolve(nombre);
        if (!Files.isRegularFile(f)) {
            error(1, "No se encontro el registro: " + nombre);
            return;
        }
        List<String> todas = Files.readAllLines(f, StandardCharsets.UTF_8);
        List<String> lineas = todas;
        String nStr = arg(resto, "--lineas");
        if (nStr != null) {
            int n;
            try {
                n = Integer.parseInt(nStr);
            } catch (NumberFormatException e) {
                error(2, "--lineas debe ser un numero entero");
                return;
            }
            int desde = Math.max(0, todas.size() - n);
            lineas = todas.subList(desde, todas.size());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nombre", nombre);
        out.put("totalLineas", todas.size());
        out.put("lineas", lineas);
        imprimir(out);
    }

    // ----------------------------------------------------------------- config

    private static final List<String> CLAVES_VALIDAS = List.of(
            "playerName", "gameDir", "versionId", "assetsRoot", "javaBin",
            "maxMemoryMb", "minMemoryMb", "extraJvmArgs", "advancedMode", "width", "height");

    private static void cmdConfig() {
        imprimir(configAMapa(Config.load()));
    }

    private static void cmdConfigSet(String[] resto) throws IOException {
        if (resto.length < 2) {
            error(2, "Uso: config-set <clave> <valor>. Claves validas: " + String.join(", ", CLAVES_VALIDAS));
            return;
        }
        String clave = resto[0];
        String valor = resto[1];
        if (!CLAVES_VALIDAS.contains(clave)) {
            error(2, "Clave desconocida: " + clave + ". Claves validas: " + String.join(", ", CLAVES_VALIDAS));
            return;
        }
        Config cfg = Config.load();
        try {
            switch (clave) {
                case "playerName":   cfg.playerName = valor; break;
                case "gameDir":      cfg.gameDir = valor; break;
                case "versionId":    cfg.versionId = valor; break;
                case "assetsRoot":   cfg.assetsRoot = valor; break;
                case "javaBin":      cfg.javaBin = valor; break;
                case "extraJvmArgs": cfg.extraJvmArgs = valor; break;
                case "advancedMode": cfg.advancedMode = Boolean.parseBoolean(valor); break;
                case "maxMemoryMb":  cfg.maxMemoryMb = Integer.parseInt(valor); break;
                case "minMemoryMb":  cfg.minMemoryMb = Integer.parseInt(valor); break;
                case "width":        cfg.width  = "null".equalsIgnoreCase(valor) ? null : Integer.valueOf(valor); break;
                case "height":       cfg.height = "null".equalsIgnoreCase(valor) ? null : Integer.valueOf(valor); break;
                default: // inalcanzable: ya se valido contra CLAVES_VALIDAS
            }
        } catch (NumberFormatException e) {
            error(2, "Valor invalido para '" + clave + "': " + valor);
            return;
        }
        cfg.save();
        imprimir(configAMapa(cfg));
    }

    private static Map<String, Object> configAMapa(Config cfg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("playerName", cfg.playerName);
        m.put("gameDir", cfg.gameDir);
        m.put("versionId", cfg.versionId);
        m.put("assetsRoot", cfg.assetsRoot);
        m.put("javaBin", cfg.javaBin);
        m.put("maxMemoryMb", cfg.maxMemoryMb);
        m.put("minMemoryMb", cfg.minMemoryMb);
        m.put("extraJvmArgs", cfg.extraJvmArgs);
        m.put("advancedMode", cfg.advancedMode);
        m.put("width", cfg.width);
        m.put("height", cfg.height);
        return m;
    }

    // ------------------------------------------------------------------ jugar

    private static void cmdJugar(String[] resto) throws IOException {
        Config cfg = Config.load();
        MinecraftLauncher.Profile p = cfg.toProfile();

        String name = arg(resto, "--name");
        if (name != null) p.playerName = name;
        String ram = arg(resto, "--ram");
        if (ram != null) {
            try {
                p.maxMemoryMb = Integer.parseInt(ram);
            } catch (NumberFormatException e) {
                error(2, "--ram debe ser un numero de MB");
                return;
            }
        }

        List<Verificador.Problema> problemas = Verificador.revisar(p);
        if (Verificador.hayBloqueantes(problemas)) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("exito", false);
            out.put("problemas", problemasAJson(problemas));
            imprimir(out);
            System.exit(1);
            return;
        }

        MinecraftLauncher ml = new MinecraftLauncher(p);
        List<String> cmd = ml.buildCommand();

        // El encabezado (comando, rutas, version de Java...) se escribe con la
        // misma clase que usa el resto del launcher; despues se suelta el
        // archivo para que el propio proceso del juego escriba en el directamente.
        Registro registro = Registro.abrir(p, cmd);
        Path logFile = registro.archivo();
        registro.close();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(p.gameDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile != null
                ? ProcessBuilder.Redirect.appendTo(logFile.toFile())
                : ProcessBuilder.Redirect.DISCARD);
        Process proc = pb.start();

        EstadoJuego.guardar(proc.pid(), logFile, p.playerName);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exito", true);
        out.put("pid", proc.pid());
        out.put("registro", logFile != null ? logFile.toString() : null);
        imprimir(out);
    }

    // ------------------------------------------------------- estado del juego
    //
    // Un jugar en segundo plano no tiene proceso Java propio que lo vigile
    // (el que lo lanzo ya termino su ejecucion). Este archivo pequeno es la
    // unica forma de que 'estado' sepa que hay una partida en curso; se
    // verifica con ProcessHandle, no de memoria, asi que un pid reciclado por
    // el sistema tras un cierre no se confunde con la partida.

    private static final class EstadoJuego {
        long pid;
        String registro;
        String jugador;
        String iniciado;

        static Path archivo() { return Rutas.carpetaConfig().resolve("juego-en-curso.json"); }

        static EstadoJuego leer() {
            Path f = archivo();
            if (!Files.isRegularFile(f)) return null;
            try {
                Map<String, Object> obj = Json.parseObject(f);
                EstadoJuego e = new EstadoJuego();
                e.pid = Json.num(obj, "pid", -1);
                e.registro = Json.str(obj, "registro");
                e.jugador = Json.str(obj, "jugador");
                e.iniciado = Json.str(obj, "iniciado");
                return e;
            } catch (Exception ex) {
                return null;
            }
        }

        boolean vivo() {
            if (pid <= 0) return false;
            return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }

        static void guardar(long pid, Path registro, String jugador) throws IOException {
            Path f = archivo();
            Files.createDirectories(f.getParent());
            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("pid", pid);
            obj.put("registro", registro != null ? registro.toString() : null);
            obj.put("jugador", jugador);
            obj.put("iniciado", java.time.Instant.now().toString());
            Files.write(f, Json.writeFlat(obj).getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------------------------------------------------------------- utiles

    private static String arg(String[] args, String key) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) return args[i + 1];
        }
        return null;
    }

    private static String strOf(Path p) { return p != null ? p.toString() : null; }

    private static List<Object> problemasAJson(List<Verificador.Problema> problemas) {
        List<Object> out = new ArrayList<>();
        for (Verificador.Problema pr : problemas) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nivel", pr.nivel.name());
            m.put("titulo", pr.titulo);
            m.put("detalle", pr.detalle);
            m.put("accion", pr.accion);
            m.put("bloquea", pr.bloquea());
            out.add(m);
        }
        return out;
    }

    private static void imprimir(Object valor) {
        System.out.println(toJson(valor));
    }

    private static void error(int codigo, String mensaje) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", mensaje);
        System.out.println(toJson(out));
        System.exit(codigo);
    }

    // ------------------------------------------------- serializador JSON propio
    //
    // Json.writeFlat (de Json.java) solo sirve para objetos de un nivel. Estos
    // verbos necesitan listas y mapas anidados (mods, problemas, lineas de
    // registro...), asi que aqui hay un serializador chico que reutiliza
    // Json.quote para el escapado de texto en vez de reinventarlo.

    @SuppressWarnings("unchecked")
    private static String toJson(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void escribir(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String) {
            sb.append(Json.quote((String) valor));
        } else if (valor instanceof Boolean || valor instanceof Number) {
            sb.append(valor.toString());
        } else if (valor instanceof Map) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) valor).entrySet()) {
                if (!primero) sb.append(',');
                primero = false;
                sb.append(Json.quote(e.getKey())).append(':');
                escribir(e.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof List) {
            sb.append('[');
            boolean primero = true;
            for (Object o : (List<Object>) valor) {
                if (!primero) sb.append(',');
                primero = false;
                escribir(o, sb);
            }
            sb.append(']');
        } else {
            // Path, enum u otro tipo: se serializa como texto, nunca se rompe.
            sb.append(Json.quote(valor.toString()));
        }
    }

    /**
     * Escribe el perfil compartido a partir de esta instalacion. Sirve para
     * tomar un equipo como referencia y llevar su idioma, sus packs de recursos
     * y sus teclas a los demas.
     */
    private static void cmdGenerarPerfil() throws IOException {
        Config cfg = Config.load();
        if (cfg.gameDir == null) error(1, "No se encontro la carpeta del juego.");
        java.nio.file.Path dir = java.nio.file.Paths.get(cfg.gameDir);
        String perfil = Sincronizador.generarDesde(dir);
        java.nio.file.Path destino = dir.resolve(Sincronizador.ARCHIVO_PERFIL);
        java.nio.file.Files.writeString(destino, perfil, java.nio.charset.StandardCharsets.UTF_8);
        long claves = perfil.lines().filter(l -> !l.startsWith("#") && !l.isBlank()).count();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("archivo", destino.toString());
        m.put("claves", claves);
        imprimir(m);
    }

    /** Aplica el perfil compartido ahora mismo (lo mismo que hace al lanzar). */
    private static void cmdSincronizar() {
        Config cfg = Config.load();
        if (cfg.gameDir == null) error(1, "No se encontro la carpeta del juego.");
        Sincronizador.Resultado r = Sincronizador.sincronizar(java.nio.file.Paths.get(cfg.gameDir));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cambios", r.cambios);
        m.put("avisos", r.avisos);
        m.put("huboCambios", r.huboCambios());
        imprimir(m);
    }
}

// ---------------------------------------------------------------------------
// Integracion pendiente en Main.java (archivo que no se ha tocado desde aqui):
//
// En el metodo main(String[] args), justo despues de comprobar args.length==0
// y antes de leer args[0] como "mode" para --check/--print/--cp/--launch,
// anadir:
//
//     if (Cli.reconoce(args[0])) { Cli.despachar(args); return;


//
// Con eso los verbos nuevos (estado, mods, activar-mod, desactivar-mod,
// registros, registro, config, config-set, jugar) quedan disponibles sin
// tocar el resto de Main.java ni sus modos existentes.
// ---------------------------------------------------------------------------
