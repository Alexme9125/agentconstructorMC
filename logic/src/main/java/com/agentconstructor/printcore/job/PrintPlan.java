package com.agentconstructor.printcore.job;

import com.agentconstructor.printcore.api.PrintConfig;
import com.agentconstructor.printcore.api.Vec3i;
import com.agentconstructor.printcore.api.Volume;
import com.agentconstructor.printcore.schem.SpongeSchematic;
import com.agentconstructor.printcore.world.BlockRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PrintPlan {
    public final int version;
    public final String id;
    public final String dimension;
    public final List<Step> steps;

    public PrintPlan(int version, String id, String dimension, List<Step> steps) {
        this.version = version;
        this.id = id;
        this.dimension = dimension;
        this.steps = List.copyOf(steps);
    }

    public static PrintPlan fromJson(JsonObject json) {
        int version = json.has("version") ? json.get("version").getAsInt() : 1;
        if (version != 1) {
            throw new IllegalArgumentException("unsupported print plan version " + version);
        }
        String id = json.get("id").getAsString();
        String dimension = json.has("dimension")
                ? json.get("dimension").getAsString()
                : "minecraft:overworld";
        JsonArray stepsJson = json.getAsJsonArray("steps");
        if (stepsJson == null || stepsJson.isEmpty()) {
            throw new IllegalArgumentException("print plan has no steps");
        }
        List<Step> steps = new ArrayList<>();
        for (JsonElement element : stepsJson) {
            steps.add(Step.fromJson(element.getAsJsonObject()));
        }
        return new PrintPlan(version, id, dimension, steps);
    }

    public Volume bounds() {
        Volume union = null;
        for (Step step : steps) {
            Volume volume = step.volume();
            union = union == null ? volume : union.union(volume);
        }
        return union;
    }

    public sealed interface Step {
        String id();

        String type();

        Volume volume();

        void collectBlocks(List<String> out);

        static Step fromJson(JsonObject json) {
            String type = json.get("type").getAsString();
            return switch (type) {
                case "fill" -> FillStep.fromJson(json);
                case "terrain" -> TerrainStep.fromJson(json);
                case "sparse" -> SparseStep.fromJson(json);
                case "paste_schem" -> PasteStep.fromJson(json);
                default -> throw new IllegalArgumentException("unknown step type " + type);
            };
        }
    }

    public static final class FillStep implements Step {
        public final String id;
        public final Vec3i from;
        public final Vec3i to;
        public final String block;

        public FillStep(String id, Vec3i from, Vec3i to, String block) {
            this.id = id;
            this.from = from;
            this.to = to;
            this.block = block;
        }

        static FillStep fromJson(JsonObject json) {
            return new FillStep(
                    json.get("id").getAsString(),
                    vec(json.getAsJsonObject("from")),
                    vec(json.getAsJsonObject("to")),
                    json.get("block").getAsString()
            );
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String type() {
            return "fill";
        }

        @Override
        public Volume volume() {
            return Volume.of(from, to);
        }

        @Override
        public void collectBlocks(List<String> out) {
            out.add(block);
        }
    }

    public static final class TerrainLayer {
        public final int y;
        public final String block;

        public TerrainLayer(int y, String block) {
            this.y = y;
            this.block = block;
        }
    }

    public static final class TerrainStep implements Step {
        public final String id;
        public final int minX;
        public final int minZ;
        public final int maxX;
        public final int maxZ;
        public final List<TerrainLayer> layers;

        public TerrainStep(String id, int minX, int minZ, int maxX, int maxZ, List<TerrainLayer> layers) {
            this.id = id;
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
            this.layers = List.copyOf(layers);
        }

        static TerrainStep fromJson(JsonObject json) {
            JsonObject from = json.getAsJsonObject("from");
            JsonObject to = json.getAsJsonObject("to");
            List<TerrainLayer> layers = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("layers")) {
                JsonObject layer = element.getAsJsonObject();
                layers.add(new TerrainLayer(layer.get("y").getAsInt(), layer.get("block").getAsString()));
            }
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("terrain step has no layers");
            }
            return new TerrainStep(
                    json.get("id").getAsString(),
                    from.get("x").getAsInt(),
                    from.get("z").getAsInt(),
                    to.get("x").getAsInt(),
                    to.get("z").getAsInt(),
                    layers
            );
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String type() {
            return "terrain";
        }

        @Override
        public Volume volume() {
            int minY = layers.get(0).y;
            int maxY = layers.get(0).y;
            for (TerrainLayer layer : layers) {
                minY = Math.min(minY, layer.y);
                maxY = Math.max(maxY, layer.y);
            }
            return new Volume(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public void collectBlocks(List<String> out) {
            for (TerrainLayer layer : layers) {
                out.add(layer.block);
            }
        }
    }

    public static final class SparseBlock {
        public final int x;
        public final int y;
        public final int z;
        public final String block;

        public SparseBlock(int x, int y, int z, String block) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }
    }

    public static final class SparseStep implements Step {
        public final String id;
        public final List<SparseBlock> blocks;

        public SparseStep(String id, List<SparseBlock> blocks) {
            this.id = id;
            this.blocks = List.copyOf(blocks);
        }

        static SparseStep fromJson(JsonObject json) {
            List<SparseBlock> blocks = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("blocks")) {
                JsonObject b = element.getAsJsonObject();
                blocks.add(new SparseBlock(
                        b.get("x").getAsInt(),
                        b.get("y").getAsInt(),
                        b.get("z").getAsInt(),
                        b.get("block").getAsString()
                ));
            }
            return new SparseStep(json.get("id").getAsString(), blocks);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String type() {
            return "sparse";
        }

        @Override
        public Volume volume() {
            if (blocks.isEmpty()) {
                return new Volume(0, 0, 0, 0, 0, 0);
            }
            SparseBlock first = blocks.get(0);
            Volume volume = new Volume(first.x, first.y, first.z, first.x, first.y, first.z);
            for (SparseBlock block : blocks) {
                volume = volume.union(new Volume(block.x, block.y, block.z, block.x, block.y, block.z));
            }
            return volume;
        }

        @Override
        public void collectBlocks(List<String> out) {
            for (SparseBlock block : blocks) {
                out.add(block.block);
            }
        }
    }

    public static final class PasteStep implements Step {
        public final String id;
        public final String schematicId;
        public final String schematic;
        public final String sha256;
        public final Vec3i origin;
        public final int rotation;
        public final boolean ignoreAir;

        public PasteStep(
                String id,
                String schematicId,
                String schematic,
                String sha256,
                Vec3i origin,
                int rotation,
                boolean ignoreAir
        ) {
            this.id = id;
            this.schematicId = schematicId;
            this.schematic = schematic;
            this.sha256 = sha256 == null ? "" : sha256.toLowerCase(Locale.ROOT);
            this.origin = origin;
            this.rotation = rotation;
            this.ignoreAir = ignoreAir;
        }

        static PasteStep fromJson(JsonObject json) {
            int rotation = json.has("rotation") ? json.get("rotation").getAsInt() : 0;
            boolean ignoreAir = json.has("ignoreAir") && json.get("ignoreAir").getAsBoolean();
            String schematicId = json.has("schematicId") ? json.get("schematicId").getAsString() : null;
            String schematic = json.has("schematic") ? json.get("schematic").getAsString() : null;
            String sha = json.has("sha256") ? json.get("sha256").getAsString() : "";
            return new PasteStep(
                    json.get("id").getAsString(),
                    schematicId,
                    schematic,
                    sha,
                    vec(json.getAsJsonObject("origin")),
                    rotation,
                    ignoreAir
            );
        }

        public Volume volumeFor(SpongeSchematic schem) {
            SpongeSchematic rotated = schem.rotateY(rotation);
            return Volume.fromSize(origin, rotated.width, rotated.height, rotated.length);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String type() {
            return "paste_schem";
        }

        @Override
        public Volume volume() {
            throw new IllegalStateException("paste volume requires the schematic");
        }

        @Override
        public void collectBlocks(List<String> out) {
        }
    }

    static Vec3i vec(JsonObject json) {
        return new Vec3i(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
    }

    public List<String> unknownBlocks(BlockRegistry registry, SchematicResolver schematics) {
        List<String> unknown = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (Step step : steps) {
            step.collectBlocks(found);
            if (step instanceof PasteStep paste) {
                SpongeSchematic schem = schematics.require(paste);
                found.addAll(schem.uniqueBlocks());
            }
        }
        for (String block : found) {
            if (!registry.isRegistered(block) && !unknown.contains(block)) {
                unknown.add(block);
            }
        }
        return unknown;
    }

    public Volume resolvedBounds(SchematicResolver schematics) {
        Volume union = null;
        for (Step step : steps) {
            Volume volume = step instanceof PasteStep paste
                    ? paste.volumeFor(schematics.require(paste))
                    : step.volume();
            union = union == null ? volume : union.union(volume);
        }
        return union;
    }

    public List<String> validate(PrintConfig config, BlockRegistry registry, SchematicResolver schematics) {
        List<String> errors = new ArrayList<>();
        if (!config.dimensionAllowed(dimension)) {
            errors.add("dimension not allowed: " + dimension);
        }
        Volume bounds;
        try {
            bounds = resolvedBounds(schematics);
        } catch (RuntimeException e) {
            errors.add(e.getMessage());
            return errors;
        }
        if (bounds.sizeX() > config.maxModuleSize || bounds.sizeZ() > config.maxModuleSize) {
            errors.add("module exceeds " + config.maxModuleSize + " on X/Z: " + bounds.sizeX() + "x" + bounds.sizeZ());
        }
        if (bounds.blockCount() > config.maxVolume) {
            errors.add("volume " + bounds.blockCount() + " exceeds maxVolume " + config.maxVolume);
        }
        if (!config.allowOutOfBounds) {
            if (bounds.minY < config.minY || bounds.maxY > config.maxY) {
                errors.add("volume " + bounds + " outside world height");
            }
        }
        for (String block : unknownBlocks(registry, schematics)) {
            errors.add("unknown block: " + block);
        }
        for (Step step : steps) {
            if (step instanceof SparseStep sparse && sparse.blocks.size() > config.maxSparseBlocks) {
                errors.add("sparse step " + sparse.id + " has " + sparse.blocks.size() + " blocks");
            }
            if (step instanceof PasteStep paste) {
                if (paste.rotation % 90 != 0) {
                    errors.add("rotation must be a multiple of 90: " + paste.rotation);
                }
                StoredSchematic stored = schematics.find(paste);
                if (stored != null && !paste.sha256.isEmpty() && !paste.sha256.equals(stored.sha256)) {
                    errors.add("schematic hash mismatch for " + paste.id);
                }
            }
        }
        return errors;
    }
}
