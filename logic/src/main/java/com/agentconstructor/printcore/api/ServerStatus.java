package com.agentconstructor.printcore.api;

import java.util.List;

public final class ServerStatus {
    public final String modVersion;
    public final String gameVersion;
    public final boolean serverReady;
    public final double tps;
    public final double mspt;
    public final int jobQueue;
    public final List<String> dimensions;

    public ServerStatus(
            String modVersion,
            String gameVersion,
            boolean serverReady,
            double tps,
            double mspt,
            int jobQueue,
            List<String> dimensions
    ) {
        this.modVersion = modVersion;
        this.gameVersion = gameVersion;
        this.serverReady = serverReady;
        this.tps = tps;
        this.mspt = mspt;
        this.jobQueue = jobQueue;
        this.dimensions = List.copyOf(dimensions);
    }

    public static ServerStatus offline(PrintConfig config) {
        return new ServerStatus("0.1.0", "1.20.1", false, 0, 0, 0, config.allowedDimensionList());
    }
}
