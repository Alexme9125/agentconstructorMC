package com.agentconstructor.printcore.world;

import com.agentconstructor.printcore.api.Volume;
import com.agentconstructor.printcore.job.VolumeSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class InMemoryWorld implements WorldEditor, BlockRegistry {
    public static final String AIR = "minecraft:air";

    private final String dimension;
    private final int minY;
    private final int maxY;
    private final Map<Long, String> blocks = new HashMap<>();
    private final Set<String> registry = new TreeSet<>();

    public InMemoryWorld(String dimension, int minY, int maxY) {
        this.dimension = dimension;
        this.minY = minY;
        this.maxY = maxY;
        registry.add(AIR);
        addVanillaDefaults();
    }

    public static InMemoryWorld overworld() {
        return new InMemoryWorld("minecraft:overworld", -64, 319);
    }

    private void addVanillaDefaults() {
        Collections.addAll(registry,
                "minecraft:air",
                "minecraft:cave_air",
                "minecraft:void_air",
                "minecraft:stone",
                "minecraft:smooth_stone",
                "minecraft:smooth_stone_slab",
                "minecraft:white_stained_glass",
                "minecraft:black_concrete",
                "minecraft:white_concrete",
                "minecraft:yellow_concrete",
                "minecraft:gray_concrete",
                "minecraft:oak_planks",
                "minecraft:oak_log",
                "minecraft:oak_stairs",
                "minecraft:dark_oak_stairs",
                "minecraft:oak_door",
                "minecraft:glass_pane",
                "minecraft:glass",
                "minecraft:lantern",
                "minecraft:torch",
                "minecraft:oak_sign",
                "minecraft:glowstone",
                "minecraft:stone_bricks",
                "minecraft:cobblestone",
                "minecraft:dirt",
                "minecraft:grass_block",
                "minecraft:oak_fence",
                "minecraft:oak_slab",
                "minecraft:stripped_oak_log",
                "minecraft:barrel",
                "minecraft:chest",
                "minecraft:crafting_table",
                "minecraft:light_gray_concrete",
                "minecraft:terracotta",
                "minecraft:white_terracotta"
        );
    }

    public void register(String blockId) {
        registry.add(BlockRegistry.idOnly(blockId));
    }

    @Override
    public boolean isRegistered(String blockId) {
        return registry.contains(BlockRegistry.idOnly(blockId));
    }

    @Override
    public List<String> list(String prefix) {
        String p = prefix == null ? "" : prefix;
        List<String> out = new ArrayList<>();
        for (String id : registry) {
            if (id.startsWith(p)) {
                out.add(id);
            }
        }
        return out;
    }

    @Override
    public String dimension() {
        return dimension;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public String getBlock(int x, int y, int z) {
        if (!inWorldHeight(y)) {
            return "minecraft:void_air";
        }
        return blocks.getOrDefault(pack(x, y, z), AIR);
    }

    @Override
    public int getHeight(String type, int x, int z) {
        for (int y = maxY; y >= minY; y--) {
            String block = getBlock(x, y, z);
            if (!isAir(block)) {
                return y + 1;
            }
        }
        return minY;
    }

    @Override
    public void setBlock(int x, int y, int z, String blockState) {
        if (!inWorldHeight(y)) {
            return;
        }
        if (isAir(blockState)) {
            blocks.remove(pack(x, y, z));
        } else {
            blocks.put(pack(x, y, z), blockState);
        }
    }

    @Override
    public VolumeSnapshot snapshot(Volume volume) {
        String[] data = new String[(int) volume.blockCount()];
        int i = 0;
        for (int y = volume.minY; y <= volume.maxY; y++) {
            for (int z = volume.minZ; z <= volume.maxZ; z++) {
                for (int x = volume.minX; x <= volume.maxX; x++) {
                    data[i++] = getBlock(x, y, z);
                }
            }
        }
        return new VolumeSnapshot(volume, data);
    }

    @Override
    public void restore(VolumeSnapshot snapshot) {
        Volume volume = snapshot.volume;
        String[] data = snapshot.blocks;
        int i = 0;
        for (int y = volume.minY; y <= volume.maxY; y++) {
            for (int z = volume.minZ; z <= volume.maxZ; z++) {
                for (int x = volume.minX; x <= volume.maxX; x++) {
                    setBlock(x, y, z, data[i++]);
                }
            }
        }
    }

    public int occupiedBlocks() {
        return blocks.size();
    }

    public static boolean isAir(String blockState) {
        String id = BlockRegistry.idOnly(blockState);
        return "minecraft:air".equals(id)
                || "minecraft:cave_air".equals(id)
                || "minecraft:void_air".equals(id);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
