package com.agentconstructor.printcore.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SchematicStore implements SchematicResolver {
    private final Map<String, StoredSchematic> byId = new ConcurrentHashMap<>();
    private final Map<String, StoredSchematic> byName = new ConcurrentHashMap<>();

    public StoredSchematic put(String filename, byte[] bytes) {
        String name = filename == null || filename.isBlank() ? "schematic.schem" : filename;
        String id = UUID.randomUUID().toString();
        StoredSchematic stored = new StoredSchematic(id, name, bytes);
        byId.put(id, stored);
        byName.put(name, stored);
        byName.put(stored.sha256, stored);
        return stored;
    }

    public Optional<StoredSchematic> get(String idOrName) {
        StoredSchematic stored = byId.get(idOrName);
        if (stored == null) {
            stored = byName.get(idOrName);
        }
        return Optional.ofNullable(stored);
    }

    public List<StoredSchematic> list() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public StoredSchematic find(PrintPlan.PasteStep step) {
        if (step.schematicId != null) {
            StoredSchematic stored = byId.get(step.schematicId);
            if (stored != null) {
                return stored;
            }
        }
        if (step.schematic != null) {
            StoredSchematic stored = byName.get(step.schematic);
            if (stored != null) {
                return stored;
            }
        }
        if (step.sha256 != null && !step.sha256.isBlank()) {
            return byName.get(step.sha256);
        }
        return null;
    }
}
