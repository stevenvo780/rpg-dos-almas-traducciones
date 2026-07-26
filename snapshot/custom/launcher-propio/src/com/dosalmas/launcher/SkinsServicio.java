package com.dosalmas.launcher;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Biblioteca local de skins del jugador. Sin Swing: solo lectura y escritura
 * en disco. El launcher NO puede aplicar una skin dentro del juego con una
 * cuenta offline y sin un mod de skins instalado (ver PanelSkins), asi que
 * este servicio se limita a lo que es honesto y util de verdad:
 *
 *   - guardar archivos PNG validados en una carpeta propia,
 *   - recordar cual esta "activa" y si su modelo es clasico o fino (slim),
 *   - detectar si el pack trae instalado algun mod de skins conocido.
 *
 * Persistencia deliberadamente simple: los PNG viven sueltos en la carpeta,
 * un archivo de texto ".activa" guarda el nombre del activo y un sidecar
 * "<nombre>.slim" (vacio, solo su presencia importa) marca el modelo fino.
 */
public final class SkinsServicio {

    /** Palabras que delatan un mod de skins conocido por su nombre de jar. */
    private static final String[] MODS_DE_SKINS_CONOCIDOS = {
            "customskinloader", "skinrestorer", "ears", "figura", "csl-"
    };

    private final Path carpeta;

    public SkinsServicio() {
        this(Rutas.carpetaConfig().resolve("skins"));
    }

    /** Constructor con carpeta explicita, util para pruebas. */
    public SkinsServicio(Path carpeta) {
        this.carpeta = carpeta;
    }

    public Path carpetaSkins() {
        return carpeta;
    }

    // ------------------------------------------------------------- Modelo

    /** Una skin de la biblioteca local. */
    public static final class SkinInfo {
        public final String nombre; // nombre de archivo, incluye ".png"
        public final Path ruta;
        public final boolean slim;  // true = modelo fino (Alex), false = clasico (Steve)

        SkinInfo(String nombre, Path ruta, boolean slim) {
            this.nombre = nombre;
            this.ruta = ruta;
            this.slim = slim;
        }
    }

    /** Resultado de buscar un mod de skins en el pack. */
    public static final class ModSkinDetectado {
        public final String nombreArchivo;
        public final boolean habilitado; // true: en mods/ ; false: en mods-disabled/

        ModSkinDetectado(String nombreArchivo, boolean habilitado) {
            this.nombreArchivo = nombreArchivo;
            this.habilitado = habilitado;
        }
    }

    // --------------------------------------------------------------- Listar

    /** Todas las skins guardadas, ordenadas por nombre. Nunca lanza si la carpeta no existe aun. */
    public List<SkinInfo> listar() {
        List<SkinInfo> out = new ArrayList<>();
        if (!Files.isDirectory(carpeta)) return out;
        try (var s = Files.list(carpeta)) {
            List<Path> pngs = new ArrayList<>();
            s.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.toLowerCase(Locale.ROOT).endsWith(".png") && Files.isRegularFile(p)) pngs.add(p);
            });
            pngs.sort(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
            for (Path p : pngs) {
                String nombre = p.getFileName().toString();
                boolean slim = Files.exists(carpeta.resolve(nombre + ".slim"));
                out.add(new SkinInfo(nombre, p, slim));
            }
        } catch (IOException e) {
            // carpeta ilegible: se devuelve lo que se tenga, nunca revienta el panel
        }
        return out;
    }

    /** Nombre de la skin marcada como activa, o null si no hay ninguna o el archivo ya no existe. */
    public String nombreActivo() {
        Path marca = carpeta.resolve(".activa");
        if (!Files.isRegularFile(marca)) return null;
        try {
            String nombre = Files.readString(marca, StandardCharsets.UTF_8).trim();
            if (nombre.isEmpty() || !Files.isRegularFile(carpeta.resolve(nombre))) return null;
            return nombre;
        } catch (IOException e) {
            return null;
        }
    }

    /** La skin activa, o null si no hay ninguna. */
    public SkinInfo activa() {
        String nombre = nombreActivo();
        if (nombre == null) return null;
        for (SkinInfo s : listar()) {
            if (s.nombre.equals(nombre)) return s;
        }
        return null;
    }

    // ------------------------------------------------------------- Importar

    /**
     * Copia un PNG externo a la biblioteca, validando que tenga forma de skin
     * de Minecraft (64x64 o 64x32, el formato clasico). Si el nombre ya
     * existe se anade un sufijo numerico; nunca se sobreescribe sin avisar.
     *
     * @throws IllegalArgumentException si el archivo no es un PNG con
     *         dimensiones de skin valida (mensaje en espanol, listo para
     *         mostrar en el panel).
     */
    public SkinInfo importar(Path origen, String nombreSugerido) throws IOException {
        if (origen == null || !Files.isRegularFile(origen)) {
            throw new IllegalArgumentException("El archivo elegido no existe.");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(origen.toFile());
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo como imagen: " + e.getMessage());
        }
        if (img == null) {
            throw new IllegalArgumentException("Ese archivo no es una imagen PNG valida.");
        }
        if (!esFormaDeSkinValida(img)) {
            throw new IllegalArgumentException(
                    "Esa imagen mide " + img.getWidth() + "x" + img.getHeight()
                    + " px. Una skin de Minecraft debe medir 64x64 (formato actual) o 64x32 (formato clasico).");
        }

        Files.createDirectories(carpeta);
        String base = sanear(nombreSugerido);
        String nombre = base + ".png";
        int contador = 2;
        while (Files.exists(carpeta.resolve(nombre))) {
            nombre = base + " (" + contador + ").png";
            contador++;
        }
        Path destino = carpeta.resolve(nombre);
        Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        return new SkinInfo(nombre, destino, false);
    }

    /** true si mide 64x64 (formato con capas) o 64x32 (formato clasico). */
    public static boolean esFormaDeSkinValida(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        return (w == 64 && (h == 64 || h == 32));
    }

    private static String sanear(String nombre) {
        String base = (nombre == null || nombre.isBlank()) ? "skin" : nombre.trim();
        int punto = base.lastIndexOf('.');
        if (punto > 0) base = base.substring(0, punto);
        base = base.replaceAll("[\\\\/:*?\"<>|]+", "_").trim();
        if (base.isEmpty()) base = "skin";
        return base;
    }

    // -------------------------------------------------------------- Borrar

    /** Elimina una skin de la biblioteca (y su marca de modelo fino si tenia). */
    public void eliminar(String nombre) throws IOException {
        Files.deleteIfExists(carpeta.resolve(nombre));
        Files.deleteIfExists(carpeta.resolve(nombre + ".slim"));
        if (nombre.equals(nombreActivo())) {
            Files.deleteIfExists(carpeta.resolve(".activa"));
        }
    }

    // --------------------------------------------------------------- Activa

    /** Marca una skin ya existente en la biblioteca como la activa. */
    public void marcarActiva(String nombre) throws IOException {
        if (!Files.isRegularFile(carpeta.resolve(nombre))) {
            throw new IllegalArgumentException("Esa skin ya no esta en la biblioteca.");
        }
        Files.createDirectories(carpeta);
        Files.writeString(carpeta.resolve(".activa"), nombre, StandardCharsets.UTF_8);
    }

    /** Cambia el modelo (clasico/fino) de una skin guardada. */
    public void setSlim(String nombre, boolean slim) throws IOException {
        Path marca = carpeta.resolve(nombre + ".slim");
        if (slim) {
            Files.createDirectories(carpeta);
            if (!Files.exists(marca)) Files.createFile(marca);
        } else {
            Files.deleteIfExists(marca);
        }
    }

    // --------------------------------------------------------- Carga de imagen

    /** Carga el PNG de una skin ya validada; null si ha desaparecido del disco. */
    public static BufferedImage cargarImagen(Path ruta) {
        try {
            return ImageIO.read(ruta.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    // --------------------------------------------------------- Deteccion de mod

    /**
     * Busca en mods/ y mods-disabled/ del pack algun mod de skins conocido.
     * Devuelve null si no hay ninguno: es el caso de hoy en RPG Dos Almas, y
     * el panel debe explicarlo con claridad en vez de fingir que existe.
     */
    public static ModSkinDetectado detectarModDeSkins(Path gameDir) {
        if (gameDir == null) return null;
        ModSkinDetectado enHabilitados = buscarEn(gameDir.resolve("mods"), true);
        if (enHabilitados != null) return enHabilitados;
        return buscarEn(gameDir.resolve("mods-disabled"), false);
    }

    private static ModSkinDetectado buscarEn(Path carpetaMods, boolean habilitado) {
        if (!Files.isDirectory(carpetaMods)) return null;
        try (var s = Files.list(carpetaMods)) {
            for (Path p : (Iterable<Path>) s.sorted()::iterator) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!n.endsWith(".jar")) continue;
                for (String clave : MODS_DE_SKINS_CONOCIDOS) {
                    if (n.contains(clave)) return new ModSkinDetectado(p.getFileName().toString(), habilitado);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
