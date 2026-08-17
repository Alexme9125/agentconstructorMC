package com.agentconstructor.printcore.world;

import com.agentconstructor.printcore.api.Volume;

public interface WorldView {
    String dimension();

    int minY();

    int maxY();

    String getBlock(int x, int y, int z);

    /**
     * Height of the first non-motion-blocking-equivalent air, matching Minecraft's
     * MOTION_BLOCKING_NO_LEAVES convention (standable surface is return-1).
     */
    int getHeight(String type, int x, int z);

    default boolean inWorldHeight(int y) {
        return y >= minY() && y <= maxY();
    }

    default boolean hasEntityOccupying(Volume volume) {
        return false;
    }
}
