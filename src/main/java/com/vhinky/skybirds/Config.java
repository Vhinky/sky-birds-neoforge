package com.vhinky.skybirds;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue FREQUENCY;
    public static final ModConfigSpec.IntValue MAX_FLOCKS;
    public static final ModConfigSpec.DoubleValue SIZE_MULTIPLIER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        ENABLED = b.comment("Show birds in the sky")
                .define("enabled", true);

        FREQUENCY = b.comment("How often new flocks appear (1.0 = default)")
                .defineInRange("frequency", 1.0, 0.1, 5.0);

        MAX_FLOCKS = b.comment("Maximum number of flocks around the player at the same time")
                .defineInRange("maxFlocks", 5, 1, 20);

        SIZE_MULTIPLIER = b.comment("Bird size multiplier")
                .defineInRange("sizeMultiplier", 1.0, 0.5, 3.0);

        SPEC = b.build();
    }

    private Config() {}
}
