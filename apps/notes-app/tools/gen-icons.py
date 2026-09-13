#!/usr/bin/env python3
"""Generate Notes launcher icons (legacy PNG densities) with stdlib only.

Design: indigo rounded tile, a white "note page" (rounded rect) with three
indigo text lines — matches the adaptive foreground in
drawable/ic_launcher_foreground.xml. Renders at 2x and box-downsamples for soft
edges.
"""
import struct, zlib, os, sys

INDIGO = (76, 110, 245, 255)      # #4C6EF5 tile
WHITE = (255, 255, 255, 255)      # page
LINE = (61, 86, 201, 255)         # #3D56C9 text lines

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


def inside_rounded_rect(x, y, x0, y0, x1, y1, r):
    rx = min(max(x, x0), x1)
    ry = min(max(y, y0), y1)
    dx = x - rx
    dy = y - ry
    return dx * dx + dy * dy <= r * r


def make_png(size):
    S = size * 2                       # supersampled canvas
    inset = S * 0.08                   # outer margin
    corner = S * 0.22                  # tile corner radius
    tile = S - 2 * inset
    cx = cy = S / 2.0

    # note page geometry
    page_w = S * 0.36
    page_h = S * 0.48
    page_r = S * 0.045
    px0 = cx - page_w / 2.0
    px1 = px0 + page_w
    py0 = cy - page_h / 2.0
    py1 = py0 + page_h
    line_x0 = px0 + page_w * 0.16
    line_x1 = px1 - page_w * 0.16
    line_h = S * 0.028
    lines_y = [py0 + page_h * 0.32, py0 + page_h * 0.52, py0 + page_h * 0.72]
    line3_x1 = line_x0 + (line_x1 - line_x0) * 0.55

    raw = bytearray(S * S * 4)
    for y in range(S):
        for x in range(S):
            rx = min(max(x - inset, 0), tile)
            ry = min(max(y - inset, 0), tile)
            dx = x - inset - rx
            dy = y - inset - ry
            inside = (dx * dx + dy * dy) <= corner * corner if (rx in (0, tile) and ry in (0, tile)) else True

            if not inside:
                col = (0, 0, 0, 0)
            elif inside_rounded_rect(x, y, px0, py0, px1, py1, page_r):
                col = WHITE
            else:
                col = INDIGO

            if col == WHITE:
                on_line = False
                for i, ly in enumerate(lines_y):
                    lx1 = line3_x1 if i == 2 else line_x1
                    if ly - line_h / 2 <= y <= ly + line_h / 2 and line_x0 <= x <= lx1:
                        on_line = True
                        break
                if on_line:
                    col = LINE

            o = (y * S + x) * 4
            raw[o:o + 4] = bytes(col)

    # box downsample 2x -> size
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