package com.dosalmas.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perfiles de rendimiento: la misma instalacion, afinada para el equipo donde
 * corre.
 *
 * El modpack rinde muy distinto en una torre con tarjeta dedicada que en un
 * portatil con grafica integrada, y ajustarlo a mano obliga a tocar tres
 * archivos que no estan a la vista (opciones del juego, Distant Horizons y
 * Embeddium). Aqui se aplican de una vez, con valores medidos en los equipos
 * reales del proyecto.
 *
 * Lo que NO hace: activar o desactivar mods. Eso cambia lo que el servidor
 * espera del cliente y se decide aparte, en la seccion de mods.
 */
public final class PerfilesServicio {

    /** Un perfil de rendimiento con todo lo que toca. */
    public enum Perfil {
        ALTO("Alto rendimiento",
             "Para equipos con tarjeta grafica dedicada. Mas distancia de vision, "
             + "sombras, particulas y horizonte lejano de calidad.",
             10, 11, 1, 7, 4, 0, true, 260,
             384, "MEDIUM", 16),
        EQUILIBRADO("Equilibrado",
             "Termino medio: buena imagen sin exigir tanto. Sirve para equipos "
             + "modestos con grafica dedicada o portatiles potentes.",
             8, 8, 1, 3, 3, 1, true, 144,
             160, "LOW", 8),
        BAJO("Bajo consumo",
             "Para portatiles con grafica integrada. Prioriza los fotogramas: "
             + "sin sombras, imagen rapida y horizonte lejano economico.",
             8, 8, 0, 1, 2, 2, false, 120,
             128, "LOW", 6);

        public final String nombre;
        public final String descripcion;
        final int renderDistance;
        final int simulationDistance;
        final int graphicsMode;      // 0 rapido, 1 elaborado
        final int biomeBlendRadius;
        final int mipmapLevels;
        final int particles;         // 0 todas, 1 disminuidas, 2 minimas
        final boolean entityShadows;
        final int maxFps;
        final int dhRadio;
        final String dhCalidad;
        final int dhHilos;

        Perfil(String nombre, String descripcion, int renderDistance, int simulationDistance,
               int graphicsMode, int biomeBlendRadius, int mipmapLevels, int particles,
               boolean entityShadows, int maxFps, int dhRadio, String dhCalidad, int dhHilos) {
            this.nombre = nombre; this.descripcion = descripcion;
            this.renderDistance = renderDistance; this.simulationDistance = simulationDistance;
            this.graphicsMode = graphicsMode; this.biomeBlendRadius = biomeBlendRadius;
            this.mipmapLevels = mipmapLevels; this.particles = particles;
            this.entityShadows = entityShadows; this.maxFps = maxFps;
            this.dhRadio = dhRadio; this.dhCalidad = dhCalidad; this.dhHilos = dhHilos;
        }

        /**
         * Memoria recomendada para este perfil, sin pasarse de la del equipo.
         *
         * Nunca mas de la mitad de la RAM. En un equipo con muy poca memoria
         * (menos de 4 GB) la mitad es menos de 2 GB, y ahi manda la mitad: dar
         * mas de lo que hay solo consigue que el sistema mate el juego.
         */
        public int memoriaMb(long ramTotalMb) {
            int deseada = (this == ALTO) ? 10240 : (this == EQUILIBRADO ? 8192 : 6144);
            long mitad = Math.max(512, ramTotalMb / 2);
            long techo = Math.min(mitad, Math.max(512, ramTotalMb - 2048));
            return (int) Math.min(deseada, techo);
        }
    }

    private PerfilesServicio() {}

    /** Resultado de aplicar un perfil, para poder contarlo al usuario. */
    public static final class Resultado {
        public final List<String> aplicados = new ArrayList<>();
        public final List<String> avisos = new ArrayList<>();
        public boolean correcto() { return avisos.isEmpty(); }
    }

    // ------------------------------------------------------------ Deteccion

    /**
     * Perfil que le corresponde a este equipo, mirando la grafica y la memoria.
     * Es solo una sugerencia inicial: el jugador manda.
     */
    public static Perfil sugerido() {
        long ram = Config.ramTotalMb();
        String gpu = descripcionGpu().toLowerCase(java.util.Locale.ROOT);
        boolean integrada = gpu.contains("intel") || gpu.contains("iris")
                || gpu.contains("uhd") || gpu.contains("vega") || gpu.contains("radeon graphics");
        boolean dedicada = gpu.contains("nvidia") || gpu.contains("geforce")
                || gpu.contains("rtx") || gpu.contains("gtx") || gpu.contains("radeon rx");

        if (dedicada && ram >= 16384) return Perfil.ALTO;
        if (integrada || ram < 12288) return Perfil.BAJO;
        return Perfil.EQUILIBRADO;
    }

    /** Descripcion de la grafica; cadena vacia si no se puede averiguar. */
    public static String descripcionGpu() {
        if (Rutas.esWindows()) {
            String s = ejecutar("powershell", "-NoProfile", "-Command",
                    "(Get-CimInstance Win32_VideoController).Name -join ', '");
            return (s != null) ? s.trim() : "";
        }
        String s = ejecutar("sh", "-c", "lspci 2>/dev/null | grep -iE 'vga|3d' | head -3");
        return (s != null) ? s.trim() : "";
    }

    private static String ejecutar(String... cmd) {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            p = pb.start();
            String salida;
            try (var in = p.getInputStream()) {
                salida = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!p.waitFor(6, java.util.concurrent.TimeUnit.SECONDS)) return null;
            return salida;
        } catch (Exception e) {
            return null;
        } finally {
            if (p != null) p.destroy();
        }
    }

    // ------------------------------------------------------------- Aplicar

    /**
     * Aplica el perfil a la instalacion. Antes de tocar nada guarda una copia
     * de cada archivo, para poder volver atras si algo no gusta.
     */
    public static Resultado aplicar(Path gameDir, Perfil perfil) {
        Resultado r = new Resultado();
        if (gameDir == null || !Files.isDirectory(gameDir)) {
            r.avisos.add("No se encontro la carpeta del juego.");
            return r;
        }
        aplicarOpciones(gameDir.resolve("options.txt"), perfil, r);
        aplicarDistantHorizons(gameDir.resolve("config").resolve("DistantHorizons.toml"), perfil, r);
        aplicarEmbeddium(gameDir.resolve("config").resolve("embeddium-options.json"), perfil, r);
        aplicarShaders(gameDir, perfil, r);
        return r;
    }

    /**
     * Shaders (Oculus). Es lo que mas cuesta con diferencia, y mas todavia con
     * Distant Horizons dibujando el horizonte lejano, asi que el pack se elige
     * segun el perfil.
     *
     * En equipos con grafica integrada se usa Shrimple, que es ligero y sigue
     * pintando el horizonte lejano. Antes se dejaba Bliss-DH y los shaders
     * APAGADOS, pero eso se probo y da 15 fotogramas por segundo en una Iris
     * Xe; Shrimple encendido se ve bien y corre.
     *
     * Aviso para quien toque esto: el Shrimple original NO funciona con Oculus
     * 1.8.0. Usa sintaxis de etiquetas (%walls, %stairs) en block.properties
     * que Oculus pasa a ResourceLocation y rechaza, y entonces el pipeline de
     * shaders no llega a crearse: el juego arranca sin shaders y solo lo dice
     * en el registro. El pack que se distribuye lleva esas etiquetas quitadas.
     */
    private static void aplicarShaders(Path gameDir, Perfil p, Resultado r) {
        Path archivo = gameDir.resolve("config").resolve("oculus.properties");
        boolean conShaders = true;
        Path packs = gameDir.resolve("shaderpacks");
        String pack = (p == Perfil.ALTO)
                ? "ComplementaryReimagined_r5.8.1.zip"
                : "Shrimple_v0.12.zip";
        // Si el pack elegido no esta, se deja el que ya hubiera puesto.
        if (Files.isDirectory(packs) && !Files.isRegularFile(packs.resolve(pack))) {
            String previo = leerClave(archivo, "shaderPack");
            if (previo != null && !previo.isBlank()) pack = previo;
        }
        try {
            if (!Files.isDirectory(gameDir.resolve("config"))) {
                r.aplicados.add("Oculus no esta instalado: se omiten los shaders");
                return;
            }
            respaldar(archivo);
            String texto = "#Escrito por el launcher al aplicar el perfil \"" + p.nombre + "\".\n"
                    + "colorSpace=SRGB\n"
                    + "disableUpdateMessage=false\n"
                    + "enableDebugOptions=false\n"
                    // Las sombras son lo que mas cuesta de un shader: en integrada
                    // se recortan a la mitad para que compense tenerlo encendido.
                    + "maxShadowRenderDistance=" + (p == Perfil.ALTO ? 32 : 16) + "\n"
                    + "shaderPack=" + pack + "\n"
                    + "enableShaders=" + conShaders + "\n";
            Files.writeString(archivo, texto, StandardCharsets.UTF_8);
            r.aplicados.add("Shaders activados (" + pack + ")");
        } catch (IOException e) {
            r.avisos.add("No se pudo escribir la configuracion de shaders: " + e.getMessage());
        }
    }

    /** Lee una clave de un archivo de propiedades; null si no esta. */
    private static String leerClave(Path archivo, String clave) {
        if (!Files.isRegularFile(archivo)) return null;
        try {
            for (String l : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
                if (l.startsWith(clave + "=")) return l.substring(clave.length() + 1).trim();
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** Opciones del juego: distancia de vision, sombras, particulas... */
    private static void aplicarOpciones(Path archivo, Perfil p, Resultado r) {
        Map<String, String> nuevos = new LinkedHashMap<>();
        nuevos.put("renderDistance", String.valueOf(p.renderDistance));
        nuevos.put("simulationDistance", String.valueOf(p.simulationDistance));
        nuevos.put("graphicsMode", String.valueOf(p.graphicsMode));
        nuevos.put("biomeBlendRadius", String.valueOf(p.biomeBlendRadius));
        nuevos.put("mipmapLevels", String.valueOf(p.mipmapLevels));
        nuevos.put("particles", String.valueOf(p.particles));
        nuevos.put("entityShadows", String.valueOf(p.entityShadows));
        nuevos.put("maxFps", String.valueOf(p.maxFps));

        try {
            List<String> lineas = Files.isRegularFile(archivo)
                    ? new ArrayList<>(Files.readAllLines(archivo, StandardCharsets.UTF_8))
                    : new ArrayList<>();
            respaldar(archivo);
            for (Map.Entry<String, String> e : nuevos.entrySet()) {
                boolean puesto = false;
                for (int i = 0; i < lineas.size(); i++) {
                    if (lineas.get(i).startsWith(e.getKey() + ":")) {
                        lineas.set(i, e.getKey() + ":" + e.getValue());
                        puesto = true;
                        break;
                    }
                }
                if (!puesto) lineas.add(e.getKey() + ":" + e.getValue());
            }
            Files.write(archivo, lineas, StandardCharsets.UTF_8);
            r.aplicados.add("Opciones del juego (" + nuevos.size() + " valores)");
        } catch (IOException e) {
            r.avisos.add("No se pudo escribir options.txt: " + e.getMessage());
        }
    }

    /** Distant Horizons: radio del horizonte, calidad e hilos. */
    private static void aplicarDistantHorizons(Path archivo, Perfil p, Resultado r) {
        if (!Files.isRegularFile(archivo)) {
            r.aplicados.add("Distant Horizons no esta instalado: se omite");
            return;
        }
        try {
            List<String> lineas = new ArrayList<>(Files.readAllLines(archivo, StandardCharsets.UTF_8));
            respaldar(archivo);
            int cambios = 0;
            for (int i = 0; i < lineas.size(); i++) {
                String l = lineas.get(i);
                String sangria = l.substring(0, l.length() - l.stripLeading().length());
                String limpio = l.strip();
                if (limpio.startsWith("lodChunkRenderDistanceRadius")) {
                    lineas.set(i, sangria + "lodChunkRenderDistanceRadius = " + p.dhRadio); cambios++;
                } else if (limpio.startsWith("horizontalQuality")) {
                    lineas.set(i, sangria + "horizontalQuality = \"" + p.dhCalidad + "\""); cambios++;
                } else if (limpio.startsWith("verticalQuality")) {
                    lineas.set(i, sangria + "verticalQuality = \"" + p.dhCalidad + "\""); cambios++;
                } else if (limpio.startsWith("numberOfThreads")) {
                    lineas.set(i, sangria + "numberOfThreads = " + p.dhHilos); cambios++;
                }
            }
            Files.write(archivo, lineas, StandardCharsets.UTF_8);
            r.aplicados.add("Distant Horizons: horizonte de " + p.dhRadio
                    + " trozos, calidad " + p.dhCalidad + ", " + p.dhHilos + " hilos (" + cambios + " ajustes)");
        } catch (IOException e) {
            r.avisos.add("No se pudo escribir la configuracion de Distant Horizons: " + e.getMessage());
        }
    }

    /** Embeddium: el motor de render. Se reescribe entero, es corto. */
    private static void aplicarEmbeddium(Path archivo, Perfil p, Resultado r) {
        boolean rapido = (p == Perfil.BAJO);
        String json = "{\n"
            + "  \"quality\": {\n"
            + "    \"weather_quality\": \"" + (rapido ? "FAST" : "DEFAULT") + "\",\n"
            + "    \"leaves_quality\": \"" + (rapido ? "FAST" : "DEFAULT") + "\",\n"
            + "    \"enable_vignette\": " + (!rapido) + "\n"
            + "  },\n"
            + "  \"advanced\": {\n"
            + "    \"animate_only_visible_textures\": true,\n"
            + "    \"use_entity_culling\": true,\n"
            + "    \"allow_direct_memory_access\": true,\n"
            + "    \"cpu_render_ahead_limit\": " + (rapido ? 3 : 5) + "\n"
            + "  },\n"
            + "  \"performance\": {\n"
            + "    \"chunk_builder_threads\": 0,\n"
            + "    \"always_defer_chunk_updates\": " + rapido + ",\n"
            + "    \"use_fog_occlusion\": true,\n"
            + "    \"use_block_face_culling\": true,\n"
            + "    \"use_compact_vertex_format\": true,\n"
            + "    \"use_translucent_face_sorting\": " + (!rapido) + "\n"
            + "  }\n"
            + "}\n";
        try {
            Files.createDirectories(archivo.getParent());
            respaldar(archivo);
            Files.writeString(archivo, json, StandardCharsets.UTF_8);
            r.aplicados.add("Embeddium ajustado a " + (rapido ? "maximo rendimiento" : "calidad"));
        } catch (IOException e) {
            r.avisos.add("No se pudo escribir la configuracion de Embeddium: " + e.getMessage());
        }
    }

    /** Copia de seguridad antes de tocar un archivo. Solo la primera vez. */
    private static void respaldar(Path archivo) {
        if (!Files.isRegularFile(archivo)) return;
        Path copia = archivo.resolveSibling(archivo.getFileName() + ".antes-de-perfiles");
        if (Files.exists(copia)) return;   // ya hay una copia del estado original
        try {
            Files.copy(archivo, copia, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            // sin copia se sigue: el perfil se puede volver a aplicar
        }
    }

    /**
     * Lee el perfil que hay puesto ahora mismo, deduciendolo de options.txt.
     * Devuelve null si no coincide con ninguno (ajustes hechos a mano).
     *
     * Se comparan LOS OCHO valores que el perfil escribe, no solo unos pocos:
     * con tres bastaba para decir "sigue puesto el perfil Alto" aunque el
     * jugador hubiera cambiado a mano las sombras o el limite de fotogramas.
     */
    public static Perfil actual(Path gameDir) {
        if (gameDir == null) return null;
        Path opciones = gameDir.resolve("options.txt");
        if (!Files.isRegularFile(opciones)) return null;
        try {
            Map<String, String> v = new LinkedHashMap<>();
            for (String l : Files.readAllLines(opciones, StandardCharsets.UTF_8)) {
                int dosPuntos = l.indexOf(':');
                if (dosPuntos > 0) v.put(l.substring(0, dosPuntos), l.substring(dosPuntos + 1).trim());
            }
            for (Perfil p : Perfil.values()) {
                if (coincide(v, "renderDistance", p.renderDistance)
                        && coincide(v, "simulationDistance", p.simulationDistance)
                        && coincide(v, "graphicsMode", p.graphicsMode)
                        && coincide(v, "biomeBlendRadius", p.biomeBlendRadius)
                        && coincide(v, "mipmapLevels", p.mipmapLevels)
                        && coincide(v, "particles", p.particles)
                        && coincide(v, "maxFps", p.maxFps)
                        && String.valueOf(p.entityShadows).equals(v.get("entityShadows"))) {
                    return p;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static boolean coincide(Map<String, String> valores, String clave, int esperado) {
        String s = valores.get(clave);
        if (s == null) return false;
        try {
            return Integer.parseInt(s) == esperado;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Integer entero(String linea) {
        try {
            return Integer.parseInt(linea.substring(linea.indexOf(':') + 1).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
