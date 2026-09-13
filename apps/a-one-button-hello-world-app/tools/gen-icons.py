#!/usr/bin/env python3
"""Generate One-Button Hello World launcher icons (legacy PNG densities).

Design: violet rounded tile, white ring, amber centre dot — the press
target from the adaptive foreground (drawable/ic_launcher_foreground.xml).
Renders at 2x and box-downsamples for soft edges. Stdlib only.
"""
import struct, zlib, os

VIOLET = (106, 27, 154, 255)     # #6A1B9A
WHITE = (255, 255, 255, 255)
AMBER = (255, 179, 0, 255)       # #FFB300

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "mipmap-{}", "ic_launcher.png")


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


def make_png(size):
    S = size * 2
    corner = S * 0.20
    inset = S * 0.06
    cx = cy = S / 2.0
    tile = S - 2 * inset
    ring_outer = S * 0.265
    ring_inner = S * 0.135
    dot_r = S * 0.070

    raw = bytearray(S * S * 4)
    for y in range(S):
        for x in range(S):
            rx = min(max(x - inset, 0), tile)
            ry = min(max(y - inset, 0), tile)
            dx = x - inset - rx
            dy = y - inset - ry
            inside = (dx * dx + dy * dy) <= corner * corner if (rx in (0, tile) and ry in (0, tile)) else True
            dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5

            if not inside:
                col = (0, 0, 0, 0)
            elif ring_inner <= dist <= ring_outer:
                col = WHITE
            elif dist < dot_r:
                col = AMBER
            else:
                col = VIOLET

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
    for name, size in DENSITIES.items():
        out = OUT_DIR.format(name)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        write_png(out, size, make_png(size))
        print(f"wrote {out} ({size}px, {os.path.getsize(out) // 1024} KiB)")


if __name__ == "__main__":
    main()