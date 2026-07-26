#!/usr/bin/env bash
# Lanzador de RPG Dos Almas.
#
# Busca un binario "java" (version 17 o superior) en este orden:
#   1) $JAVA_HOME/bin/java
#   2) "java" del PATH
#   3) un JRE incluido junto a este script, en ./jre/bin/java (si existe)
#
# Cualquier argumento recibido (--check, --print, --cp, --launch, o ninguno
# para abrir la interfaz grafica) se reenvia tal cual al jar del launcher.
set -euo pipefail

# ------------------------------------------------------- Ubicar este script

resolver_dir() {
    # Sigue symlinks (por ejemplo /usr/bin/dosalmas-launcher -> /usr/lib/...)
    # y devuelve la carpeta real donde vive el script final.
    local origen="$1" destino
    while [[ -L "$origen" ]]; do
        destino="$(readlink "$origen")"
        if [[ "$destino" != /* ]]; then
            destino="$(dirname "$origen")/$destino"
        fi
        origen="$destino"
    done
    (cd "$(dirname "$origen")" && pwd)
}

# Dentro de un AppImage montado, el runtime exporta APPDIR con la raiz real
# del AppDir; en ese caso el jar vive directamente ahi.
if [[ -n "${APPDIR:-}" ]]; then
    DIR="$APPDIR"
else
    DIR="$(resolver_dir "$0")"
fi

JAR="$DIR/DosAlmasLauncher.jar"

if [[ ! -f "$JAR" ]]; then
    echo "Error: no se encontro '$JAR'." >&2
    echo "Este script debe permanecer en la misma carpeta que DosAlmasLauncher.jar." >&2
    exit 1
fi

# --------------------------------------------------------- Resolucion de Java

# Imprime el numero de version mayor (17, 21, 8...) de un binario java por
# stdout, o falla (return != 0) si no se puede determinar.
version_mayor() {
    local bin="$1" primera_linea ver mayor
    primera_linea="$("$bin" -version 2>&1 | head -n1)" || return 1
    ver="$(printf '%s\n' "$primera_linea" | grep -oE '"[^"]+"' | head -n1 | tr -d '"')"
    [[ -z "$ver" ]] && return 1
    if [[ "$ver" == 1.* ]]; then
        # Formato antiguo (Java 8 y anteriores): "1.8.0_312" -> mayor = 8
        ver="${ver#1.}"
    fi
    mayor="${ver%%.*}"
    mayor="${mayor%%_*}"
    [[ "$mayor" =~ ^[0-9]+$ ]] || return 1
    echo "$mayor"
}

# Devuelve 0 y el binario si es ejecutable y su version mayor es >= 17.
probar_candidato() {
    local bin="$1" mayor
    [[ -x "$bin" ]] || return 1
    mayor="$(version_mayor "$bin")" || return 1
    [[ "$mayor" -ge 17 ]] || return 1
    return 0
}

JAVA_BIN=""
PROBADOS=()

if [[ -n "${JAVA_HOME:-}" ]]; then
    candidato="$JAVA_HOME/bin/java"
    PROBADOS+=("\$JAVA_HOME/bin/java -> $candidato")
    if probar_candidato "$candidato"; then JAVA_BIN="$candidato"; fi
fi

if [[ -z "$JAVA_BIN" ]] && command -v java >/dev/null 2>&1; then
    candidato="$(command -v java)"
    PROBADOS+=("java del PATH -> $candidato")
    if probar_candidato "$candidato"; then JAVA_BIN="$candidato"; fi
fi

if [[ -z "$JAVA_BIN" && -x "$DIR/jre/bin/java" ]]; then
    candidato="$DIR/jre/bin/java"
    PROBADOS+=("JRE incluido -> $candidato")
    if probar_candidato "$candidato"; then JAVA_BIN="$candidato"; fi
fi

if [[ -z "$JAVA_BIN" ]]; then
    {
        echo "=========================================================="
        echo " No se encontro un Java 17 (o superior) para ejecutar el"
        echo " launcher de RPG Dos Almas."
        echo "=========================================================="
        if [[ "${#PROBADOS[@]}" -gt 0 ]]; then
            echo "Se probaron estas rutas sin exito:"
            for p in "${PROBADOS[@]}"; do echo "  - $p"; done
        else
            echo "No se encontro ningun binario 'java' (ni JAVA_HOME, ni PATH,"
            echo "ni un JRE incluido junto al launcher)."
        fi
        echo
        echo "Instala un Java 17 o mas reciente y vuelve a intentarlo:"
        echo "  Arch / CachyOS : sudo pacman -S jdk17-openjdk"
        echo "  Ubuntu / Debian: sudo apt install openjdk-17-jre"
        echo "  Fedora         : sudo dnf install java-17-openjdk"
    } >&2
    exit 1
fi

# Gestores de ventanas en mosaico (Hyprland, sway, i3, awesome...) no son
# "reparenting", y AWT no los reconoce: la ventana sale completamente en negro.
# Esta variable se lo indica. Es inofensiva en el resto de escritorios, pero
# solo se pone si el usuario no la ha fijado ya.
if [[ -z "${_JAVA_AWT_WM_NONREPARENTING:-}" ]]; then
    escritorio="${XDG_CURRENT_DESKTOP:-}${XDG_SESSION_DESKTOP:-}"
    if [[ "${escritorio,,}" =~ (hyprland|sway|i3|awesome|bspwm|river|dwm|qtile|xmonad) ]]; then
        export _JAVA_AWT_WM_NONREPARENTING=1
    fi
fi

echo "Usando Java: $JAVA_BIN" >&2
exec "$JAVA_BIN" -jar "$JAR" "$@"
