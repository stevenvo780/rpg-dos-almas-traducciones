package com.stev.dosalmascompat;

import net.minecraftforge.common.ForgeConfigSpec;

final class CompatConfig {
    static final ForgeConfigSpec SPEC;
    static final ForgeConfigSpec.BooleanValue AUTO_DRINK_ENABLED;
    static final ForgeConfigSpec.IntValue CHECK_INTERVAL_TICKS;
    static final ForgeConfigSpec.IntValue MINIMUM_WATER_PURITY;
    static final ForgeConfigSpec.BooleanValue ALLOW_UNSAFE_WHEN_CRITICAL;
    static final ForgeConfigSpec.IntValue CRITICAL_THIRST_LEVEL;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
            "Integración entre el Feeding Upgrade de Sophisticated Backpacks",
            "y la sed de Thirst Was Taken."
        ).push("autoDrink");

        AUTO_DRINK_ENABLED = builder
            .comment("Permite que un Feeding Upgrade activo consuma bebidas desde su mochila.")
            .define("enabled", true);

        CHECK_INTERVAL_TICKS = builder
            .comment("Intervalo de comprobación. 20 ticks equivalen aproximadamente a un segundo.")
            .defineInRange("checkIntervalTicks", 20, 5, 200);

        MINIMUM_WATER_PURITY = builder
            .comment(
                "Pureza mínima habitual del agua: 0 sucia, 1 algo sucia,",
                "2 aceptable, 3 purificada. Las bebidas sin pureza se consideran seguras."
            )
            .defineInRange("minimumWaterPurity", 2, 0, 3);

        ALLOW_UNSAFE_WHEN_CRITICAL = builder
            .comment("Permite agua de menor pureza como último recurso cuando la sed es crítica.")
            .define("allowUnsafeWaterWhenCritical", true);

        CRITICAL_THIRST_LEVEL = builder
            .comment("Nivel de sed (0-20) a partir del cual se permite el último recurso.")
            .defineInRange("criticalThirstLevel", 4, 0, 20);

        builder.pop();
        SPEC = builder.build();
    }

    private CompatConfig() {
    }
}
