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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morphdemo.data.RunSplit
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * CARD 12 - Running, estilo Strava: del resumen de la carrera a los parciales.
 *
 * Es el mismo morph por voxeles de [VoxelMorphCard], aplicado al caso real que se quiere
 * llevar a la app: arriba se lee el resumen de la carrera (distancia, ritmo, velocidad,
 * tiempo y FC media) y al tocar los voxeles vuelan y rearman la grafica que Strava muestra
 * para una carrera: las **barras de ritmo por km**, con la linea de FC encima.
 *
 * El mapeo pieza a pieza:
 *
 *  - el panel del resumen se convierte en el fondo del area de trazado;
 *  - el separador de los chips se convierte en la linea base;
 *  - cada tile de estadistica se convierte en el parcial de su km.
 *
 * En reposo los voxeles se dibujan planos y sin juntas, asi que cada estado se ve solido;
 * el volumen solo existe durante el vuelo.
 */

/** Alto fijo: el mismo requisito de siempre, la card no cambia de tamano al alternar. */
val RunVoxelCardHeight: Dp = 240.dp

/** Lado objetivo del voxel, en dp. Mas chico = mas cubos y mas costo por frame. */
private const val RUN_VOXEL_DP = 26f

/** Cuanto se reparte el retardo entre los voxeles (0 = todos a la vez). */
private const val RUN_STAGGER = 0.42f

// --- Geometria de la grafica, en fraccion del area de contenido -------------------------

private const val RUN_BASELINE_Y = 0.86f
private const val RUN_BAR_X0 = 0.055f
private const val RUN_BAR_STEP_X = 0.18f
private const val RUN_BAR_W = 0.15f
private const val RUN_BAR_MAX_H = 0.66f

/** Rojo de pulso, como el que Strava usa para la frecuencia cardiaca. */
private val RunHrColor = Color(0xFFE5484D)

private val RunPi = PI.toFloat()

/**
 * Geometria del estado INFO, en orden: panel, separador y los cinco tiles (distancia,
 * ritmo, velocidad, tiempo y FC media). El estado GRAFICA se calcula en [buildRunPieces]
 * con RUN_BAR_* y las fracciones de los parciales.
 *
 * La lista esta en formato plano a proposito: `docs/render-offline/run_offline.py` la lee
 * con una regex para replicar la matematica sin copiar las constantes a mano.
 */
private val RUN_INFO_RECTS = listOf(
    VRect(0.00f, 0.00f, 1.00f, 0.78f),
    VRect(0.04f, 0.80f, 0.92f, 0.012f),
    VRect(0.00f, 0.02f, 0.62f, 0.44f),
    VRect(0.66f, 0.02f, 0.34f, 0.20f),
    VRect(0.66f, 0.24f, 0.34f, 0.20f),
    VRect(0.00f, 0.50f, 0.48f, 0.26f),
    VRect(0.52f, 0.50f, 0.48f, 0.26f),
)

/**
 * Construye las piezas y calcula cuantos voxeles necesita cada una.
 *
 * Igual que en [VoxelMorphCard]: la subdivision toma el maximo entre la que pide el estado
 * INFO y la que pide el estado GRAFICA, para que los cubos no salgan aplastados en ningun
 * extremo del vuelo.
 */
private fun buildRunPieces(
    splits: List<RunSplit>,
    accent: Color,
    tileColor: Color,
    axisColor: Color,
    contentWDp: Float,
    contentHDp: Float,
): List<VoxelPiece> {
    fun colsFor(wNorm: Float) = (wNorm * contentWDp / RUN_VOXEL_DP).roundToInt().coerceAtLeast(1)
    fun rowsFor(hNorm: Float) = (hNorm * contentHDp / RUN_VOXEL_DP).roundToInt().coerceAtLeast(1)

    fun piece(from: VRect, to: VRect, fromColor: Color, toColor: Color) = VoxelPiece(
        from = from,
        to = to,
        fromColor = fromColor,
        toColor = toColor,
        cols = maxOf(colsFor(from.w), colsFor(to.w)),
        rows = maxOf(rowsFor(from.h), rowsFor(to.h)),
    )

    val pieces = mutableListOf<VoxelPiece>()

    // 1. Panel del resumen -> fondo del area de trazado.
    pieces += piece(
        RUN_INFO_RECTS[0],
        VRect(0f, 0.02f, 1f, 0.88f),
        accent.copy(alpha = 0.10f),
        accent.copy(alpha = 0.05f),
    )

    // 2. Separador de los chips -> linea base.
    pieces += piece(
        RUN_INFO_RECTS[1],
        VRect(0.03f, RUN_BASELINE_Y, 0.94f, 0.010f),
        axisColor.copy(alpha = 0.30f),
        axisColor,
    )

    // 3. Cada tile de estadistica -> el parcial de su km.
    splits.forEachIndexed { i, split ->
        val chartH = split.fraction * RUN_BAR_MAX_H
        pieces += piece(
            from = RUN_INFO_RECTS[2 + i],
            to = VRect(
                RUN_BAR_X0 + i * RUN_BAR_STEP_X,
                RUN_BASELINE_Y - chartH,
                RUN_BAR_W,
                chartH,
            ),
            fromColor = tileColor,
            toColor = lerp(accent, Color.White, 0.05f * i),
        )
    }

    return pieces
}

/**
 * CARD 12 - resumen de carrera -> parciales por km (estilo Strava).
 *
 * Toca la card para alternar entre el resumen y la grafica.
 *
 * @param autoToggleMs si no es null, alterna sola cada ese intervalo (modo auto-demo).
 */
@Composable
fun RunVoxelCard(
    title: String,
    distance: String,
    distanceUnit: String,
    pace: String,
    speed: String,
    time: String,
    avgHr: String,
    hint: String,
    splits: List<RunSplit>,
    barColor: Color,
    modifier: Modifier = Modifier,
    autoToggleMs: Long? = null,
) {
    var showChart by remember { mutableStateOf(false) }

    if (autoToggleMs != null) {
        LaunchedEffect(autoToggleMs) {
            while (true) {
                delay(autoToggleMs)
                showChart = !showChart
            }
        }
    }

    val t by animateFloatAsState(
        targetValue = if (showChart) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "runVoxelMorph",
    )

    val accent = barColor
    val tileColor = MaterialTheme.colorScheme.surfaceVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val infoAlpha = (1f - t / 0.30f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)

    val hrMin = splits.minOf { it.heartRate }
    val hrMax = splits.maxOf { it.heartRate }
    val hrSpan = (hrMax - hrMin).coerceAtLeast(1)
    val avgFraction = splits.map { it.fraction }.average().toFloat()
    val avgY = RUN_BASELINE_Y - avgFraction * RUN_BAR_MAX_H
    val avgPace = pace.substringBefore(" ")

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(RunVoxelCardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(CardPadding)) {
            val cw = maxWidth
            val ch = maxHeight

            val pieces = remember(splits, accent, tileColor, axisColor, cw, ch) {
                buildRunPieces(
                    splits = splits,
                    accent = accent,
                    tileColor = tileColor,
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

                            // Onda diagonal sobre el layout de DESTINO: los parciales se
                            // rearman en barrido, no todos a la vez.
                            val dstU = piece.to.x + ((col + 0.5f) / piece.cols) * piece.to.w
                            val dstV = piece.to.y + ((row + 0.5f) / piece.rows) * piece.to.h
                            val wave = (dstU + (1f - dstV)) * 0.5f

                            val delay = (0.62f * wave + 0.38f * seed).coerceIn(0f, 1f)
                            val local =
                                ((t - delay * RUN_STAGGER) / (1f - RUN_STAGGER)).coerceIn(0f, 1f)

                            // 0 en reposo, 1 en el pico del vuelo, 0 al aterrizar.
                            val flight = sin(local * RunPi).coerceAtLeast(0f)

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
                val scan = sin(t * RunPi).coerceAtLeast(0f)
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

                // --- Overlays de la grafica: ritmo medio y pulso -------------------
                if (chartAlpha > 0.01f) {
                    drawLine(
                        color = muted.copy(alpha = 0.55f * chartAlpha),
                        start = Offset(w * 0.03f, avgY * h),
                        end = Offset(w * 0.97f, avgY * h),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 8.dp.toPx())
                        ),
                    )

                    val hrPath = Path()
                    splits.forEachIndexed { i, split ->
                        val hrNorm = (split.heartRate - hrMin).toFloat() / hrSpan
                        val x = w * (RUN_BAR_X0 + i * RUN_BAR_STEP_X + RUN_BAR_W / 2f)
                        val y = h * (RUN_BASELINE_Y - (0.20f + 0.70f * hrNorm) * RUN_BAR_MAX_H)
                        if (i == 0) hrPath.moveTo(x, y) else hrPath.lineTo(x, y)
                    }
                    drawPath(
                        path = hrPath,
                        color = RunHrColor.copy(alpha = chartAlpha),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                    splits.forEachIndexed { i, split ->
                        val hrNorm = (split.heartRate - hrMin).toFloat() / hrSpan
                        val x = w * (RUN_BAR_X0 + i * RUN_BAR_STEP_X + RUN_BAR_W / 2f)
                        val y = h * (RUN_BASELINE_Y - (0.20f + 0.70f * hrNorm) * RUN_BAR_MAX_H)
                        drawCircle(
                            color = RunHrColor.copy(alpha = chartAlpha),
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, y),
                        )
                    }
                }
            }

            // --- Texto del estado INFO -------------------------------------------
            if (infoAlpha > 0.01f) {
                RunLabel(title, cw * 0.03f, ch * 0.03f, cw * 0.60f, infoAlpha, 10.sp, muted)
                RunLabel(
                    "$distance $distanceUnit",
                    cw * 0.03f, ch * 0.10f, cw * 0.60f,
                    infoAlpha, 24.sp, onSurface, FontWeight.ExtraBold,
                )

                RunLabel("RITMO", cw * 0.68f, ch * 0.045f, cw * 0.32f, infoAlpha, 8.sp, muted)
                RunLabel(
                    pace, cw * 0.68f, ch * 0.095f, cw * 0.34f,
                    infoAlpha, 11.sp, onSurface, FontWeight.Bold,
                )

                RunLabel("VELOCIDAD", cw * 0.68f, ch * 0.265f, cw * 0.32f, infoAlpha, 8.sp, muted)
                RunLabel(
                    speed, cw * 0.68f, ch * 0.315f, cw * 0.34f,
                    infoAlpha, 11.sp, onSurface, FontWeight.Bold,
                )

                RunLabel("TIEMPO", cw * 0.03f, ch * 0.525f, cw * 0.44f, infoAlpha, 8.sp, muted)
                RunLabel(
                    time, cw * 0.03f, ch * 0.575f, cw * 0.44f,
                    infoAlpha, 13.sp, onSurface, FontWeight.Bold,
                )

                RunLabel("FC MEDIA", cw * 0.55f, ch * 0.525f, cw * 0.44f, infoAlpha, 8.sp, muted)
                RunLabel(
                    avgHr, cw * 0.55f, ch * 0.575f, cw * 0.44f,
                    infoAlpha, 13.sp, onSurface, FontWeight.Bold,
                )

                RunLabel(hint, cw * 0.04f, ch * 0.825f, cw * 0.90f, infoAlpha, 9.sp, muted)
            }

            // --- Texto del estado GRAFICA ----------------------------------------
            if (chartAlpha > 0.01f) {
                RunLabel("Ritmo por km", cw * 0.03f, ch * 0.015f, cw * 0.50f, chartAlpha, 9.sp, muted)
                RunLabel("FC", cw * 0.86f, ch * 0.015f, cw * 0.12f, chartAlpha, 9.sp, RunHrColor, FontWeight.Bold)

                RunLabel(
                    "medio $avgPace", cw * 0.03f, ch * (avgY - 0.075f), cw * 0.40f,
                    chartAlpha, 8.sp, muted,
                )

                splits.forEachIndexed { i, split ->
                    val barX = RUN_BAR_X0 + i * RUN_BAR_STEP_X
                    val barTop = RUN_BASELINE_Y - split.fraction * RUN_BAR_MAX_H

                    RunLabel(
                        split.pace,
                        cw * barX, ch * (barTop - 0.075f), cw * RUN_BAR_W,
                        chartAlpha, 8.sp, muted, FontWeight.SemiBold,
                        TextAlign.Center,
                    )
                    RunLabel(
                        "${i + 1} km",
                        cw * barX, ch * 0.885f, cw * RUN_BAR_W,
                        chartAlpha, 8.sp, muted, FontWeight.Normal,
                        TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunLabel(
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
