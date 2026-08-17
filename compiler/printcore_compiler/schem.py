from __future__ import annotations

import hashlib
import re

from . import nbt

VERSION = 2
DATA_VERSION_1_20_1 = 3465
FACINGS = ("north", "east", "south", "west")


def index(x: int, y: int, z: int, width: int, length: int) -> int:
    return (y * length + z) * width + x


class Schematic:
    def __init__(self, width: int, height: int, length: int, blocks: list[str], data_version: int = DATA_VERSION_1_20_1):
        expected = width * height * length
        if len(blocks) != expected:
            raise ValueError(f"block array size {len(blocks)} != {expected}")
        self.width = width
        self.height = height
        self.length = length
        self.blocks = blocks
        self.data_version = data_version

    @classmethod
    def filled(cls, width: int, height: int, length: int, block: str = "minecraft:air") -> "Schematic":
        return cls(width, height, length, [block] * (width * height * length))

    def get(self, x: int, y: int, z: int) -> str:
        return self.blocks[index(x, y, z, self.width, self.length)]

    def set(self, x: int, y: int, z: int, block: str) -> None:
        self.blocks[index(x, y, z, self.width, self.length)] = block

    def fill(self, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, block: str, hollow: bool = False) -> None:
        xa, xb = sorted((x1, x2))
        ya, yb = sorted((y1, y2))
        za, zb = sorted((z1, z2))
        for y in range(ya, yb + 1):
            for z in range(za, zb + 1):
                for x in range(xa, xb + 1):
                    if hollow and x not in (xa, xb) and y not in (ya, yb) and z not in (za, zb):
                        continue
                    if 0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length:
                        self.set(x, y, z, block)

    def unique_blocks(self) -> list[str]:
        seen: list[str] = []
        for block in self.blocks:
            if block not in seen:
                seen.append(block)
        return seen

    def to_bytes(self) -> bytes:
        palette: dict[str, int] = {"minecraft:air": 0}
        indices = []
        for block in self.blocks:
            state = block or "minecraft:air"
            if state not in palette:
                palette[state] = len(palette)
            indices.append(palette[state])
        compound = {
            "Version": VERSION,
            "DataVersion": self.data_version,
            "Width": nbt.NbtShort(self.width),
            "Height": nbt.NbtShort(self.height),
            "Length": nbt.NbtShort(self.length),
            "PaletteMax": len(palette),
            "Palette": {name: idx for name, idx in palette.items()},
            "BlockData": encode_varints(indices),
        }
        return nbt.write_gzip_compound(compound)

    @classmethod
    def from_bytes(cls, data: bytes) -> "Schematic":
        root = nbt.read_gzip_compound(data)
        if int(root["Version"]) != VERSION:
            raise ValueError(f"unsupported schematic version {root['Version']}")
        width = int(root["Width"])
        height = int(root["Height"])
        length = int(root["Length"])
        palette = {int(v): k for k, v in root["Palette"].items()}
        expected = width * height * length
        indices = decode_varints(root["BlockData"], expected)
        blocks = [palette.get(i, "minecraft:air") for i in indices]
        return cls(width, height, length, blocks, int(root.get("DataVersion", DATA_VERSION_1_20_1)))

    def rotate_y(self, degrees: int) -> "Schematic":
        d = ((degrees % 360) + 360) % 360
        if d == 0:
            return self
        new_width = self.length if d in (90, 270) else self.width
        new_length = self.width if d in (90, 270) else self.length
        out = Schematic.filled(new_width, self.height, new_length)
        for y in range(self.height):
            for z in range(self.length):
                for x in range(self.width):
                    nx, ny, nz = rotate_offset(x, y, z, d, self.width, self.length)
                    out.set(nx, ny, nz, rotate_state(self.get(x, y, z), d))
        return out


def rotate_offset(x: int, y: int, z: int, degrees: int, width: int, length: int) -> tuple[int, int, int]:
    d = ((degrees % 360) + 360) % 360
    if d == 0:
        return x, y, z
    if d == 90:
        return z, y, width - 1 - x
    if d == 180:
        return width - 1 - x, y, length - 1 - z
    if d == 270:
        return length - 1 - z, y, x
    raise ValueError("rotation must be a multiple of 90")


def rotate_state(state: str, degrees: int) -> str:
    d = ((degrees % 360) + 360) % 360
    if d == 0 or "[" not in state:
        return state
    steps = d // 90
    match = re.search(r"facing=(north|south|east|west)", state)
    if match:
        nxt = FACINGS[(FACINGS.index(match.group(1)) + steps) % 4]
        state = state.replace(f"facing={match.group(1)}", f"facing={nxt}")
    axis = re.search(r"axis=(x|z)", state)
    if axis and steps % 2 == 1:
        nxt = "z" if axis.group(1) == "x" else "x"
        state = state.replace(f"axis={axis.group(1)}", f"axis={nxt}")
    return state


def encode_varints(values: list[int]) -> bytes:
    out = bytearray()
    for value in values:
        v = value
        while v & ~0x7F:
            out.append((v & 0x7F) | 0x80)
            v >>= 7
        out.append(v)
    return bytes(out)


def decode_varints(data: bytes, count: int) -> list[int]:
    out = []
    i = 0
    for _ in range(count):
        value = 0
        shift = 0
        while True:
            b = data[i]
            i += 1
            value |= (b & 0x7F) << shift
            if b & 0x80 == 0:
                break
            shift += 7
        out.append(value)
    return out


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()
