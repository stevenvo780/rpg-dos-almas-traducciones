# RPG Dos Almas - Compatibilidad

Módulo Forge 1.20.1 propio para integrar sistemas del modpack que no se
comunican entre sí.

## Integración disponible

El `Feeding Upgrade` de Sophisticated Backpacks puede beber automáticamente
desde la misma mochila usando la API de Thirst Was Taken.

- Solo funciona cuando el upgrade está instalado y activado.
- Respeta la lista blanca o negra configurada en el filtro del upgrade.
- Respeta sus niveles `ANY`, `HALF` y `FULL`, aplicados a la sed.
- Prioriza bebidas sin riesgo y agua de mayor pureza.
- Por defecto exige pureza aceptable (`2`) o purificada (`3`).
- Con sed crítica (`4` o menos), puede usar agua insegura como último recurso.
- La botella o recipiente vuelve primero a la mochila.
- El tag `dos_almas_compat:auto_drink_blacklist` permite excluir objetos.

La configuración se genera en `config/dos-almas-compat-common.toml`.

MapSyncer 1.0.3 también recibe las referencias remapeadas de
`Blocks.AIR` y `Fluids.EMPTY` que necesita su proxy de lectura. Esto evita el
`NoSuchFieldException` que el mod produce en Forge productivo al generar mapas
o limpiar sus cachés durante el apagado.

## Compilación

El proyecto usa los JAR instalados localmente solo como dependencias de
compilación. No los redistribuye ni incorpora código o recursos ajenos.

```bash
./gradlew clean build
```

El resultado queda en `build/libs/dos_almas_compat-1.0.2.jar`.
