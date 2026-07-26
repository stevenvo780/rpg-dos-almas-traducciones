package com.dosalmas.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deja la instalacion como debe estar JUSTO ANTES de lanzar el juego.
 *
 * El problema que resuelve: Minecraft lee options.txt al arrancar y lo
 * REESCRIBE ENTERO al cerrarse, con lo que tenga en memoria. Cualquier cambio
 * hecho desde fuera mientras se juega -- el idioma, los packs de recursos, las
 * teclas -- se pierde en cuanto el jugador sale. Ha pasado tres veces: las
 * teclas de un equipo, su traduccion, y la configuracion entera de otro.
 *
 * La solucion es aplicar la configuracion en el unico momento en que nadie la
 * puede pisar: cuando el launcher va a arrancar el juego y este todavia no
 * corre. A partir de ahi, el juego arranca ya con lo correcto y al cerrarse
 * reescribe esos mismos valores.
 *
 * Se aplica lo que dice un "perfil compartido" (perfil-compartido.txt junto a
 * la instalacion): idioma, packs de recursos y teclas. Todo lo demas -- la
 * resolucion, el volumen, la sensibilidad del raton, la calidad grafica -- es
 * de cada jugador y NO se toca.
 */
public final class Sincronizador {

    /** Nombre del archivo que describe lo que debe compartirse entre equipos. */
    public static final String ARCHIVO_PERFIL = "perfil-compartido.txt";

    /** Clave de options.txt con la lista de packs de recursos activos. */
    private static final String CLAVE_PACKS = "resourcePacks";

    /**
     * Version de datos del archivo de opciones.
     *
     * Si falta, Minecraft supone que el archivo es antiquisimo y le pasa TODOS
     * los conversores de formato, incluido el que traduce las teclas del
     * formato numerico viejo (key_key.attack:-100) al moderno
     * (key_key.attack:key.mouse.left). Al encontrarse nombres modernos, ese
     * conversor lanza NumberFormatException, Minecraft DESCARTA EL ARCHIVO
     * ENTERO y arranca con todo por defecto: en ingles y sin packs de recursos.
     * En el registro solo queda un "Failed to load options" que nadie mira.
     *
     * Paso de verdad en una instalacion nueva. Por eso el perfil compartido
     * lleva la version y aqui se garantiza que este.
     */
    private static final String CLAVE_VERSION = "version";

    private Sincronizador() {}

    /** Lo que se cambio, para poder contarlo en el registro de la partida. */
    public static final class Resultado {
        public final List<String> cambios = new ArrayList<>();
        public final List<String> avisos = new ArrayList<>();
        public boolean huboCambios() { return !cambios.isEmpty(); }
    }

    /**
     * Aplica el perfil compartido a la instalacion. Silencioso si no hay
     * perfil: el launcher tiene que funcionar igual sin el.
     */
    public static Resultado sincronizar(Path gameDir) {
        Resultado r = new Resultado();
        if (gameDir == null || !Files.isDirectory(gameDir)) return r;

        Path perfil = gameDir.resolve(ARCHIVO_PERFIL);
        if (!Files.isRegularFile(perfil)) return r;   // sin perfil no hay nada que imponer

        Map<String, String> deseado;
        try {
            deseado = leerPerfil(perfil);
        } catch (IOException e) {
            r.avisos.add("No se pudo leer " + ARCHIVO_PERFIL + ": " + e.getMessage());
            return r;
        }
        if (deseado.isEmpty()) return r;

        Path opciones = gameDir.resolve("options.txt");
        try {
            List<String> lineas = Files.isRegularFile(opciones)
                    ? new ArrayList<>(Files.readAllLines(opciones, StandardCharsets.UTF_8))
                    : new ArrayList<>();

            // Solo se reescribe si de verdad hace falta: asi no se toca la
            // fecha del archivo en cada arranque.
            Map<String, String> actual = aMapa(lineas);

            // La lista de packs no se puede imponer a ciegas: ver depurarPacks.
            if (deseado.containsKey(CLAVE_PACKS)) {
                deseado.put(CLAVE_PACKS, depurarPacks(
                        deseado.get(CLAVE_PACKS), actual.get(CLAVE_PACKS), gameDir, r));
            }

            Map<String, String> aCambiar = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : deseado.entrySet()) {
                String tiene = actual.get(e.getKey());
                // La version solo se pone si falta: nunca se pisa la que tenga
                // el equipo. Imponerla degradaria el archivo de quien tuviera
                // una version mas nueva del juego y le dispararia conversores
                // de formato que no le tocan.
                if (e.getKey().equals(CLAVE_VERSION)) {
                    if (tiene == null || tiene.isBlank()) aCambiar.put(e.getKey(), e.getValue());
                    continue;
                }
                if (!e.getValue().equals(tiene)) aCambiar.put(e.getKey(), e.getValue());
            }
            if (aCambiar.isEmpty()) return r;

            respaldarUnaVez(opciones);
            aplicar(lineas, aCambiar);
            Files.write(opciones, lineas, StandardCharsets.UTF_8);

            for (Map.Entry<String, String> e : aCambiar.entrySet()) {
                r.cambios.add(describir(e.getKey(), e.getValue()));
            }
        } catch (IOException e) {
            r.avisos.add("No se pudo escribir options.txt: " + e.getMessage());
        }
        return r;
    }

    // ------------------------------------------------------------- Detalles

    /**
     * Lee el perfil compartido. Formato: una clave de options.txt por linea,
     * con el mismo separador que usa Minecraft (dos puntos). Las lineas que
     * empiezan por # son comentarios.
     *
     *   lang:es_es
     *   resourcePacks:["file/RPG-Dos-Almas-Espanol"]
     *   key_key.betterzoom.zoom:key.keyboard.x
     */
    private static Map<String, String> leerPerfil(Path archivo) throws IOException {
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
            String s = l.strip();
            if (s.isEmpty() || s.startsWith("#")) continue;
            int i = s.indexOf(':');
            if (i <= 0) continue;
            m.put(s.substring(0, i), s.substring(i + 1));
        }
        return m;
    }

    private static Map<String, String> aMapa(List<String> lineas) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : lineas) {
            int i = l.indexOf(':');
            if (i > 0) m.put(l.substring(0, i), l.substring(i + 1));
        }
        return m;
    }

    /** Sustituye las claves que ya estan y añade las que falten. */
    private static void aplicar(List<String> lineas, Map<String, String> cambios) {
        for (Map.Entry<String, String> e : cambios.entrySet()) {
            String prefijo = e.getKey() + ":";
            boolean puesta = false;
            for (int i = 0; i < lineas.size(); i++) {
                if (lineas.get(i).startsWith(prefijo)) {
                    lineas.set(i, prefijo + e.getValue());
                    puesta = true;
                    break;
                }
            }
            if (!puesta) lineas.add(prefijo + e.getValue());
        }
    }

    /**
     * Packs que el perfil quiere activar y no estan instalados. Lo usa el
     * Verificador para poder avisar antes de lanzar.
     */
    public static List<String> packsQueFaltan(Path gameDir) throws IOException {
        List<String> faltan = new ArrayList<>();
        Path perfil = gameDir.resolve(ARCHIVO_PERFIL);
        if (!Files.isRegularFile(perfil)) return faltan;

        String lista = leerPerfil(perfil).get(CLAVE_PACKS);
        Path carpeta = gameDir.resolve("resourcepacks");
        for (String entrada : leerLista(lista)) {
            if (!existePack(carpeta, entrada)) faltan.add(nombreDePack(entrada));
        }
        return faltan;
    }

    // --------------------------------------------------------- Packs

    /**
     * Ajusta la lista de packs antes de imponerla. Hace dos cosas que no se
     * pueden dar por supuestas:
     *
     * 1. DESCARTA las referencias 'file/<x>' cuyo archivo o carpeta no esta en
     *    disco. Minecraft resuelve cada entrada contra resourcepacks/ y la que
     *    no resuelve la ignora EN SILENCIO: el juego arranca entero en ingles,
     *    sin un solo aviso, y al cerrarse borra la linea. En el siguiente
     *    arranque el launcher la volveria a escribir y el juego a descartarla,
     *    un bucle que no converge, no falla y no avisa a nadie. Mejor no
     *    escribirla y decir que falta.
     *
     * 2. CONSERVA los packs que el jugador haya anadido por su cuenta. Antes se
     *    reemplazaba la linea entera y se los borraba en cada arranque.
     */
    private static String depurarPacks(String deseado, String actual, Path gameDir, Resultado r) {
        List<String> quiere = leerLista(deseado);
        if (quiere.isEmpty()) return deseado;   // formato inesperado: no tocar

        Path carpeta = gameDir.resolve("resourcepacks");
        List<String> finales = new ArrayList<>();
        List<String> ausentes = new ArrayList<>();
        for (String entrada : quiere) {
            if (existePack(carpeta, entrada)) finales.add(entrada);
            else ausentes.add(nombreDePack(entrada));
        }

        // Lo que el jugador tenia y no viene en el perfil es suyo: va al final,
        // que en options.txt es la posicion de mayor prioridad.
        for (String entrada : leerLista(actual)) {
            if (!finales.contains(entrada) && existePack(carpeta, entrada)) {
                finales.add(entrada);
            }
        }

        if (!ausentes.isEmpty()) {
            r.avisos.add("no estan instalados y no se activan: " + String.join(", ", ausentes));
        }
        return escribirLista(finales);
    }

    /** Las entradas que no son 'file/<x>' las aporta el propio juego o un mod. */
    private static boolean existePack(Path carpeta, String entrada) {
        if (!entrada.startsWith("file/")) return true;
        return Files.exists(carpeta.resolve(entrada.substring(5)));
    }

    private static String nombreDePack(String entrada) {
        return entrada.startsWith("file/") ? entrada.substring(5) : entrada;
    }

    /** Lee la lista de options.txt: ["a","b"]. Devuelve vacio si no lo es. */
    private static List<String> leerLista(String valor) {
        List<String> out = new ArrayList<>();
        if (valor == null) return out;
        String s = valor.strip();
        if (!s.startsWith("[") || !s.endsWith("]")) return out;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(s);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static String escribirLista(List<String> valores) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < valores.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(valores.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    /** Copia del options.txt original, solo la primera vez. */
    private static void respaldarUnaVez(Path opciones) {
        if (!Files.isRegularFile(opciones)) return;
        Path copia = opciones.resolveSibling("options.txt.antes-de-sincronizar");
        if (Files.exists(copia)) return;
        try {
            Files.copy(opciones, copia);
        } catch (IOException e) {
            // sin copia se sigue: el perfil se puede volver a aplicar
        }
    }

    /** Frase legible de lo que se cambio, para el registro. */
    private static String describir(String clave, String valor) {
        if (clave.equals("lang")) return "idioma -> " + valor;
        if (clave.equals(CLAVE_PACKS)) {
            // Se cuenta sobre la lista ya depurada, que solo contiene packs que
            // estan en disco. Contar sobre la cadena del perfil daria un numero
            // falso: decia "3 activados" en equipos donde se activaban cero.
            long n = leerLista(valor).stream().filter(e -> e.startsWith("file/")).count();
            return "packs de recursos -> " + n + " activados";
        }
        if (clave.startsWith("key_")) return "tecla " + clave.substring(4) + " -> " + valor;
        return clave + " -> " + valor;
    }

    // ------------------------------------------------------- Crear el perfil

    /**
     * Genera un perfil compartido a partir de una instalacion que ya esta como
     * se quiere. Sirve para tomar un equipo como referencia y llevar su idioma,
     * sus packs y sus teclas a los demas.
     */
    public static String generarDesde(Path gameDir) throws IOException {
        Path opciones = gameDir.resolve("options.txt");
        if (!Files.isRegularFile(opciones)) {
            throw new IOException("no hay options.txt en " + gameDir);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Perfil compartido de RPG Dos Almas.\n")
          .append("# El launcher aplica estos valores JUSTO ANTES de arrancar el juego,\n")
          .append("# que es el unico momento en que Minecraft no los puede sobrescribir.\n")
          .append("# Lo que no aparezca aqui (resolucion, volumen, calidad grafica...)\n")
          .append("# es de cada jugador y no se toca.\n\n");

        List<String> teclas = new ArrayList<>();
        for (String l : Files.readAllLines(opciones, StandardCharsets.UTF_8)) {
            if (l.startsWith("lang:") || l.startsWith("resourcePacks:")
                    || l.startsWith("incompatibleResourcePacks:")
                    || l.startsWith(CLAVE_VERSION + ":")) {
                sb.append(l).append('\n');
            } else if (l.startsWith("key_")) {
                teclas.add(l);
            }
        }
        sb.append('\n').append("# --- teclas (").append(teclas.size()).append(") ---\n");
        for (String t : teclas) sb.append(t).append('\n');
        return sb.toString();
    }
}
