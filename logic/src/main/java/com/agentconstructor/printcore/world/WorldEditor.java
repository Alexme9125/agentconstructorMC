package com.agentconstructor.printcore.world;

import com.agentconstructor.printcore.api.Volume;
import com.agentconstructor.printcore.job.VolumeSnapshot;

public interface WorldEditor extends WorldView {
    void setBlock(int x, int y, int z, String blockState);

    VolumeSnapshot snapshot(Volume volume);

    void restore(VolumeSnapshot snapshot);

    default void preload(Volume volume) {
    }
}
