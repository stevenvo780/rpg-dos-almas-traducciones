package com.dosalmas.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Entrada por linea de comandos del launcher. Sirve para validar el nucleo
 * antes de tener interfaz grafica, y como modo de depuracion permanente.
 *
 *   --print     muestra el comando que se ejecutaria (no lanza el juego)
 *   --check     verifica la instalacion (classpath, assets, java, natives)
 *   --launch    lanza el juego y muestra su salida en vivo
 *
 * Ademas, los verbos de {@link Cli} (estado, mods, activar-mod, ...) devuelven
 * lo mismo en JSON, pensados para agentes y para el servidor MCP. Se despachan
 * antes que los modos con guiones y no cambian el comportamiento de estos.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            LauncherWindow.abrir(); // sin argumentos: interfaz grafica
            return;
        }
        String mode = args[0];
        if (mode.equals("--help") || mode.equals("-h")) { ayuda(); return; }

        // Verbos en JSON para agentes y para el servidor MCP. Van antes de los
        // modos con guiones y no comparten nombre con ninguno, asi que --check,
        // --print, --cp y --launch se comportan exactamente igual que siempre.
        if (Cli.reconoce(mode)) { Cli.despachar(args); return; }

        // La configuracion guardada y la deteccion automatica son la base;
        // los argumentos solo sobreescriben lo que se indique de forma expresa.
        Config cfg = Config.load();
        MinecraftLauncher.Profile p = cfg.toProfile();

        String gameDir = arg(args, "--gameDir");
        if (gameDir != null) {
            p.gameDir = Paths.get(gameDir);
            // Al cambiar de instalacion, lo demas se vuelve a deducir de ella.
            if (arg(args, "--version") == null) {
                String v = Rutas.detectarVersionId(p.gameDir);
                if (v != null) p.versionId = v;
            }
            if (arg(args, "--assets") == null) {
                Path a = Rutas.detectarAssets(p.gameDir, p.versionId);
                if (a != null) p.assetsRoot = a;
            }
        }
        if (arg(args, "--version") != null) p.versionId  = arg(args, "--version");
        if (arg(args, "--assets")  != null) p.assetsRoot = Paths.get(arg(args, "--assets"));
        if (arg(args, "--java")    != null) p.javaBin    = Paths.get(arg(args, "--java"));
        if (arg(args, "--name")    != null) p.playerName = arg(args, "--name");
        if (arg(args, "--ram")     != null) p.maxMemoryMb = Integer.parseInt(arg(args, "--ram"));

        try {
            switch (mode) {
                case "--print":  print(p);  break;
                case "--cp":     classpath(p); break;
                case "--launch": launch(p); break;
                case "--check":
                default:         check(p);  break;
            }
        } catch (InstalacionIncompletaException e) {
            imprimirProblemas(e.problemas());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("\nError: " + Mensajes.explicar(e));
            System.exit(1);
        }
    }

    private static void ayuda() {
        System.out.println("""
                Launcher de RPG Dos Almas

                  (sin argumentos)  abre la interfaz grafica
                  --check           verifica la instalacion y sale
                  --print           muestra el comando de lanzamiento
                  --cp              lista el classpath
                  --launch          lanza el juego en esta consola

                Opciones: --gameDir RUTA  --version ID  --assets RUTA
                          --java RUTA     --name NOMBRE --ram MB

                Sin opciones, el launcher detecta la instalacion por su cuenta.

                Verbos en JSON (para agentes y para el servidor MCP):
                  estado                          resumen de la instalacion
                  mods                            lista mods activos y desactivados
                  activar-mod ARCHIVO             mueve el jar a mods/
                  desactivar-mod ARCHIVO          mueve el jar a mods-disabled/
                  registros                       lista los registros guardados
                  registro NOMBRE [--lineas N]    muestra un registro
                  config                          muestra la configuracion
                  config-set CLAVE VALOR          cambia un valor y lo guarda
                  jugar --name X [--ram N]        lanza el juego en segundo plano""");
    }

    // ------------------------------------------------------------- Modos

    private static void check(MinecraftLauncher.Profile p) throws Exception {
        System.out.println("== Verificacion de la instalacion ==");
        System.out.println("  carpeta del juego : " + describir(p.gameDir));
        System.out.println("  version           : " + p.versionId);
        System.out.println("  carpeta de assets : " + describir(p.assetsRoot));
        System.out.println("  java del juego    : " + describirJava(p.javaBin));

        List<Verificador.Problema> problemas = Verificador.revisar(p);

        // El classpath solo se puede describir si la version es legible.
        if (p.gameDir != null && Files.isRegularFile(p.versionJson())) {
            try {
                MinecraftLauncher ml = new MinecraftLauncher(p);
                System.out.println("  classpath         : " + ml.buildClasspath().size() + " entradas"
                        + (ml.missingLibraries().isEmpty() ? ""
                           : "  (faltan " + ml.missingLibraries().size() + ")"));
                System.out.println("  Java que pide     : " + ml.requiredJavaMajor());
            } catch (IOException e) {
                System.out.println("  classpath         : no se pudo calcular");
            }
        }

        System.out.println("\nUUID offline de '" + p.playerName + "': "
                + MinecraftLauncher.offlineUuid(p.playerName));

        System.out.println();
        if (problemas.isEmpty()) {
            System.out.println("Todo correcto: la instalacion esta lista para jugar.");
            return;
        }
        imprimirProblemas(problemas);
        if (Verificador.hayBloqueantes(problemas)) System.exit(1);
    }

    private static void imprimirProblemas(List<Verificador.Problema> problemas) {
        long fallos = problemas.stream().filter(Verificador.Problema::bloquea).count();
        long avisos = problemas.size() - fallos;
        System.out.println("Se encontraron " + fallos + " fallo(s) y " + avisos + " aviso(s):\n");
        for (Verificador.Problema pr : problemas) System.out.println(pr + "\n");
    }

    private static String describir(Path p) {
        if (p == null) return "(no encontrada)";
        return p + (Files.exists(p) ? "" : "   <-- no existe");
    }

    private static String describirJava(Path p) {
        if (p == null) return "(no encontrado)";
        int v = Rutas.versionMayorDe(p);
        return p + (v > 0 ? "   (Java " + v + ")" : "   <-- no se pudo comprobar");
    }

    /** Lista el classpath entrada por entrada (diagnostico de conflictos de modulos). */
    private static void classpath(MinecraftLauncher.Profile p) throws Exception {
        List<String> cp = new MinecraftLauncher(p).buildClasspath();
        System.out.println("== Classpath (" + cp.size() + " entradas) ==");
        for (String c : cp) {
            String name = c.substring(c.lastIndexOf('/') + 1);
            System.out.println((name.contains("client") || name.contains("forge") || name.contains("minecraft")
                    ? " *  " : "    ") + name);
        }
    }

    private static void print(MinecraftLauncher.Profile p) throws Exception {
        List<String> cmd = new MinecraftLauncher(p).buildCommand();
        System.out.println("== Comando de lanzamiento (" + cmd.size() + " argumentos) ==");
        for (String c : cmd) {
            System.out.println(c.length() > 300 ? c.substring(0, 300) + "  ...[" + c.length() + " chars]" : c);
        }
    }

    private static void launch(MinecraftLauncher.Profile p) throws Exception {
        MinecraftLauncher ml = new MinecraftLauncher(p);
        System.out.println("Lanzando " + p.versionId + " como '" + p.playerName + "'...");
        Process proc = ml.launch();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) System.out.println(line);
        }
        int code = proc.waitFor();
        System.out.println("El juego termino con codigo " + code);
        System.exit(code);
    }

    // ------------------------------------------------------------ Utiles

    private static void ok(String label, boolean good, String detail) {
        System.out.printf("  [%s] %-28s %s%n", good ? "OK" : "!!", label, detail);
    }

    private static String arg(String[] args, String key) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) return args[i + 1];
        }
        return null;
    }

    private static String orDefault(String v, String def) { return (v == null) ? def : v; }

    private static Path path(String v, String def) { return Paths.get(orDefault(v, def)); }
}
