# Copias de seguridad de RPG Dos Almas

Estado verificado el 2026-07-22: el servidor activo no corre dentro de Docker.
Forge 1.20.1 se ejecuta como servicio de usuario
`rpg-dos-almas-server.service` y escucha en el puerto 25565.

## Flujo automático

1. `rpg-dos-almas-backup.timer` comprueba el estado cada hora.
2. Si no hay conexiones de jugadores y han pasado al menos 20 horas desde la
   última copia, detiene brevemente el servidor y archiva el mundo `Dos-Almas`.
   Aunque RCON está habilitado para administración local, este mecanismo no
   depende de él: la detección usa conexiones TCP y se repite justo antes de
   detener el servicio, por lo que sigue existiendo una carrera residual muy
   pequeña si alguien entra exactamente en ese instante.
3. El archivo local se valida como tar, recibe un SHA256 y se guarda en
   `/home/stev/Minecraft/Respaldos/Mundos`. Sólo después de confirmar el NAS se
   conservan las 30 copias locales más recientes; una copia no sincronizada no
   se elimina.
4. Cada archivo se publica de forma atómica y sin `--delete` en
   `nas@192.168.78.144:/mnt/pool/backups/kratos/minecraft/RPG-Dos-Almas/`.
   Se validan el SHA256 local, el temporal remoto y el archivo remoto definitivo.
5. A las 05:00 el NAS incluye `/mnt/pool/backups` en un snapshot Restic cifrado
   sobre el remote `gdrive-humanizar`. Su retención es de 7 copias diarias, 4
   semanales y 6 mensuales, con verificación del 5 % de los packs.

La llegada al NAS es inmediata, pero **Drive es asíncrono**. Un archivo nuevo no
está protegido en Drive hasta que termine correctamente el siguiente trabajo de
las 05:00; el RPO de Drive puede acercarse a 24 horas. Verificar el NAS no prueba
por sí solo que el archivo ya figure en un snapshot de Drive.

Si el NAS no está disponible, la copia local permanece intacta y el timer
reintenta la transferencia en el siguiente ciclo. La transferencia no mantiene
el servidor detenido. El NAS conserva estas copias de forma acumulativa; hay que
vigilar su capacidad antes de definir una retención destructiva.

## Comprobaciones

```bash
systemctl --user status rpg-dos-almas-backup.timer
journalctl --user -u rpg-dos-almas-backup.service -n 100 --no-pager
cd /home/stev/Minecraft/Respaldos/Mundos
sha256sum -c Dos-Almas-AAAA-MM-DD_HHMMSS.tar.zst.sha256
```

En el NAS:

```bash
cd /mnt/pool/backups/kratos/minecraft/RPG-Dos-Almas
sha256sum -c Dos-Almas-AAAA-MM-DD_HHMMSS.tar.zst.sha256
grep -E 'backup rc=|forget\+prune rc=|check rc=|backup END' \
  /mnt/pool/backups/logs/restic-backups-AAAAMMDD-050001.log
```

Para demostrar que un archivo concreto ya llegó al repositorio cifrado de Drive,
ejecutar en el NAS después de un trabajo correcto (requiere privilegios porque
las credenciales Restic están bajo `/root`):

```bash
sudo bash -lc 'source /root/.restic-nas.env; restic ls latest /mnt/pool/backups/kratos/minecraft/RPG-Dos-Almas/Dos-Almas-AAAA-MM-DD_HHMMSS.tar.zst'
```

## Restauración transaccional

Elegir primero el archivo y sustituir `AAAA-MM-DD_HHMMSS`. La extracción se hace
en un directorio temporal del mismo disco; el mundo anterior no se borra y se
restaura automáticamente si el servidor nuevo no abre el puerto.

```bash
set -euo pipefail
server=/home/stev/Minecraft/Servidor/RPG-Dos-Almas
backups=/home/stev/Minecraft/Respaldos/Mundos
archive=Dos-Almas-AAAA-MM-DD_HHMMSS.tar.zst
stamp=$(date +%Y%m%d-%H%M%S)
stage="$server/.restore-Dos-Almas-$stamp"
previous="$server/Dos-Almas.pre-restore-$stamp"
failed="$server/Dos-Almas.failed-restore-$stamp"

cd "$backups"
sha256sum -c "$archive.sha256"
mkdir "$stage"
tar --zstd -xf "$archive" -C "$stage"
test -f "$stage/Dos-Almas/level.dat"

systemctl --user stop rpg-dos-almas-server.service
mv "$server/Dos-Almas" "$previous"
if ! mv "$stage/Dos-Almas" "$server/Dos-Almas"; then
  mv "$previous" "$server/Dos-Almas"
  systemctl --user start rpg-dos-almas-server.service
  exit 1
fi
rmdir "$stage"

restored=false
if systemctl --user start rpg-dos-almas-server.service; then
  for _ in $(seq 1 45); do
    if systemctl --user is-active --quiet rpg-dos-almas-server.service && \
       ss -Hltn '( sport = :25565 )' | grep -q .; then
      restored=true
      break
    fi
    sleep 2
  done
fi
if [[ "$restored" != true ]]; then
  systemctl --user stop rpg-dos-almas-server.service || true
  mv "$server/Dos-Almas" "$failed"
  mv "$previous" "$server/Dos-Almas"
  systemctl --user start rpg-dos-almas-server.service
  exit 1
fi
printf 'Restauración correcta. Mundo anterior conservado en %s\n' "$previous"
```

Para recuperar desde Drive primero se restaura el snapshot Restic en el NAS y
después se copia el `.tar.zst` y su `.sha256` verificado hacia este equipo. La
clave del repositorio Restic permanece en Vaultwarden y no se guarda aquí.

## Hardening administrativo pendiente en el NAS

El cron `/etc/cron.d/nas-restic-backups` corre como `root`, pero actualmente
invoca `/home/nas/scripts/restic-backups-to-gdrive.sh`, archivo reemplazable por
el usuario `nas`. Lo mismo aplica al notifier que ese script ejecuta. Aunque el
backup funciona, esta propiedad permite una escalada local a root si la cuenta
`nas` o una de sus llaves SSH queda comprometida.

La corrección requiere una sesión administrativa legítima en el NAS:

1. Instalar el script Restic y el notifier en `/usr/local/sbin/`, con directorio
   y archivos `root:root` no modificables por `nas`.
2. Cambiar la variable `NOTIFY` de la copia root-owned para apuntar al notifier
   root-owned.
3. Cambiar el cron para ejecutar la copia de `/usr/local/sbin/`.
4. Validar `bash -n`, ownership/modos, una ejecución manual y el siguiente cron.

No se debe resolver haciendo que cron copie o ejecute un instalador controlado
por `nas`; el contenido debe entrar desde una fuente confiable y quedar fijado
por root. Hasta completar esto, no considerar cerrada la seguridad del job de
Drive aunque sus snapshots e integridad hayan sido verificados.
