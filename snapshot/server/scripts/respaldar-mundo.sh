#!/usr/bin/env bash
set -euo pipefail
umask 077

server_dir="/home/stev/Minecraft/Servidor/RPG-Dos-Almas"
server_service="rpg-dos-almas-server.service"
world="Dos-Almas"
server_port=25565
force_backup="${RPG_BACKUP_FORCE:-false}"
backup_dir="/home/stev/Minecraft/Respaldos/Mundos"
lock_file="$backup_dir/.backup.lock"
nas_host="nas@192.168.78.144"
nas_dir="/mnt/pool/backups/kratos/minecraft/RPG-Dos-Almas"
nas_key="/home/stev/.ssh/pc-stev-backup"
known_hosts="/home/stev/.ssh/known_hosts"
ssh_options=(
    -i "$nas_key"
    -o BatchMode=yes
    -o IdentitiesOnly=yes
    -o ConnectTimeout=15
    -o UserKnownHostsFile="$known_hosts"
    -o StrictHostKeyChecking=yes
)
rsync_ssh="ssh -i $nas_key -o BatchMode=yes -o IdentitiesOnly=yes -o ConnectTimeout=15 -o UserKnownHostsFile=$known_hosts -o StrictHostKeyChecking=yes"

log() {
    printf '%s %s\n' "$(date -Is)" "$*"
}

latest_backup() {
    local candidate candidate_epoch latest="" latest_epoch=0
    local -a candidates=()

    shopt -s nullglob
    candidates=("$backup_dir"/Dos-Almas-*.tar.zst)
    shopt -u nullglob

    for candidate in "${candidates[@]}"; do
        [[ -f "$candidate" ]] || continue
        if ! candidate_epoch=$(stat -c %Y "$candidate"); then
            log "ERROR: no se pudo consultar la fecha de $candidate" >&2
            return 1
        fi
        if (( candidate_epoch > latest_epoch )); then
            latest="$candidate"
            latest_epoch=$candidate_epoch
        fi
    done
    printf '%s\n' "$latest"
}

connected_players() {
    local connections

    if ! connections=$(ss -Htn state established "( sport = :$server_port )"); then
        log "ERROR: no se pudieron consultar las conexiones al servidor" >&2
        return 1
    fi
    if [[ -z "$connections" ]]; then
        printf '0\n'
    else
        awk 'END { print NR }' <<<"$connections"
    fi
}

sync_to_nas() {
    local archive base sidecar local_hash remote_hash remote_partial_hash
    local -a archives=()

    shopt -s nullglob
    archives=("$backup_dir"/Dos-Almas-*.tar.zst)
    shopt -u nullglob

    if (( ${#archives[@]} == 0 )); then
        log "No hay respaldos locales para copiar al NAS"
        return 0
    fi
    if [[ ! -r "$nas_key" || ! -r "$known_hosts" ]]; then
        log "ERROR: faltan la llave SSH o known_hosts para el NAS"
        return 1
    fi
    if ! ssh "${ssh_options[@]}" "$nas_host" \
        "mkdir -p '$nas_dir/.incoming' && chmod 700 '$nas_dir' '$nas_dir/.incoming'"; then
        log "ERROR: no se pudo preparar el destino del NAS"
        return 1
    fi

    log "Verificando y copiando respaldos pendientes al NAS"
    for archive in "${archives[@]}"; do
        base=$(basename "$archive")
        sidecar="$archive.sha256"
        # Aceptar tanto las copias periódicas como snapshots manuales etiquetados,
        # por ejemplo Dos-Almas-pre-mods-2026-07-22_184127.tar.zst.
        if [[ ! "$base" =~ ^Dos-Almas-([A-Za-z0-9._-]+-)?[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{4}([0-9]{2})?\.tar\.zst$ ]]; then
            log "ERROR: nombre de respaldo inesperado: $base"
            return 1
        fi
        if [[ ! -f "$sidecar" ]]; then
            log "ERROR: falta la suma SHA256 local de $base"
            return 1
        fi
        if ! (cd "$backup_dir" && sha256sum -c "$base.sha256" >/dev/null); then
            log "ERROR: la copia local no supera SHA256: $base"
            return 1
        fi
        if ! local_hash=$(sha256sum "$archive"); then
            log "ERROR: no se pudo calcular SHA256 de $base"
            return 1
        fi
        local_hash=${local_hash%% *}

        # Normalizar el sidecar para que sea portable y publicarlo junto al archivo.
        if ! printf '%s  %s\n' "$local_hash" "$base" >"$sidecar.tmp"; then
            log "ERROR: no se pudo preparar el sidecar de $base"
            return 1
        fi
        if ! mv -f -- "$sidecar.tmp" "$sidecar"; then
            log "ERROR: no se pudo publicar el sidecar local de $base"
            return 1
        fi

        if ! remote_hash=$(ssh "${ssh_options[@]}" "$nas_host" \
            "if [ -f '$nas_dir/$base' ]; then sha256sum '$nas_dir/$base'; fi"); then
            log "ERROR: no se pudo consultar $base en el NAS"
            return 1
        fi
        remote_hash=${remote_hash%% *}
        if [[ "$remote_hash" == "$local_hash" ]] && \
            ssh "${ssh_options[@]}" "$nas_host" \
                "cd '$nas_dir' && sha256sum -c '$base.sha256' >/dev/null 2>&1"; then
            log "NAS ya verificado: $base"
            continue
        fi

        # Los temporales viven en .incoming, ruta excluida del trabajo Restic.
        # El archivo definitivo aparece únicamente después de verificarlo.
        if ! rsync -rt --checksum --human-readable -e "$rsync_ssh" \
            "$archive" "$nas_host:$nas_dir/.incoming/$base.partial"; then
            log "ERROR: falló la transferencia de $base al NAS"
            return 1
        fi
        if ! rsync -rt --checksum --human-readable -e "$rsync_ssh" \
            "$sidecar" "$nas_host:$nas_dir/.incoming/$base.sha256.partial"; then
            log "ERROR: falló la transferencia del sidecar de $base"
            return 1
        fi
        if ! remote_partial_hash=$(ssh "${ssh_options[@]}" "$nas_host" \
            "sha256sum '$nas_dir/.incoming/$base.partial'"); then
            log "ERROR: no se pudo verificar el temporal de $base en el NAS"
            return 1
        fi
        remote_partial_hash=${remote_partial_hash%% *}
        if [[ "$remote_partial_hash" != "$local_hash" ]]; then
            log "ERROR: el SHA256 temporal del NAS no coincide para $base"
            return 1
        fi
        if ! ssh "${ssh_options[@]}" "$nas_host" \
            "mv -f -- '$nas_dir/.incoming/$base.sha256.partial' '$nas_dir/$base.sha256' && mv -f -- '$nas_dir/.incoming/$base.partial' '$nas_dir/$base'"; then
            log "ERROR: no se pudo publicar atómicamente $base en el NAS"
            return 1
        fi
        if ! ssh "${ssh_options[@]}" "$nas_host" \
            "cd '$nas_dir' && sha256sum -c '$base.sha256' >/dev/null"; then
            log "ERROR: la copia definitiva del NAS no supera SHA256: $base"
            return 1
        fi
        log "NAS verificado: $base"
    done
}

server_initially_active=false
server_was_active=false
temporary=""
temporary_sidecar=""
orphan_archive=""

restart_server() {
    local attempt

    if [[ "$server_was_active" != true ]]; then
        return 0
    fi
    if ! systemctl --user start "$server_service"; then
        log "ERROR: no se pudo iniciar el servidor"
        return 1
    fi
    for ((attempt = 1; attempt <= 45; attempt++)); do
        if systemctl --user is-active --quiet "$server_service" && \
            ss -Hltn "( sport = :$server_port )" | grep -q .; then
            server_was_active=false
            log "Servidor reiniciado y puerto $server_port verificado"
            return 0
        fi
        sleep 2
    done
    log "ERROR: el servidor no quedó activo en el puerto $server_port"
    return 1
}

cleanup() {
    local rc=$?
    trap - EXIT
    if [[ -n "$temporary" ]]; then
        rm -f -- "$temporary"
    fi
    if [[ -n "$temporary_sidecar" ]]; then
        rm -f -- "$temporary_sidecar"
    fi
    if [[ -n "$orphan_archive" && -f "$orphan_archive" && ! -f "$orphan_archive.sha256" ]]; then
        rm -f -- "$orphan_archive"
    fi
    if ! restart_server; then
        rc=1
    fi
    exit "$rc"
}

on_term() {
    log "Respaldo interrumpido por SIGTERM"
    exit 143
}

on_int() {
    log "Respaldo interrumpido por SIGINT"
    exit 130
}

mkdir -p "$backup_dir"
exec 9>"$lock_file"
flock -n 9 || exit 0

# Reintentar primero cualquier transferencia pendiente. Esto no interrumpe el
# servidor y permite recuperar una caída temporal del NAS en el siguiente ciclo.
nas_sync_rc=0
if ! sync_to_nas; then
    nas_sync_rc=1
fi

# No interrumpir una partida. RCON está desactivado, por lo que se usa el estado
# de las conexiones TCP y se vuelve a comprobar justo antes de detener Java.
if ! players=$(connected_players); then
    exit 1
fi
if (( players > 0 )); then
    log "Respaldo local pospuesto: hay $players jugador(es) conectado(s)"
    exit "$nas_sync_rc"
fi

# Una copia correcta cada 20 horas como máximo.
if ! latest=$(latest_backup); then
    exit 1
fi
if [[ -n "$latest" && "$force_backup" != true ]]; then
    if ! latest_epoch=$(stat -c %Y "$latest"); then
        log "ERROR: no se pudo consultar la fecha del último respaldo"
        exit 1
    fi
    now=$(date +%s)
    if (( now - latest_epoch < 72000 )); then
        log "Todavía no corresponde crear otro respaldo local"
        exit "$nas_sync_rc"
    fi
fi

# Evitar llenar el disco si el NAS queda inaccesible durante varios días.
if ! world_kib=$(du -sk "$server_dir/$world"); then
    log "ERROR: no se pudo calcular el tamaño del mundo"
    exit 1
fi
world_kib=${world_kib%%[[:space:]]*}
if [[ ! "$world_kib" =~ ^[0-9]+$ ]]; then
    log "ERROR: tamaño de mundo no válido: $world_kib"
    exit 1
fi
if ! available_kib=$(df --output=avail "$backup_dir" | awk 'NR == 2 { print $1 }'); then
    log "ERROR: no se pudo consultar el espacio disponible"
    exit 1
fi
if [[ ! "$available_kib" =~ ^[0-9]+$ ]]; then
    log "ERROR: espacio disponible no válido: $available_kib"
    exit 1
fi
required_kib=$((world_kib * 2 + 1048576))
if (( available_kib < required_kib )); then
    log "ERROR: espacio insuficiente para otra copia (disponible=${available_kib}KiB, requerido=${required_kib}KiB)"
    exit 1
fi

if ! server_state=$(systemctl --user show -p ActiveState --value "$server_service"); then
    log "ERROR: no se pudo consultar el estado del servidor"
    exit 1
fi
case "$server_state" in
    active) server_initially_active=true ;;
    inactive) server_initially_active=false ;;
    *)
        log "Respaldo pospuesto: estado no seguro del servidor ($server_state)"
        exit 1
        ;;
esac

stamp=$(date +%Y-%m-%d_%H%M%S)
archive="$backup_dir/Dos-Almas-$stamp.tar.zst"
temporary="$archive.incomplete"
trap cleanup EXIT
trap on_term TERM
trap on_int INT

if [[ "$server_initially_active" == true ]]; then
    if ! players=$(connected_players); then
        exit 1
    fi
    if (( players > 0 )); then
        log "Respaldo local pospuesto en la segunda comprobación: hay $players jugador(es) conectado(s)"
        exit "$nas_sync_rc"
    fi
    log "Deteniendo el servidor para crear un snapshot consistente"
    # Desde aquí, incluso una señal o un fallo de stop debe dejar el servidor
    # arrancado de nuevo al salir.
    server_was_active=true
    if ! systemctl --user stop "$server_service"; then
        log "ERROR: no se pudo detener el servidor"
        exit 1
    fi
fi

if ! tar --zstd -cf "$temporary" -C "$server_dir" "$world"; then
    log "ERROR: no se pudo crear el archivo del mundo"
    exit 1
fi
if ! tar --zstd -tf "$temporary" >/dev/null; then
    log "ERROR: el archivo recién creado no supera la validación de tar"
    exit 1
fi
archive_base=$(basename "$archive")
temporary_sidecar="$archive.sha256.incomplete"
if ! local_hash=$(sha256sum "$temporary"); then
    log "ERROR: no se pudo calcular la suma SHA256 del respaldo"
    exit 1
fi
local_hash=${local_hash%% *}
if ! printf '%s  %s\n' "$local_hash" "$archive_base" >"$temporary_sidecar"; then
    log "ERROR: no se pudo preparar la suma SHA256 del respaldo"
    exit 1
fi
orphan_archive="$archive"
if ! mv -f -- "$temporary" "$archive"; then
    log "ERROR: no se pudo publicar el respaldo local"
    exit 1
fi
temporary=""
if ! mv -f -- "$temporary_sidecar" "$archive.sha256"; then
    log "ERROR: no se pudo publicar la suma SHA256 del respaldo"
    rm -f -- "$archive"
    orphan_archive=""
    exit 1
fi
temporary_sidecar=""
orphan_archive=""
if ! (cd "$backup_dir" && sha256sum -c "$archive_base.sha256" >/dev/null); then
    log "ERROR: el respaldo local no supera SHA256"
    rm -f -- "$archive" "$archive.sha256"
    exit 1
fi
log "Respaldo local verificado: $archive_base"

# La partida vuelve a estar disponible antes de transferir por red.
if ! restart_server; then
    exit 1
fi

# Copia versionada al NAS. El NAS ejecuta a diario un backup Restic cifrado de
# /mnt/pool/backups hacia Google Drive, por lo que esta ruta queda incluida.
if ! sync_to_nas; then
    exit 1
fi

# Conservar las 30 copias locales más recientes, pero sólo después de confirmar
# que todas las copias pendientes están en el NAS. En el NAS no se borra nada.
mapfile -t old_backups < <(find "$backup_dir" -maxdepth 1 -type f -name 'Dos-Almas-*.tar.zst' -printf '%T@ %p\n' | sort -nr | tail -n +31 | cut -d' ' -f2-)
for old in "${old_backups[@]}"; do
    [[ "$old" == "$backup_dir"/Dos-Almas-*.tar.zst ]] || continue
    rm -f -- "$old" "$old.sha256"
done
