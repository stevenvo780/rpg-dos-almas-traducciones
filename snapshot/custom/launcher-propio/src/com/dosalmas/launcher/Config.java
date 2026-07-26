package com.dosalmas.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Configuracion persistente del launcher: un JSON plano (un solo nivel,
 * valores String/Number/Boolean) guardado en la carpeta de configuracion del
 * usuario. Se apoya en {@link Json} tanto para leer como para escribir
 * ({@link Json#writeFlat}), sin anadir un formato propio.
 */
public final class Config {

    // Sin valores de ninguna maquina concreta: null significa "por detectar".
    // Rutas se encarga de encontrarlos en el equipo donde se ejecute.
    public String playerName    = "Jugador";
    public String gameDir       = null;
    public String versionId     = null;
    public String assetsRoot    = null;
    public String javaBin       = null;
    public int maxMemoryMb      = 0;      // 0 = calcular segun la RAM del equipo
    public int minMemoryMb      = 2048;
    public String extraJvmArgs  = "";
    public boolean advancedMode = false;
    public Integer width        = null;
    public Integer height       = null;

    // -------------------------------------------------------- Ubicacion

    /** Ruta del config.json: XDG en Linux/mac, APPDATA en Windows. */
    public static Path configFile() {
        return Rutas.carpetaConfig().resolve("config.json");
    }

    // -------------------------------------------------------------- Carga

    /**
     * Carga la configuracion guardada y completa lo que falte detectandolo en
     * este equipo. Si el archivo esta corrupto se sigue adelante con lo
     * detectado: nunca se impide abrir el launcher por un config ilegible.
     */
    public static Config load() {
        Config cfg = new Config();
        Path file = configFile();
        if (Files.isRegularFile(file)) cfg.leerArchivo(file);
        cfg.completarConDeteccion();
        return cfg;
    }

    /** Rellena con deteccion automatica todo lo que no venga del archivo. */
    public void completarConDeteccion() {
        Path dir = (gameDir != null && !gameDir.isBlank()) ? Paths.get(gameDir) : null;
        if (dir == null || !Rutas.esGameDir(dir)) {
            Path detectado = Rutas.detectarGameDir();
            if (detectado != null) dir = detectado;
        }
        gameDir = (dir != null) ? dir.toString() : null;

        if (versionId == null || versionId.isBlank()) {
            String v = Rutas.detectarVersionId(dir);
            versionId = (v != null) ? v : Rutas.VERSION_POR_DEFECTO;
        }
        if (assetsRoot == null || assetsRoot.isBlank() || !Files.isDirectory(Paths.get(assetsRoot))) {
            Path a = Rutas.detectarAssets(dir, versionId);
            if (a != null) assetsRoot = a.toString();
        }
        if (javaBin == null || javaBin.isBlank() || !Files.isExecutable(Paths.get(javaBin))) {
            Path j = Rutas.detectarJava(dir, javaRequerido(dir, versionId));
            if (j != null) javaBin = j.toString();
        }
        if (maxMemoryMb <= 0) maxMemoryMb = memoriaRecomendadaMb();
        if (minMemoryMb <= 0) minMemoryMb = Math.min(2048, maxMemoryMb);
    }

    /** Version mayor de Java que pide el pack; 17 si no se puede leer. */
    private static int javaRequerido(Path gameDir, String versionId) {
        if (gameDir == null || versionId == null) return 17;
        try {
            Path json = gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".json");
            if (!Files.isRegularFile(json)) return 17;
            var jv = Json.obj(Json.parseObject(json), "javaVersion");
            long mayor = (jv != null) ? Json.num(jv, "majorVersion", 17) : 17;
            return (mayor > 0) ? (int) mayor : 17;
        } catch (Exception e) {
            return 17;
        }
    }

    /**
     * Memoria por defecto: la mitad de la RAM del equipo, entre 2 y 8 GB. Un
     * valor fijo de 8 GB deja sin arrancar a un equipo de 8 GB o menos.
     */
    public static int memoriaRecomendadaMb() {
        long totalMb = ramTotalMb();
        long mitad = totalMb / 2;
        long elegido = Math.max(2048, Math.min(8192, mitad));
        return (int) (elegido / 512 * 512);
    }

    /** RAM total del equipo en MB; 8192 si no se puede leer. */
    public static long ramTotalMb() {
        try {
            com.sun.management.OperatingSystemMXBean bean =
                    (com.sun.management.OperatingSystemMXBean)
                            java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            long mb = bean.getTotalMemorySize() / (1024 * 1024);
            return (mb > 0) ? mb : 8192;
        } catch (Throwable t) {
            return 8192;
        }
    }

    private void leerArchivo(Path file) {
        Config cfg = this;
        try {
            Map<String, Object> obj = Json.parseObject(file);
            cfg.playerName   = strOr(obj, "playerName", cfg.playerName);
            cfg.gameDir      = strOr(obj, "gameDir", cfg.gameDir);
            cfg.versionId    = strOr(obj, "versionId", cfg.versionId);
            cfg.assetsRoot   = strOr(obj, "assetsRoot", cfg.assetsRoot);
            cfg.javaBin      = strOr(obj, "javaBin", cfg.javaBin);
            cfg.maxMemoryMb  = (int) Json.num(obj, "maxMemoryMb", cfg.maxMemoryMb);
            cfg.minMemoryMb  = (int) Json.num(obj, "minMemoryMb", cfg.minMemoryMb);
            cfg.extraJvmArgs = strOr(obj, "extraJvmArgs", cfg.extraJvmArgs);
            cfg.advancedMode = boolOr(obj, "advancedMode", cfg.advancedMode);
            Long w = numOrNull(obj, "width");
            Long h = numOrNull(obj, "height");
            cfg.width  = (w != null) ? w.intValue() : null;
            cfg.height = (h != null) ? h.intValue() : null;
        } catch (Throwable t) {
            // Un config dañado no puede impedir abrir el launcher: se avisa y
            // se sigue con lo que se detecte en el equipo.
            System.err.println("Aviso: config.json ilegible, se usan valores detectados (" + t + ")");
        }
    }

    /** Guarda esta configuracion, creando la carpeta destino si hace falta. */
    public void save() throws IOException {
        Path file = configFile();
        Files.createDirectories(file.getParent());
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("playerName", playerName);
        obj.put("gameDir", gameDir);
        obj.put("versionId", versionId);
        obj.put("assetsRoot", assetsRoot);
        obj.put("javaBin", javaBin);
        obj.put("maxMemoryMb", maxMemoryMb);
        obj.put("minMemoryMb", minMemoryMb);
        obj.put("extraJvmArgs", extraJvmArgs);
        obj.put("advancedMode", advancedMode);
        if (width != null) obj.put("width", width);
        if (height != null) obj.put("height", height);
        Files.write(file, Json.writeFlat(obj).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------- Conversion a Profile

    /**
     * Traduce esta configuracion al Profile que espera el nucleo del launcher.
     * Las rutas sin detectar quedan a null: el Verificador las reporta con un
     * mensaje entendible en vez de reventar aqui con un NullPointerException.
     */
    public MinecraftLauncher.Profile toProfile() {
        MinecraftLauncher.Profile p = new MinecraftLauncher.Profile();
        p.gameDir = ruta(gameDir);
        p.versionId = (versionId != null) ? versionId : Rutas.VERSION_POR_DEFECTO;
        p.assetsRoot = ruta(assetsRoot);
        p.javaBin = ruta(javaBin);
        p.playerName = playerName;
        p.maxMemoryMb = maxMemoryMb;
        p.minMemoryMb = minMemoryMb;
        if (extraJvmArgs != null && !extraJvmArgs.isBlank()) {
            p.extraJvmArgs = new ArrayList<>();
            for (String a : extraJvmArgs.trim().split("\\s+")) {
                if (!a.isEmpty()) p.extraJvmArgs.add(a);
            }
        }
        p.width = width;
        p.height = height;
        return p;
    }

    // ---------------------------------------------------------------- Utiles

    /** Texto -> Path, o null si esta vacio (no hay ruta que valga "null"). */
    private static Path ruta(String s) {
        return (s == null || s.isBlank()) ? null : Paths.get(s);
    }

    private static String strOr(Map<String, Object> obj, String key, String def) {
        Object v = obj.get(key);
        return (v instanceof String) ? (String) v : def;
    }

    private static boolean boolOr(Map<String, Object> obj, String key, boolean def) {
        Object v = obj.get(key);
        return (v instanceof Boolean) ? (Boolean) v : def;
    }

    private static Long numOrNull(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return (v instanceof Number) ? ((Number) v).longValue() : null;
    }
}
