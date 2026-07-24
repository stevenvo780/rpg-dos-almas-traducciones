# Puente público de RPG Dos Almas

El acceso público usa dos saltos TCP:

1. el equipo del servidor abre un túnel SSH inverso y publica Minecraft en
   `127.0.0.1:25566` del VPS;
2. `systemd-socket-proxyd` escucha públicamente en el puerto 25565 del VPS y
   entrega cada conexión al túnel.

La cuenta `minecraft-tunnel` del VPS no tiene shell interactiva. Su clave
autorizada permite únicamente crear el forward remoto
`127.0.0.1:25566`. El firewall del VPS abre solamente `25565/tcp`.

## Archivos

- `local/rpg-dos-almas-tunnel.service`: unidad systemd de usuario del equipo
  que aloja Minecraft.
- `vps/rpg-dos-almas-proxy.socket`: socket público del VPS.
- `vps/rpg-dos-almas-proxy.service`: proxy TCP activado por el socket.

El registro DNS debe ser un `A` con TTL 600 que apunte el subdominio elegido a
la IPv4 del VPS. Como Minecraft usa su puerto predeterminado, no hace falta un
registro SRV.
