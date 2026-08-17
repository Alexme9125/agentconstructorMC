package com.agentconstructor.printcore.api;

/** Inclusive axis-aligned box. */
public final class Volume {
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    public Volume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static Volume of(Vec3i a, Vec3i b) {
        return new Volume(a.x, a.y, a.z, b.x, b.y, b.z);
    }

    public static Volume fromSize(Vec3i origin, int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        return new Volume(origin.x, origin.y, origin.z,
                origin.x + sizeX - 1, origin.y + sizeY - 1, origin.z + sizeZ - 1);
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long blockCount() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public Volume union(Volume other) {
        return new Volume(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY),
                Math.max(maxZ, other.maxZ)
        );
    }

    public int indexOf(int x, int y, int z) {
        int lx = x - minX;
        int ly = y - minY;
        int lz = z - minZ;
        return (ly * sizeZ() + lz) * sizeX() + lx;
    }

    @Override
    public String toString() {
        return "(" + minX + "," + minY + "," + minZ + ")-(" + maxX + "," + maxY + "," + maxZ + ")";
    }
}
