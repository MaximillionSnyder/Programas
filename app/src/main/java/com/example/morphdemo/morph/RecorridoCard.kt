package com.example.morphdemo.morph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Alto fijo de la card: el mismo contenedor en todos los estados. */
val RecorridoCardHeight: Dp = 288.dp

private const val RecorridoDurationMs = 2400

/** Area de los cubos: toda la card, con un pelin de desborde que el borde recorta. */
private const val CUBE_X = -0.01f
private const val CUBE_Y = -0.02f
private const val CUBE_W = 1.02f
private const val CUBE_H = 1.05f

/** Area de la ruta: la zona media, para no pisar el titulo ni el pie. */
private const val ROUTE_X = 0.02f
private const val ROUTE_Y = 0.19f
private const val ROUTE_W = 0.97f
private const val ROUTE_H = 0.65f

private const val COLS = 10
private const val ROWS = 6

// --- Tiempos, todos en fraccion del progreso total -------------------------------------

private const val CUBE_POP = 0.06f
private const val FADE_DUR = 0.12f
private const val FALL_DUR = 0.28f
private const val DEBRIS_SCALE = 0.34f

// --- Colores muestreados del video de referencia ----------------------------------------

private val RecorridoBackground = Color(0xFF242E3E)
private val RecorridoText = Color(0xFFF2F7F8)
private val RouteColor = Color(0xFF5FE7D5)
private val RouteGlow = Color(0xFF46D8C6)
private val StartDot = Color(0xFFF9B4BB)
private val EndDot = Color(0xFF74EEDE)

/**
 * Paleta de los cubos, ponderada: casi todo azul oscuro con acentos teal, menta y gris.
 * El segundo valor es cuantas veces entra el color en el sorteo (peso relativo).
 */
private val CubePalette: List<Pair<Color, Int>> = listOf(
    Color(0xFF1A2A40) to 8,
    Color(0xFF1F2E44) to 7,
    Color(0xFF24374F) to 8,
    Color(0xFF2A3F58) to 7,
    Color(0xFF2C4059) to 6,
    Color(0xFF314A62) to 4,
    Color(0xFF345063) to 4,
    Color(0xFF3A5D6C) to 3,
    Color(0xFF45707F) to 3,
    Color(0xFF498A90) to 4,
    Color(0xFF559E9D) to 4,
    Color(0xFF5AB0AF) to 4,
    Color(0xFF68D4C9) to 3,
    Color(0xFF79D2C9) to 4,
    Color(0xFF8DDDD4) to 2,
    Color(0xFFB1BBC5) to 4,
    Color(0xFFC0CBD1) to 3,
)

/**
 * Ruta normalizada (0f..1f dentro del area de la ruta), de inicio a fin. Son los puntos
 * muestreados del track del video: subida corta, meseta con un escalon, y remate empinado.
 * No se suavizan con curvas: los micro-escalones del GPX son parte del dibujo.
 */
private val Route = listOf(
    Offset(0.163f, 0.873f),
    Offset(0.238f, 0.809f),
    Offset(0.267f, 0.774f),
    Offset(0.291f, 0.667f),
    Offset(0.316f, 0.652f),
    Offset(0.340f, 0.631f),
    Offset(0.364f, 0.601f),
    Offset(0.389f, 0.576f),
    Offset(0.413f, 0.570f),
    Offset(0.437f, 0.560f),
    Offset(0.462f, 0.570f),
    Offset(0.486f, 0.540f),
    Offset(0.511f, 0.449f),
    Offset(0.535f, 0.418f),
    Offset(0.559f, 0.403f),
    Offset(0.584f, 0.413f),
    Offset(0.608f, 0.413f),
    Offset(0.632f, 0.403f),
    Offset(0.657f, 0.388f),
    Offset(0.681f, 0.357f),
    Offset(0.705f, 0.317f),
    Offset(0.730f, 0.286f),
    Offset(0.754f, 0.271f),
    Offset(0.808f, 0.215f),
)

/** Etiqueta "300 puntos" del final: a la derecha del punto, como en el video. */
private val EndLabelAnchor = Offset(ROUTE_X + 0.773f * ROUTE_W, ROUTE_Y + 0.33f * ROUTE_H)

/** Un cubo ya resuelto con su orden de entrada y de salida. */
private data class RecorridoCube(
    val col: Int,
    val row: Int,
    val color: Color,
    val size: Float,
    val dx: Float,
    val dy: Float,
    val appearStart: Float,
    val vanishStart: Float,
    val falls: Boolean,
    val landY: Float,
)

/**
 * Rejilla determinista de cubos. Nada de azar por fotograma: la misma semilla produce
 * siempre el mismo tablero, asi la animacion se puede grabar y comparar.
 */
private fun buildCubes(): List<RecorridoCube> {
    val rnd = Random(20260927)
    val palette = buildList {
        CubePalette.forEach { (color, weight) -> repeat(weight) { add(color) } }
    }
    return buildList {
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                // Ola de entrada: frente en cuna. Nace en el borde izquierdo a media
                // altura y avanza en diagonal; cada columna se abre a lo alto mientras la
                // siguiente arranca, que es lo que dibuja el triangulo del video.
                val dc = (col + 0.5f) / COLS
                val dr = abs((row + 0.5f - 3f) * 2f / ROWS)
                val dist = dc + dr * 0.48f
                val size = if (rnd.nextFloat() < 0.07f) {
                    0.45f + rnd.nextFloat() * 0.15f
                } else {
                    0.80f + rnd.nextFloat() * 0.15f
                }
                val falls = row >= 3 && rnd.nextFloat() < 0.28f
                add(
                    RecorridoCube(
                        col = col,
                        row = row,
                        color = palette[rnd.nextInt(palette.size)],
                        size = size,
                        dx = (rnd.nextFloat() - 0.5f) * 0.08f,
                        dy = (rnd.nextFloat() - 0.5f) * 0.08f,
                        appearStart = 0.01f + dist * 0.20f + rnd.nextFloat() * 0.05f,
                        // La disolucion barre de izquierda a derecha, detras de la ola.
                        vanishStart = if (falls) {
                            0.26f + col * 0.022f + rnd.nextFloat() * 0.06f
                        } else {
                            0.26f + col * 0.026f + rnd.nextFloat() * 0.06f
                        },
                        falls = falls,
                        landY = 0.95f + rnd.nextFloat() * 0.06f,
                    )
                )
            }
        }
    }
}

private fun range(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOutCubic(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

private fun routePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    return path
}

/**
 * Card "RECORRIDO": la rejilla de cubos entra en ola, se disuelve dejando caer escombros
 * al borde inferior y sobre ella se dibuja la ruta desde el punto de inicio (rosa) hasta
 * el de fin (menta). Los textos entran escalonados y el pie se escribe a maquina.
 *
 * Se dispara sola al aparecer; al tocar la card se vuelve a reproducir.
 *
 * @param autoReplayMs si no es null, repite la animacion en bucle (modo auto-demo).
 */
@Composable
fun RecorridoCard(
    modifier: Modifier = Modifier,
    title: String = "RECORRIDO",
    trailing: String = "300 puntos",
    endLabel: String = "300 puntos",
    caption: String = "Inicio y fin marcados",
    autoReplayMs: Long? = null,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val cubes = remember { buildCubes() }

    val replay: () -> Unit = {
        scope.launch {
            progress.stop()
            progress.snapTo(0f)
            progress.animateTo(1f, tween(RecorridoDurationMs, easing = LinearEasing))
        }
    }

    LaunchedEffect(autoReplayMs) {
        if (autoReplayMs == null) {
            progress.animateTo(1f, tween(RecorridoDurationMs, easing = LinearEasing))
            return@LaunchedEffect
        }
        // En auto-demo el periodo es lo que dura un ciclo completo: reveal + pausa.
        val pause = (autoReplayMs - RecorridoDurationMs).coerceAtLeast(200L)
        while (true) {
            progress.stop()
            progress.snapTo(0f)
            progress.animateTo(1f, tween(RecorridoDurationMs, easing = LinearEasing))
            delay(pause)
        }
    }

    // Se lee el valor en composicion: los textos lo necesitan para su alfa.
    val t = progress.value

    val titleT = range(t, 0.30f, 0.40f)
    val endLabelT = range(t, 0.10f, 0.18f)
    val captionT = range(t, 0.58f, 0.86f)

    Card(
        onClick = replay,
        modifier = modifier.fillMaxWidth().height(RecorridoCardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = RecorridoBackground),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cw = maxWidth
            val ch = maxHeight

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRecorridoCubes(progress.value, cubes)
                drawRecorridoRoute(progress.value)
            }

            Text(
                text = title,
                modifier = Modifier
                    .offset(x = 20.dp, y = 16.dp + 6.dp * (1f - titleT))
                    .alpha(titleT),
                fontSize = 15.sp,
                letterSpacing = 0.6.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RecorridoText,
                maxLines = 1,
            )

            Text(
                text = trailing,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 20.dp, top = 16.dp)
                    .alpha(titleT),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RecorridoText,
                maxLines = 1,
            )

            Text(
                text = endLabel,
                modifier = Modifier
                    .offset(x = cw * EndLabelAnchor.x, y = ch * EndLabelAnchor.y)
                    .alpha(endLabelT),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RecorridoText,
                maxLines = 1,
            )

            val shown = (captionT * caption.length).toInt().coerceIn(0, caption.length)
            Text(
                text = caption.take(shown),
                modifier = Modifier.offset(x = 20.dp, y = ch * 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RecorridoText,
                maxLines = 1,
            )
        }
    }
}

/**
 * Los cubos entran en ola (escala con rebote), se mantienen y luego se disuelven: unos
 * se desvanecen con una deriva hacia abajo y otros caen de verdad hasta el borde inferior,
 * donde quedan como escombros.
 */
private fun DrawScope.drawRecorridoCubes(t: Float, cubes: List<RecorridoCube>) {
    val gx = size.width * CUBE_X
    val gy = size.height * CUBE_Y
    val cellW = size.width * CUBE_W / COLS
    val cellH = size.height * CUBE_H / ROWS

    cubes.forEach { cube ->
        val raw = ((t - cube.appearStart) / CUBE_POP).coerceIn(0f, 1f)
        if (raw <= 0f) return@forEach
        val pop = easeOutCubic(raw)

        var w = cellW * cube.size * pop * (1f + 0.10f * sin(PI.toFloat() * raw))
        var h = cellH * cube.size * pop * (1f + 0.10f * sin(PI.toFloat() * raw))
        var cx = gx + (cube.col + 0.5f) * cellW + cube.dx * cellW
        var cy = gy + (cube.row + 0.5f) * cellH + cube.dy * cellH
        var alpha = pop

        if (cube.falls) {
            val v = ((t - cube.vanishStart) / FALL_DUR).coerceIn(0f, 1f)
            if (v > 0f) {
                // Caida con gravedad: se queda donde aterriza, ya encogido.
                cy += (size.height * cube.landY - cy) * (v * v)
                val s = 1f - (1f - DEBRIS_SCALE) * v
                w *= s
                h *= s
            }
        } else {
            val v = ((t - cube.vanishStart) / FADE_DUR).coerceIn(0f, 1f)
            if (v > 0f) {
                alpha *= 1f - v
                cy += cellH * 0.5f * v
                val s = 1f - 0.35f * v
                w *= s
                h *= s
            }
        }
        if (alpha <= 0.01f || w <= 0f) return@forEach

        drawRoundRect(
            color = cube.color.copy(alpha = alpha),
            topLeft = Offset(cx - w / 2f, cy - h / 2f),
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.12f, h * 0.12f),
        )
    }
}

/**
 * La ruta se dibuja recortando el trazo con `PathMeasure`: el punto rosa aparece primero,
 * la linea crece desde el, y el punto menta cierra el recorrido. El trazo lleva un halo
 * tenue debajo para imitar el brillo del video.
 */
private fun DrawScope.drawRecorridoRoute(t: Float) {
    val pts = Route.map { p ->
        Offset(
            size.width * (ROUTE_X + p.x * ROUTE_W),
            size.height * (ROUTE_Y + p.y * ROUTE_H),
        )
    }
    if (pts.size < 2) return

    val path = routePath(pts)
    val measure = PathMeasure().apply { setPath(path, false) }

    val drawT = range(t, 0.13f, 0.70f)
    val stroke = size.width * 0.013f

    if (drawT > 0f) {
        val segment = Path()
        measure.getSegment(0f, (measure.length * drawT).coerceAtLeast(0.001f), segment, true)
        drawPath(
            path = segment,
            color = RouteGlow.copy(alpha = 0.10f),
            style = Stroke(width = stroke * 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            path = segment,
            color = RouteColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    val startAlpha = range(t, 0.10f, 0.17f)
    if (startAlpha > 0f) {
        drawCircle(color = StartDot.copy(alpha = startAlpha), radius = size.width * 0.026f, center = pts.first())
    }

    val endAlpha = range(t, 0.66f, 0.72f)
    if (endAlpha > 0f) {
        drawCircle(color = EndDot.copy(alpha = endAlpha), radius = size.width * 0.028f, center = pts.last())
    }
}
