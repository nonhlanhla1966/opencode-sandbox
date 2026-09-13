#!/usr/bin/env python3
"""Generate Tip Calculator launcher icons (legacy PNG densities) with stdlib only.

Design: green rounded tile, white "banknote" (rounded rect) side-by-side with a
white coin (tip) — matches the adaptive foreground in
drawable/ic_launcher_foreground.xml. Renders at 2x and box-downsamples for soft
edges.
"""
import struct, zlib, os, sys

GREEN = (46, 125, 50, 255)     # #2E7D32
WHITE = (255, 255, 255, 255)

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
    inset = S * 0.06                   # outer margin
    corner = S * 0.20                  # tile corner radius
    tile = S - 2 * inset
    cx = cy = S / 2.0

    # bill (rounded rect) + coin circle geometry
    bill_w = S * 0.32
    bill_h = S * 0.24
    bill_r = S * 0.035
    bx0 = cx - bill_w * 0.42
    bx1 = bx0 + bill_w
    by0 = cy - bill_h / 2.0
    by1 = cy + bill_h / 2.0
    coin_cc = cx + bill_w * 0.30      # coin centre x (right of bill centre)
    coin_r = S * 0.115

    raw = bytearray(S * S * 4)
    for y in range(S):
        for x in range(S):
            # inside tile rounded rect?
            rx = min(max(x - inset, 0), tile)
            ry = min(max(y - inset, 0), tile)
            dx = x - inset - rx
            dy = y - inset - ry
            inside = (dx * dx + dy * dy) <= corner * corner if (rx in (0, tile) and ry in (0, tile)) else True
            dist_coin = ((x - coin_cc) ** 2 + (y - cy) ** 2) ** 0.5

            if not inside:
                col = (0, 0, 0, 0)
            elif inside_rounded_rect(x, y, bx0, by0, bx1, by1, bill_r):
                col = WHITE
            elif dist_coin <= coin_r:
                col = WHITE
            else:
                col = GREEN

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