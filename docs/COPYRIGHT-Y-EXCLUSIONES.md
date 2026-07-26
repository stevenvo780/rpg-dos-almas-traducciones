# Copyright, licencias y exclusiones

Este repositorio conserva el trabajo de configuración y automatización de RPG
Dos Almas sin publicar los binarios ni los recursos originales de Minecraft,
Forge o los mods.

## Política de contenido

Se incluyen:

- scripts, documentación y configuración del proyecto;
- código fuente original de los módulos propios de compatibilidad;
- código fuente del launcher propio, su documentación y su empaquetado;
- manifiestos con nombres, tamaños, hashes y direcciones oficiales de descarga;
- fuentes del instalador, sin el payload binario;
- el icono PNG 64×64 creado específicamente para RPG Dos Almas;
- el overlay de traducción destinado al uso privado del grupo.

Se excluyen:

- Minecraft, Forge, launchers de terceros y entornos Java;
- mods JAR, bibliotecas, ejecutables e instaladores compilados;
- shaders, texturas y resource packs de terceros;
- mundos, backups, datos de jugadores, capturas, logs y cachés;
- claves SSH, credenciales RCON, archivos `.env`, contraseñas y tokens;
- los scripts de operación que enumeran las máquinas y las cuentas del grupo.

El launcher propio se publica solo como fuentes. El JRE que empaqueta, los JAR
de dependencias y las salidas de `build/` y `dist/` quedan fuera.

`limpiar-clientes-tras-reinicio.sh` no se publica aunque no contenga ninguna
contraseña: lee las claves del entorno, pero el propio script es un inventario
de las direcciones y los nombres de cuenta de los equipos del grupo.

Un hash o una URL en un manifiesto identifica una dependencia, pero no concede
derecho a redistribuirla. Cada dependencia debe descargarse desde su fuente
oficial y usarse conforme a su licencia. Antes de hacer público el overlay de
traducción se deben revisar individualmente las licencias de los mods cuyos
textos adapta; mientras tanto el repositorio debe permanecer privado.

La ausencia de un binario en Git no lo elimina del equipo ni de los respaldos
existentes. El mundo se protege mediante el sistema de backups descrito en
`docs/BACKUPS.md`.
