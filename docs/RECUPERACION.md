# Recuperación del proyecto

## Alcance

Git conserva el plano de reconstrucción. El mundo real se recupera desde los
backups locales/NAS; los mods y Minecraft se vuelven a obtener desde fuentes
autorizadas.

## Servidor

1. Instalar Java 17 y Forge 47.4.20 para Minecraft 1.20.1.
2. Compilar los módulos propios guardados en `snapshot/custom` con Java 17.
   Sus dependencias de compilación deben obtenerse desde sus fuentes legítimas;
   los JAR ajenos no están incluidos.
3. Recuperar los JAR de los demás mods usando
   `snapshot/manifests/mods.json` y los manifiestos de
   `snapshot/modpack`.
4. Copiar `snapshot/server/config` y `snapshot/server/defaultconfigs`.
5. Copiar los scripts de `snapshot/server/scripts`, revisar rutas locales y dar
   permiso de ejecución.
6. Crear `server.properties` a partir de
   `snapshot/server/server.properties.example`; definir localmente cualquier
   valor marcado como `CONFIGURAR_LOCALMENTE`.
7. Instalar las unidades de `snapshot/server/systemd-user` en
   `~/.config/systemd/user`, ejecutar `systemctl --user daemon-reload` y
   habilitar las unidades necesarias.
8. Restaurar el mundo siguiendo `snapshot/server/docs/BACKUPS.md`.

La definición del puente TCP está en `snapshot/server/infra`. La clave SSH, la
cuenta remota y el registro DNS deben provisionarse de nuevo; deliberadamente
no forman parte del repositorio.

Antes de parar o reiniciar el servidor hay que comprobar las conexiones al
puerto 25565.

## Clientes

El cliente potente y el portátil tienen configuraciones separadas:

- `snapshot/client/tower`;
- `snapshot/client/portable`.

La carpeta portátil conserva el perfil de baja GPU y la iluminación dinámica
sin shaders. Los dos perfiles deben recibir exactamente el conjunto compatible
de mods indicado por sus inventarios.

## Instalador Windows

Las fuentes están en `snapshot/distribution/isa-windows`. El ejecutable no se
guarda en Git porque incorpora un payload con mods de terceros. Para compilarlo
se necesita preparar localmente un payload adquirido de forma legítima y seguir
el script `Instalador-Windows/compilar-instalador.sh`.

## Validación

Después de recuperar o actualizar:

```bash
python3 tools/verify_repository.py
python3 -m unittest discover -s tools/tests -v
python3 -m unittest discover -s tools/translator/tests -v
```

El verificador falla si detecta binarios excluidos, nombres sensibles, claves
privadas o asignaciones evidentes de secretos.
