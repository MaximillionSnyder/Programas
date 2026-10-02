#!/usr/bin/env python3
"""
Render offline de RunVoxelCard (seccion 12: carrera -> parciales, estilo Strava).

Las constantes se LEEN del propio .kt, no se copian: si alguien mueve RUN_BAR_STEP_X o
cambia la geometria info->grafica, aqui cambia tambien. Mismo criterio que voxel_offline.py.

Que verifica:
  1. En t=0 cada voxel esta exactamente dentro de su pieza INFO y no hay ningun vuelo.
  2. En t=1 cada pieza cae exactamente en su rect GRAFICA y las barras reproducen las
     fracciones de los parciales (altura = fraction * RUN_BAR_MAX_H, base en RUN_BASELINE_Y).
  3. A mitad del recorrido imprime mapas ASCII (como COMPARACION-VARIANTES.md) y, si hay
     ffmpeg, escribe PNGs con la silueta de los voxeles y la linea de FC.

Uso:  python3 run_offline.py [frames...]     (por defecto 0 / 0.35 / 0.7 / 1)
"""
import math
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
KT = HERE.parent.parent / "app/src/main/java/com/example/morphdemo/morph/RunVoxelCard.kt"
DATA = HERE.parent.parent / "app/src/main/java/com/example/morphdemo/data/SampleData.kt"
MAIN = HERE.parent.parent / "app/src/main/java/com/example/morphdemo/MainActivity.kt"

# El contenido de la card en el dispositivo de referencia: 327 x 200 dp.
CONTENT_W_DP = 327.0
CONTENT_H_DP = 200.0

src = KT.read_text(encoding="utf-8")
data_src = DATA.read_text(encoding="utf-8")
main_src = MAIN.read_text(encoding="utf-8")


def config(name, enum_hint):
    """Lee el valor de una perilla en la llamada de MainActivity; si no, el default."""
    call = re.search(rf"{name}\s*=\s*{enum_hint}\.(\w+)", main_src)
    default = re.search(rf"{name}:\s*{enum_hint}\s*=\s*{enum_hint}\.(\w+)", src)
    got = call or default
    if not got:
        sys.exit(f"no encuentro la perilla {name}")
    return got.group(1), "MainActivity" if call else "default del composable"


WAVE, WAVE_SRC = config("waveDirection", "VoxelWave")
SPIN, SPIN_SRC = config("spinMode", "VoxelSpin")
m_turns = re.search(r"spinTurns\s*=\s*([0-9.]+)f", main_src) or re.search(
    r"spinTurns:\s*Float\s*=\s*([0-9.]+)f", src
)
SPIN_TURNS = float(m_turns.group(1)) if m_turns else 1.5


def konst(name, cast=float):
    """Los Float llevan 'f' al final; los Int no."""
    m = re.search(rf"const val {name}\s*=\s*([0-9.]+)(f?)\b", src)
    if not m:
        sys.exit(f"no encuentro {name} en {KT.name}")
    return cast(m.group(1))


VOXEL_DP = konst("RUN_VOXEL_DP")
STAGGER = konst("RUN_STAGGER")
BASELINE_Y = konst("RUN_BASELINE_Y")
BAR_X0 = konst("RUN_BAR_X0")
BAR_STEP_X = konst("RUN_BAR_STEP_X")
BAR_W = konst("RUN_BAR_W")
BAR_MAX_H = konst("RUN_BAR_MAX_H")


def info_rects():
    """Los VRect del bloque RUN_INFO_RECTS, en orden: panel, separador y 5 tiles."""
    m = re.search(r"RUN_INFO_RECTS\s*=\s*listOf\((.*?)\n\)", src, re.S)
    if not m:
        sys.exit("no encuentro el bloque RUN_INFO_RECTS")
    rects = [
        tuple(float(v) for v in r)
        for r in re.findall(
            r"VRect\((-?[\d.]+)f,\s*(-?[\d.]+)f,\s*(-?[\d.]+)f,\s*(-?[\d.]+)f\)", m.group(1)
        )
    ]
    if len(rects) != 7:
        sys.exit(f"esperaba 7 rects INFO, encontre {len(rects)}")
    return rects


INFO = info_rects()
SPLITS = [
    (m.group(1), float(m.group(2)), int(m.group(3)))
    for m in re.finditer(
        r'RunSplit\("([^"]+)",\s*([0-9.]+)f,\s*(\d+)\)', data_src
    )
]
if not SPLITS:
    sys.exit(f"no encontre parciales en {DATA.name}")


# --- Misma matematica que buildRunPieces() -----------------------------------------------


def build_pieces():
    pieces = []
    # 1. Panel -> fondo del area de trazado.
    pieces.append((INFO[0], (0.0, 0.02, 1.0, 0.88)))
    # 2. Separador -> linea base.
    pieces.append((INFO[1], (0.03, BASELINE_Y, 0.94, 0.010)))
    # 3. Tile i -> barra i.
    for i, (_, fraction, _) in enumerate(SPLITS):
        h = fraction * BAR_MAX_H
        pieces.append((INFO[2 + i], (BAR_X0 + i * BAR_STEP_X, BASELINE_Y - h, BAR_W, h)))

    out = []
    for from_r, to_r in pieces:
        cols = max(
            max(1, round(from_r[2] * CONTENT_W_DP / VOXEL_DP)),
            max(1, round(to_r[2] * CONTENT_W_DP / VOXEL_DP)),
        )
        rows = max(
            max(1, round(from_r[3] * CONTENT_H_DP / VOXEL_DP)),
            max(1, round(to_r[3] * CONTENT_H_DP / VOXEL_DP)),
        )
        out.append((from_r, to_r, cols, rows))
    return out


PIECES = build_pieces()


def i32(v):
    v &= 0xFFFFFFFF
    return v - 0x100000000 if v >= 0x80000000 else v


def hash01(n):
    """Replica del hash determinista de Kotlin (Int de 32 bits con signo)."""
    x = i32(n * 374761393 + 668265263)
    x = i32((x ^ (x >> 13)) * 1274126177)
    x = i32(x ^ (x >> 16))
    return (x & 0x7FFFFFFF) / float(0x7FFFFFFF)


def lerp(a, b, t):
    return a + (b - a) * t


def wave_for(name, u, v):
    """Replica de las siete direcciones de VoxelWave."""
    if name == "DIAGONAL_BL_TR":
        return (u + (1.0 - v)) * 0.5
    if name == "LEFT_TO_RIGHT":
        return u
    if name == "RIGHT_TO_LEFT":
        return 1.0 - u
    if name == "CENTER_OUT":
        return max(abs(u - 0.5), abs(v - 0.5)) * 2.0
    if name == "EDGES_IN":
        return 1.0 - max(abs(u - 0.5), abs(v - 0.5)) * 2.0
    if name == "BOTTOM_TO_TOP":
        return 1.0 - v
    if name == "TOP_TO_BOTTOM":
        return v
    sys.exit(f"direccion de onda desconocida: {name}")


def voxels_at(t):
    """Devuelve, por pieza, cada voxel con su centro y tamano interpolados (en dp)."""
    out = []
    for p_index, (from_r, to_r, cols, rows) in enumerate(PIECES):
        rect = tuple(lerp(from_r[i], to_r[i], t) for i in range(4))
        w = rect[2] * CONTENT_W_DP
        h = rect[3] * CONTENT_H_DP
        cell_w, cell_h = w / cols, h / rows
        piece = []
        for col in range(cols):
            for row in range(rows):
                seed = hash01(p_index * 977 + col * 131 + row * 17)
                dst_u = to_r[0] + ((col + 0.5) / cols) * to_r[2]
                dst_v = to_r[1] + ((row + 0.5) / rows) * to_r[3]
                wave = wave_for(WAVE, dst_u, dst_v)
                delay = min(1.0, max(0.0, 0.62 * wave + 0.38 * seed))
                local = min(1.0, max(0.0, (t - delay * STAGGER) / (1.0 - STAGGER)))
                flight = max(0.0, math.sin(local * math.pi))
                jitter = hash01(p_index * 31 + col * 7 + row * 3) - 0.5
                drift = jitter * 18.0 * flight
                lift = 30.0 * flight * (0.55 + 0.45 * seed)
                cx = rect[0] * CONTENT_W_DP + (col + 0.5) * cell_w + drift
                cy = rect[1] * CONTENT_H_DP + (row + 0.5) * cell_h - lift
                piece.append((cx, cy, cell_w, cell_h, flight))
        out.append(piece)
    return out


def total_voxels():
    return sum(len(p) for p in voxels_at(0.0))


# --- Comprobaciones numericas --------------------------------------------------------------


def check():
    ok = True

    # 1. En t=0 ningun voxel vuela y todos caen dentro de su pieza INFO.
    for (from_r, _, _, _), piece in zip(PIECES, voxels_at(0.0)):
        for cx, cy, cw, ch, flight in piece:
            if flight > 1e-6:
                print("FALLA: hay vuelo en t=0")
                ok = False
            if not (
                from_r[0] * CONTENT_W_DP - 0.5 <= cx - cw / 2
                and cx + cw / 2 <= (from_r[0] + from_r[2]) * CONTENT_W_DP + 0.5
                and from_r[1] * CONTENT_H_DP - 0.5 <= cy - ch / 2
                and cy + ch / 2 <= (from_r[1] + from_r[3]) * CONTENT_H_DP + 0.5
            ):
                print("FALLA: un voxel se sale de su pieza INFO en t=0")
                ok = False

    # 2. En t=1 las barras reproducen los parciales.
    print("parcial  ritmo   frac   barra(top,bottom)   alto")
    for i, (pace, fraction, _) in enumerate(SPLITS):
        from_r, to_r, _, _ = PIECES[2 + i]
        top = to_r[1]
        bottom = to_r[1] + to_r[3]
        expect_h = fraction * BAR_MAX_H
        expect_x = BAR_X0 + i * BAR_STEP_X
        if abs(to_r[3] - expect_h) > 1e-6 or abs(to_r[0] - expect_x) > 1e-6:
            print(f"FALLA: la barra {i + 1} no coincide con la formula")
            ok = False
        if abs(bottom - BASELINE_Y) > 1e-6:
            print(f"FALLA: la barra {i + 1} no apoya en la linea base")
            ok = False
        if not (0.0 <= top and bottom <= 1.0 and 0.0 <= to_r[0] and to_r[0] + to_r[2] <= 1.0):
            print(f"FALLA: la barra {i + 1} se sale del lienzo")
            ok = False
        print(
            f"  {i + 1}     {pace}   {fraction:.2f}   "
            f"({top:.3f},{bottom:.3f})   {to_r[3]:.3f}"
        )

    # 3. En t=1 ningun voxel vuela y todos caen dentro de su pieza GRAFICA.
    for (_, to_r, _, _), piece in zip(PIECES, voxels_at(1.0)):
        for cx, cy, cw, ch, flight in piece:
            if flight > 1e-6:
                print("FALLA: hay vuelo en t=1")
                ok = False
            if not (
                to_r[0] * CONTENT_W_DP - 0.5 <= cx - cw / 2
                and cx + cw / 2 <= (to_r[0] + to_r[2]) * CONTENT_W_DP + 0.5
                and to_r[1] * CONTENT_H_DP - 0.5 <= cy - ch / 2
                and cy + ch / 2 <= (to_r[1] + to_r[3]) * CONTENT_H_DP + 0.5
            ):
                print("FALLA: un voxel se sale de su pieza GRAFICA en t=1")
                ok = False

    # 4. La direccion de onda configurada da retardos dentro de 0..1 en todo el destino.
    for _, to_r, cols, rows in PIECES:
        for col in range(cols):
            for row in range(rows):
                u = to_r[0] + ((col + 0.5) / cols) * to_r[2]
                v = to_r[1] + ((row + 0.5) / rows) * to_r[3]
                w = wave_for(WAVE, u, v)
                if not 0.0 <= w <= 1.0:
                    print(f"FALLA: wave {WAVE} fuera de 0..1 ({w:.3f})")
                    ok = False

    print(f"\nvoxeles: {total_voxels()} en {len(PIECES)} piezas")
    print("OK" if ok else "HAY FALLAS")
    return 0 if ok else 1


# --- Mapas ASCII --------------------------------------------------------------------------


def ascii_map(t, cols=52, rows=16):
    """El panel de fondo se marca con '.' para que se lean los tiles y las barras."""
    lines = []
    cells = [[" "] * cols for _ in range(rows)]
    for p_index, piece in enumerate(voxels_at(t)):
        for cx, cy, cw, ch, flight in piece:
            x0 = int((cx - cw / 2) / CONTENT_W_DP * cols)
            x1 = int((cx + cw / 2) / CONTENT_W_DP * cols - 1e-9)
            y0 = int((cy - ch / 2) / CONTENT_H_DP * rows)
            y1 = int((cy + ch / 2) / CONTENT_H_DP * rows - 1e-9)
            if p_index == 0:
                mark = "."
            elif p_index == 1:
                mark = "_"
            else:
                mark = "o" if flight > 0.05 else "#"
            for yy in range(max(0, y0), min(rows - 1, y1) + 1):
                for xx in range(max(0, x0), min(cols - 1, x1) + 1):
                    cells[yy][xx] = mark
    for row in cells:
        lines.append("|" + "".join(row) + "|")
    return "\n".join(lines)


# --- PNG (sin color exacto de tema: violeta para voxeles, rojo para FC) -------------------


def render_png(t, path):
    W, H = 420, 300
    PAD = 12
    bg = (0xF5, 0xF4, 0xFB)
    img = bytearray(bg * (W * H))

    def put(x, y, color):
        if 0 <= x < W and 0 <= y < H:
            o = (y * W + x) * 3
            img[o:o + 3] = bytes(color)

    def fill(x0, y0, x1, y1, color):
        for yy in range(max(0, int(y0)), min(H, int(y1) + 1)):
            for xx in range(max(0, int(x0)), min(W, int(x1) + 1)):
                put(xx, yy, color)

    for p_index, piece in enumerate(voxels_at(t)):
        # La pieza 0 es el panel de fondo: gris muy claro. Las demas, violeta.
        base_color = (0xE3, 0xE1, 0xF2) if p_index == 0 else (0x6C, 0x5C, 0xE7)
        for cx, cy, cw, ch, flight in piece:
            color = (0x9B, 0x8F, 0xF5) if flight > 0.05 else base_color
            x0 = PAD + (cx - cw / 2) / CONTENT_W_DP * (W - 2 * PAD)
            x1 = PAD + (cx + cw / 2) / CONTENT_W_DP * (W - 2 * PAD)
            y0 = PAD + (cy - ch / 2) / CONTENT_H_DP * (H - 2 * PAD)
            y1 = PAD + (cy + ch / 2) / CONTENT_H_DP * (H - 2 * PAD)
            fill(x0, y0, x1, y1, color)

    hr = (0xE5, 0x48, 0x4D)
    if t > 0.55:
        alpha = min(1.0, (t - 0.55) / 0.45)
        if alpha > 0.5:
            hr_min = min(s[2] for s in SPLITS)
            hr_max = max(s[2] for s in SPLITS)
            span = max(1, hr_max - hr_min)
            pts = []
            for i, (_, _, bpm) in enumerate(SPLITS):
                x = BAR_X0 + i * BAR_STEP_X + BAR_W / 2
                y = BASELINE_Y - (0.20 + 0.70 * (bpm - hr_min) / span) * BAR_MAX_H
                pts.append(
                    (
                        PAD + x * (W - 2 * PAD),
                        PAD + y * (H - 2 * PAD),
                    )
                )
            for (xa, ya), (xb, yb) in zip(pts, pts[1:]):
                steps = int(max(abs(xb - xa), abs(yb - ya))) + 1
                for s in range(steps + 1):
                    put(int(xa + (xb - xa) * s / steps), int(ya + (yb - ya) * s / steps), hr)

    raw = HERE / f"run_t{t:05.2f}.raw"
    png = Path(path)
    raw.write_bytes(bytes(img))
    try:
        subprocess.run(
            ["ffmpeg", "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
             "-s", f"{W}x{H}", "-i", str(raw), "-frames:v", "1", str(png)],
            check=True,
        )
        print("escrito", png.name)
    except (FileNotFoundError, subprocess.CalledProcessError):
        print("sin ffmpeg: se omite", png.name)
    finally:
        raw.unlink(missing_ok=True)


if __name__ == "__main__":
    print(f"INFO: {len(INFO)} piezas, GRÁFICA: {len(PIECES)} piezas, "
          f"{total_voxels()} voxeles\n")
    print(f"perillas: waveDirection={WAVE} ({WAVE_SRC}), "
          f"spinMode={SPIN} ({SPIN_SRC}), spinTurns={SPIN_TURNS} rad\n")
    status = check()
    frames = [float(x) for x in sys.argv[1:]] or [0.0, 0.35, 0.7, 1.0]
    sheet = []
    for t in frames:
        print(f"\n=== t={t:.2f} ===")
        print(ascii_map(t))
        sheet.append(t)
    for t in sheet:
        render_png(t, HERE / f"run_t{t:05.2f}.png")
    sys.exit(status)
