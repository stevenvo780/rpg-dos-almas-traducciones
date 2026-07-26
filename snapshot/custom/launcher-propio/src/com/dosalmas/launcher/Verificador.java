package com.dosalmas.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprueba que una instalacion esta completa ANTES de lanzar el juego.
 *
 * La razon de existir de esta clase: cuando falta una pieza, Minecraft no
 * dice que falta; muere con un volcado de la JVM que no se parece en nada a
 * la causa. Aqui se detecta antes y se explica en una frase que el jugador
 * pueda entender y accionar.
 *
 * La usan por igual la linea de comandos (--check) y la interfaz grafica.
 */
public final class Verificador {

    private Verificador() {}

    public enum Nivel { BLOQUEANTE, AVISO }

    /** Un problema concreto, con lo que hay que hacer para resolverlo. */
    public static final class Problema {
        public final Nivel nivel;
        public final String titulo;
        public final String detalle;
        public final String accion;

        public Problema(Nivel nivel, String titulo, String detalle, String accion) {
            this.nivel = nivel;
            this.titulo = titulo;
            this.detalle = detalle;
            this.accion = accion;
        }

        public boolean bloquea() { return nivel == Nivel.BLOQUEANTE; }

        @Override public String toString() {
            return (bloquea() ? "[FALLO] " : "[AVISO] ") + titulo
                    + (detalle.isEmpty() ? "" : "\n         " + detalle)
                    + (accion.isEmpty()  ? "" : "\n         -> " + accion);
        }
    }

    public static boolean hayBloqueantes(List<Problema> problemas) {
        return problemas.stream().anyMatch(Problema::bloquea);
    }

    // ------------------------------------------------------------- Revision

    /** Revision completa. El launcher se construye aqui si no se pasa uno ya creado. */
    public static List<Problema> revisar(MinecraftLauncher.Profile p, MinecraftLauncher ml) {
        List<Problema> out = new ArrayList<>();

        if (p.gameDir == null) {
            out.add(bloqueo("No se encontro la carpeta del juego",
                    "El launcher busco el modpack en las ubicaciones habituales y no lo hallo.",
                    "Abre Ajustes e indica la carpeta que contiene 'mods' y 'versions'."));
            return out;   // sin carpeta no tiene sentido seguir comprobando
        }
        if (!Files.isDirectory(p.gameDir)) {
            out.add(bloqueo("La carpeta del juego no existe",
                    p.gameDir.toString(),
                    "Abre Ajustes y corrige la ruta de la carpeta del juego."));
            return out;
        }
        if (!Files.isWritable(p.gameDir)) {
            out.add(bloqueo("La carpeta del juego es de solo lectura",
                    p.gameDir + " -- el juego necesita escribir partidas, opciones y registros.",
                    "Copia el modpack a una carpeta tuya, o corrige los permisos."));
        }

        // ------------------------------------------------ version del pack
        if (!Files.isRegularFile(p.versionJson())) {
            out.add(bloqueo("Falta el archivo de la version '" + p.versionId + "'",
                    p.versionJson().toString(),
                    "Comprueba que la carpeta del juego es la correcta y que la version esta instalada."));
            return out;   // sin el JSON no se puede construir nada
        }
        if (!Files.isRegularFile(p.versionJar())) {
            out.add(bloqueo("Falta el jar de la version '" + p.versionId + "'",
                    p.versionJar().toString(),
                    "Reinstala la version o restaura la carpeta desde una copia de seguridad."));
        }

        // ------------------------------------------------------- natives
        Path natives = p.nativesDir();
        if (!Files.isDirectory(natives)) {
            try {
                Files.createDirectories(natives);
            } catch (IOException e) {
                out.add(bloqueo("No existe la carpeta de librerias nativas y no se pudo crear",
                        natives.toString(),
                        "Revisa los permisos de la carpeta del juego."));
            }
        }

        // ---------------------------------------------------------- Java
        revisarJava(p, ml, out);

        // ----------------------------------------------------- classpath
        if (ml != null) {
            try {
                List<String> cp = ml.buildClasspath();
                List<String> faltan = ml.missingLibraries();
                if (!faltan.isEmpty()) {
                    out.add(bloqueo("Faltan " + faltan.size() + " librerias del juego",
                            resumir(faltan),
                            "Vuelve a copiar la carpeta 'libraries' completa desde una instalacion buena."));
                }
                long forge = cp.stream()
                        .filter(s -> s.contains("minecraftforge") || s.contains("net/minecraft/client"))
                        .count();
                if (forge < 8) {
                    out.add(bloqueo("El classpath no incluye los jars de Forge/FML",
                            "Encontrados " + forge + ", se esperan al menos 8.",
                            "Falta el archivo '*Additional.json' de la version, o la carpeta 'libraries' esta incompleta."));
                }
                // Sin estos dos la JVM no llega ni a crear la capa de modulos.
                exigirEnClasspath(cp, "bootstraplauncher", out);
                exigirEnClasspath(cp, "securejarhandler", out);
            } catch (IOException e) {
                out.add(bloqueo("No se pudo leer la informacion de la version",
                        String.valueOf(e.getMessage()),
                        "El archivo de version puede estar dañado."));
            }
        }

        // -------------------------------------------------------- assets
        revisarAssets(p, ml, out);

        // --------------------------------------------------------- packs
        revisarPacks(p, out);

        return out;
    }

    /**
     * Comprueba que los packs que el perfil compartido va a activar estan de
     * verdad en disco.
     *
     * Sin esto el fallo es invisible: Minecraft descarta en silencio las
     * entradas 'file/<x>' que no resuelve, arranca entero en ingles y no dice
     * nada. El jugador reporta "faltan traducciones" y la instalacion parece
     * perfecta. Se avisa, no se bloquea: el juego funciona, solo que sin
     * traducir, y dejar a alguien sin jugar por eso seria peor.
     */
    private static void revisarPacks(MinecraftLauncher.Profile p, List<Problema> out) {
        Path perfil = p.gameDir.resolve(Sincronizador.ARCHIVO_PERFIL);
        if (!Files.isRegularFile(perfil)) return;

        List<String> faltan;
        try {
            faltan = Sincronizador.packsQueFaltan(p.gameDir);
        } catch (IOException e) {
            return;   // ya se avisa de la lectura del perfil en la sincronizacion
        }
        if (faltan.isEmpty()) return;

        out.add(new Problema(Nivel.AVISO,
                faltan.size() == 1 ? "Falta un pack de recursos"
                                   : "Faltan " + faltan.size() + " packs de recursos",
                "El perfil compartido activa " + resumir(faltan) + ", pero no esta en "
                        + p.gameDir.resolve("resourcepacks")
                        + ". Minecraft los descarta sin avisar y el juego arranca en ingles.",
                "Copia esos packs desde el equipo de referencia a la carpeta 'resourcepacks'."));
    }

    /** Revision para un perfil suelto (construye el launcher si puede). */
    public static List<Problema> revisar(MinecraftLauncher.Profile p) {
        MinecraftLauncher ml = null;
        if (p.gameDir != null && Files.isRegularFile(p.versionJson())) {
            try {
                ml = new MinecraftLauncher(p);
            } catch (IOException e) {
                // se reporta mas abajo como version ilegible
            }
        }
        return revisar(p, ml);
    }

    // -------------------------------------------------------------- Partes

    private static void revisarJava(MinecraftLauncher.Profile p, MinecraftLauncher ml, List<Problema> out) {
        int requerido = (ml != null) ? ml.requiredJavaMajor() : -1;

        if (p.javaBin == null) {
            out.add(bloqueo("No se encontro Java " + (requerido > 0 ? requerido : 17),
                    "El juego necesita su propia version de Java, distinta de la del sistema.",
                    "Instala Java " + (requerido > 0 ? requerido : 17)
                            + ", o indica en Ajustes la ruta de un java " + (requerido > 0 ? requerido : 17) + "."));
            return;
        }
        if (!Files.isExecutable(p.javaBin)) {
            out.add(bloqueo("El Java indicado no existe o no se puede ejecutar",
                    p.javaBin.toString(),
                    "Corrige la ruta de Java en Ajustes."));
            return;
        }
        int real = Rutas.versionMayorDe(p.javaBin);
        if (requerido > 0 && real > 0 && real != requerido) {
            out.add(bloqueo("El juego necesita Java " + requerido + " y el elegido es Java " + real,
                    p.javaBin.toString(),
                    "Con otra version el arranque falla con un error ilegible. "
                            + "Elige un Java " + requerido + " en Ajustes."));
        } else if (real < 0) {
            out.add(aviso("No se pudo comprobar la version de Java",
                    p.javaBin.toString(),
                    "Si el juego no arranca, revisa que sea un Java "
                            + (requerido > 0 ? requerido : 17) + "."));
        }
    }

    private static void revisarAssets(MinecraftLauncher.Profile p, MinecraftLauncher ml, List<Problema> out) {
        if (p.assetsRoot == null) {
            out.add(bloqueo("No se encontro la carpeta de recursos (assets)",
                    "Sin ella el juego arranca sin sonidos ni idiomas, o no arranca.",
                    "Indica la carpeta de assets en Ajustes."));
            return;
        }
        if (!Files.isDirectory(p.assetsRoot)) {
            out.add(bloqueo("La carpeta de recursos (assets) no existe",
                    p.assetsRoot.toString(),
                    "Corrige la ruta en Ajustes."));
            return;
        }
        String id = null;
        if (ml != null) {
            var idx = Json.obj(ml.versionJson(), "assetIndex");
            if (idx != null) id = Json.str(idx, "id");
        }
        if (id != null && !Files.isRegularFile(p.assetsRoot.resolve("indexes").resolve(id + ".json"))) {
            out.add(bloqueo("Falta el indice de recursos '" + id + "'",
                    p.assetsRoot.resolve("indexes").resolve(id + ".json").toString(),
                    "Copia la carpeta 'assets' completa desde una instalacion que funcione."));
            return;
        }
        Path objetos = p.assetsRoot.resolve("objects");
        if (!Files.isDirectory(objetos)) {
            out.add(bloqueo("La carpeta de recursos no tiene 'objects'",
                    objetos.toString(),
                    "La copia de assets esta incompleta."));
            return;
        }
        long subcarpetas = contarSubcarpetas(objetos, 1000);
        if (subcarpetas < 100) {
            out.add(aviso("La carpeta de recursos parece incompleta",
                    "Solo tiene " + subcarpetas + " subcarpetas en 'objects' (lo normal son mas de 1000).",
                    "Puede que la copia se interrumpiera; el juego arrancaria sin sonidos."));
        }
    }

    /** Cuenta subcarpetas parando en el limite: 'objects' tiene miles de entradas. */
    private static long contarSubcarpetas(Path dir, int limite) {
        try (var s = Files.list(dir)) {
            return s.filter(Files::isDirectory).limit(limite).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void exigirEnClasspath(List<String> cp, String fragmento, List<Problema> out) {
        boolean hay = cp.stream().anyMatch(s -> s.contains(fragmento));
        if (!hay) {
            out.add(bloqueo("Falta '" + fragmento + "' en el classpath",
                    "Sin ese componente la maquina virtual no puede preparar los modulos de Forge.",
                    "La carpeta 'libraries' esta incompleta."));
        }
    }

    private static String resumir(List<String> items) {
        int max = 8;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, items.size()); i++) {
            sb.append(i > 0 ? ", " : "").append(items.get(i));
        }
        if (items.size() > max) sb.append(" y ").append(items.size() - max).append(" mas");
        return sb.toString();
    }

    private static Problema bloqueo(String titulo, String detalle, String accion) {
        return new Problema(Nivel.BLOQUEANTE, titulo, detalle, accion);
    }

    private static Problema aviso(String titulo, String detalle, String accion) {
        return new Problema(Nivel.AVISO, titulo, detalle, accion);
    }
}
