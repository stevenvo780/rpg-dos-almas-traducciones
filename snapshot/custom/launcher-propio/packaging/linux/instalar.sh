#!/usr/bin/env bash
# Instala un acceso directo de RPG Dos Almas para el usuario actual
# (sin privilegios de administrador): copia el .desktop a
# ~/.local/share/applications con las rutas correctas apuntando a esta
# misma carpeta, donde sea que la hayas extraido.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/DosAlmasLauncher.jar"
SCRIPT="$DIR/dosalmas-launcher.sh"
ICONO="$DIR/dosalmas-launcher.png"
DESKTOP_ORIGEN="$DIR/dosalmas-launcher.desktop"

for f in "$JAR" "$SCRIPT" "$ICONO" "$DESKTOP_ORIGEN"; do
    if [[ ! -f "$f" ]]; then
        echo "Error: falta '$f'. Ejecuta este script desde la carpeta" >&2
        echo "donde extrajiste el tarball del launcher, sin mover archivos sueltos." >&2
        exit 1
    fi
done

chmod +x "$SCRIPT"

DESTINO_APPS="$HOME/.local/share/applications"
mkdir -p "$DESTINO_APPS"
DESTINO_DESKTOP="$DESTINO_APPS/dosalmas-launcher.desktop"

sed -e "s|^Exec=.*|Exec=\"$SCRIPT\" %U|" \
    -e "s|^Icon=.*|Icon=$ICONO|" \
    "$DESKTOP_ORIGEN" > "$DESTINO_DESKTOP"

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$DESTINO_APPS" >/dev/null 2>&1 || true
fi

echo "Listo. Acceso directo instalado en:"
echo "  $DESTINO_DESKTOP"
echo "Deberia aparecer en el menu de aplicaciones (categoria Juegos)."
echo "Si no aparece de inmediato, cierra sesion y vuelve a entrar, o"
echo "reinicia el menu de tu escritorio."
