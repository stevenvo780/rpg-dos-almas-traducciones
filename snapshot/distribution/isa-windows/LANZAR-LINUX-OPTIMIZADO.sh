#!/usr/bin/env bash
set -euo pipefail

tlauncher_jar="${TLAUNCHER_JAR:-$HOME/Descargas/TLauncher.jar}"
previous_profile=

if [[ ! -f "$tlauncher_jar" ]]; then
    printf 'No se encontro TLauncher en %s\n' "$tlauncher_jar" >&2
    exit 1
fi

restore_profile() {
    if [[ -n "$previous_profile" ]] && command -v asusctl >/dev/null 2>&1; then
        asusctl profile set "$previous_profile" >/dev/null 2>&1 || true
    fi
}
trap restore_profile EXIT HUP INT TERM

if command -v asusctl >/dev/null 2>&1; then
    previous_profile=$(asusctl profile get 2>/dev/null |
        sed -n 's/^Active profile: //p' | head -n 1)
    asusctl profile set Quiet >/dev/null 2>&1 || true
fi

env LANG=es_CO.utf8 LC_ALL=es_CO.utf8 ALSOFT_DRIVERS=pulse \
    java -Dfile.encoding=UTF-8 -jar "$tlauncher_jar" || true

# El starter de TLauncher puede reemplazar el proceso inicial. Mantener Quiet
# hasta que hayan terminado tanto el launcher como el cliente de Minecraft.
while ps -u "$(id -u)" -o args= |
    grep -Eq '[o]rg\.tlauncher|Minecraft-RPG-Dos-Almas/runtime/.*/bin/java'; do
    sleep 5
done
