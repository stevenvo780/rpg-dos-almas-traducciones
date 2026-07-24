# Minecraft público mediante la VPS de Ágora

Estado comprobado el 2026-07-22. El servidor Forge 1.20.1 `RPG Dos Almas`
corre en `kratos` y escucha en TCP 25565. La VPS pública elegida es
`agora-storage` (`76.13.118.239`), conectada a `kratos` por Tailscale. El relay
está instalado y probado, pero permanece deshabilitado y cerrado por UFW.

## Entrada pública

- Entrada prevista sin cambio DNS: `hub.elenxos.com:25565`.
- Nombre definitivo recomendado: `minecraft.elenxos.com`.
- Registro pendiente en Hostinger: `A minecraft → 76.13.118.239`, inicialmente
  con TTL 300. Minecraft Java usa 25565 por defecto, por lo que no necesita TLS,
  Caddy, certificado ni registro SRV mientras se conserve ese puerto.
- No usar `agora.elenxos.com`: actualmente es un CNAME de Vercel y no termina en
  esta VPS.

## Flujo

```text
Jugador
  → hub.elenxos.com:25565 / 76.13.118.239:25565
  → UFW de agora-storage
  → minecraft-proxy.socket + systemd-socket-proxyd
  → Tailscale cifrado, 100.64.0.6 → 100.64.0.1:25565
  → rpg-dos-almas-server.service en kratos
```

La VPS y `kratos` ya se alcanzan por el puerto 25565 dentro de Tailscale. El
relay no usa el SSH de root, no añade otro daemon y no modifica Caddy ni los
contenedores de Ágora.

## Bloqueo de identidad antes de publicar

- El servidor sigue con `online-mode=false` y el relay cerrado.
- Hay dos identidades conocidas; ambas usan UUID generados en modo offline y
  tienen datos en `playerdata`, `advancements` y `stats`.
- Cambiar a `online-mode=true` exige que ambos jugadores posean cuentas
  oficiales. Sus UUID online serán distintos, así que se deben migrar los tres
  conjuntos de archivos para conservar inventario, posición, avances y
  estadísticas.
- No publicar el puerto con `online-mode=false`: permitiría suplantar nombres.
- No automatizar la VPS desde Minecraft con la llave SSH general de `stev`, que
  tiene acceso root a `agora-storage`. La apertura se hará desde el plano de
  control administrativo después de la migración.

## Seguridad del relay

- Al activarse, UFW sólo publicará TCP 25565. El backend permanece dentro de
  Tailscale.
- `systemd-socket-proxyd` se ejecuta con usuario dinámico, filesystem protegido,
  sin privilegios nuevos y con un máximo de 128 conexiones.
- Tras pasar a modo online, `white-list=false` aceptará jugadores autenticados
  no incluidos en una lista. Activar whitelist si el servidor no debe ser
  abierto a cualquiera.

Minecraft verá la dirección Tailscale de la VPS, no la IP pública real de cada
jugador. Un ataque de volumen contra 25565 también puede afectar otros servicios
de `agora-storage`; para aislamiento o IP real por jugador se necesita un relay
dedicado o un proxy compatible con el protocolo de Minecraft.

## Archivos desplegados

En `agora-storage`:

- `/etc/systemd/system/minecraft-proxy.socket`
- `/etc/systemd/system/minecraft-proxy.service`

En `kratos`:

- `/home/stev/Minecraft/Servidor/RPG-Dos-Almas/server.properties`
- `/home/stev/Minecraft/Servidor/RPG-Dos-Almas/PUBLICACION.md`

## Activación y comprobación

Primero confirmar que ambos jugadores usan cuentas oficiales, obtener sus UUID
online, crear una copia consistente del mundo y migrar los archivos de cada UUID
offline. Después, sin jugadores conectados, cambiar una única clave efectiva a
`online-mode=true`, reiniciar y validar Minecraft:

```bash
systemctl --user restart rpg-dos-almas-server.service
systemctl --user is-active rpg-dos-almas-server.service
ss -Hltn '( sport = :25565 )'
```

En `agora-storage`:

```bash
systemctl enable --now minecraft-proxy.socket
ufw allow 25565/tcp comment 'Minecraft RPG Dos Almas via Tailscale'
systemctl status minecraft-proxy.socket minecraft-proxy.service
ss -Hltn '( sport = :25565 )'
```

Desde una red externa:

```bash
nc -vz hub.elenxos.com 25565
```

La prueba funcional final es entrar con un cliente Forge 1.20.1 y el modpack de
RPG Dos Almas.

## Reversión

En `agora-storage`:

```bash
ufw delete allow 25565/tcp
systemctl disable --now minecraft-proxy.socket
```

Esto retira inmediatamente la entrada pública sin detener Minecraft en
`kratos`. La unidad `.service` termina al cerrar sus conexiones.
