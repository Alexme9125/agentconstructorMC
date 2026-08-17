package com.agentconstructor.printcore.job;

import com.agentconstructor.printcore.api.Volume;

public final class VolumeSnapshot {
    public final Volume volume;
    public final String[] blocks;

    public VolumeSnapshot(Volume volume, String[] blocks) {
        this.volume = volume;
        this.blocks = blocks;
        if (blocks.length != volume.blockCount()) {
            throw new IllegalArgumentException("snapshot size mismatch");
        }
    }
}
