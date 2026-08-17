package com.agentconstructor.printcore.job;

import com.agentconstructor.printcore.schem.SpongeSchematic;

public interface SchematicResolver {
    StoredSchematic find(PrintPlan.PasteStep step);

    default SpongeSchematic require(PrintPlan.PasteStep step) {
        StoredSchematic stored = find(step);
        if (stored == null) {
            throw new IllegalArgumentException("schematic not found for step " + step.id);
        }
        if (!stored.matchesHash(step.sha256)) {
            throw new IllegalArgumentException("schematic hash mismatch for step " + step.id);
        }
        return stored.schematic;
    }
}
