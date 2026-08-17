package com.agentconstructor.printcore.schem;

import com.agentconstructor.printcore.api.Vec3i;
import com.agentconstructor.printcore.nbt.Nbt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sponge Schematic v2. BlockData index = (y * length + z) * width + x.
 * DataVersion 3465 is Minecraft 1.20.1.
 */
public final class SpongeSchematic {
    public static final int VERSION = 2;
    public static final int DATA_VERSION_1_20_1 = 3465;

    public final int width;
    public final int height;
    public final int length;
    public final int dataVersion;
    public final String[] blocks;

    public SpongeSchematic(int width, int height, int length, int dataVersion, String[] blocks) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.dataVersion = dataVersion;
        this.blocks = blocks;
        if (blocks.length != width * height * length) {
            throw new IllegalArgumentException("block array size mismatch");
        }
    }

    public static SpongeSchematic create(int width, int height, int length, String[] blocks) {
        return new SpongeSchematic(width, height, length, DATA_VERSION_1_20_1, blocks);
    }

    public static int index(int x, int y, int z, int width, int length) {
        return (y * length + z) * width + x;
    }

    public String get(int x, int y, int z) {
        return blocks[index(x, y, z, width, length)];
    }

    public byte[] toBytes() {
        try {
            Map<String, Integer> palette = new LinkedHashMap<>();
            palette.put("minecraft:air", 0);
            int[] indices = new int[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                String state = blocks[i] == null || blocks[i].isBlank() ? "minecraft:air" : blocks[i];
                Integer id = palette.get(state);
                if (id == null) {
                    id = palette.size();
                    palette.put(state, id);
                }
                indices[i] = id;
            }
            Nbt.Compound root = new Nbt.Compound();
            root.put("Version", VERSION);
            root.put("DataVersion", dataVersion);
            root.put("Width", (short) width);
            root.put("Height", (short) height);
            root.put("Length", (short) length);
            root.put("PaletteMax", palette.size());
            Nbt.Compound paletteTag = new Nbt.Compound();
            for (Map.Entry<String, Integer> entry : palette.entrySet()) {
                paletteTag.put(entry.getKey(), entry.getValue());
            }
            root.put("Palette", paletteTag);
            root.put("BlockData", encodeVarInts(indices));
            return Nbt.writeGzip(root);
        } catch (IOException e) {
            throw new IllegalStateException("failed to encode schematic", e);
        }
    }

    public static SpongeSchematic fromBytes(byte[] gzipped) {
        try {
            Nbt.Compound root = Nbt.readGzip(gzipped);
            int version = root.getInt("Version", -1);
            if (version != VERSION) {
                throw new IllegalArgumentException("unsupported schematic version " + version);
            }
            int width = root.getShort("Width", (short) 0) & 0xFFFF;
            int height = root.getShort("Height", (short) 0) & 0xFFFF;
            int length = root.getShort("Length", (short) 0) & 0xFFFF;
            int dataVersion = root.getInt("DataVersion", DATA_VERSION_1_20_1);
            Nbt.Compound paletteTag = root.getCompound("Palette");
            if (paletteTag == null) {
                throw new IllegalArgumentException("schematic missing Palette");
            }
            Map<Integer, String> palette = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : paletteTag.entries.entrySet()) {
                palette.put(((Number) entry.getValue()).intValue(), entry.getKey());
            }
            byte[] blockData = root.getBytes("BlockData");
            if (blockData == null) {
                throw new IllegalArgumentException("schematic missing BlockData");
            }
            int expected = width * height * length;
            int[] indices = decodeVarInts(blockData, expected);
            String[] blocks = new String[expected];
            for (int i = 0; i < expected; i++) {
                String state = palette.get(indices[i]);
                blocks[i] = state == null ? "minecraft:air" : state;
            }
            return new SpongeSchematic(width, height, length, dataVersion, blocks);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid schematic", e);
        }
    }

    public SpongeSchematic rotateY(int degrees) {
        int d = ((degrees % 360) + 360) % 360;
        if (d == 0) {
            return this;
        }
        int newWidth = Vec3i.rotatedWidth(d, width, length);
        int newLength = Vec3i.rotatedLength(d, width, length);
        String[] next = new String[newWidth * height * newLength];
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    Vec3i rotated = new Vec3i(x, y, z).rotateY(d, width, length);
                    String state = rotateBlockState(get(x, y, z), d);
                    next[index(rotated.x, rotated.y, rotated.z, newWidth, newLength)] = state;
                }
            }
        }
        return new SpongeSchematic(newWidth, height, newLength, dataVersion, next);
    }

    public List<String> uniqueBlocks() {
        List<String> out = new ArrayList<>();
        for (String block : blocks) {
            if (block != null && !out.contains(block)) {
                out.add(block);
            }
        }
        return out;
    }

    public static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String rotateBlockState(String state, int degrees) {
        int d = ((degrees % 360) + 360) % 360;
        if (d == 0 || !state.contains("[")) {
            return state;
        }
        int steps = d / 90;
        Matcher facing = Pattern.compile("facing=(north|south|east|west)").matcher(state);
        String out = state;
        if (facing.find()) {
            String next = rotateFacing(facing.group(1), steps);
            out = out.replace("facing=" + facing.group(1), "facing=" + next);
        }
        Matcher axis = Pattern.compile("axis=(x|z)").matcher(out);
        if (axis.find() && steps % 2 == 1) {
            String next = "x".equals(axis.group(1)) ? "z" : "x";
            out = out.replace("axis=" + axis.group(1), "axis=" + next);
        }
        return out;
    }

    private static String rotateFacing(String facing, int steps) {
        String[] order = {"north", "east", "south", "west"};
        int idx = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(facing)) {
                idx = i;
                break;
            }
        }
        return order[(idx + steps) % 4];
    }

    static byte[] encodeVarInts(int[] values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int value : values) {
            int v = value;
            while ((v & ~0x7F) != 0) {
                out.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            out.write(v);
        }
        return out.toByteArray();
    }

    static int[] decodeVarInts(byte[] data, int count) {
        int[] out = new int[count];
        int i = 0;
        int index = 0;
        while (index < count) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (i >= data.length) {
                    throw new IllegalArgumentException("truncated BlockData");
                }
                int b = data[i++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }
            out[index++] = value;
        }
        return out;
    }

    public static String utf8(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}
