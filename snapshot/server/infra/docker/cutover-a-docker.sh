#!/usr/bin/env bash
# CUTOVER: migra el servidor nativo (systemd) al contenedor Docker (itzg).
# Requiere 0 jugadores. Auto-rollback si el contenedor no queda sirviendo.
# Downtime esperado: ~20-40s.
set -uo pipefail

S=/home/stev/Minecraft/Servidor/RPG-Dos-Almas
INFRA="$S/infra/docker"
UNIT=rpg-dos-almas-docker.service
NATIVE=rpg-dos-almas-server.service
USERUNITS="$HOME/.config/systemd/user"
RCON=/home/stev/.local/bin/dosalmas-rcon
port=25565

log(){ printf '\n>>> %s\n' "$*"; }
fail(){ printf '\n!!! FALLO: %s\n' "$*" >&2; }

rollback() {
  printf '\n### ROLLBACK: volviendo al servicio nativo\n'
  systemctl --user stop "$UNIT" 2>/dev/null || true
  (cd "$INFRA" && docker compose down 2>/dev/null) || true
  docker rm -f rpg-dos-almas 2>/dev/null || true
  systemctl --user enable "$NATIVE" 2>/dev/null || true
  systemctl --user start "$NATIVE"
  for i in $(seq 1 45); do
    if systemctl --user is-active --quiet "$NATIVE" && ss -Hltn "( sport = :$port )" | grep -q .; then
      printf '### ROLLBACK OK: servicio nativo sirviendo 25565\n'; return 0
    fi
    sleep 2
  done
  printf '### ROLLBACK: el servicio nativo no confirmo puerto; revisar manualmente\n'
}

# --- 0) precondiciones ---
log "0) Verificando 0 jugadores conectados"
pl=$("$RCON" cmd list 2>/dev/null | grep -oE 'are [0-9]+' | grep -oE '[0-9]+' || echo "?")
if [ "$pl" != "0" ]; then fail "Hay jugadores conectados ($pl) o RCON no responde. Aborto."; exit 1; fi
echo "   0 jugadores OK"

log "0b) Guardando el mundo (save-all flush) antes de detener"
"$RCON" cmd save-all flush >/dev/null 2>&1 || true
sleep 3

# --- 1) detener y deshabilitar el servicio nativo ---
log "1) Deteniendo y deshabilitando $NATIVE"
systemctl --user stop "$NATIVE"
systemctl --user disable "$NATIVE" 2>/dev/null || true

log "1b) Esperando a que el proceso java libere el mundo (session.lock)"
for i in $(seq 1 30); do
  if ! pgrep -f "forge/1.20.1-47.4.20" >/dev/null 2>&1; then echo "   java terminado"; break; fi
  sleep 1
done

# --- 2) instalar la unidad Docker ---
log "2) Instalando unidad $UNIT en $USERUNITS"
mkdir -p "$USERUNITS"
cp "$INFRA/$UNIT" "$USERUNITS/$UNIT"
systemctl --user daemon-reload

# --- 3) arrancar el contenedor via la unidad (inyecta RCON_PASSWORD del server.properties) ---
log "3) Arrancando el contenedor (docker compose up via unidad)"
if ! systemctl --user start "$UNIT"; then
  fail "La unidad no arranco. Rollback."
  rollback; exit 1
fi

# --- 4) esperar Done + puerto en el host ---
log "4) Esperando a que el contenedor sirva 25565 en el host (hasta 300s)"
ok=false
for i in $(seq 1 60); do
  sleep 5
  if docker logs rpg-dos-almas 2>&1 | grep -q 'Done ([0-9.]*s)!' && ss -Hltn "( sport = :$port )" | grep -q .; then ok=true; break; fi
  if [ "$(docker inspect -f '{{.State.Running}}' rpg-dos-almas 2>/dev/null)" != true ]; then fail "Contenedor no esta running"; break; fi
done
if [ "$ok" != true ]; then fail "El contenedor no llego a Done/puerto en 300s. Rollback."; rollback; exit 1; fi
echo "   contenedor sirviendo 25565 OK"

# --- 5) verificar RCON (consola admin) ---
log "5) Verificando RCON (dosalmas-rcon)"
sleep 5
if ! "$RCON" cmd list >/dev/null 2>&1; then fail "RCON no responde tras cutover. Rollback."; rollback; exit 1; fi
echo "   RCON OK"

# --- 6) swap del script de backup a la version docker ---
log "6) Cambiando respaldar-mundo.sh a la version Docker"
if [ ! -f "$S/respaldar-mundo.sh.systemd-bak" ]; then cp -a "$S/respaldar-mundo.sh" "$S/respaldar-mundo.sh.systemd-bak"; fi
cp -a "$S/respaldar-mundo.sh.docker" "$S/respaldar-mundo.sh"
echo "   backup script -> docker (original en respaldar-mundo.sh.systemd-bak)"

log "CUTOVER COMPLETO. Servidor bajo Docker (contenedor rpg-dos-almas)."
docker ps --filter name=rpg-dos-almas --format '   {{.Names}}  {{.Status}}  {{.Ports}}'
exit 0
