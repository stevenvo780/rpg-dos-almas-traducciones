package com.stev.dosalmascompat;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DosAlmasCompat.MOD_ID)
public final class DosAlmasCompat {
    public static final String MOD_ID = "dos_almas_compat";

    public DosAlmasCompat() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CompatConfig.SPEC);
        MinecraftForge.EVENT_BUS.addListener(AutoDrinkHandler::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(
            MapSyncerCompatibility::onServerAboutToStart
        );
    }
}
