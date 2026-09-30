package com.example.morphdemo.morph

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morphdemo.data.Category
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * CARD C - transmutacion por voxeles.
 *
 * Las dos tecnicas anteriores mueven PIEZAS enteras: o interpolas tu el rectangulo de cada
 * pieza (Card A) o dejas que Compose anime sus bounds (Card B). Aqui la unidad del morph ya
 * no es la pieza: es el VOXEL.
 *
 * La idea:
 *  1. La geometria de las piezas es la misma que en la Card A (panel -> canaleta,
 *     track -> linea base, segmento i -> barra i). Eso garantiza que en reposo los dos
 *     estados se vean iguales que los de la Card A.
 *  2. Cada pieza se subdivide en una rejilla de voxeles que la TESELAN exactamente: la
 *     subdivision se calcula con el mayor numero de filas/columnas que necesita la pieza en
 *     cualquiera de sus dos estados, para que los voxeles sean casi cubicos al volar.
 *  3. Cada voxel tiene su propio tiempo. El retardo combina una onda diagonal sobre el
 *     layout de DESTINO (las barras se rearman en barrido, de abajo-izquierda a
 *     arriba-derecha) con un jitter determinista, para que el conjunto se lea como un
 *     enjambre y no como una rejilla rigida.
 *  4. Mientras vuela, el voxel se dibuja como un cubo pseudo-3D: cara frontal, tapa y
 *     costado, con sombreado por cara y un borde emisivo del color de acento.
 *
 * En reposo (progreso local 0 o 1) el voxel se dibuja plano y sin juntas, asi que la card
 * se ve solida: el volumen solo existe durante el vuelo.
 */

private const val V_SEG_GAP = 0.022f
private const val V_BASELINE_Y = 0.88f
private const val V_BAR_START_X = 0.30f
private const val V_BAR_STEP_X = 0.175f
private const val V_BAR_W = 0.16f
private const val V_BAR_MAX_H = 0.72f

/** Lado objetivo del voxel, en dp. Mas chico = mas cubos y mas costo por frame. */
private const val VOXEL_DP = 24f

/** Cuanto se reparte el retardo entre los voxeles (0 = todos a la vez). */
private const val STAGGER = 0.42f

private const val PI_F = PI.toFloat()

/** Rectangulo normalizado (0..1) dentro del area de contenido de la card. */
private data class VRect(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Una pieza del morph, ya subdividida.
 *
 * @param cols columnas de voxeles, @param rows filas de voxeles.
 */
private data class VoxelPiece(
    val from: VRect,
    val to: VRect,
    val fromColor: Color,
    val toColor: Color,
    val cols: Int,
    val rows: Int,
)

private fun lerpVRect(a: VRect, b: VRect, t: Float) = VRect(
    x = a.x + (b.x - a.x) * t,
    y = a.y + (b.y - a.y) * t,
    w = a.w + (b.w - a.w) * t,
    h = a.h + (b.h - a.h) * t,
)

/**
 * Hash determinista 0..1 a partir de un entero.
 *
 * Se usa para que cada voxel tenga su propio jitter y sentido de giro sin guardar estado:
 * el mismo indice da siempre el mismo valor, asi que la animacion es reproducible.
 */
private fun hash01(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x shr 13)) * 1274126177
    x = x xor (x shr 16)
    return (x and 0x7FFFFFFF) / 0x7FFFFFFF.toFloat()
}

/**
 * Construye las piezas y calcula cuantos voxeles necesita cada una.
 *
 * La subdivision toma el maximo entre la que pide el estado INFO y la que pide el estado
 * GRAFICA: una barra es ancha y baja en INFO y estrecha y alta en GRAFICA, y hay que cubrir
 * ambas para que los cubos no salgan aplastados en ningun extremo.
 */
private fun buildVoxelPieces(
    categories: List<Category>,
    accent: Color,
    trackColor: Color,
    axisColor: Color,
    contentWDp: Float,
    contentHDp: Float,
): List<VoxelPiece> {
    val n = categories.size
    val segW = (1f - V_SEG_GAP * (n - 1)) / n

    fun colsFor(wNorm: Float) = (wNorm * contentWDp / VOXEL_DP).roundToInt().coerceAtLeast(1)
    fun rowsFor(hNorm: Float) = (hNorm * contentHDp / VOXEL_DP).roundToInt().coerceAtLeast(1)

    fun piece(from: VRect, to: VRect, fromColor: Color, toColor: Color) = VoxelPiece(
        from = from,
        to = to,
        fromColor = fromColor,
        toColor = toColor,
        cols = maxOf(colsFor(from.w), colsFor(to.w)),
        rows = maxOf(rowsFor(from.h), rowsFor(to.h)),
    )

    val pieces = mutableListOf<VoxelPiece>()

    // 1. Panel del hero -> canaleta del eje Y.
    pieces += piece(
        VRect(0f, 0f, 1f, 0.52f),
        VRect(0f, 0f, 0.24f, 1f),
        accent.copy(alpha = 0.10f),
        accent.copy(alpha = 0.08f),
    )

    // 2. Track del progreso -> linea base.
    pieces += piece(
        VRect(0f, 0.86f, 1f, 0.07f),
        VRect(0.28f, V_BASELINE_Y, 0.72f, 0.012f),
        trackColor,
        axisColor,
    )

    // 3. Los protagonistas: cada segmento del progreso -> su barra.
    categories.forEachIndexed { i, category ->
        val chartH = category.fraction * V_BAR_MAX_H
        pieces += piece(
            VRect(i * (segW + V_SEG_GAP), 0.86f, segW, 0.07f),
            VRect(V_BAR_START_X + i * V_BAR_STEP_X, V_BASELINE_Y - chartH, V_BAR_W, chartH),
            category.color,
            category.color,
        )
    }

    return pieces
}

/**
 * Dibuja un voxel.
 *
 * Si `flight` es ~0 el voxel esta en reposo y se pinta plano, del tamano exacto de su celda
 * (con medio pixel de mas para que no aparezcan lineas de costura entre voxeles vecinos).
 * Si esta volando se dibuja como cubo: cara frontal + tapa + costado, con el ancho de la
 * cara frontal encogido por el coseno del giro, que es lo que da la sensacion de volumen.
 */
private fun DrawScope.drawVoxel(
    cx: Float,
    cy: Float,
    cellW: Float,
    cellH: Float,
    color: Color,
    accent: Color,
    flight: Float,
    lift: Float,
    spin: Float,
    scale: Float,
) {
    if (flight < 0.02f) {
        drawRect(
            color = color,
            topLeft = Offset(cx - cellW * 0.5f, cy - cellH * 0.5f),
            size = Size(cellW + 0.5f, cellH + 0.5f),
        )
        return
    }

    // El giro alrededor del eje Y se aproxima encogiendo la cara frontal por |cos| y
    // dibujando el costado con el ancho que asoma: |sin|.
    val angle = spin * flight * 1.5f
    val cosA = abs(cos(angle))
    val sinA = abs(sin(angle))

    val frontH = cellH * scale
    val frontW = cellW * scale * (0.30f + 0.70f * cosA)
    val depth = min(cellW, cellH) * 0.38f * (0.20f + 0.80f * sinA) * scale
    val sideSign = if (spin >= 0f) 1f else -1f

    val left = cx - frontW * 0.5f
    val top = cy - lift - frontH * 0.5f
    val right = left + frontW
    val bottom = top + frontH

    val front = color
    val topFace = lerp(color, Color.White, 0.30f)
    val sideFace = lerp(color, Color.Black, 0.28f)

    // Tapa: paralelogramo que se abre hacia arriba.
    val topPath = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        lineTo(right + depth * sideSign * 0.45f, top - depth)
        lineTo(left + depth * sideSign * 0.45f, top - depth)
        close()
    }
    drawPath(topPath, topFace)

    // Costado: paralelogramo que se abre hacia el lado opuesto al giro.
    val sidePath = Path().apply {
        moveTo(right, top)
        lineTo(right, bottom)
        lineTo(right - depth * sideSign * 0.45f, bottom - depth)
        lineTo(right - depth * sideSign * 0.45f, top - depth)
        close()
    }
    drawPath(sidePath, sideFace)

    // Cara frontal.
    drawRoundRect(
        color = front,
        topLeft = Offset(left, top),
        size = Size(frontW, frontH),
        cornerRadius = CornerRadius(min(frontW, frontH) * 0.12f),
    )

    // Borde emisivo: solo mientras el voxel esta en el aire.
    drawRoundRect(
        color = accent.copy(alpha = 0.55f * flight),
        topLeft = Offset(left, top),
        size = Size(frontW, frontH),
        cornerRadius = CornerRadius(min(frontW, frontH) * 0.12f),
        style = Stroke(width = 1.2.dp.toPx()),
    )
}

/**
 * CARD C - transmutacion por voxeles (tercera tecnica).
 *
 * Toca la card para alternar entre informacion y grafica.
 */
@Composable
fun VoxelMorphCard(
    title: String,
    amount: String,
    subtitle: String,
    categories: List<Category>,
    modifier: Modifier = Modifier,
) {
    var showChart by remember { mutableStateOf(false) }

    val t by animateFloatAsState(
        targetValue = if (showChart) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "voxelMorph",
    )

    val accent = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val infoAlpha = (1f - t / 0.30f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(MorphCardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(CardPadding)) {
            val cw = maxWidth
            val ch = maxHeight

            val pieces = remember(categories, accent, trackColor, axisColor, cw, ch) {
                buildVoxelPieces(
                    categories = categories,
                    accent = accent,
                    trackColor = trackColor,
                    axisColor = axisColor,
                    contentWDp = cw.value,
                    contentHDp = ch.value,
                )
            }

            // --- Los voxeles -----------------------------------------------------
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val liftMax = 30.dp.toPx()

                pieces.forEachIndexed { pieceIndex, piece ->
                    val rect = lerpVRect(piece.from, piece.to, t)
                    val originX = rect.x * w
                    val originY = rect.y * h
                    val pieceW = rect.w * w
                    val pieceH = rect.h * h
                    val cellW = pieceW / piece.cols
                    val cellH = pieceH / piece.rows

                    for (col in 0 until piece.cols) {
                        for (row in 0 until piece.rows) {
                            val seed = hash01(pieceIndex * 977 + col * 131 + row * 17)
                            val spin = seed * 2f - 1f

                            // Onda diagonal sobre el layout de DESTINO: las barras se
                            // rearman en barrido, no todas a la vez.
                            val dstU = piece.to.x + ((col + 0.5f) / piece.cols) * piece.to.w
                            val dstV = piece.to.y + ((row + 0.5f) / piece.rows) * piece.to.h
                            val wave = (dstU + (1f - dstV)) * 0.5f

                            val delay = (0.62f * wave + 0.38f * seed).coerceIn(0f, 1f)
                            val local =
                                ((t - delay * STAGGER) / (1f - STAGGER)).coerceIn(0f, 1f)

                            // 0 en reposo, 1 en el pico del vuelo, 0 al aterrizar.
                            val flight = sin(local * PI_F).coerceAtLeast(0f)

                            val cx = originX + (col + 0.5f) * cellW
                            val cy = originY + (row + 0.5f) * cellH
                            val jitter = (hash01(pieceIndex * 31 + col * 7 + row * 3) - 0.5f)
                            val drift = jitter * 18.dp.toPx() * flight

                            val base = lerp(piece.fromColor, piece.toColor, local)
                            val glowing = lerp(base, accent, 0.30f * flight)

                            drawVoxel(
                                cx = cx + drift,
                                cy = cy,
                                cellW = cellW,
                                cellH = cellH,
                                color = glowing,
                                accent = accent,
                                flight = flight,
                                lift = liftMax * flight * (0.55f + 0.45f * seed),
                                spin = spin,
                                scale = 1f + 0.22f * flight,
                            )
                        }
                    }
                }

                // --- Linea de escaneo: refuerza la lectura de "algo esta transmutando".
                val scan = sin(t * PI_F).coerceAtLeast(0f)
                if (scan > 0.02f) {
                    val band = 26.dp.toPx()
                    val scanY = t * (h + band * 2f) - band
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accent.copy(alpha = 0.18f * scan),
                                Color.Transparent,
                            ),
                            startY = scanY - band,
                            endY = scanY + band,
                        ),
                        topLeft = Offset(0f, scanY - band),
                        size = Size(w, band * 2f),
                    )
                }
            }

            // --- Texto del estado INFO -------------------------------------------
            if (infoAlpha > 0.01f) {
                VoxelLabel(title, cw * 0.05f, ch * 0.055f, cw * 0.90f, infoAlpha, 10.sp, muted)
                VoxelLabel(
                    amount, cw * 0.045f, ch * 0.14f, cw * 0.90f,
                    infoAlpha, 26.sp, onSurface, FontWeight.ExtraBold,
                )
                VoxelLabel(subtitle, cw * 0.05f, ch * 0.40f, cw * 0.90f, infoAlpha, 10.sp, muted)
            }

            // --- Texto del estado GRAFICA ----------------------------------------
            if (chartAlpha > 0.01f) {
                VoxelLabel("Total", cw * 0.02f, ch * 0.02f, cw * 0.20f, chartAlpha, 8.sp, muted)
                VoxelLabel(
                    amount, cw * 0.02f, ch * 0.08f, cw * 0.21f,
                    chartAlpha, 11.sp, onSurface, FontWeight.Bold,
                )

                categories.forEachIndexed { i, category ->
                    val barX = V_BAR_START_X + i * V_BAR_STEP_X
                    val chartH = category.fraction * V_BAR_MAX_H
                    val barY = V_BASELINE_Y - chartH

                    VoxelLabel(
                        category.amountText,
                        cw * barX, ch * (barY - 0.075f), cw * V_BAR_W,
                        chartAlpha, 8.sp, muted, FontWeight.SemiBold,
                        TextAlign.Center,
                    )
                    VoxelLabel(
                        category.name,
                        cw * barX, ch * 0.905f, cw * V_BAR_W,
                        chartAlpha, 8.sp, muted, FontWeight.Normal,
                        TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoxelLabel(
    text: String,
    x: Dp,
    y: Dp,
    width: Dp,
    alpha: Float,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = Modifier
            .offset(x = x, y = y)
            .width(width)
            .alpha(alpha),
        fontSize = fontSize,
        lineHeight = fontSize * 1.15f,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
    )
}
