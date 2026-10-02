package com.example.morphdemo.morph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Alto fijo: el mismo que [RecorridoCardHeight], para poder comparar las dos lado a lado. */
val RecorridoVoxelHeight: Dp = 288.dp

/**
 * Duracion del ciclo. Medida del video de referencia: 30 fotogramas a 30 fps, o sea ~1 s.
 * Es mas de dos veces mas rapida que [RecorridoCard], y esa es parte de lasensacion.
 */
private const val VoxDurationMs = 1000

// --- Malla, en fraccion del alto/ancho de la card ---------------------------------------

private const val COLS = 15
private const val ROWS = 10

/** La rejilla no llena la card: los bordes son los medidos en el video de referencia. */
private const val GRID_L = 0.005f
private const val GRID_T = 0.137f
private const val GRID_W = 0.990f
private const val GRID_H = 0.833f

/** Celda respecto al paso de la rejilla. En el video casi se tocan. */
private const val CELL = 0.82f

/** Cuanto se estrecha la cuna hacia la derecha: 0 = recto, 1 = punta. */
private const val TAPER = 0.62f

/** Reparto del orden de entrada: dominio el avance a la derecha, secundario la altura. */
private const val SWEEP = 0.74f

/** Duracion del "pop" de cada celda, en fraccion del progreso total. */
private const val POP = 0.055f

/** Probabilidad de celda de polvo (mucho mas pequena que las demas). */
private const val DUST = 0.09f

// --- Colores muestreados del video ------------------------------------------------------

private val VoxBackground = Color(0xFF222D3A)

/**
 * Rampa de las celdas, de oscura a clara. En el video el brillo crece de izquierda a
 * derecha: la masa arranca casi negra a la izquierda y termina en menta y blanco a la
 * derecha. Se recorre esta rampa en funcion de la columna, con un poco de azar encima.
 */
private val VoxRamp = listOf(
    Color(0xFF1D3145),
    Color(0xFF223049),
    Color(0xFF25374F),
    Color(0xFF2B4258),
    Color(0xFF2D4865),
    Color(0xFF35576C),
    Color(0xFF3E6774),
    Color(0xFF4A7F86),
    Color(0xFF539699),
    Color(0xFF58A5A3),
    Color(0xFF65CDC3),
    Color(0xFF6BB1B4),
    Color(0xFF8AAAB2),
    Color(0xFFA5BEC6),
    Color(0xFFC2CED0),
)

/** Una celda con su tamano, color y momento de entrada, todo ya resuelto. */
private data class Voxel(
    val col: Int,
    val row: Int,
    val color: Color,
    val scale: Float,
    val start: Float,
)

/**
 * Rejilla determinista de celdas. La cuna se construye con dos reglas:
 *
 *  - **silueta**: en la columna 0 caben todas las filas, y en la de la derecha solo cabe
 *    una franja central que se estrecha de forma lineal. De ahi el perfil de flecha.
 *  - **entrada**: el orden es el avance horizontal mas la distancia al eje, asi que la
 *    ola nace en la celda del extremo izquierdo y se abre hacia dentro mientras avanza.
 *
 * Misma semilla, mismo tablero: la animacion se puede grabar y comparar fotograma a fotograma.
 */
private fun buildVoxels(): List<Voxel> {
    val rnd = Random(20260930)
    val half = (ROWS - 1) / 2f
    return buildList {
        for (col in 0 until COLS) {
            val colT = col / (COLS - 1f)
            // Media anchura vertical que todavia hay en esta columna.
            val halfSpan = 1f - TAPER * colT
            for (row in 0 until ROWS) {
                val normRow = abs(row - half) / half
                if (normRow > halfSpan) continue
                val roll = rnd.nextFloat()
                val scale = when {
                    roll < DUST -> 0.40f + rnd.nextFloat() * 0.12f
                    roll < 0.34f -> 0.66f + rnd.nextFloat() * 0.10f
                    else -> 0.80f + rnd.nextFloat() * 0.12f
                }
                // El brillo lo manda la columna; el azar solo lo desordena un poco.
                val bright = (colT * 0.88f + (rnd.nextFloat() - 0.5f) * 0.46f).coerceIn(0f, 1f)
                val color = VoxRamp[(bright * (VoxRamp.size - 1)).toInt()]
                add(
                    Voxel(
                        col = col,
                        row = row,
                        color = color,
                        scale = scale,
                        start = colT * SWEEP + normRow * (1f - SWEEP),
                    )
                )
            }
        }
    }
}

private fun range(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)

/**
 * VARIANTE 11 - Recorrido en voxeles, tomada del video de referencia `VID_20260930_011102`.
 *
 * Que cambia respecto a la variante 10 ([RecorridoCard]), que tambien salio de un video:
 *
 *  - la malla es de 15 x 10 en vez de 10 x 6, y el paso de celda es cuadrado;
 *  - las celdas **se quedan**: no hay disolucion ni escombros, la forma se construye y ya;
 *  - la silueta es una cuna que se estrecha hacia la derecha, no un rectangulo;
 *  - el brillo crece de izquierda a derecha: la masa arranca casi negra y acaba en menta;
 *  - y el ciclo dura ~1 s en vez de 2.4 s.
 *
 * La card es solo la animacion: sin textos, sin numeros y sin ruta.
 *
 * Se dispara sola al aparecer; al tocar la card se vuelve a reproducir.
 *
 * @param autoReplayMs si no es null, repite la animacion en bucle (modo auto-demo).
 */
@Composable
fun RecorridoVoxelCard(
    modifier: Modifier = Modifier,
    autoReplayMs: Long? = null,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val voxels = remember { buildVoxels() }

    val replay: () -> Unit = {
        scope.launch {
            progress.stop()
            progress.snapTo(0f)
            progress.animateTo(1f, tween(VoxDurationMs, easing = LinearEasing))
        }
    }

    LaunchedEffect(autoReplayMs) {
        if (autoReplayMs == null) {
            progress.animateTo(1f, tween(VoxDurationMs, easing = LinearEasing))
            return@LaunchedEffect
        }
        val pause = (autoReplayMs - VoxDurationMs).coerceAtLeast(200L)
        while (true) {
            progress.stop()
            progress.snapTo(0f)
            progress.animateTo(1f, tween(VoxDurationMs, easing = LinearEasing))
            delay(pause)
        }
    }

    Card(
        onClick = replay,
        modifier = modifier.fillMaxWidth().height(RecorridoVoxelHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = VoxBackground),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVoxels(progress.value, voxels)
        }
    }
}

/**
 * Las celdas entran con un pop de menos a mas: aparecen de cero, se pasan de tamano al
 * crecer y se asientan. Las que todavia no han entrado no se dibujan, asi que en t=0 no hay
 * ni una celda en pantalla.
 */
private fun DrawScope.drawVoxels(t: Float, voxels: List<Voxel>) {
    val gx = size.width * GRID_L
    val gy = size.height * GRID_T
    val cellW = size.width * GRID_W / COLS
    val cellH = size.height * GRID_H / ROWS
    val pitch = minOf(cellW, cellH)
    val w = pitch * CELL
    val h = pitch * CELL
    val radius = pitch * 0.10f

    voxels.forEach { v ->
        val raw = range(t, v.start, v.start + POP)
        if (raw <= 0f) return@forEach
        // Leve rebote: se pasa de tamano y vuelve, sin llegar a rebotar de mas.
        val pop = easeOutCubic(raw) * (1f + 0.10f * sin(PI.toFloat() * raw))

        val cx = gx + (v.col + 0.5f) * cellW
        val cy = gy + (v.row + 0.5f) * cellH
        val bw = w * v.scale * pop
        val bh = h * v.scale * pop
        if (bw <= 0.5f) return@forEach

        drawRoundRect(
            color = v.color,
            topLeft = Offset(cx - bw / 2f, cy - bh / 2f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(radius * v.scale, radius * v.scale),
        )
    }
}
