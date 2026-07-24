#!/usr/bin/env bash
set -euo pipefail

package_dir=$(cd "$(dirname "$0")" && pwd)
game_dir="$HOME/.minecraft-rpg-dos-almas"
tl_dir="$HOME/.tlauncher"
properties="$tl_dir/tlauncher-2.0.properties"

mkdir -p "$game_dir" "$tl_dir"
[[ ! -f "$properties" ]] || cp -a "$properties" "$properties.backup-rpg-dos-almas"
cp -a "$package_dir/pack/." "$game_dir/"
install -Dm755 "$package_dir/LANZAR-LINUX-OPTIMIZADO.sh" \
    "$HOME/.local/bin/rpg-dos-almas-optimizado"
install -Dm755 "$package_dir/TLauncher-RPG.desktop" \
    "$HOME/.local/share/applications/TLauncher-RPG.desktop"
if [[ -d "$HOME/Escritorio" ]]; then
    install -Dm755 "$package_dir/TLauncher-RPG.desktop" \
        "$HOME/Escritorio/TLauncher-RPG.desktop"
fi

ram_kb=$(awk '/MemTotal/ {print $2}' /proc/meminfo)
if (( ram_kb >= 24000000 )); then memory=8192
elif (( ram_kb >= 16000000 )); then memory=6144
elif (( ram_kb >= 12000000 )); then memory=5120
else memory=4096
fi

touch "$properties"
set_property() {
    local key=$1 value=$2
    if grep -q "^${key}=" "$properties"; then
        sed -i "s#^${key}=.*#${key}=${value}#" "$properties"
    else
        printf '%s=%s\n' "$key" "$value" >> "$properties"
    fi
}
set_property minecraft.gamedir "$game_dir"
set_property minecraft.memory.ram3 "$memory"
set_property login.version.game "Forge 1.20.1"
set_property minecraft.versions.modified true

cat > "$game_dir/SERVIDOR-RPG-DOS-ALMAS.txt" <<'EOF'
Servidor: 192.168.78.210:25565
Version: Forge 1.20.1
EOF

printf '\nInstalacion completada. Usa el acceso Minecraft RPG Dos Almas.\n'
printf 'Durante el juego se activa Quiet; al cerrar se restaura el perfil anterior.\n'
printf 'Servidor: 192.168.78.210:25565\n'
