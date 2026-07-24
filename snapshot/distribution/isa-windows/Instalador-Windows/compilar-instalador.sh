#!/usr/bin/env bash
set -euo pipefail

distribution_dir=$(cd "$(dirname "$0")/.." && pwd)
source_dir="$distribution_dir/Instalador-Windows"
output_dir="${OUTPUT_DIR:-$HOME/Descargas}"
output_file="$output_dir/RPG-Dos-Almas-Instalador-Windows.exe"
makensis_bin="${MAKENSIS_BIN:-$HOME/Minecraft/Herramientas/NSIS-3.12/runtime/bin/makensis}"

if [[ ! -x "$makensis_bin" ]]; then
    printf 'No se encontro makensis en %s\n' "$makensis_bin" >&2
    exit 1
fi

mkdir -p "$output_dir"
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-$(stat -c %Y "$distribution_dir/pack/options.txt")}"

"$makensis_bin" -V3 \
    -DPAYLOAD_DIR="$distribution_dir/pack" \
    -DHELPER_DIR="$source_dir" \
    -DOUTPUT_FILE="$output_file" \
    "$source_dir/RPG-Dos-Almas.nsi"

(
    cd "$output_dir"
    sha256sum "$(basename "$output_file")" > "$(basename "$output_file").sha256"
    sha256sum --quiet -c "$(basename "$output_file").sha256"
)

printf 'Instalador creado: %s\n' "$output_file"
