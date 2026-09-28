#!/usr/bin/env python3
"""Render offline de la matematica de RecorridoCard (sustituto sin dispositivo).

Replica las constantes y curvas de RecorridoCard.kt y dibuja la card a 550x432
(mitad del tamano real en el POCO X6 Pro a densidad 3x). Los textos NO se
renderizan: se marcan como cajas translucidas en su posicion final.
"""
import math
import os
import random
import zlib
import struct

W, H = 550, 432
DURATION = 2.4
FPS = 30
FRAMES = int(DURATION * FPS) + 12

CUBE_X, CUBE_Y, CUBE_W, CUBE_H = -0.01, -0.02, 1.02, 1.05
ROUTE_X, ROUTE_Y, ROUTE_W, ROUTE_H = 0.02, 0.19, 0.97, 0.65
COLS, ROWS = 10, 6
CUBE_POP, FADE_DUR, FALL_DUR = 0.06, 0.09, 0.28
DEBRIS_SCALE = 0.34

BG = (0x24, 0x2E, 0x3E)
TEXT = (0xF2, 0xF7, 0xF8)
ROUTE = (0x5F, 0xE7, 0xD5)
GLOW = (0x46, 0xD8, 0xC6)
START = (0xF9, 0xB4, 0xBB)
END = (0x74, 0xEE, 0xDE)

PALETTE = [
    ((0x1A, 0x2A, 0x40), 8), ((0x1F, 0x2E, 0x44), 7), ((0x24, 0x37, 0x4F), 8),
    ((0x2A, 0x3F, 0x58), 7), ((0x2C, 0x40, 0x59), 6), ((0x31, 0x4A, 0x62), 5),
    ((0x34, 0x50, 0x63), 5), ((0x3A, 0x5D, 0x6C), 4), ((0x45, 0x70, 0x7F), 4),
    ((0x49, 0x8A, 0x90), 4), ((0x55, 0x9E, 0x9D), 4), ((0x5A, 0xB0, 0xAF), 4),
    ((0x68, 0xD4, 0xC9), 3), ((0x79, 0xD2, 0xC9), 3), ((0x8D, 0xDD, 0xD4), 2),
    ((0xB1, 0xBB, 0xC5), 3), ((0xC0, 0xCB, 0xD1), 2),
]

ROUTE_PTS = [
    (0.163, 0.873), (0.238, 0.809), (0.267, 0.774), (0.291, 0.667),
    (0.316, 0.652), (0.340, 0.631), (0.364, 0.601), (0.389, 0.576),
    (0.413, 0.570), (0.437, 0.560), (0.462, 0.570), (0.486, 0.540),
    (0.511, 0.449), (0.535, 0.418), (0.559, 0.403), (0.584, 0.413),
    (0.608, 0.413), (0.632, 0.403), (0.657, 0.388), (0.681, 0.357),
    (0.705, 0.317), (0.730, 0.286), (0.754, 0.271), (0.808, 0.215),
]

END_LABEL = (ROUTE_X + 0.773 * ROUTE_W, ROUTE_Y + 0.33 * ROUTE_H)


def clamp(x, lo=0.0, hi=1.0):
    return lo if x < lo else hi if x > hi else x


def rng(t, a, b):
    return clamp((t - a) / (b - a))


def ease_out_cubic(x):
    return 1 - (1 - x) ** 3


def build_cubes():
    rnd = random.Random(20260927)
    pool = [c for c, w in PALETTE for _ in range(w)]
    cubes = []
    for row in range(ROWS):
        for col in range(COLS):
            dc = (col + 0.5) / COLS
            dr = abs((row + 0.5 - 3) * 2 / ROWS)
            dist = dc + dr * 0.48
            size = (0.45 + rnd.random() * 0.15) if rnd.random() < 0.10 else (0.76 + rnd.random() * 0.16)
            falls = row >= 3 and rnd.random() < 0.28
            cubes.append(dict(
                col=col, row=row, color=pool[rnd.randrange(len(pool))], size=size,
                dx=(rnd.random() - 0.5) * 0.08, dy=(rnd.random() - 0.5) * 0.08,
                appear=0.01 + dist * 0.23 + rnd.random() * 0.05,
                vanish=(0.26 + col * 0.022 + rnd.random() * 0.06) if falls
                else (0.26 + col * 0.026 + rnd.random() * 0.06),
                falls=falls, landY=0.95 + rnd.random() * 0.06,
            ))
    return cubes


def route_path():
    pts = [(W * (ROUTE_X + p[0] * ROUTE_W), H * (ROUTE_Y + p[1] * ROUTE_H)) for p in ROUTE_PTS]
    return pts


PATH_SAMPLES = route_path()


class Canvas:
    def __init__(self):
        self.px = bytearray(W * H * 3)
        for i in range(W * H):
            self.px[i * 3:i * 3 + 3] = bytes(BG)

    def blend(self, x, y, color, a):
        if a <= 0.004 or x < 0 or y < 0 or x >= W or y >= H:
            return
        i = (y * W + x) * 3
        for c in range(3):
            dst = self.px[i + c]
            self.px[i + c] = max(0, min(255, int(dst + (color[c] - dst) * a)))

    def disc(self, cx, cy, r, color, a=1.0):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    self.blend(x, y, color, a * clamp((r - d) / 1.2))

    def thick(self, pts, width, color, a):
        r = width / 2
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            dx, dy = bx - ax, by - ay
            ln = math.hypot(dx, dy) or 1e-6
            for y in range(int(min(ay, by) - r - 1), int(max(ay, by) + r + 2)):
                for x in range(int(min(ax, bx) - r - 1), int(max(ax, bx) + r + 2)):
                    u = clamp(((x + 0.5 - ax) * dx + (y + 0.5 - ay) * dy) / (ln * ln))
                    d = math.hypot(x + 0.5 - ax - u * dx, y + 0.5 - ay - u * dy)
                    if d <= r + 1:
                        self.blend(x, y, color, a * clamp((r - d) / 1.2))

    def cube(self, cx, cy, w, h, color, a):
        x0, y0 = cx - w / 2, cy - h / 2
        cr = min(w, h) * 0.12
        for y in range(int(y0), int(y0 + h) + 1):
            for x in range(int(x0), int(x0 + w) + 1):
                px, py = x + 0.5 - x0, y + 0.5 - y0
                d = math.hypot(max(cr - px, 0, px - (w - cr)), max(cr - py, 0, py - (h - cr)))
                if d <= cr:
                    self.blend(x, y, color, a)

    def png(self, path):
        raw = bytearray()
        for y in range(H):
            raw.append(0)
            raw += self.px[y * W * 3:(y + 1) * W * 3]

        def chunk(tag, data):
            c = struct.pack(">I", len(data)) + tag + data
            return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 6))
        png += chunk(b"IEND", b"")
        open(path, "wb").write(png)


def total_len(samples):
    return sum(math.hypot(bx - ax, by - ay) for (ax, ay), (bx, by) in zip(samples, samples[1:]))


PATH_LEN = total_len(PATH_SAMPLES)


def segment(samples, frac):
    target = PATH_LEN * frac
    out = [samples[0]]
    acc = 0.0
    for (ax, ay), (bx, by) in zip(samples, samples[1:]):
        seg = math.hypot(bx - ax, by - ay)
        if acc + seg >= target:
            u = (target - acc) / seg if seg else 0
            out.append((ax + (bx - ax) * u, ay + (by - ay) * u))
            break
        out.append((bx, by))
        acc += seg
    return out


def frame(t, cubes):
    c = Canvas()
    gx, gy = W * CUBE_X, H * CUBE_Y
    cellW, cellH = W * CUBE_W / COLS, H * CUBE_H / ROWS

    for cube in cubes:
        raw = clamp((t - cube["appear"]) / CUBE_POP)
        if raw <= 0:
            continue
        pop = ease_out_cubic(raw)
        bump = 1 + 0.10 * math.sin(math.pi * raw)
        w = cellW * cube["size"] * pop * bump
        h = cellH * cube["size"] * pop * bump
        cx = gx + (cube["col"] + 0.5) * cellW + cube["dx"] * cellW
        cy = gy + (cube["row"] + 0.5) * cellH + cube["dy"] * cellH
        alpha = pop
        if cube["falls"]:
            v = clamp((t - cube["vanish"]) / FALL_DUR)
            if v > 0:
                cy += (H * cube["landY"] - cy) * v * v
                k = 1 - (1 - DEBRIS_SCALE) * v
                w *= k
                h *= k
        else:
            v = clamp((t - cube["vanish"]) / FADE_DUR)
            if v > 0:
                alpha *= 1 - v
                cy += cellH * 0.5 * v
                k = 1 - 0.35 * v
                w *= k
                h *= k
        if alpha > 0.01 and w > 0:
            c.cube(cx, cy, w, h, cube["color"], alpha)

    drawT = rng(t, 0.13, 0.70)
    if drawT > 0:
        seg = segment(PATH_SAMPLES, max(drawT, 0.002))
        c.thick(seg, W * 0.013 * 1.9, GLOW, 0.10)
        c.thick(seg, W * 0.013, ROUTE, 1.0)

    sa = rng(t, 0.10, 0.17)
    if sa > 0:
        c.disc(*PATH_SAMPLES[0], W * 0.026, START, sa)
    ea = rng(t, 0.66, 0.72)
    if ea > 0:
        c.disc(*PATH_SAMPLES[-1], W * 0.028, END, ea)

    titleT = rng(t, 0.26, 0.36)
    endT = rng(t, 0.10, 0.18)
    capT = rng(t, 0.58, 0.86)
    if titleT > 0:
        c.cube(60 + 70, 48 + 15, 140, 30, TEXT, titleT * 0.85)
        c.cube(W - 60 - 80, 48 + 15, 160, 30, TEXT, titleT * 0.85)
    if endT > 0:
        c.cube(W * END_LABEL[0] + 85, H * END_LABEL[1] + 14, 170, 28, TEXT, endT * 0.85)
    if capT > 0:
        c.cube(60 + 210, H * 0.85 + 16, 420 * capT, 32, TEXT, 0.85)
    return c


def main():
    out = "/data/data/com.termux/files/usr/tmp/opencode/recorrido_offline"
    os.makedirs(out, exist_ok=True)
    cubes = build_cubes()
    for i in range(FRAMES):
        t = min(i / FPS / DURATION, 1.0)
        frame(t, cubes).png(f"{out}/f_{i:03d}.png")
    print("frames:", FRAMES, "->", out)


if __name__ == "__main__":
    main()
