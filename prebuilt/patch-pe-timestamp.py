#!/usr/bin/env python3
"""Stamp a fixed PE timestamp into a Windows DLL.

GNU ld zeroes the timestamp (``--no-insert-timestamp``) and lld writes a value
derived from the output hash, so neither toolchain can be told "use this one
value". Both are fixed up here after the link instead, which also keeps the two
Windows architectures consistent with each other.

The value comes from ``SOURCE_DATE_EPOCH`` (the reproducible-builds convention)
and falls back to the timestamp of the HEAD commit. There is deliberately no
"use the current time" fallback: that would silently make the artifact
non-reproducible and, because SAC/Defender reputation is keyed on the file hash,
start every build's reputation from zero.

Usage:
    patch-pe-timestamp.py <dll> [<dll> ...]

Exit status is non-zero when a file cannot be parsed or no timestamp is available.
"""

import os
import struct
import subprocess
import sys

IMAGE_DIRECTORY_ENTRY_EXPORT = 0

PE32_MAGIC = 0x10B
PE32_PLUS_MAGIC = 0x20B


def fail(message):
    sys.exit("patch-pe-timestamp: " + message)


def u16(data, offset):
    return struct.unpack_from("<H", data, offset)[0]


def u32(data, offset):
    return struct.unpack_from("<I", data, offset)[0]


def write_u32(data, offset, value):
    struct.pack_into("<I", data, offset, value)


def resolve_timestamp():
    raw = os.environ.get("SOURCE_DATE_EPOCH", "").strip()
    if raw:
        try:
            return int(raw, 10)
        except ValueError:
            fail("SOURCE_DATE_EPOCH is not an integer: " + repr(raw))

    here = os.path.dirname(os.path.abspath(__file__))
    try:
        out = subprocess.check_output(
            ["git", "-C", here, "log", "-1", "--format=%ct"],
            stderr=subprocess.DEVNULL,
        ).decode().strip()
    except Exception:
        out = ""
    if out.isdigit():
        return int(out)

    fail("no timestamp available: set SOURCE_DATE_EPOCH, or run inside a git checkout")


def rva_to_offset(data, pe_offset, size_of_optional_header, rva):
    sections = pe_offset + 24 + size_of_optional_header
    count = u16(data, pe_offset + 6)
    for index in range(count):
        base = sections + index * 40
        virtual_size = u32(data, base + 8)
        virtual_address = u32(data, base + 12)
        raw_size = u32(data, base + 16)
        raw_pointer = u32(data, base + 20)
        span = max(virtual_size, raw_size)
        if virtual_address <= rva < virtual_address + span:
            return rva - virtual_address + raw_pointer
    return None


def compute_checksum(data, checksum_offset):
    total = 0
    length = len(data)
    for offset in range(0, length - 1, 2):
        if offset == checksum_offset or offset == checksum_offset + 2:
            total += 0
        else:
            total += data[offset] | (data[offset + 1] << 8)
        total = (total & 0xFFFF) + (total >> 16)
    if length % 2:
        total += data[-1]
        total = (total & 0xFFFF) + (total >> 16)
    total = (total & 0xFFFF) + (total >> 16)
    return (total + length) & 0xFFFFFFFF


def patch(path, timestamp):
    try:
        with open(path, "rb") as handle:
            original = handle.read()
    except OSError as error:
        fail("cannot read " + path + ": " + str(error))

    if len(original) < 0x40 or original[:2] != b"MZ":
        fail(path + ": not a PE image (no MZ header)")

    data = bytearray(original)
    pe_offset = u32(data, 0x3C)
    if data[pe_offset:pe_offset + 4] != b"PE\0\0":
        fail(path + ": not a PE image (no PE signature)")

    coff_offset = pe_offset + 4
    optional_offset = coff_offset + 20
    size_of_optional_header = u16(data, coff_offset + 16)
    magic = u16(data, optional_offset)

    if magic == PE32_MAGIC:
        data_directory_offset = optional_offset + 96
        check_sum_offset = optional_offset + 64
    elif magic == PE32_PLUS_MAGIC:
        data_directory_offset = optional_offset + 112
        check_sum_offset = optional_offset + 64
    else:
        fail(path + ": unknown optional header magic 0x%x" % magic)

    coff_timestamp_offset = coff_offset + 4
    fields = [coff_timestamp_offset]

    export_rva = u32(data, data_directory_offset + IMAGE_DIRECTORY_ENTRY_EXPORT * 8)
    if export_rva:
        export_offset = rva_to_offset(data, pe_offset, size_of_optional_header, export_rva)
        if export_offset is not None:
            fields.append(export_offset + 4)

    if all(u32(data, offset) == timestamp for offset in fields):
        print("unchanged " + path)
        return

    for offset in fields:
        write_u32(data, offset, timestamp)

    original_checksum = u32(data, check_sum_offset)
    if original_checksum:
        write_u32(data, check_sum_offset, compute_checksum(data, check_sum_offset))

    with open(path, "wb") as handle:
        handle.write(data)
    print("patched %s: 0x%08x -> 0x%08x%s" % (
        path, u32(original, coff_timestamp_offset), timestamp,
        "" if len(fields) > 1 else " (no export directory)"))


def main(argv):
    if len(argv) < 2:
        fail("usage: patch-pe-timestamp.py <dll> [<dll> ...]")
    timestamp = resolve_timestamp()
    for path in argv[1:]:
        patch(path, timestamp)


if __name__ == "__main__":
    main(sys.argv)
