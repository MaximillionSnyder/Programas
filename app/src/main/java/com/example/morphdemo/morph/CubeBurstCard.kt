package com.example.morphdemo.morph

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

val BurstCardHeight: Dp = 268.dp

private val BurstBodyHeight: Dp = 168.dp

/** Estado GRAFICA: donde aterrizan los cubos. */
private const val BURST_BASELINE = 0.88f
private const val BURST_MAX_H = 0.76f
private const val BURST_MIN_H = 0.05f

/** Centro del numero grande: de ahi nacen todos los cubos. */
private const val ORIGIN_X = 0.07f
private const val ORIGIN_Y = 0.24f

/** Dispersion del enjambre alrededor del numero, en coordenadas normalizadas. */
private const val JITTER_X = 0.05f
private const val JITTER_Y = 0.07f

/** Cuanto del recorrido propio de cada cubo dedica a crecer desde cero. */
private const val BURST_GROW = 0.30f

/** Altura del arco que describe cada cubo al volar. */
private const val BURST_ARC = 0.26f

private const val BURST_STAGGER = 0.40f

/**
 * Generador pseudoaleatorio determinista: el mismo indice da siempre el mismo
 * desplazamiento, asi el enjambre no cambia entre fotogramas ni entre ejecuciones.
 */
private fun jitter(index: Int, salt: Int): Float {
    var h = index * 374761393 + salt * 668265263
    h = (h xor (h shr 13)) * 1274126177
    h = h xor (h shr 16)
    return ((h and 0xFFFF).toFloat() / 0xFFFF.toFloat()) * 2f - 1f
}

/**
 * VARIANTE 2 - El dato se deshace en cubos.
 *
 * En reposo la tarjeta muestra **solo la informacion**: el grafico no existe. Al tocar,
 * los cubos **nacen del propio numero**, crecen desde tamano cero y vuelan en **arco**
 * hasta su posicion en la grafica, mientras el numero se desvanece.
 *
 * La idea es que no parezca que aparece una grafica, sino que **el dato se descompone** en
 * las piezas que lo explican.
 *
 * @param series valores normalizados 0f..1f, uno por cubo.
 */
@Composable
fun CubeBurstCard(
    title: String,
    trailing: String,
    headlineLabel: String,
    headlineValue: String,
    headlineUnit: String?,
    details: String,
    hint: String,
    maxLabel: String,
    minLabel: String,
    series: List<Float>,
    cubeColor: Color,
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

    val progress: State<Float> = animateFloatAsState(
        targetValue = if (showChart) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "cubeBurst",
    )
    val t by progress

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    // El numero se desvanece mientras los cubos crecen: se "deshace" en ellos.
    val infoAlpha = (1f - t / 0.22f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.70f) / 0.30f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(BurstCardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = muted,
                )
                Text(text = trailing, fontSize = 11.sp, color = muted)
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(BurstBodyHeight)) {
                val cw = maxWidth
                val ch = maxHeight

                Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val tNow = progress.value
                    val count = series.size
                    if (count == 0) return@Canvas

                    val cell = size.width / count
                    val originX = size.width * ORIGIN_X
                    val originY = size.height * ORIGIN_Y

                    series.forEachIndexed { index, fraction ->
                        val start = (index.toFloat() / count) * BURST_STAGGER
                        val span = 1f - BURST_STAGGER
                        val local = ((tNow - start) / span).coerceIn(0f, 1f)

                        val targetH = size.height * (BURST_MIN_H + fraction * BURST_MAX_H)
                        val targetW = cell * 0.60f
                        val targetX = index * cell
                        val targetY = size.height * BURST_BASELINE - targetH

                        // Nace en un punto del enjambre alrededor del numero.
                        val p0x = originX + jitter(index, 1) * size.width * JITTER_X
                        val p0y = originY + jitter(index, 2) * size.height * JITTER_Y

                        // Curva de Bezier cuadratica: el punto de control va por encima,
                        // asi el vuelo describe un arco y no una linea recta.
                        val c1x = (p0x + targetX) / 2f
                        val c1y = minOf(p0y, targetY) - size.height * BURST_ARC
                        val u = 1f - local
                        val x = u * u * p0x + 2f * u * local * c1x + local * local * targetX
                        val y = u * u * p0y + 2f * u * local * c1y + local * local * targetY

                        // Crece desde cero: al principio es un punto, no un cubo.
                        val grow = (local / BURST_GROW).coerceAtMost(1f)
                        val w = targetW * grow
                        val h = targetH * grow

                        // Voltereta mientras vuela.
                        val baseDepth = targetW * 0.30f
                        val spin = if (local < 1f) sin(local * PI.toFloat() * 1.5f) else 0f
                        val depth = baseDepth * (1f + 0.9f * spin)

                        drawCube(
                            cube = Cube(x = x, y = y, w = w, h = h),
                            depth = depth,
                            color = cubeColor,
                        )
                    }
                }

                // --- Estado DATO: solo informacion ------------------------------
                if (infoAlpha > 0.01f) {
                    Text(
                        text = headlineLabel,
                        modifier = Modifier.offset(x = 0.dp, y = 0.dp).alpha(infoAlpha),
                        fontSize = 11.sp,
                        color = muted,
                        maxLines = 1,
                    )
                    Row(
                        modifier = Modifier.offset(x = 0.dp, y = ch * 0.10f).alpha(infoAlpha),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            text = headlineValue,
                            fontSize = 34.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = onSurface,
                            maxLines = 1,
                        )
                        if (headlineUnit != null) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = headlineUnit,
                                modifier = Modifier.padding(bottom = 5.dp),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = muted,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = details,
                        modifier = Modifier.offset(x = 0.dp, y = ch * 0.46f).alpha(infoAlpha),
                        fontSize = 11.sp,
                        color = muted,
                        maxLines = 1,
                    )
                    Text(
                        text = hint,
                        modifier = Modifier
                            .offset(x = 0.dp, y = ch * (BURST_BASELINE + 0.02f))
                            .alpha(infoAlpha),
                        fontSize = 10.sp,
                        color = muted,
                        maxLines = 1,
                    )
                }

                // --- Estado GRAFICA ---------------------------------------------
                if (chartAlpha > 0.01f) {
                    Text(
                        text = maxLabel,
                        modifier = Modifier.offset(x = 0.dp, y = 0.dp).alpha(chartAlpha),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = muted,
                        maxLines = 1,
                    )
                    Text(
                        text = minLabel,
                        modifier = Modifier
                            .offset(x = 0.dp, y = ch * (BURST_BASELINE + 0.03f))
                            .alpha(chartAlpha),
                        fontSize = 10.sp,
                        color = muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
