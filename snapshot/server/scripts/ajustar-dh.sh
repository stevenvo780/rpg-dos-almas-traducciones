#!/usr/bin/env bash
set -euo pipefail

config="/home/stev/Minecraft/Servidor/RPG-Dos-Almas/config/DistantHorizons.toml"
state="/home/stev/Minecraft/Servidor/RPG-Dos-Almas/.dh-mode"
players=$(ss -Htn state established '( sport = :25565 )' | wc -l)

if (( players > 0 )); then
    mode="jugando"
    threads=8
    ratio="0.6"
    requests=6
    generation=false
else
    mode="vacio"
    threads=16
    ratio="0.85"
    requests=12
    # La pregeneracion se habilita solo manualmente durante mantenimiento.
    generation=false
fi

[[ -f "$state" ]] && [[ "$(<"$state")" == "$mode" ]] && exit 0

sed -i -E \
    -e "s/^([[:space:]]*)numberOfThreads = .*/\\1numberOfThreads = ${threads}/" \
    -e "s/^([[:space:]]*)threadRunTimeRatio = .*/\\1threadRunTimeRatio = \"${ratio}\"/" \
    -e "s/^([[:space:]]*)generationRequestRateLimit = .*/\\1generationRequestRateLimit = ${requests}/" \
    -e "s/^([[:space:]]*)enableDistantGeneration = .*/\\1enableDistantGeneration = ${generation}/" \
    "$config"
printf '%s' "$mode" > "$state"
