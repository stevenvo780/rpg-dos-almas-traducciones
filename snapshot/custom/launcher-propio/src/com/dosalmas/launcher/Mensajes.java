package com.dosalmas.launcher;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;

/**
 * Traduce las excepciones tecnicas a frases que el jugador pueda entender.
 *
 * El caso que motiva esta clase: el mensaje de NoSuchFileException es solo la
 * ruta, sin ninguna palabra. Mostrado tal cual en un dialogo, el jugador ve
 * una linea suelta que no explica nada ni dice que hacer.
 */
public final class Mensajes {

    private Mensajes() {}

    /** Frase con causa y accion para una excepcion cualquiera. */
    public static String explicar(Throwable t) {
        if (t == null) return "Error desconocido.";

        if (t instanceof InstalacionIncompletaException) {
            return t.getMessage();
        }
        if (t instanceof NoSuchFileException) {
            return "No se encontro este archivo o carpeta:\n\n  "
                    + ((NoSuchFileException) t).getFile()
                    + "\n\nComprueba en Ajustes que las rutas del juego son correctas.";
        }
        if (t instanceof AccessDeniedException) {
            return "No hay permiso para acceder a:\n\n  "
                    + ((AccessDeniedException) t).getFile()
                    + "\n\nRevisa los permisos de esa carpeta, o instala el juego en una carpeta tuya.";
        }
        String msg = (t.getMessage() != null) ? t.getMessage() : t.toString();
        if (t instanceof IOException && msg.contains("error=2")) {
            return "No se pudo ejecutar el programa de Java indicado.\n\n" + msg
                    + "\n\nCorrige la ruta de Java en Ajustes.";
        }
        if (t instanceof IOException && msg.contains("error=13")) {
            return "El archivo de Java indicado no tiene permiso de ejecucion.\n\n" + msg;
        }
        if (t instanceof OutOfMemoryError) {
            return "El launcher se quedo sin memoria. Baja la memoria maxima en Ajustes.";
        }
        return msg;
    }

    /** Explicacion de un codigo de salida anomalo del juego. */
    public static String explicarSalida(int codigo, long segundos) {
        StringBuilder sb = new StringBuilder();
        if (codigo == 0) return "El juego se cerro con normalidad.";

        switch (codigo) {
            case 137:
            case 143:
                sb.append("El sistema cerro el juego por falta de memoria.\n")
                  .append("Baja la memoria maxima en Ajustes y vuelve a intentarlo.");
                break;
            case 1:
                sb.append("El juego termino con un error (codigo 1).");
                break;
            default:
                sb.append("El juego termino con el codigo ").append(codigo).append('.');
        }
        if (segundos >= 0 && segundos < 45) {
            sb.append("\n\nSolo duro ").append(segundos)
              .append(" segundos, asi que no llego a abrirse: el fallo ocurrio durante el arranque. ")
              .append("Revisa la consola del modo avanzado o el registro guardado.");
        }
        return sb.toString();
    }
}
