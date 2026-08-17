package com.agentconstructor.printcore.api;

import java.util.List;
import java.util.Set;

public final class PrintConfig {
    public static final int DEFAULT_PORT = 9471;
    public static final int DEFAULT_MODULE_MAX = 128;
    public static final int DEFAULT_BLOCKS_PER_TICK = 4096;
    public static final long DEFAULT_MAX_VOLUME = 128L * 128L * 64L;
    public static final long DEFAULT_MAX_READ_VOLUME = 32L * 32L * 32L;
    public static final int DEFAULT_MAX_SPARSE = 4096;

    public final String bind;
    public final int port;
    public final String token;
    public final int maxModuleSize;
    public final int blocksPerTick;
    public final long maxVolume;
    public final long maxReadVolume;
    public final int maxSparseBlocks;
    public final int minY;
    public final int maxY;
    public final int buildMinY;
    public final int buildMaxY;
    public final Set<String> allowedDimensions;
    public final boolean allowForceOverwrite;
    public final boolean allowOutOfBounds;
    public final boolean skipMaterialChecks;

    public PrintConfig(
            String bind,
            int port,
            String token,
            int maxModuleSize,
            int blocksPerTick,
            long maxVolume,
            long maxReadVolume,
            int maxSparseBlocks,
            int minY,
            int maxY,
            int buildMinY,
            int buildMaxY,
            Set<String> allowedDimensions,
            boolean allowForceOverwrite,
            boolean allowOutOfBounds,
            boolean skipMaterialChecks
    ) {
        this.bind = bind;
        this.port = port;
        this.token = token == null ? "" : token;
        this.maxModuleSize = maxModuleSize;
        this.blocksPerTick = blocksPerTick;
        this.maxVolume = maxVolume;
        this.maxReadVolume = maxReadVolume;
        this.maxSparseBlocks = maxSparseBlocks;
        this.minY = minY;
        this.maxY = maxY;
        this.buildMinY = buildMinY;
        this.buildMaxY = buildMaxY;
        this.allowedDimensions = Set.copyOf(allowedDimensions);
        this.allowForceOverwrite = allowForceOverwrite;
        this.allowOutOfBounds = allowOutOfBounds;
        this.skipMaterialChecks = skipMaterialChecks;
    }

    public static PrintConfig defaults() {
        return new PrintConfig(
                "127.0.0.1",
                DEFAULT_PORT,
                "printcore-dev-token",
                DEFAULT_MODULE_MAX,
                DEFAULT_BLOCKS_PER_TICK,
                DEFAULT_MAX_VOLUME,
                DEFAULT_MAX_READ_VOLUME,
                DEFAULT_MAX_SPARSE,
                -64,
                319,
                94,
                223,
                Set.of("minecraft:overworld", "city:main_city"),
                false,
                false,
                false
        );
    }

    public PrintConfig withToken(String newToken) {
        return new PrintConfig(
                bind, port, newToken, maxModuleSize, blocksPerTick, maxVolume, maxReadVolume,
                maxSparseBlocks, minY, maxY, buildMinY, buildMaxY, allowedDimensions,
                allowForceOverwrite, allowOutOfBounds, skipMaterialChecks
        );
    }

    public boolean dimensionAllowed(String dimension) {
        return allowedDimensions.isEmpty() || allowedDimensions.contains(dimension);
    }

    public List<String> allowedDimensionList() {
        return List.copyOf(allowedDimensions);
    }
}
