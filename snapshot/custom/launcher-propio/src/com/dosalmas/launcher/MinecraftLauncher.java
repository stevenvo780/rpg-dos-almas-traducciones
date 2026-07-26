package com.dosalmas.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nucleo del launcher: lee un JSON de version de Minecraft y construye el
 * comando de lanzamiento. No instala nada; asume una instalacion existente.
 *
 * Puntos delicados que este codigo resuelve (verificados sobre el pack real):
 *  - El JSON principal NO lista los jars de Forge/FML: hay que fusionar el
 *    archivo hermano *Additional.json (additionalFiles) o Forge no arranca.
 *  - El campo library.downloads.artifact.path es inconsistente (a veces ya
 *    trae el prefijo "libraries/"), asi que la ruta se recalcula desde el
 *    nombre Maven y solo se usa 'path' como respaldo.
 *  - El jar de version no aparece en 'libraries': se agrega por convencion.
 */
public final class MinecraftLauncher {

    /** Datos de una instalacion lista para lanzar. */
    public static final class Profile {
        public Path gameDir;          // carpeta del pack (.minecraft del perfil)
        public String versionId;      // p.ej. "Forge 1.20.1"
        public Path assetsRoot;       // carpeta de assets (puede ser compartida)
        public Path javaBin;          // binario java con el que correr el juego
        public String playerName = "Jugador";
        public int minMemoryMb = 2048;
        public int maxMemoryMb = 8192;
        public List<String> extraJvmArgs = new ArrayList<>();
        public Integer width;         // null = sin resolucion personalizada
        public Integer height;

        public Path versionDir() { return gameDir.resolve("versions").resolve(versionId); }
        public Path versionJson() { return versionDir().resolve(versionId + ".json"); }
        public Path versionJar()  { return versionDir().resolve(versionId + ".jar"); }
        public Path nativesDir()  { return versionDir().resolve("natives"); }
        public Path librariesDir(){ return gameDir.resolve("libraries"); }
    }

    public static final String LAUNCHER_NAME = "DosAlmasLauncher";
    public static final String LAUNCHER_VERSION = "0.1.0";

    private final Profile profile;
    private final Map<String, Object> versionJson;
    private final Os os = Os.current();
    private List<String> missingLibraries = new ArrayList<>();
    private Sincronizador.Resultado ultimaSincronizacion;

    public MinecraftLauncher(Profile profile) throws IOException {
        this.profile = profile;
        this.versionJson = Json.parseObject(profile.versionJson());
    }

    // ------------------------------------------------------------- Comando

    /** Construye el comando completo: java + args JVM + mainClass + args de juego. */
    public List<String> buildCommand() throws IOException {
        Map<String, String> vars = buildPlaceholders();

        List<String> cmd = new ArrayList<>();
        cmd.add(profile.javaBin.toString());

        // Memoria primero, para que el usuario pueda sobreescribirla con extraJvmArgs.
        cmd.add("-Xms" + profile.minMemoryMb + "M");
        cmd.add("-Xmx" + profile.maxMemoryMb + "M");
        cmd.addAll(recolectorRecomendado());
        cmd.addAll(profile.extraJvmArgs);

        cmd.addAll(renderArguments(Json.obj(versionJson, "arguments"), "jvm", vars));

        String mainClass = Json.str(versionJson, "mainClass");
        if (mainClass == null || mainClass.isEmpty()) {
            throw new IOException("el JSON de version no declara mainClass");
        }
        cmd.add(mainClass);

        cmd.addAll(renderArguments(Json.obj(versionJson, "arguments"), "game", vars));
        return cmd;
    }

    /**
     * Lanza el juego. La salida (stdout+stderr) queda disponible en el proceso.
     *
     * Antes de arrancar se verifica la instalacion: sin esto, cualquier pieza
     * ausente se convertia en un volcado de la JVM imposible de interpretar.
     */
    public Process launch() throws IOException {
        List<Verificador.Problema> problemas = Verificador.revisar(profile, this);
        if (Verificador.hayBloqueantes(problemas)) {
            throw new InstalacionIncompletaException(problemas);
        }
        // Ultimo momento en que la configuracion se puede tocar sin que el
        // juego la pise: aun no corre, y al arrancar leera lo que dejemos.
        ultimaSincronizacion = Sincronizador.sincronizar(profile.gameDir);
        return launchSinVerificar();
    }

    /** Lo que la sincronizacion cambio en el ultimo lanzamiento; nunca null. */
    public Sincronizador.Resultado ultimaSincronizacion() {
        return (ultimaSincronizacion != null) ? ultimaSincronizacion : new Sincronizador.Resultado();
    }

    /** Lanza sin comprobar nada. Reservado a diagnostico; la GUI usa launch(). */
    public Process launchSinVerificar() throws IOException {
        List<String> cmd = buildCommand();
        ProcessBuilder pb = new ProcessBuilder(cmd);   // String[] evita problemas con espacios
        pb.directory(profile.gameDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /**
     * Recolector de basura concurrente, si el Java elegido lo admite.
     *
     * G1 provoca tirones perceptibles con muchos mods, y Distant Horizons lo
     * avisa expresamente al arrancar. Shenandoah reparte ese trabajo y da una
     * imagen mas estable. Se comprueba antes de usarlo: una opcion que la
     * maquina virtual no conozca impide arrancar el juego.
     *
     * El segundo argumento evita los tirones de una recoleccion completa
     * provocada por System.gc(), pero sin desactivarla: con
     * ExplicitGCInvokesConcurrent esa llamada se atiende de forma concurrente.
     * Se prefiere a DisableExplicitGC porque Distant Horizons usa System.gc()
     * para soltar la memoria directa de sus LOD; desactivarla le impide
     * liberarla y hace que avise al jugador en el chat al entrar al mundo.
     *
     * Si el jugador ya eligio un recolector en sus argumentos, se respeta.
     */
    private List<String> recolectorRecomendado() {
        for (String a : profile.extraJvmArgs) {
            if (a.contains("GC") || a.startsWith("-XX:+Use")) return List.of();
        }
        if (Rutas.admiteOpcion(profile.javaBin, "-XX:+UseShenandoahGC")) {
            return List.of("-XX:+UseShenandoahGC", "-XX:+ExplicitGCInvokesConcurrent");
        }
        return List.of();
    }

    // -------------------------------------------------------- Placeholders

    private Map<String, String> buildPlaceholders() throws IOException {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("auth_player_name", profile.playerName);
        v.put("auth_uuid", offlineUuid(profile.playerName).toString().replace("-", ""));
        v.put("auth_access_token", "0");         // sin cuenta real
        v.put("auth_xuid", "0");
        v.put("clientid", "0");
        v.put("user_type", "legacy");            // offline
        v.put("version_name", profile.versionId);
        v.put("version_type", orDefault(Json.str(versionJson, "type"), "release"));
        v.put("game_directory", profile.gameDir.toString());
        v.put("assets_root", profile.assetsRoot.toString());
        v.put("game_assets", profile.assetsRoot.toString()); // formatos antiguos
        v.put("assets_index_name", assetIndexName());
        v.put("natives_directory", profile.nativesDir().toString());
        v.put("library_directory", profile.librariesDir().toString());
        v.put("classpath_separator", File.pathSeparator);
        v.put("classpath", String.join(File.pathSeparator, buildClasspath()));
        v.put("launcher_name", LAUNCHER_NAME);
        v.put("launcher_version", LAUNCHER_VERSION);
        if (profile.width != null)  v.put("resolution_width", String.valueOf(profile.width));
        if (profile.height != null) v.put("resolution_height", String.valueOf(profile.height));
        return v;
    }

    private String assetIndexName() {
        Map<String, Object> idx = Json.obj(versionJson, "assetIndex");
        String id = (idx != null) ? Json.str(idx, "id") : null;
        return orDefault(id, orDefault(Json.str(versionJson, "assets"), "legacy"));
    }

    /**
     * UUID offline: el mismo que usa el servidor vanilla para jugadores sin
     * cuenta. Debe ser estable para que el jugador conserve inventario y avances.
     */
    public static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    // ----------------------------------------------------------- Classpath

    /** Rutas del classpath, en orden: librerias -> jars adicionales -> jar de version. */
    public List<String> buildClasspath() throws IOException {
        LinkedHashSet<String> cp = new LinkedHashSet<>();
        List<String> faltantes = new ArrayList<>();

        for (Object lib : Json.list(versionJson, "libraries")) {
            if (!(lib instanceof Map)) continue;
            if (!isAllowed(Json.list(lib, "rules"))) continue;
            String name = Json.str(lib, "name");
            if (!archCompatible(name)) continue;
            Path jar = resolveLibrary(lib);
            if (jar != null && Files.isRegularFile(jar)) {
                cp.add(jar.toString());
            } else {
                // Se anota en vez de descartarse en silencio: una libreria
                // ausente hace que el juego muera despues, con un error que
                // no apunta a la causa.
                faltantes.add((name != null) ? name : String.valueOf(jar));
            }
        }
        this.missingLibraries = faltantes;

        // Jars de Forge/FML que solo viven en el archivo *Additional.json.
        //
        // Ojo: los mapeados del cliente (-slim y -srg) NO van al classpath. FML
        // los localiza por su cuenta a partir de -DlibraryDirectory. Si se anaden,
        // JPMS los convierte en modulos automaticos que exportan los mismos
        // paquetes que el modulo 'minecraft' y el arranque falla con
        // ResolutionException. El propio -DignoreList del JSON lo confirma:
        // enumera lo que si va en el classpath (client-extra, fmlcore, forge-...)
        // y esos dos no aparecen.
        for (Path extra : additionalJars()) {
            if (!Files.isRegularFile(extra)) continue;
            String n = extra.getFileName().toString().toLowerCase(Locale.ROOT);
            if (n.contains("-slim.jar") || n.contains("-srg.jar")) continue;
            cp.add(extra.toString());
        }

        // El jar de version no se lista en 'libraries': se agrega por convencion
        // (aparece en ignoreList, senal de que Forge lo espera en el classpath).
        Path versionJar = profile.versionJar();
        if (Files.isRegularFile(versionJar)) cp.add(versionJar.toString());

        if (cp.isEmpty()) throw new IOException("el classpath quedo vacio: revisa la instalacion");
        return new ArrayList<>(cp);
    }

    /**
     * Jars declarados en el archivo hermano *Additional.json (TLauncher/Forge).
     * Sin ellos, BootstrapLauncher arranca sin FML y el juego falla.
     */
    private List<Path> additionalJars() throws IOException {
        List<Path> out = new ArrayList<>();
        Path dir = profile.versionDir();
        if (!Files.isDirectory(dir)) return out;

        try (var stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream.filter(f -> {
                String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith("additional.json");
            })::iterator) {
                Map<String, Object> extra = Json.parseObject(p);
                for (Object entry : Json.list(extra, "additionalFiles")) {
                    String rel = (entry instanceof Map) ? Json.str(entry, "path") : String.valueOf(entry);
                    if (rel == null || !rel.toLowerCase(Locale.ROOT).endsWith(".jar")) continue; // el .txt de mappings no va al classpath
                    out.add(profile.gameDir.resolve(rel));
                }
            }
        }
        return out;
    }

    /**
     * Bloque 'artifact' de una libreria (sha1, size, path, url).
     *
     * Ojo: este pack lo trae en la raiz de la entrada, no bajo 'downloads'
     * como el formato de Mojang. Se aceptan los dos para no depender de quien
     * genero el JSON.
     */
    static Map<String, Object> artifactOf(Object lib) {
        Map<String, Object> raiz = Json.obj(lib, "artifact");
        if (raiz != null) return raiz;
        Map<String, Object> downloads = Json.obj(lib, "downloads");
        return (downloads != null) ? Json.obj(downloads, "artifact") : null;
    }

    /**
     * Arquitectura del clasificador Maven frente a la del equipo. Las reglas
     * del JSON solo distinguen sistema operativo, asi que sin esto en Windows
     * entrarian tambien las natives de x86 y de arm64.
     */
    boolean archCompatible(String name) {
        if (name == null) return true;
        String[] parts = name.split(":");
        if (parts.length < 4) return true;          // sin clasificador
        String cls = parts[3].toLowerCase(Locale.ROOT);
        String arch = os.normalizedArch();
        // De mas especifico a menos: "x86_64" contiene "x86".
        if (cls.contains("arm64") || cls.contains("aarch_64") || cls.contains("aarch64")) {
            return arch.equals("arm64");
        }
        if (cls.contains("x86_64") || cls.contains("x64")) return arch.equals("x64");
        if (cls.contains("x86") || cls.contains("i386")) return arch.equals("x86");
        return true;
    }

    /** Librerias que las reglas exigen pero no estan en disco (tras buildClasspath). */
    public List<String> missingLibraries() { return missingLibraries; }

    /** Version mayor de Java que pide el pack (17 en este modpack); -1 si no la declara. */
    public int requiredJavaMajor() {
        Map<String, Object> jv = Json.obj(versionJson, "javaVersion");
        if (jv == null) return -1;
        // Viene como numero (17.0), no como texto: Json.str daria la cadena "17.0".
        long mayor = Json.num(jv, "majorVersion", -1);
        return (int) mayor;
    }

    /**
     * Ruta real de una libreria. Se recalcula desde el nombre Maven porque el
     * campo 'path' del JSON es inconsistente (a veces ya trae "libraries/").
     */
    private Path resolveLibrary(Object lib) {
        String name = Json.str(lib, "name");
        if (name != null) {
            Path fromName = profile.librariesDir().resolve(mavenPath(name));
            if (Files.isRegularFile(fromName)) return fromName;
        }
        Map<String, Object> artifact = artifactOf(lib);
        String path = (artifact != null) ? Json.str(artifact, "path") : null;
        if (path != null) {
            Path candidate = path.startsWith("libraries/")
                    ? profile.gameDir.resolve(path)
                    : profile.librariesDir().resolve(path);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return (name != null) ? profile.librariesDir().resolve(mavenPath(name)) : null;
    }

    /** "grupo:artefacto:version[:clasificador][@ext]" -> ruta Maven relativa. */
    public static String mavenPath(String gav) {
        String ext = "jar";
        int at = gav.indexOf('@');
        if (at >= 0) { ext = gav.substring(at + 1); gav = gav.substring(0, at); }

        String[] parts = gav.split(":");
        if (parts.length < 3) return gav.replace(':', '/');
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = (parts.length > 3) ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/"
                + artifact + "-" + version + classifier + "." + ext;
    }

    // --------------------------------------------------------- Argumentos

    /** Renderiza arguments.jvm o arguments.game aplicando reglas y placeholders. */
    private List<String> renderArguments(Map<String, Object> arguments, String key, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        if (arguments == null) return out;

        for (Object entry : Json.list(arguments, key)) {
            if (entry instanceof String) {
                out.add(substitute((String) entry, vars));
                continue;
            }
            if (!(entry instanceof Map)) continue;
            if (!isAllowed(Json.list(entry, "rules"))) continue;

            Object value = ((Map<?, ?>) entry).get("value");
            if (value == null) value = ((Map<?, ?>) entry).get("values");
            if (value instanceof String) {
                out.add(substitute((String) value, vars));
            } else if (value instanceof List) {
                for (Object v : (List<?>) value) {
                    if (v != null) out.add(substitute(String.valueOf(v), vars));
                }
            }
        }
        return out;
    }

    private static final Pattern VAR = Pattern.compile("\\$\\{([a-zA-Z_]+)\\}");

    private static String substitute(String text, Map<String, String> vars) {
        Matcher m = VAR.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // -------------------------------------------------------------- Reglas

    /**
     * Reglas de Mojang: sin reglas = permitido. Con reglas, el veredicto parte
     * de "denegado" y cada regla que coincide lo sobreescribe (gana la ultima).
     */
    boolean isAllowed(List<Object> rules) {
        if (rules == null || rules.isEmpty()) return true;
        boolean allowed = false;
        for (Object r : rules) {
            if (!(r instanceof Map)) continue;
            if (!ruleMatches(r)) continue;
            allowed = "allow".equals(Json.str(r, "action"));
        }
        return allowed;
    }

    private boolean ruleMatches(Object rule) {
        Map<String, Object> osCond = Json.obj(rule, "os");
        if (osCond != null) {
            String name = Json.str(osCond, "name");
            if (name != null && !name.equals(os.name)) return false;
            String arch = Json.str(osCond, "arch");
            if (arch != null && !matchesRegex(arch, os.arch)) return false;
            String version = Json.str(osCond, "version");
            if (version != null && !matchesRegex(version, os.version)) return false;
        }
        Map<String, Object> features = Json.obj(rule, "features");
        if (features != null) {
            for (Map.Entry<String, Object> e : features.entrySet()) {
                boolean wanted = Boolean.TRUE.equals(e.getValue());
                boolean active = isFeatureActive(e.getKey());
                if (wanted != active) return false;
            }
        }
        return true;
    }

    /** Solo activamos resolucion personalizada; demo y quick-play quedan fuera. */
    private boolean isFeatureActive(String feature) {
        if ("has_custom_resolution".equals(feature)) {
            return profile.width != null && profile.height != null;
        }
        return false;
    }

    private static boolean matchesRegex(String regex, String value) {
        try {
            return Pattern.compile(regex).matcher(value).find();
        } catch (Exception e) {
            return regex.equals(value);
        }
    }

    /** Sistema operativo tal como lo nombran los JSON de Mojang. */
    public static final class Os {
        public final String name;
        public final String arch;
        public final String version;

        private Os(String name, String arch, String version) {
            this.name = name; this.arch = arch; this.version = version;
        }

        public static Os current() {
            String raw = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String name = raw.contains("win") ? "windows"
                        : (raw.contains("mac") || raw.contains("osx")) ? "osx"
                        : "linux";
            return new Os(name,
                    System.getProperty("os.arch", "amd64"),
                    System.getProperty("os.version", ""));
        }

        public boolean isWindows() { return "windows".equals(name); }

        /** Arquitectura en los terminos que usan los clasificadores Maven. */
        public String normalizedArch() {
            String a = arch.toLowerCase(Locale.ROOT);
            if (a.equals("amd64") || a.equals("x86_64") || a.equals("x64")) return "x64";
            if (a.equals("aarch64") || a.equals("arm64")) return "arm64";
            if (a.equals("x86") || a.equals("i386") || a.equals("i486")
                    || a.equals("i586") || a.equals("i686")) return "x86";
            return a;
        }
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    public Map<String, Object> versionJson() { return versionJson; }

    public Path resolveJavaFallback() {
        String home = System.getProperty("java.home");
        return Paths.get(home, "bin", os.isWindows() ? "java.exe" : "java");
    }
}
