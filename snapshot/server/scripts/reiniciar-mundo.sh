#!/usr/bin/env bash
#
# Genera un mundo nuevo desde cero conservando permisos y configuracion.
#
# Lo que se BORRA: mapa, construcciones, inventarios, niveles, progreso de
# misiones, parcelas y estadisticas. Lo que se CONSERVA: operadores, lista
# blanca, baneos, server.properties, mods, y las configuraciones de mod por
# mundo (sembradas en defaultconfigs).
#
# El mundo viejo no se borra: se RENOMBRA. Sigue en disco hasta que alguien
# decida quitarlo a mano, ademas del respaldo comprimido.
#
# Uso:
#   ./reiniciar-mundo.sh --ensayo     muestra lo que haria, no toca nada
#   ./reiniciar-mundo.sh --ejecutar   lo hace de verdad
#
set -euo pipefail

RAIZ="/home/stev/Minecraft/Servidor/RPG-Dos-Almas"
MUNDO="$RAIZ/Dos-Almas"
PLANTILLA="$RAIZ/datapacks-plantilla"
SERVICIO="rpg-dos-almas-server.service"
CONTENEDOR="rpg-dos-almas"
SELLO="$(date +%Y-%m-%d_%H%M%S)"
RETIRADO="$RAIZ/Dos-Almas-retirado-$SELLO"

ensayo=true
case "${1:-}" in
    --ejecutar) ensayo=false ;;
    --ensayo|"") ensayo=true ;;
    *) echo "uso: $0 [--ensayo|--ejecutar]" >&2; exit 2 ;;
esac

paso() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
info() { printf '   %s\n' "$*"; }
hacer() {
    if $ensayo; then printf '   [ensayo] %s\n' "$*"
    else printf '   %s\n' "$*"; eval "$@"; fi
}
abortar() { printf '\n\033[31mABORTADO: %s\033[0m\n' "$*" >&2; exit 1; }

$ensayo && printf '\033[33mMODO ENSAYO: no se toca nada.\033[0m\n'

# ---------------------------------------------------------------- 1. Requisitos
paso "1/8  Comprobaciones previas"

[ -d "$MUNDO" ] || abortar "no existe el mundo en $MUNDO"
[ -d "$PLANTILLA" ] || abortar "no existe la plantilla de datapacks en $PLANTILLA"

n_dp=$(find "$PLANTILLA" -type f | wc -l)
[ "$n_dp" -ge 180 ] || abortar "la plantilla de datapacks solo tiene $n_dp archivos, se esperan ~189"
info "plantilla de datapacks: $n_dp archivos en $(ls -1 "$PLANTILLA" | wc -l) paquetes"

libre=$(df --output=avail -k "$RAIZ" | tail -1)
ocupa=$(du -sk "$MUNDO" | cut -f1)
[ "$libre" -gt $((ocupa * 2)) ] || abortar "poco espacio: hacen falta $((ocupa*2/1024))MB y hay $((libre/1024))MB"
info "espacio libre: $((libre/1024/1024))GB, el mundo ocupa $((ocupa/1024))MB"

# Nadie dentro. Se comprueba contra el servidor, no contra suposiciones.
if docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR"; then
    conectados=$(docker exec "$CONTENEDOR" rcon-cli list 2>/dev/null \
                 | grep -oE 'There are [0-9]+' | grep -oE '[0-9]+' || echo 0)
    info "jugadores conectados: $conectados"
    [ "$conectados" = "0" ] || abortar "hay $conectados jugador(es) dentro; avisa y espera a que salgan"
else
    info "el contenedor no esta corriendo"
fi

# ------------------------------------------------------------------ 2. Respaldo
paso "2/8  Respaldo completo y verificado del mundo actual"
info "sin esto no se sigue: es la unica vuelta atras"

# Si acaba de hacerse uno, no se repite: el script de respaldo para y arranca
# el servidor para tomar la copia consistente, y hacerlo dos veces no aporta.
ya=$(find /home/stev/Minecraft/Respaldos/Mundos -name 'Dos-Almas-*.tar.zst' \
     -newermt '-45 minutes' 2>/dev/null | head -1)
if [ -n "$ya" ]; then
    info "ya hay uno reciente: $(basename "$ya") ($(du -h "$ya" | cut -f1)), no se repite"
else
    hacer "RPG_BACKUP_FORCE=true '$RAIZ/respaldar-mundo.sh'"
    if ! $ensayo; then
        ya=$(find /home/stev/Minecraft/Respaldos/Mundos -name 'Dos-Almas-*.tar.zst' \
             -newermt '-10 minutes' | head -1)
        [ -n "$ya" ] || abortar "el respaldo no genero ningun archivo nuevo"
        info "respaldo: $(basename "$ya") ($(du -h "$ya" | cut -f1))"
    fi
fi

# -------------------------------------------------------------------- 3. Parada
paso "3/8  Parar el servidor"
# El servidor NO lo gestiona systemd: corre como contenedor Docker y su ciclo
# de vida va por 'docker stop/start', igual que hace respaldar-mundo.sh. La
# unidad de systemd es una via antigua que hoy esta inactiva; usarla dejaba el
# contenedor vivo y el guion abortaba en este punto.
hacer "docker stop '$CONTENEDOR'"
if ! $ensayo; then
    for i in $(seq 1 60); do
        docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR" || break
        sleep 2
    done
    docker ps --format '{{.Names}}' | grep -qx "$CONTENEDOR" \
        && abortar "el contenedor sigue vivo tras 120s"
    info "servidor parado"
fi

# ------------------------------------------------------------------- 4. Retirar
paso "4/8  Retirar el mundo viejo (se renombra, NO se borra)"
hacer "mv '$MUNDO' '$RETIRADO'"
info "queda en: $RETIRADO"

# -------------------------------------------------------------------- 5. Semilla
paso "5/8  Semilla aleatoria"
semilla=$(grep -E '^level-seed=' "$RAIZ/server.properties" | cut -d= -f2-)
if [ -n "$semilla" ]; then
    hacer "sed -i 's/^level-seed=.*/level-seed=/' '$RAIZ/server.properties'"
    info "se vacia level-seed (tenia: $semilla)"
else
    info "level-seed ya esta vacio: el servidor elegira una al azar"
fi

# ------------------------------------------------------------------ 6. Datapacks
paso "6/8  Colocar los datapacks ANTES de generar"
info "van dentro del mundo; si no estan al crearlo, nacen desactivados"
hacer "mkdir -p '$MUNDO/datapacks'"
hacer "cp -a '$PLANTILLA/.' '$MUNDO/datapacks/'"

# ---------------------------------------------------------------- 7. Cache mapas
paso "7/8  Limpiar la cache de mapas del servidor"
info "guarda el mapa del mundo viejo indexado por dimension"
if [ -d "$RAIZ/server_map_cache" ]; then
    hacer "mv '$RAIZ/server_map_cache' '$RAIZ/server_map_cache-retirado-$SELLO'"
fi

# ------------------------------------------------------------------ 8. Arrancar
paso "8/8  Arrancar y generar el mundo nuevo"
hacer "docker start '$CONTENEDOR'"

if ! $ensayo; then
    info "esperando a que el servidor acepte conexiones..."
    for i in $(seq 1 90); do
        if docker exec "$CONTENEDOR" rcon-cli list >/dev/null 2>&1; then
            info "servidor en marcha tras ${i}0s aprox"
            break
        fi
        sleep 5
    done
    docker exec "$CONTENEDOR" rcon-cli list >/dev/null 2>&1 \
        || abortar "el servidor no respondio; mira: docker logs --tail 80 $CONTENEDOR"

    printf '\n'
    paso "Comprobaciones finales"
    info "datapacks activos:"
    docker exec "$CONTENEDOR" rcon-cli "datapack list" 2>/dev/null | sed 's/^/     /'
    info "operadores conservados: $(python3 -c "
import json; print(', '.join(o['name'] for o in json.load(open('$RAIZ/ops.json'))))" 2>/dev/null)"
    info "tamaño del mundo nuevo: $(du -sh "$MUNDO" 2>/dev/null | cut -f1)"
fi

printf '\n\033[32mListo.\033[0m\n'
cat <<FIN

Falta un paso en CADA cliente, o veran el mapa y el horizonte del mundo viejo
pintados encima del nuevo:

    ./limpiar-clientes-tras-reinicio.sh

Xaero indexa por direccion del servidor, asi que su mapa colisiona seguro.
Distant Horizons guarda su base de datos de terreno lejano por mundo.

El mundo viejo sigue en $RETIRADO
Cuando estes seguro de que no lo necesitas:  rm -rf '$RETIRADO'
FIN
