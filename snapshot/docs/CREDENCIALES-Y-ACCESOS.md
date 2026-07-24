# Credenciales y accesos — RPG Dos Almas

Fecha de revisión: `2026-07-24`.

Este documento describe dónde viven los accesos sin contener valores secretos.
No pegar aquí contraseñas, tokens, claves privadas ni contenido de archivos de
credenciales.

## Almacén local consolidado

Las credenciales compartidas expresamente para este proyecto se consolidaron
en:

```text
/home/stev/Minecraft/.env
```

El archivo pertenece a `stev:stev`, usa modo `0600`, está fuera del repositorio
Git y las reglas públicas ignoran `.env` y `.env.*`.

Variables disponibles, sin reproducir valores:

| Grupo | Variables |
|---|---|
| Portátil Linux legado | `LEGACY_LINUX_LAPTOP_SSH_HOST`, `LEGACY_LINUX_LAPTOP_SSH_PORT`, `LEGACY_LINUX_LAPTOP_SSH_USER`, `LEGACY_LINUX_LAPTOP_SSH_PASSWORD` |
| PC Windows por SSH | `WINDOWS_PC_SSH_HOST`, `WINDOWS_PC_SSH_PORT`, `WINDOWS_PC_SSH_USER`, `WINDOWS_PC_SSH_PASSWORD` |
| VPS puente | `VPS_SSH_HOST`, `VPS_SSH_PORT`, `VPS_SSH_USER` |
| DNS legado de GoDaddy | `GODADDY_API_TOKEN` |
| Torre y servidor público | `MINECRAFT_TOWER_LAN_HOST`, `MINECRAFT_PUBLIC_HOST`, `MINECRAFT_PUBLIC_PORT` |
| NAS | `NAS_SSH_HOST`, `NAS_SSH_USER` |
| Referencias locales | `RCON_CREDENTIALS_FILE`, `SSH_CONFIG_FILE`, `GITHUB_REPOSITORY` |

No ejecutar el `.env` como un script. Si una herramienta necesita variables de
entorno, cargarlas en una shell sin trazado y cerrar esa shell al terminar:

```bash
set +x
set -a
source /home/stev/Minecraft/.env
set +a
```

No pasar secretos como argumentos de línea de comandos porque pueden aparecer
en la tabla de procesos.

## Almacenes que permanecen separados

- RCON: `Servidor/RPG-Dos-Almas/.rcon-credentials`, modo restrictivo.
- SSH: configuración y claves bajo `/home/stev/.ssh`.
- GitHub CLI: keyring del usuario; no copiar su token a `.env`.
- Secretos de contenedores: ubicaciones documentadas únicamente por referencia
  en `Documentacion/DOCKER-ACCESOS.md`.
- Restic/Vaultwarden: credenciales administradas en el NAS; no duplicarlas.

## Accesos no secretos

- Minecraft público: `minecraft.stevenvallejo.com:25565`.
- VPS puente: acceso administrativo por SSH y forwarding restringido según
  `Servidor/RPG-Dos-Almas/infra`.
- NAS de respaldos: destino descrito en
  `Servidor/RPG-Dos-Almas/BACKUPS.md`.
- GitHub público: `stevenvo780/rpg-dos-almas-traducciones`.

## Rotación recomendada

Las contraseñas y el token que fueron escritos en una conversación deben
considerarse expuestos aunque el chat sea privado. Después de confirmar que el
entorno funciona:

1. Rotar las dos contraseñas SSH.
2. Revocar y regenerar el token de GoDaddy si todavía se utiliza.
3. Actualizar únicamente el `.env` local, manteniendo modo `0600`.
4. Verificar que la credencial antigua ya no funcione.
5. Guardar la copia de recuperación en un gestor de contraseñas, no en Git.

## Comprobación antes de publicar

Desde el repositorio:

```bash
git check-ignore -v --no-index .env
git check-ignore -v --no-index nested/.env.local
python3 tools/verify_repository.py
git diff --cached --name-only
```

La salida publicada nunca debe incluir `.env`, `.rcon-credentials`, claves,
tokens ni archivos con datos de jugadores.
