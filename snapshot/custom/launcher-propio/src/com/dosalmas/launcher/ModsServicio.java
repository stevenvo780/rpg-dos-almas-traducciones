package com.dosalmas.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Logica pura (sin Swing) para listar y alternar los mods del pack. Sigue la
 * convencion ya establecida en el gameDir: los jars activos viven en
 * "mods/" y los desactivados en "mods-disabled/"; activar o desactivar un
 * mod es simplemente moverlo de una carpeta a la otra, nunca borrarlo.
 *
 * No anade ni descarga mods nuevos: solo opera sobre los jars que ya existen
 * en el pack.
 */
public final class ModsServicio {

    /** Datos de un mod, ya sea que este activo o desactivado. */
    public static final class ModInfo implements Comparable<ModInfo> {
        public final String archivo;
        public final String nombre;
        public final String version;
        public final long tamanoBytes;
        public final boolean activo;

        ModInfo(String archivo, String nombre, String version, long tamanoBytes, boolean activo) {
            this.archivo = archivo;
            this.nombre = nombre;
            this.version = version;
            this.tamanoBytes = tamanoBytes;
            this.activo = activo;
        }

        public String tamanoFormateado() { return formatoTamano(tamanoBytes); }

        @Override public int compareTo(ModInfo otro) {
            return this.nombre.compareToIgnoreCase(otro.nombre);
        }
    }

    /** Resultado explicito de una operacion; nunca se lanza una excepcion muda a quien llama. */
    public static final class Resultado {
        public final boolean exito;
        public final String mensaje;

        private Resultado(boolean exito, String mensaje) {
            this.exito = exito;
            this.mensaje = mensaje;
        }

        public static Resultado ok(String mensaje)    { return new Resultado(true, mensaje); }
        public static Resultado error(String mensaje) { return new Resultado(false, mensaje); }
    }

    /**
     * Resultado de un listado: los mods encontrados y los errores no fatales
     * (carpeta ausente, jar ilegible...). Nunca se descarta un problema en
     * silencio: si algo no se pudo leer, queda en {@code errores} para que la
     * interfaz lo muestre.
     */
    public static final class Listado {
        public final List<ModInfo> mods;
        public final List<String> errores;

        Listado(List<ModInfo> mods, List<String> errores) {
            this.mods = mods;
            this.errores = errores;
        }
    }

    private static final class Metadatos {
        final String nombre;
        final String version;
        Metadatos(String nombre, String version) { this.nombre = nombre; this.version = version; }
    }

    private final Path modsDir;
    private final Path modsDisabledDir;

    // Nombre/version deducidos no cambian mientras el archivo no cambie de
    // contenido; cachear evita reabrir 134 jars en cada refresco de la lista.
    private final Map<String, Metadatos> cache = new ConcurrentHashMap<>();

    public ModsServicio(Path gameDir) {
        if (gameDir == null) throw new IllegalArgumentException("gameDir no puede ser null");
        this.modsDir = gameDir.resolve("mods");
        this.modsDisabledDir = gameDir.resolve("mods-disabled");
    }

    public Path modsDir()         { return modsDir; }
    public Path modsDisabledDir() { return modsDisabledDir; }

    // ------------------------------------------------------------- Listado

    /**
     * Lista todos los mods, activos y desactivados, ordenados por nombre
     * legible. Lee metadatos de cada jar (mods.toml / MANIFEST.MF): es
     * trabajo de disco, llamar siempre fuera del hilo de la interfaz.
     *
     * Nunca lanza: un problema de una carpeta o un jar concreto va a
     * {@link Listado#errores} y el resto del listado sigue siendo valido.
     */
    public Listado listar() {
        List<ModInfo> out = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        agregarCarpeta(modsDir, true, out, errores, true);
        agregarCarpeta(modsDisabledDir, false, out, errores, false);
        Collections.sort(out);
        return new Listado(out, errores);
    }

    private void agregarCarpeta(Path carpeta, boolean activo, List<ModInfo> out, List<String> errores,
                                 boolean obligatoria) {
        if (!Files.isDirectory(carpeta)) {
            // mods-disabled/ ausente no es un error: sencillamente no hay
            // ningun mod desactivado todavia. mods/ ausente si lo es: sin
            // ella no hay pack que lanzar.
            if (obligatoria) errores.add("No se encontro la carpeta de mods activos: " + carpeta);
            return;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(carpeta, "*.jar")) {
            for (Path jar : ds) {
                if (Files.isRegularFile(jar)) out.add(leer(jar, activo));
            }
        } catch (IOException e) {
            errores.add("No se pudo leer la carpeta " + carpeta + ": " + e.getMessage());
        }
    }

    private ModInfo leer(Path jar, boolean activo) {
        String archivo = jar.getFileName().toString();
        long tamano;
        try { tamano = Files.size(jar); } catch (IOException e) { tamano = 0L; }
        Metadatos m = cache.computeIfAbsent(archivo, k -> leerMetadatos(jar));
        return new ModInfo(archivo, m.nombre, m.version, tamano, activo);
    }

    // El valor va casi siempre entre comillas dobles; el apostrofe de un
    // nombre como "NoCube's" no puede cortar la captura, asi que solo la
    // comilla doble cierra el valor (las comillas simples son rarisimas en
    // mods.toml y aqui no aparecen).
    private static final Pattern VERSION_TOML = Pattern.compile("(?m)^\\s*version\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern DISPLAYNAME_TOML = Pattern.compile("(?m)^\\s*displayName\\s*=\\s*\"([^\"]*)\"");

    /**
     * Nombre y version de un jar: primero intenta META-INF/mods.toml (exacto);
     * si la version es una plantilla sin resolver ("${file.jarVersion}") la
     * busca en el manifest, que Forge rellena al construir el jar. Si nada de
     * eso existe, cae en una heuristica sobre el propio nombre de archivo.
     */
    private static Metadatos leerMetadatos(Path jar) {
        String nombre = null;
        String version = null;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry toml = zip.getEntry("META-INF/mods.toml");
            if (toml != null) {
                String contenido = leerTexto(zip, toml);
                Matcher mv = VERSION_TOML.matcher(contenido);
                if (mv.find()) version = mv.group(1).trim();
                Matcher mn = DISPLAYNAME_TOML.matcher(contenido);
                if (mn.find()) nombre = mn.group(1).trim();
            }
            if (version != null && version.contains("${")) {
                ZipEntry manifiesto = zip.getEntry("META-INF/MANIFEST.MF");
                version = (manifiesto != null) ? leerImplementationVersion(leerTexto(zip, manifiesto)) : null;
            }
        } catch (IOException e) {
            // jar ilegible o corrupto: se sigue con la heuristica de nombre de archivo
        }
        String archivo = jar.getFileName().toString();
        if (nombre == null || nombre.isBlank()) nombre = nombreDesdeArchivo(archivo);
        if (version == null || version.isBlank()) version = versionDesdeArchivo(archivo);
        return new Metadatos(nombre, version);
    }

    private static String leerTexto(ZipFile zip, ZipEntry entrada) throws IOException {
        try (var in = zip.getInputStream(entrada)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String leerImplementationVersion(String manifiesto) {
        if (manifiesto == null) return null;
        for (String linea : manifiesto.split("\\r?\\n")) {
            if (linea.startsWith("Implementation-Version:")) {
                String v = linea.substring("Implementation-Version:".length()).trim();
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }

    // ------------------------------------------------- Heuristica de archivo
    //
    // Solo se usa cuando el jar no trae mods.toml legible (no deberia pasar
    // en un pack de Forge, pero un jar raro no puede tumbar la lista).

    private static final Pattern VERSION_EN_NOMBRE = Pattern.compile("(\\d+(?:\\.\\d+){1,4}[a-zA-Z0-9+_.-]*)$");
    private static final Pattern INICIO_VERSION = Pattern.compile("^(.*?)[-_ ]v?\\d");

    static String nombreDesdeArchivo(String archivo) {
        String base = quitarExtension(archivo);
        Matcher m = INICIO_VERSION.matcher(base);
        String recortado = m.find() ? m.group(1) : base;
        recortado = recortado.replace('_', ' ').replace('-', ' ').trim();
        return recortado.isEmpty() ? base : recortado;
    }

    static String versionDesdeArchivo(String archivo) {
        Matcher m = VERSION_EN_NOMBRE.matcher(quitarExtension(archivo));
        return m.find() ? m.group(1) : "?";
    }

    private static String quitarExtension(String archivo) {
        return archivo.endsWith(".jar") ? archivo.substring(0, archivo.length() - 4) : archivo;
    }

    // -------------------------------------------------------- Activar/Desactivar

    /** Mueve un mod de mods-disabled/ a mods/. No hace nada si ya esta activo. */
    public Resultado activar(String archivo) {
        return mover(modsDisabledDir, modsDir, archivo, true);
    }

    /** Mueve un mod de mods/ a mods-disabled/. No hace nada si ya esta desactivado. */
    public Resultado desactivar(String archivo) {
        return mover(modsDir, modsDisabledDir, archivo, false);
    }

    private Resultado mover(Path origen, Path destino, String archivo, boolean activando) {
        // Solo un nombre de archivo, nunca una ruta. Sin esto, una ruta absoluta
        // hace que resolve() ignore la carpeta de mods y se mueva cualquier
        // archivo del equipo; y ".." permite salirse de la instalacion. Importa
        // porque estos metodos los invoca tambien un agente por el servidor MCP.
        Resultado v = validarNombre(archivo);
        if (v != null) return v;

        Path desde = origen.resolve(archivo);
        Path hacia = destino.resolve(archivo);
        if (!Files.isRegularFile(desde)) {
            return Resultado.error("No se encuentra \"" + archivo + "\" en " + origen);
        }
        try {
            Files.createDirectories(destino);
            Files.move(desde, hacia);
            return Resultado.ok("\"" + archivo + "\" " + (activando ? "activado" : "desactivado") + ".");
        } catch (FileAlreadyExistsException e) {
            return Resultado.error("Ya existe \"" + archivo + "\" en " + destino + "; no se ha movido nada.");
        } catch (IOException e) {
            return Resultado.error("No se pudo mover \"" + archivo + "\":\n\n" + Mensajes.explicar(e));
        }
    }

    /**
     * Comprueba que lo recibido es el nombre de un jar y nada mas. Devuelve
     * null si es aceptable, o el error a devolver si no lo es.
     */
    private static Resultado validarNombre(String archivo) {
        if (archivo == null || archivo.isBlank()) {
            return Resultado.error("No se indico ningun archivo.");
        }
        if (archivo.contains("/") || archivo.contains("\\")
                || archivo.contains("..") || archivo.contains(":")) {
            return Resultado.error("\"" + archivo + "\" no es un nombre de archivo valido: "
                    + "indica solo el nombre del jar, sin rutas.");
        }
        if (!archivo.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            return Resultado.error("\"" + archivo + "\" no es un archivo .jar.");
        }
        return null;
    }

    // -------------------------------------------------------------- Utiles

    /** "1234" -> "1,2 KB" / "3,4 MB" / "1,20 GB", con coma decimal (es). */
    public static String formatoTamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.forLanguageTag("es"), "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.forLanguageTag("es"), "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.forLanguageTag("es"), "%.2f GB", gb);
    }
}
