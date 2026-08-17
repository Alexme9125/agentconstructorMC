package com.agentconstructor.printcore.world;

import com.agentconstructor.printcore.api.Volume;
import com.agentconstructor.printcore.job.VolumeSnapshot;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MinecraftWorldAdapter implements WorldEditor, BlockRegistry {
    private final ServerLevel level;

    public MinecraftWorldAdapter(ServerLevel level) {
        this.level = level;
    }

    @Override
    public String dimension() {
        ResourceLocation key = level.dimension().location();
        return key.toString();
    }

    @Override
    public int minY() {
        return level.getMinBuildHeight();
    }

    @Override
    public int maxY() {
        return level.getMaxBuildHeight() - 1;
    }

    @Override
    public String getBlock(int x, int y, int z) {
        if (!inWorldHeight(y)) {
            return "minecraft:void_air";
        }
        return serialize(level.getBlockState(new BlockPos(x, y, z)));
    }

    @Override
    public int getHeight(String type, int x, int z) {
        Heightmap.Types mapped = switch (type) {
            case "WORLD_SURFACE" -> Heightmap.Types.WORLD_SURFACE;
            case "OCEAN_FLOOR" -> Heightmap.Types.OCEAN_FLOOR;
            case "MOTION_BLOCKING" -> Heightmap.Types.MOTION_BLOCKING;
            default -> Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        };
        return level.getHeight(mapped, x, z);
    }

    @Override
    public boolean hasEntityOccupying(Volume volume) {
        AABB box = new AABB(
                volume.minX, volume.minY, volume.minZ,
                volume.maxX + 1, volume.maxY + 1, volume.maxZ + 1
        );
        return !level.getEntities((net.minecraft.world.entity.Entity) null, box, entity -> !(entity instanceof Player)).isEmpty();
    }

    @Override
    public void setBlock(int x, int y, int z, String blockState) {
        if (!inWorldHeight(y)) {
            return;
        }
        level.setBlock(new BlockPos(x, y, z), parse(blockState), 2);
    }

    @Override
    public VolumeSnapshot snapshot(Volume volume) {
        String[] data = new String[(int) volume.blockCount()];
        int i = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = volume.minY; y <= volume.maxY; y++) {
            for (int z = volume.minZ; z <= volume.maxZ; z++) {
                for (int x = volume.minX; x <= volume.maxX; x++) {
                    data[i++] = serialize(level.getBlockState(pos.set(x, y, z)));
                }
            }
        }
        return new VolumeSnapshot(volume, data);
    }

    @Override
    public void restore(VolumeSnapshot snapshot) {
        Volume volume = snapshot.volume;
        int i = 0;
        for (int y = volume.minY; y <= volume.maxY; y++) {
            for (int z = volume.minZ; z <= volume.maxZ; z++) {
                for (int x = volume.minX; x <= volume.maxX; x++) {
                    setBlock(x, y, z, snapshot.blocks[i++]);
                }
            }
        }
    }

    @Override
    public void preload(Volume volume) {
        int minCx = volume.minX >> 4;
        int maxCx = volume.maxX >> 4;
        int minCz = volume.minZ >> 4;
        int maxCz = volume.maxZ >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    @Override
    public boolean isRegistered(String blockId) {
        ResourceLocation location = ResourceLocation.tryParse(BlockRegistry.idOnly(blockId));
        return location != null && ForgeRegistries.BLOCKS.containsKey(location);
    }

    @Override
    public List<String> list(String prefix) {
        String p = prefix == null ? "" : prefix;
        List<String> out = new ArrayList<>();
        for (ResourceLocation key : ForgeRegistries.BLOCKS.getKeys()) {
            String id = key.toString();
            if (id.startsWith(p)) {
                out.add(id);
            }
        }
        return out;
    }

    public static BlockState parse(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("unknown block state " + text, e);
        }
    }

    public static String serialize(BlockState state) {
        StringBuilder builder = new StringBuilder();
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        builder.append(key);
        Map<Property<?>, Comparable<?>> values = state.getValues();
        if (!values.isEmpty()) {
            builder.append('[');
            boolean first = true;
            for (Map.Entry<Property<?>, Comparable<?>> entry : values.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(entry.getKey().getName()).append('=').append(nameValue(entry.getKey(), entry.getValue()));
            }
            builder.append(']');
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String nameValue(Property<T> property, Comparable<?> value) {
        return property.getName((T) value);
    }
}
