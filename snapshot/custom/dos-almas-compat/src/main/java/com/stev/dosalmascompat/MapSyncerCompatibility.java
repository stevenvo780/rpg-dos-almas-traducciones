package com.stev.dosalmascompat;

import java.lang.reflect.Field;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prepara el proxy de MapSyncer con referencias remapeadas por Forge.
 *
 * <p>MapSyncer 1.0.3 busca por reflexión los nombres de desarrollo
 * {@code Blocks.AIR} y {@code Fluids.EMPTY}. Esos nombres no existen tal cual
 * en el entorno productivo de Forge 1.20.1. Este adaptador conserva el JAR
 * original y proporciona los mismos objetos mediante referencias que
 * ForgeGradle sí puede remapear.</p>
 */
final class MapSyncerCompatibility {
    private static final Logger LOGGER = LoggerFactory.getLogger(
        MapSyncerCompatibility.class
    );
    private static final String FACTORY_CLASS =
        "com.mapsyncer.platform.PlaceholderBlockGetterFactory";

    private MapSyncerCompatibility() {
    }

    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            Class<?> factory = Class.forName(FACTORY_CLASS);
            setStaticField(factory, "blockGetterClass", BlockGetter.class);
            setStaticField(factory, "blocksClass", Blocks.class);
            setStaticField(factory, "fluidsClass", Fluids.class);
            setStaticField(
                factory,
                "airDefaultState",
                Blocks.AIR.defaultBlockState()
            );
            setStaticField(
                factory,
                "emptyFluidState",
                Fluids.EMPTY.defaultFluidState()
            );
            LOGGER.info(
                "Compatibilidad de MapSyncer inicializada con referencias remapeadas."
            );
        } catch (ClassNotFoundException ignored) {
            // MapSyncer es opcional; no hay nada que integrar si no está instalado.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn(
                "No se pudo preparar la compatibilidad de MapSyncer.",
                exception
            );
        }
    }

    private static void setStaticField(
        Class<?> owner,
        String name,
        Object value
    ) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
