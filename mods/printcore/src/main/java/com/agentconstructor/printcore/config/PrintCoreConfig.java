package com.agentconstructor.printcore.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class PrintCoreConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> BIND;
    public static final ForgeConfigSpec.IntValue PORT;
    public static final ForgeConfigSpec.ConfigValue<String> TOKEN;
    public static final ForgeConfigSpec.IntValue MAX_MODULE_SIZE;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue MAX_VOLUME;
    public static final ForgeConfigSpec.IntValue MAX_READ_VOLUME;
    public static final ForgeConfigSpec.IntValue MAX_SPARSE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FORCE_OVERWRITE;
    public static final ForgeConfigSpec.BooleanValue ALLOW_OUT_OF_BOUNDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("http");
        BIND = builder.define("bind", "127.0.0.1");
        PORT = builder.defineInRange("port", 9471, 1, 65535);
        TOKEN = builder.define("token", "printcore-dev-token");
        builder.pop();
        builder.push("limits");
        MAX_MODULE_SIZE = builder.defineInRange("maxModuleSize", 128, 1, 512);
        BLOCKS_PER_TICK = builder.defineInRange("blocksPerTick", 4096, 1, 200000);
        MAX_VOLUME = builder.defineInRange("maxVolume", 128 * 128 * 64, 1, Integer.MAX_VALUE);
        MAX_READ_VOLUME = builder.defineInRange("maxReadVolume", 32 * 32 * 32, 1, Integer.MAX_VALUE);
        MAX_SPARSE = builder.defineInRange("maxSparseBlocks", 4096, 1, 65536);
        ALLOWED_DIMENSIONS = builder.defineList("allowedDimensions",
                List.of("minecraft:overworld", "city:main_city"), o -> o instanceof String);
        ALLOW_FORCE_OVERWRITE = builder.comment("Survival players must never get this. Admin/agent default is still false.")
                .define("allowForceOverwrite", false);
        ALLOW_OUT_OF_BOUNDS = builder.define("allowOutOfBounds", false);
        builder.pop();
        SPEC = builder.build();
    }

    private PrintCoreConfig() {
    }
}
