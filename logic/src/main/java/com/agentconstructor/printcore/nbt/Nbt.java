package com.agentconstructor.printcore.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Minimal NBT codec covering the tags used by Sponge Schematic v2. */
public final class Nbt {
    public static final byte TAG_END = 0;
    public static final byte TAG_BYTE = 1;
    public static final byte TAG_SHORT = 2;
    public static final byte TAG_INT = 3;
    public static final byte TAG_LONG = 4;
    public static final byte TAG_FLOAT = 5;
    public static final byte TAG_DOUBLE = 6;
    public static final byte TAG_BYTE_ARRAY = 7;
    public static final byte TAG_STRING = 8;
    public static final byte TAG_LIST = 9;
    public static final byte TAG_COMPOUND = 10;
    public static final byte TAG_INT_ARRAY = 11;
    public static final byte TAG_LONG_ARRAY = 12;

    private Nbt() {
    }

    public static Compound readGzip(byte[] gzipped) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipped));
             DataInputStream in = new DataInputStream(gzip)) {
            byte type = in.readByte();
            if (type != TAG_COMPOUND) {
                throw new IOException("root NBT must be a compound, got " + type);
            }
            readString(in); // root name
            return readCompound(in);
        }
    }

    public static byte[] writeGzip(Compound root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeByte(TAG_COMPOUND);
            writeString(out, "Schematic");
            writeCompound(out, root);
        }
        return bytes.toByteArray();
    }

    private static Compound readCompound(DataInputStream in) throws IOException {
        Compound compound = new Compound();
        while (true) {
            byte type = in.readByte();
            if (type == TAG_END) {
                return compound;
            }
            String name = readString(in);
            compound.put(name, readPayload(in, type));
        }
    }

    private static Object readPayload(DataInputStream in, byte type) throws IOException {
        return switch (type) {
            case TAG_BYTE -> in.readByte();
            case TAG_SHORT -> in.readShort();
            case TAG_INT -> in.readInt();
            case TAG_LONG -> in.readLong();
            case TAG_FLOAT -> in.readFloat();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_BYTE_ARRAY -> {
                int len = in.readInt();
                byte[] data = new byte[len];
                in.readFully(data);
                yield data;
            }
            case TAG_STRING -> readString(in);
            case TAG_LIST -> {
                byte listType = in.readByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<>(len);
                for (int i = 0; i < len; i++) {
                    list.add(readPayload(in, listType));
                }
                yield new NbtList(listType, list);
            }
            case TAG_COMPOUND -> readCompound(in);
            case TAG_INT_ARRAY -> {
                int len = in.readInt();
                int[] data = new int[len];
                for (int i = 0; i < len; i++) {
                    data[i] = in.readInt();
                }
                yield data;
            }
            case TAG_LONG_ARRAY -> {
                int len = in.readInt();
                long[] data = new long[len];
                for (int i = 0; i < len; i++) {
                    data[i] = in.readLong();
                }
                yield data;
            }
            default -> throw new IOException("unsupported NBT tag " + type);
        };
    }

    private static void writeCompound(DataOutputStream out, Compound compound) throws IOException {
        for (Map.Entry<String, Object> entry : compound.entries.entrySet()) {
            byte type = typeOf(entry.getValue());
            out.writeByte(type);
            writeString(out, entry.getKey());
            writePayload(out, entry.getValue());
        }
        out.writeByte(TAG_END);
    }

    private static void writePayload(DataOutputStream out, Object value) throws IOException {
        if (value instanceof Byte b) {
            out.writeByte(b);
        } else if (value instanceof Short s) {
            out.writeShort(s);
        } else if (value instanceof Integer i) {
            out.writeInt(i);
        } else if (value instanceof Long l) {
            out.writeLong(l);
        } else if (value instanceof Float f) {
            out.writeFloat(f);
        } else if (value instanceof Double d) {
            out.writeDouble(d);
        } else if (value instanceof byte[] bytes) {
            out.writeInt(bytes.length);
            out.write(bytes);
        } else if (value instanceof String s) {
            writeString(out, s);
        } else if (value instanceof NbtList list) {
            out.writeByte(list.type);
            out.writeInt(list.values.size());
            for (Object item : list.values) {
                writePayload(out, item);
            }
        } else if (value instanceof Compound compound) {
            writeCompound(out, compound);
        } else if (value instanceof int[] ints) {
            out.writeInt(ints.length);
            for (int i : ints) {
                out.writeInt(i);
            }
        } else if (value instanceof long[] longs) {
            out.writeInt(longs.length);
            for (long l : longs) {
                out.writeLong(l);
            }
        } else {
            throw new IOException("cannot write NBT payload " + value.getClass());
        }
    }

    private static byte typeOf(Object value) {
        if (value instanceof Byte) {
            return TAG_BYTE;
        }
        if (value instanceof Short) {
            return TAG_SHORT;
        }
        if (value instanceof Integer) {
            return TAG_INT;
        }
        if (value instanceof Long) {
            return TAG_LONG;
        }
        if (value instanceof Float) {
            return TAG_FLOAT;
        }
        if (value instanceof Double) {
            return TAG_DOUBLE;
        }
        if (value instanceof byte[]) {
            return TAG_BYTE_ARRAY;
        }
        if (value instanceof String) {
            return TAG_STRING;
        }
        if (value instanceof NbtList) {
            return TAG_LIST;
        }
        if (value instanceof Compound) {
            return TAG_COMPOUND;
        }
        if (value instanceof int[]) {
            return TAG_INT_ARRAY;
        }
        if (value instanceof long[]) {
            return TAG_LONG_ARRAY;
        }
        throw new IllegalArgumentException("unknown NBT type " + value.getClass());
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] data = new byte[len];
        in.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(data.length);
        out.write(data);
    }

    public static final class Compound {
        public final Map<String, Object> entries = new LinkedHashMap<>();

        public void put(String key, Object value) {
            entries.put(key, value);
        }

        public Object get(String key) {
            return entries.get(key);
        }

        public int getInt(String key, int fallback) {
            Object value = entries.get(key);
            if (value instanceof Number n) {
                return n.intValue();
            }
            return fallback;
        }

        public short getShort(String key, short fallback) {
            Object value = entries.get(key);
            if (value instanceof Number n) {
                return n.shortValue();
            }
            return fallback;
        }

        public String getString(String key) {
            Object value = entries.get(key);
            return value instanceof String s ? s : null;
        }

        public byte[] getBytes(String key) {
            Object value = entries.get(key);
            return value instanceof byte[] b ? b : null;
        }

        public Compound getCompound(String key) {
            Object value = entries.get(key);
            return value instanceof Compound c ? c : null;
        }

        public int[] getInts(String key) {
            Object value = entries.get(key);
            return value instanceof int[] i ? i : null;
        }
    }

    public static final class NbtList {
        public final byte type;
        public final List<Object> values;

        public NbtList(byte type, List<Object> values) {
            this.type = type;
            this.values = values;
        }
    }
}
