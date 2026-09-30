#!/usr/bin/env python3
"""
Render offline de RecorridoVoxelCard.

Las constantes se LEEN del propio .kt, no se copian: si alguien cambia TAPER o COLS en
Kotlin, aqui cambia tambien. Es la diferencia con docs/render-offline/recorrido_offline.py,
que redeclara las suyas y por eso puede divergir sin que nadie se entere.

Lo que NO replica exactamente: kotlin.random.Random. El PRNG de Kotlin no es el de Python,
asi que el color y el tamano exactos de cada celda salen distintos. La silueta, el orden de
entrada y el bright ramp si coinciden, que es lo que se quiere comprobar.

Uso:  python3 voxel_offline.py [frames...]     (salida en voxel_frames/)
"""
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
KT = HERE.parent.parent / "app/src/main/java/com/example/morphdemo/morph/RecorridoVoxelCard.kt"
OUT = HERE / "voxel_frames"

W, H = 852, 480          # el tamaño del video de referencia
CARD = (165.0, 30.0, 745.0, 465.0)

# --- Constantes leidas del Kotlin --------------------------------------------------------

src = KT.read_text(encoding="utf-8")


def const(name, cast=float):
    m = re.search(rf"const val {name}\s*=\s*([0-9.]+)", src)
    if not m:
        sys.exit(f"no encuentro {name} en {KT.name}")
    return cast(m.group(1))


def konst(name, cast=float):
    """Los Float llevan 'f' al final; los Int no."""
    m = re.search(rf"const val {name}\s*=\s*([0-9.]+)(f?)\b", src)
    if not m:
        sys.exit(f"no encuentro {name} en {KT.name}")
    return cast(m.group(1))


COLS = konst("COLS", int)
ROWS = konst("ROWS", int)
CELL = konst("CELL")
TAPER = konst("TAPER")
SWEEP = konst("SWEEP")
POP = konst("POP")
DUST = konst("DUST")
GRID_L, GRID_T = konst("GRID_L"), konst("GRID_T")
GRID_W, GRID_H = konst("GRID_W"), konst("GRID_H")
CAPTION_IN = konst("CAPTION_IN")

BG = (0x22, 0x2D, 0x3A)
TEXT = (0xF2, 0xF7, 0xF8)
ROUTE = (0x5F, 0xE7, 0xD5)
HALO = (0x46, 0xD8, 0xC6)
START = (0xF4, 0xB3, 0xBD)
END = (0x74, 0xEE, 0xDE)

ramp = [(int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))
        for h in re.findall(r"Color\(0xFF([0-9A-Fa-f]{6})\)", src.split("VoxRamp")[1])]

route = {}
for key in ("RouteStart", "RouteEnd", "RouteCtrl"):
    m = re.search(rf"{key}\s*=\s*Offset\(\s*([0-9.]+)f,\s*([0-9.]+)f", src)
    route[key] = (float(m.group(1)), float(m.group(2)))

# --- Geometria (misma formula que buildVoxels/drawVoxels) ---------------------------------


def build():
    half = (ROWS - 1) / 2.0
    out = []
    for col in range(COLS):
        colT = col / (COLS - 1.0)
        half_span = 1.0 - TAPER * colT
        for row in range(ROWS):
            norm_row = abs(row - half) / half
            if norm_row > half_span:
                continue
            out.append((col, row, colT, norm_row))
    return out


VOXELS = build()


def ease_out_cubic(x):
    return 1.0 - (1.0 - x) ** 3


def range01(t, a, b):
    return max(0.0, min(1.0, (t - a) / (b - a)))


def render(t):
    img = bytearray()
    cw, ch = CARD[2] - CARD[0], CARD[3] - CARD[1]
    gx = CARD[0] + cw * GRID_L
    gy = CARD[1] + ch * GRID_T
    cell_w = cw * GRID_W / COLS
    cell_h = ch * GRID_H / ROWS
    pitch = min(cell_w, cell_h)
    side = pitch * CELL

    # fondo + tarjeta redondeada (se pinta rectangular, el Canvas real ya recorta)
    for y in range(H):
        for x in range(W):
            inside = CARD[0] <= x <= CARD[2] and CARD[1] <= y <= CARD[3]
            p = BG if inside else (0x1B, 0x26, 0x33)
            img += bytes(p)

    for col, row, colT, norm_row in VOXELS:
        start = colT * SWEEP + norm_row * (1.0 - SWEEP)
        raw = range01(t, start, start + POP)
        if raw <= 0:
            continue
        import math
        pop = ease_out_cubic(raw) * (1.0 + 0.10 * math.sin(math.pi * raw))
        # el color real sale del PRNG de Kotlin; aqui se usa solo la rampa por columna
        bright = max(0.0, min(1.0, colT ** 0.85))
        r, g, b = ramp[int(bright * (len(ramp) - 1))]
        cx = gx + (col + 0.5) * cell_w
        cy = gy + (row + 0.5) * cell_h
        s = side * pop
        x0, y0 = int(cx - s / 2), int(cy - s / 2)
        for yy in range(max(0, y0), min(H, y0 + int(s))):
            for xx in range(max(0, x0), min(W, x0 + int(s))):
                o = (yy * W + xx) * 3
                img[o:o + 3] = bytes((r, g, b))

    # ruta: Bezier cuadratica muestreada, recortada con PathMeasure
    a = (CARD[0] + cw * route["RouteStart"][0], CARD[1] + ch * route["RouteStart"][1])
    b = (CARD[0] + cw * route["RouteEnd"][0], CARD[1] + ch * route["RouteEnd"][1])
    c = (CARD[0] + cw * route["RouteCtrl"][0], CARD[1] + ch * route["RouteCtrl"][1])
    pts = []
    for i in range(49):
        u = i / 48.0
        v = 1.0 - u
        pts.append((v * v * a[0] + 2 * v * u * c[0] + u * u * b[0],
                    v * v * a[1] + 2 * v * u * c[1] + u * u * b[1]))
    # Longitud de arco acumulada, como hace PathMeasure.getSegment
    seg = [0.0]
    for i in range(1, len(pts)):
        seg.append(seg[-1] + ((pts[i][0] - pts[i - 1][0]) ** 2
                              + (pts[i][1] - pts[i - 1][1]) ** 2) ** 0.5)
    total = seg[-1]
    draw_t = range01(t, 0.62, 0.90)
    target = total * draw_t
    sw = max(1, int(cw * 0.009))
    for i in range(1, len(pts)):
        if seg[i] > target:
            break
        px, py = int(pts[i][0]), int(pts[i][1])
        for d in range(-sw, sw + 1):
            for e in range(-sw, sw + 1):
                xx, yy = px + d, py + e
                if 0 <= xx < W and 0 <= yy < H and d * d + e * e <= sw * sw:
                    o = (yy * W + xx) * 3
                    img[o:o + 3] = bytes(ROUTE)
    sa = range01(t, 0.63, 0.67)
    if sa > 0:
        r0 = int(ch * 0.029)
        for yy in range(int(a[1]) - r0, int(a[1]) + r0):
            for xx in range(int(a[0]) - r0, int(a[0]) + r0):
                if 0 <= xx < W and 0 <= yy < H and (xx - a[0]) ** 2 + (yy - a[1]) ** 2 <= r0 * r0:
                    o = (yy * W + xx) * 3
                    img[o:o + 3] = bytes(START)
    return bytes(img)


def dump_map():
    print(f"COLS={COLS} ROWS={ROWS} TAPER={TAPER} SWEEP={SWEEP} POP={POP}")
    print(f"celdas en la cuna: {len(VOXELS)} de {COLS*ROWS} posibles\n")
    print("silueta (. = fuera de la cuna, X = celda):")
    present = {(c, r) for c, r, _, _ in VOXELS}
    for r in range(ROWS):
        print("  " + "".join("X" if (c, r) in present else "." for c in range(COLS)))
    print("\nnumero de filas visibles por columna (debe decrecer):")
    counts = [sum(1 for c, r, _, _ in VOXELS if c == col) for col in range(COLS)]
    print("  " + " ".join(str(n) for n in counts))


if __name__ == "__main__":
    dump_map()
    frames = [float(x) for x in sys.argv[1:]] or [0.0, 0.12, 0.3, 0.55, 0.78, 0.92, 1.0]
    OUT.mkdir(exist_ok=True)
    for t in frames:
        raw = OUT / f"t{t:05.2f}.raw"
        raw.write_bytes(render(t))
        png = OUT / f"t{t:05.2f}.png"
        subprocess.run(["ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
                        "-s", f"{W}x{H}", "-i", str(raw), "-frames:v", "1", str(png)], check=True)
        raw.unlink()
        print("escrito", png.name)
