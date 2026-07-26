package com.dosalmas.launcher;

import java.io.IOException;
import java.util.List;

/**
 * Se lanza cuando la instalacion no esta en condiciones de arrancar. Lleva
 * consigo la lista de problemas para que quien la reciba pueda explicarselos
 * al jugador en vez de mostrar una traza.
 */
public class InstalacionIncompletaException extends IOException {

    private final transient List<Verificador.Problema> problemas;

    public InstalacionIncompletaException(List<Verificador.Problema> problemas) {
        super(resumen(problemas));
        this.problemas = problemas;
    }

    public List<Verificador.Problema> problemas() { return problemas; }

    private static String resumen(List<Verificador.Problema> problemas) {
        StringBuilder sb = new StringBuilder("La instalacion no esta completa:\n");
        for (Verificador.Problema p : problemas) {
            if (p.bloquea()) sb.append("\n- ").append(p.titulo);
        }
        return sb.toString();
    }
}
