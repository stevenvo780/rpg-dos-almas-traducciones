# Publicación de RPG Dos Almas

Estado comprobado el 2026-07-24.

## Dirección

- Nombre público: `minecraft.stevenvallejo.com`.
- Destino DNS: registro `A` hacia la IPv4 del VPS.
- Puerto: `25565/tcp`, el predeterminado de Minecraft Java.
- DNS autoritativo: Vercel, zona `stevenvallejo.com`.
- No se necesita un registro SRV mientras se conserve el puerto 25565.

El registro se creó mediante la cuenta de Vercel que administra los dominios
del entorno Prizma. No se modificaron el sitio principal ni los demás
subdominios.

## Flujo

```text
Jugador
  → minecraft.stevenvallejo.com:25565
  → socket público del VPS
  → systemd-socket-proxyd
  → túnel SSH inverso restringido, 127.0.0.1:25566
  → rpg-dos-almas-server.service, 127.0.0.1:25565
```

La cuenta remota `minecraft-tunnel` no tiene shell interactiva. Su clave sólo
permite publicar el forward remoto en `127.0.0.1:25566`. El VPS expone
únicamente el socket `25565/tcp` para Minecraft.

Las definiciones reproducibles están en `infra/`:

- `local/rpg-dos-almas-tunnel.service`;
- `vps/rpg-dos-almas-proxy.socket`;
- `vps/rpg-dos-almas-proxy.service`.

## Seguridad

El servidor necesita `online-mode=false` para los clientes actuales. La lista
blanca está activada y forzada, pero el modo offline permite que alguien que
conozca exactamente un nombre autorizado intente suplantarlo. Es el riesgo
residual de esta configuración.

No desactivar `white-list` ni `enforce-whitelist` mientras el servidor sea
público. Para eliminar la suplantación se necesitan cuentas oficiales,
`online-mode=true` y una migración controlada de los UUID de jugadores.

## Operación

Antes de parar o reiniciar:

```bash
ss -Htn state established '( sport = :25565 )'
```

Estado local:

```bash
systemctl --user status rpg-dos-almas-server.service
systemctl --user status rpg-dos-almas-tunnel.service
ss -Hltn '( sport = :25565 )'
```

Prueba desde una red externa:

```bash
dig +short minecraft.stevenvallejo.com A
nc -vz minecraft.stevenvallejo.com 25565
```

## Reversión

Detener el túnel local retira el backend público sin apagar Minecraft:

```bash
systemctl --user stop rpg-dos-almas-tunnel.service
```

Para una retirada completa también se debe desactivar el socket del VPS y
eliminar únicamente el registro `minecraft` de la zona DNS de Vercel.
