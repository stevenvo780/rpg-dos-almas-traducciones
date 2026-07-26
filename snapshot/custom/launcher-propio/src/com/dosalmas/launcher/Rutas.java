package com.dosalmas.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Deteccion de rutas de la instalacion. Es el unico origen de verdad: ni
 * Config ni Main pueden traer rutas escritas a mano, porque el launcher debe
 * funcionar igual en la torre, en el portatil y en el equipo de Isa.
 *
 * La busqueda va siempre de lo mas especifico a lo mas general:
 *   1. junto al propio jar (caso de una carpeta portable o del instalador)
 *   2. ubicaciones habituales del usuario
 *   3. la instalacion clasica de Minecraft
 * Si nada aparece, se devuelve null y quien llama decide que hacer; nunca se
 * inventa una ruta que no existe.
 */
public final class Rutas {

    private Rutas() {}

    public static final String VERSION_POR_DEFECTO = "Forge 1.20.1";

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** Nombre del ejecutable de Java segun el sistema. */
    public static String nombreJava(boolean sinConsola) {
        if (!WINDOWS) return "java";
        return sinConsola ? "javaw.exe" : "java.exe";
    }

    // ------------------------------------------------------- Carpeta del jar

    /**
     * Carpeta donde vive el jar del launcher. Devuelve null si no se puede
     * determinar (algunos cargadores de clases no lo exponen).
     */
    public static Path carpetaDelJar() {
        try {
            var origen = Rutas.class.getProtectionDomain().getCodeSource();
            if (origen == null) return null;
            Path p = Paths.get(origen.getLocation().toURI());
            return Files.isDirectory(p) ? p : p.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------- Carpeta del juego

    /**
     * Carpeta del pack. Se considera valida solo si contiene 'versions' con
     * alguna version instalada, para no confundirla con una carpeta vacia.
     */
    public static Path detectarGameDir() {
        for (Path c : candidatosGameDir()) {
            if (esGameDir(c)) return c;
        }
        return null;
    }

    /** Todas las rutas donde se busca el pack, en orden de preferencia. */
    public static List<Path> candidatosGameDir() {
        List<Path> out = new ArrayList<>();
        Path jar = carpetaDelJar();
        if (jar != null) {
            // La carpeta del propio jar es la primera candidata: en la
            // distribucion a otro equipo el launcher viaja DENTRO del pack,
            // junto a 'mods' y 'versions'.
            add(out, jar);
            add(out, jar.resolve("pack"));
            add(out, jar.resolve("RPG-Dos-Almas"));
            add(out, jar.resolve(".minecraft"));
            Path padre = jar.getParent();
            if (padre != null) {
                add(out, padre);
                add(out, padre.resolve("RPG-Dos-Almas"));
                add(out, padre.resolve("pack"));
            }
        }
        Path casa = Paths.get(System.getProperty("user.home", "."));
        if (WINDOWS) {
            String appData = System.getenv("APPDATA");
            Path base = (appData != null && !appData.isEmpty())
                    ? Paths.get(appData) : casa.resolve("AppData").resolve("Roaming");
            add(out, base.resolve(".minecraft-rpg-dos-almas"));
            add(out, base.resolve(".minecraft"));
        }
        add(out, casa.resolve("Minecraft").resolve("RPG-Dos-Almas"));
        add(out, casa.resolve("Minecraft").resolve("Cliente").resolve("RPG-Dos-Almas"));
        add(out, casa.resolve(".minecraft"));
        return out;
    }

    private static void add(List<Path> out, Path p) {
        if (p != null && !out.contains(p)) out.add(p);
    }

    /** Una carpeta es del juego si tiene 'versions' con al menos un JSON de version. */
    public static boolean esGameDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return false;
        Path versions = dir.resolve("versions");
        if (!Files.isDirectory(versions)) return false;
        return detectarVersionId(dir) != null;
    }

    // ----------------------------------------------------------- Version

    /**
     * Id de la version instalada. Si hay varias se prefiere la que trae Forge
     * y, en ultimo caso, la primera por orden alfabetico (determinista).
     */
    public static String detectarVersionId(Path gameDir) {
        if (gameDir == null) return null;
        Path versions = gameDir.resolve("versions");
        if (!Files.isDirectory(versions)) return null;

        List<String> encontradas = new ArrayList<>();
        try (var stream = Files.list(versions)) {
            for (Path dir : (Iterable<Path>) stream.sorted()::iterator) {
                if (!Files.isDirectory(dir)) continue;
                String id = dir.getFileName().toString();
                if (Files.isRegularFile(dir.resolve(id + ".json"))) encontradas.add(id);
            }
        } catch (IOException e) {
            return null;
        }
        if (encontradas.isEmpty()) return null;
        if (encontradas.contains(VERSION_POR_DEFECTO)) return VERSION_POR_DEFECTO;
        for (String id : encontradas) {
            if (id.toLowerCase(Locale.ROOT).contains("forge")) return id;
        }
        return encontradas.get(0);
    }

    // ------------------------------------------------------------- Assets

    /**
     * Carpeta de assets: la del propio pack si esta completa y, solo en Linux,
     * el almacen de Prism como respaldo (asi funciona hoy la torre).
     */
    public static Path detectarAssets(Path gameDir, String versionId) {
        String indice = idDeAssets(gameDir, versionId);
        for (Path c : candidatosAssets(gameDir)) {
            if (tieneIndice(c, indice)) return c;
        }
        // Sin indice reconocible, vale la primera que exista con la forma correcta.
        for (Path c : candidatosAssets(gameDir)) {
            if (Files.isDirectory(c.resolve("indexes")) && Files.isDirectory(c.resolve("objects"))) return c;
        }
        return null;
    }

    private static List<Path> candidatosAssets(Path gameDir) {
        List<Path> out = new ArrayList<>();
        if (gameDir != null) add(out, gameDir.resolve("assets"));
        Path jar = carpetaDelJar();
        if (jar != null) add(out, jar.resolve("assets"));
        Path casa = Paths.get(System.getProperty("user.home", "."));
        if (!WINDOWS) {
            add(out, casa.resolve(".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/assets"));
            add(out, casa.resolve(".local/share/PrismLauncher/assets"));
        }
        add(out, casa.resolve(".minecraft").resolve("assets"));
        return out;
    }

    private static boolean tieneIndice(Path assets, String indice) {
        return indice != null && assets != null
                && Files.isRegularFile(assets.resolve("indexes").resolve(indice + ".json"));
    }

    /** Lee el id del indice de assets desde el JSON de version, sin parsearlo entero dos veces. */
    private static String idDeAssets(Path gameDir, String versionId) {
        if (gameDir == null || versionId == null) return null;
        try {
            Path json = gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".json");
            if (!Files.isRegularFile(json)) return null;
            var obj = Json.parseObject(json);
            var idx = Json.obj(obj, "assetIndex");
            String id = (idx != null) ? Json.str(idx, "id") : null;
            return (id != null) ? id : Json.str(obj, "assets");
        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------------------- Java

    /**
     * Java con el que lanzar el JUEGO (no el launcher). El juego exige una
     * version mayor concreta: dar un Java mas nuevo rompe el arranque de Forge
     * de forma ilegible, asi que aqui solo se acepta la que pide el pack.
     */
    public static Path detectarJava(Path gameDir, int mayorRequerido) {
        for (Path c : candidatosJava(gameDir)) {
            if (Files.isExecutable(c) && versionMayorDe(c) == mayorRequerido) return c;
        }
        return null;
    }

    /** Candidatos de Java, del mas especifico del pack al mas general del sistema. */
    public static List<Path> candidatosJava(Path gameDir) {
        List<Path> out = new ArrayList<>();
        String exe = nombreJava(false);
        if (gameDir != null) {
            add(out, gameDir.resolve("jre17").resolve("bin").resolve(exe));
            add(out, gameDir.resolve("runtime").resolve("bin").resolve(exe));
        }
        Path jar = carpetaDelJar();
        if (jar != null) {
            add(out, jar.resolve("jre").resolve("bin").resolve(exe));
            add(out, jar.resolve("data/jvm/java-runtime-gamma/bin").resolve(exe));
            Path padre = jar.getParent();
            if (padre != null) {
                add(out, padre.resolve("jre").resolve("bin").resolve(exe));
                // Asi esta el proyecto en la torre: el jar en dist/ y el JRE
                // un nivel por encima, en data/jvm/.
                add(out, padre.resolve("data/jvm/java-runtime-gamma/bin").resolve(exe));
            }
        }
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            add(out, Paths.get(javaHome, "bin", exe));
        }
        // El Java con el que corre este launcher: valido solo si su version coincide.
        String propio = System.getProperty("java.home");
        if (propio != null) add(out, Paths.get(propio, "bin", exe));

        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isEmpty()) add(out, Paths.get(dir, exe));
            }
        }
        // JRE instalados en el sistema (Fedora, Arch, Debian los ponen aqui).
        for (String base : new String[] {"/usr/lib/jvm", "/usr/lib64/jvm", "/opt/java"}) {
            Path dir = Paths.get(base);
            if (!Files.isDirectory(dir)) continue;
            try (var s = Files.list(dir)) {
                for (Path jvm : (Iterable<Path>) s.sorted()::iterator) {
                    add(out, jvm.resolve("bin").resolve(exe));
                }
            } catch (IOException e) {
                // carpeta ilegible: se ignora y se sigue con el resto
            }
        }
        return out;
    }

    /**
     * Version mayor de un binario de Java (17, 21...), o -1 si no se puede
     * determinar. Primero se lee el archivo 'release' del JRE, que es
     * instantaneo; solo si no esta se ejecuta 'java -version'.
     */
    public static int versionMayorDe(Path javaBin) {
        if (javaBin == null) return -1;
        Path bin = javaBin.getParent();
        Path hogar = (bin != null) ? bin.getParent() : null;
        if (hogar != null) {
            int v = versionDesdeRelease(hogar.resolve("release"));
            if (v > 0) return v;
        }
        return versionEjecutando(javaBin);
    }

    private static int versionDesdeRelease(Path release) {
        if (!Files.isRegularFile(release)) return -1;
        try {
            for (String linea : Files.readAllLines(release, StandardCharsets.UTF_8)) {
                if (linea.startsWith("JAVA_VERSION=")) {
                    return mayorDeCadena(linea.substring("JAVA_VERSION=".length()).replace("\"", "").trim());
                }
            }
        } catch (IOException e) {
            // release ilegible: se intenta ejecutando el binario
        }
        return -1;
    }

    private static int versionEjecutando(Path javaBin) {
        if (!Files.isExecutable(javaBin)) return -1;
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(javaBin.toString(), "-version");
            pb.redirectErrorStream(true);
            p = pb.start();
            String salida;
            try (var in = p.getInputStream()) {
                salida = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) return -1;
            int comillas = salida.indexOf('"');
            if (comillas < 0) return -1;
            int fin = salida.indexOf('"', comillas + 1);
            if (fin < 0) return -1;
            return mayorDeCadena(salida.substring(comillas + 1, fin));
        } catch (Exception e) {
            return -1;
        } finally {
            if (p != null) p.destroy();
        }
    }

    /**
     * Comprueba si un Java admite una opcion de maquina virtual. Se usa para
     * activar el recolector de basura concurrente solo donde exista: pasarle
     * una opcion desconocida a la JVM impide que el juego arranque.
     */
    public static boolean admiteOpcion(Path javaBin, String opcion) {
        if (javaBin == null || !Files.isExecutable(javaBin)) return false;
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(javaBin.toString(), opcion, "-version");
            pb.redirectErrorStream(true);
            p = pb.start();
            String salida;
            try (var in = p.getInputStream()) {
                salida = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!p.waitFor(5, TimeUnit.SECONDS)) return false;
            if (p.exitValue() != 0) return false;
            String s = salida.toLowerCase(Locale.ROOT);
            return !s.contains("unrecognized") && !s.contains("not supported")
                    && !s.contains("error:");
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }

    /** "17.0.15" -> 17 ; "1.8.0_312" -> 8 */
    static int mayorDeCadena(String version) {
        if (version == null || version.isEmpty()) return -1;
        String v = version.startsWith("1.") ? version.substring(2) : version;
        int i = 0;
        while (i < v.length() && Character.isDigit(v.charAt(i))) i++;
        if (i == 0) return -1;
        try {
            return Integer.parseInt(v.substring(0, i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------ Config y registros

    /** Carpeta de configuracion del launcher: APPDATA en Windows, XDG en el resto. */
    public static Path carpetaConfig() {
        if (WINDOWS) {
            String appData = System.getenv("APPDATA");
            Path base = (appData != null && !appData.isEmpty())
                    ? Paths.get(appData)
                    : Paths.get(System.getProperty("user.home"), "AppData", "Roaming");
            return base.resolve("RPGDosAlmasLauncher");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = (xdg != null && !xdg.isEmpty())
                ? Paths.get(xdg)
                : Paths.get(System.getProperty("user.home"), ".config");
        return base.resolve("rpg-dos-almas-launcher");
    }

    public static Path carpetaLogs() { return carpetaConfig().resolve("logs"); }

    public static boolean esWindows() { return WINDOWS; }
}
