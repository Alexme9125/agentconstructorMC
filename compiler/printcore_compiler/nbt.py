from __future__ import annotations

import gzip
import struct
from typing import Any


TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


def write_gzip_compound(compound: dict[str, Any], root_name: str = "Schematic") -> bytes:
    payload = bytearray()
    payload.append(TAG_COMPOUND)
    payload.extend(_write_string(root_name))
    payload.extend(_write_compound_body(compound))
    return gzip.compress(bytes(payload))


def read_gzip_compound(data: bytes) -> dict[str, Any]:
    raw = gzip.decompress(data)
    view = memoryview(raw)
    pos = 0
    tag, pos = _u8(view, pos)
    if tag != TAG_COMPOUND:
        raise ValueError(f"root NBT must be compound, got {tag}")
    _, pos = _read_string(view, pos)
    compound, pos = _read_compound(view, pos)
    return compound


def _write_compound_body(compound: dict[str, Any]) -> bytes:
    out = bytearray()
    for key, value in compound.items():
        tag = _type_of(value)
        out.append(tag)
        out.extend(_write_string(key))
        out.extend(_write_payload(value, tag))
    out.append(TAG_END)
    return bytes(out)


def _write_payload(value: Any, tag: int) -> bytes:
    if tag == TAG_BYTE:
        return struct.pack(">b", int(value))
    if tag == TAG_SHORT:
        return struct.pack(">h", int(value))
    if tag == TAG_INT:
        return struct.pack(">i", int(value))
    if tag == TAG_LONG:
        return struct.pack(">q", int(value))
    if tag == TAG_FLOAT:
        return struct.pack(">f", float(value))
    if tag == TAG_DOUBLE:
        return struct.pack(">d", float(value))
    if tag == TAG_BYTE_ARRAY:
        data = bytes(value)
        return struct.pack(">i", len(data)) + data
    if tag == TAG_STRING:
        return _write_string(str(value))
    if tag == TAG_COMPOUND:
        return _write_compound_body(value)
    if tag == TAG_INT_ARRAY:
        out = struct.pack(">i", len(value))
        out += b"".join(struct.pack(">i", int(v)) for v in value)
        return out
    if tag == TAG_LIST:
        list_type, items = value
        out = bytes([list_type]) + struct.pack(">i", len(items))
        for item in items:
            out += _write_payload(item, list_type)
        return out
    raise TypeError(f"cannot write NBT tag {tag}")


def _type_of(value: Any) -> int:
    if isinstance(value, NbtByte):
        return TAG_BYTE
    if isinstance(value, NbtShort):
        return TAG_SHORT
    if isinstance(value, dict):
        return TAG_COMPOUND
    if isinstance(value, (bytes, bytearray)):
        return TAG_BYTE_ARRAY
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, tuple) and len(value) == 2 and isinstance(value[0], int):
        return TAG_LIST
    if isinstance(value, bool) or isinstance(value, int):
        return TAG_INT
    if isinstance(value, float):
        return TAG_FLOAT
    if isinstance(value, list) and (not value or isinstance(value[0], int)):
        return TAG_INT_ARRAY
    raise TypeError(f"cannot infer NBT type for {type(value)}")


def _write_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def _read_compound(view: memoryview, pos: int) -> tuple[dict[str, Any], int]:
    out: dict[str, Any] = {}
    while True:
        tag, pos = _u8(view, pos)
        if tag == TAG_END:
            return out, pos
        name, pos = _read_string(view, pos)
        value, pos = _read_payload(view, pos, tag)
        out[name] = value


def _read_payload(view: memoryview, pos: int, tag: int) -> tuple[Any, int]:
    if tag == TAG_BYTE:
        return struct.unpack_from(">b", view, pos)[0], pos + 1
    if tag == TAG_SHORT:
        return struct.unpack_from(">h", view, pos)[0], pos + 2
    if tag == TAG_INT:
        return struct.unpack_from(">i", view, pos)[0], pos + 4
    if tag == TAG_LONG:
        return struct.unpack_from(">q", view, pos)[0], pos + 8
    if tag == TAG_FLOAT:
        return struct.unpack_from(">f", view, pos)[0], pos + 4
    if tag == TAG_DOUBLE:
        return struct.unpack_from(">d", view, pos)[0], pos + 8
    if tag == TAG_BYTE_ARRAY:
        length, pos = struct.unpack_from(">i", view, pos)[0], pos + 4
        return bytes(view[pos : pos + length]), pos + length
    if tag == TAG_STRING:
        return _read_string(view, pos)
    if tag == TAG_COMPOUND:
        return _read_compound(view, pos)
    if tag == TAG_LIST:
        list_type, pos = _u8(view, pos)
        length, pos = struct.unpack_from(">i", view, pos)[0], pos + 4
        items = []
        for _ in range(length):
            item, pos = _read_payload(view, pos, list_type)
            items.append(item)
        return (list_type, items), pos
    if tag == TAG_INT_ARRAY:
        length, pos = struct.unpack_from(">i", view, pos)[0], pos + 4
        values = list(struct.unpack_from(">" + "i" * length, view, pos))
        return values, pos + 4 * length
    if tag == TAG_LONG_ARRAY:
        length, pos = struct.unpack_from(">i", view, pos)[0], pos + 4
        values = list(struct.unpack_from(">" + "q" * length, view, pos))
        return values, pos + 8 * length
    raise ValueError(f"unsupported NBT tag {tag}")


def _read_string(view: memoryview, pos: int) -> tuple[str, int]:
    length = struct.unpack_from(">H", view, pos)[0]
    pos += 2
    return bytes(view[pos : pos + length]).decode("utf-8"), pos + length


def _u8(view: memoryview, pos: int) -> tuple[int, int]:
    return view[pos], pos + 1


class NbtByte(int):
    pass


class NbtShort(int):
    pass
