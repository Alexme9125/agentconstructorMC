package com.agentconstructor.printcore.api;

import java.util.Objects;

/** Integer block position. Independent of Minecraft's BlockPos. */
public final class Vec3i {
    public final int x;
    public final int y;
    public final int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3i add(int dx, int dy, int dz) {
        return new Vec3i(x + dx, y + dy, z + dz);
    }

    public Vec3i add(Vec3i other) {
        return add(other.x, other.y, other.z);
    }

    /** Rotate this local offset around Y by 0/90/180/270 clockwise, given original size (width=X, length=Z). */
    public Vec3i rotateY(int degrees, int width, int length) {
        int d = ((degrees % 360) + 360) % 360;
        return switch (d) {
            case 0 -> this;
            case 90 -> new Vec3i(z, y, width - 1 - x);
            case 180 -> new Vec3i(width - 1 - x, y, length - 1 - z);
            case 270 -> new Vec3i(length - 1 - z, y, x);
            default -> throw new IllegalArgumentException("rotation must be a multiple of 90, got " + degrees);
        };
    }

    public static int rotatedWidth(int degrees, int width, int length) {
        int d = ((degrees % 360) + 360) % 360;
        return (d == 90 || d == 270) ? length : width;
    }

    public static int rotatedLength(int degrees, int width, int length) {
        int d = ((degrees % 360) + 360) % 360;
        return (d == 90 || d == 270) ? width : length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vec3i other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
