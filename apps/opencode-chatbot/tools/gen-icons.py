#!/usr/bin/env python3
"""Generate OpenCode Chatbot legacy launcher icons (raw PNG, stdlib only).

Design: solid blue tile, white rounded chat bubble with a small tail, three
indigo dots — the same symbol as drawable/ic_launcher_foreground.xml.
Supersampled 2x then box-downsampled for soft edges.
"""
import struct
import zlib
import os
import sys

BLUE = (21, 101, 192, 255)       # #1565C0 tile
WHITE = (255, 255, 255, 255)
INDIGO = (76, 110, 245, 255)     # #4C6EF5 dots

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "src", "main", "res", "mipmap-{}", "ic_launcher.png")


def png_chunk(tag, data):
    return (struct.pack(">I", len(data)) + tag + data +
            struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def write_png(path, size, px):
    raw = b"".join(b"\x00" + bytes(px[y * size * 4:(y + 1) * size * 4])
                   for y in range(size))
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(png_chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)))
        f.write(png_chunk(b"IDAT", zlib.compress(raw, 9)))
        f.write(png_chunk(b"IEND", b""))


def rounded_rect_contains(x, y, left, top, right, bottom, r):
    """True when (x,y) is inside a rounded rectangle."""
    if x < left or x > right or y < top or y > bottom:
        return False
    cx = min(max(x, left + r), right - r)
    cy = min(max(y, top + r), bottom - r)
    dx = x - cx
    dy = y - cy
    return (dx * dx + dy * dy) <= r * r


def in_dot(x, y, cx, cy, r):
    dx = x - cx
    dy = y - cy
    return (dx * dx + dy * dy) <= r * r


def sample(x, y):
    """Return color at a point in the design's coordinate space (S=size)."""
    S = 2.0
    # scaled coordinates where the design sits in the central ~66% square
    left, top, right, bottom = 0.18 * S, 0.16 * S, 0.82 * S, 0.80 * S
    bubble_r = 0.07 * S

    # legacy icon is a full-bleed tile: entire canvas = blue rounded square
    tile_r = 0.20 * S
    inset = 0.02 * S
    if not rounded_rect_contains(x, y, inset, inset, S - inset, S - inset, tile_r):
        return (0, 0, 0, 0)

    # white chat bubble
    if rounded_rect_contains(x, y, left, top, right, bottom, bubble_r):
        # tail triangle at bottom-left of bubble
        if (x >= left - 0.06 * S and x <= left + 0.05 * S
                and y >= bottom and y <= bottom + 0.09 * S):
            # triangle from (left, bottom) widening down-left
            tx = (x - (left - 0.06 * S)) / (0.11 * S)
            ty = (y - bottom) / (0.09 * S)
            if ty >= 1 - tx:
                return INDIGO if in_dot(x, y, 0.44 * S, 0.52 * S, 0.05 * S) else WHITE
            return BLUE
        # three dots
        for cx, cy in ((0.405 * S, 0.485 * S), (0.50 * S, 0.485 * S), (0.595 * S, 0.485 * S)):
            if in_dot(x, y, cx, cy, 0.035 * S):
                return INDIGO
        return WHITE

    return BLUE


def make_png(size):
    S = size * 2  # supersample
    raw = bytearray(S * S * 4)
    for y in range(S):
        yy = y + 0.5
        for x in range(S):
            xx = x + 0.5
            col = sample(xx / S, yy / S)
            o = (y * S + x) * 4
            raw[o:o + 4] = bytes(col)

    final = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            s = y * 2 * S + x * 2
            o = (y * size + x) * 4
            for c in range(4):
                v = 0
                for dy in (0, 1):
                    for dx in (0, 1):
                        v += raw[(s + dy * S + dx) * 4 + c]
                final[o + c] = v // 4
    return bytes(final)


def main():
    base = OUT_DIR
    for name, size in DENSITIES.items():
        out = base.format(name)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        write_png(out, size, make_png(size))
        print("wrote {} ({}px, {} KiB)".format(out, size, os.path.getsize(out) // 1024))


if __name__ == "__main__":
    main()