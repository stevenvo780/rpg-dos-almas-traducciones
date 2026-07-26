package com.dosalmas.launcher;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro en disco de una partida.
 *
 * Existe porque los fallos mas dificiles de diagnosticar ocurren ANTES de que
 * el juego arranque del todo: si la maquina virtual muere montando los
 * modulos de Forge, no llega a escribirse nada en logs/latest.log del juego.
 * Aqui queda todo, ademas del comando exacto y del entorno.
 *
 * Se conservan las ultimas SESIONES_QUE_SE_GUARDAN partidas; el resto se borra
 * para no llenar el disco.
 */
public final class Registro implements AutoCloseable {

    private static final int SESIONES_QUE_SE_GUARDAN = 10;
    private static final DateTimeFormatter SELLO =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path archivo;
    private final BufferedWriter salida;

    private Registro(Path archivo, BufferedWriter salida) {
        this.archivo = archivo;
        this.salida = salida;
    }

    public Path archivo() { return archivo; }

    /**
     * Abre el registro de una partida. Si no se puede escribir (disco lleno,
     * permisos), devuelve un registro que no hace nada: no tener log jamas
     * debe impedir jugar.
     */
    public static Registro abrir(MinecraftLauncher.Profile p, List<String> cmd) {
        try {
            Path dir = Rutas.carpetaLogs();
            Files.createDirectories(dir);
            rotar(dir);
            Path f = dir.resolve("launcher-" + LocalDateTime.now().format(SELLO) + ".log");
            BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Registro r = new Registro(f, w);
            r.cabecera(p, cmd);
            return r;
        } catch (IOException | RuntimeException e) {
            return new Registro(null, null);
        }
    }

    private void cabecera(MinecraftLauncher.Profile p, List<String> cmd) {
        escribir("=== RPG Dos Almas -- registro de la partida ===");
        escribir("fecha        : " + LocalDateTime.now());
        escribir("launcher     : " + MinecraftLauncher.LAUNCHER_NAME + " " + MinecraftLauncher.LAUNCHER_VERSION);
        escribir("sistema      : " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        escribir("java launcher: " + System.getProperty("java.version") + " -- " + System.getProperty("java.home"));
        escribir("java del juego: " + p.javaBin
                + (p.javaBin != null ? "  (Java " + Rutas.versionMayorDe(p.javaBin) + ")" : ""));
        escribir("carpeta juego: " + p.gameDir);
        escribir("version      : " + p.versionId);
        escribir("assets       : " + p.assetsRoot);
        escribir("memoria      : " + p.minMemoryMb + " MB - " + p.maxMemoryMb + " MB");
        escribir("jugador      : " + p.playerName + "  uuid " + MinecraftLauncher.offlineUuid(p.playerName));
        escribir("");
        escribir("--- comando ---");
        for (String c : cmd) escribir("  " + c);
        escribir("");
        escribir("--- salida del juego ---");
    }

    public void linea(String texto) { escribir(texto); }

    public void cierre(int codigo) {
        escribir("");
        escribir("--- el juego termino con codigo " + codigo + " ---");
    }

    private void escribir(String texto) {
        if (salida == null) return;
        try {
            salida.write(texto);
            salida.newLine();
            salida.flush();   // si el juego muere de golpe, lo escrito debe estar en disco
        } catch (IOException e) {
            // un fallo al registrar no puede interrumpir la partida
        }
    }

    @Override
    public void close() {
        if (salida == null) return;
        try {
            salida.close();
        } catch (IOException e) {
            // nada que hacer al cerrar
        }
    }

    /** Deja solo las ultimas sesiones, borrando las mas antiguas. */
    private static void rotar(Path dir) {
        List<Path> logs = new ArrayList<>();
        try (var s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                String n = f.getFileName().toString();
                if (n.startsWith("launcher-") && n.endsWith(".log")) logs.add(f);
            }
        } catch (IOException e) {
            return;
        }
        if (logs.size() < SESIONES_QUE_SE_GUARDAN) return;
        logs.sort(java.util.Comparator.comparing(f -> f.getFileName().toString()));
        for (int i = 0; i <= logs.size() - SESIONES_QUE_SE_GUARDAN; i++) {
            try {
                Files.deleteIfExists(logs.get(i));
            } catch (IOException e) {
                // si no se puede borrar, se deja
            }
        }
    }
}
