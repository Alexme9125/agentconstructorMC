package com.agentconstructor.printcore.job;

import com.agentconstructor.printcore.schem.SpongeSchematic;

import java.util.Locale;

public final class StoredSchematic {
    public final String id;
    public final String filename;
    public final String sha256;
    public final byte[] bytes;
    public final SpongeSchematic schematic;

    public StoredSchematic(String id, String filename, byte[] bytes) {
        this.id = id;
        this.filename = filename;
        this.bytes = bytes;
        this.sha256 = SpongeSchematic.sha256(bytes);
        this.schematic = SpongeSchematic.fromBytes(bytes);
    }

    public boolean matchesHash(String expected) {
        return expected == null || expected.isBlank()
                || sha256.equals(expected.toLowerCase(Locale.ROOT));
    }
}
