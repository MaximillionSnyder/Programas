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

val RainCardHeight: Dp = 268.dp

private val RainBodyHeight: Dp = 168.dp

/** Estado GRAFICA: donde aterrizan los cubos. */
private const val RAIN_BASELINE = 0.88f
private const val RAIN_MAX_H = 0.76f
private const val RAIN_MIN_H = 0.05f

/** Cuanto del recorrido se dedica al escalonado entre cubos. */
private const val RAIN_STAGGER = 0.50f

/** Fraccion del recorrido propio de cada cubo que dura la caida; el resto es el impacto. */
private const val RAIN_FALL_END = 0.72f

/** Aplastado al aterrizar: se comprime en vertical y se ensancha en horizontal. */
private const val RAIN_SQUASH = 0.34f
private const val RAIN_WIDEN = 0.24f

/**
 * VARIANTE 1 - Lluvia de cubos.
 *
 * En reposo la tarjeta muestra **solo la informacion**: el grafico no se ve, porque los
 * cubos viven por encima del lienzo y el `Canvas` los recorta. Al tocar, cada cubo **cae**
 * con aceleracion de gravedad, escalonado de izquierda a derecha, y **se aplasta contra la
 * linea base** antes de quedarse quieto. Al final aparecen las etiquetas.
 *
 * Al volver, los cubos suben y desaparecen por arriba, que es el mismo camino al reves.
 *
 * @param series valores normalizados 0f..1f, uno por cubo.
 */
@Composable
fun CubeRainCard(
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
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "cubeRain",
    )
    val t by progress

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    // La info se va rapido, antes de que aterrice el primer cubo.
    val infoAlpha = (1f - t / 0.18f).coerceIn(0f, 1f)
    // Las etiquetas entran cuando ya casi todo ha aterrizado.
    val chartAlpha = ((t - 0.72f) / 0.28f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(RainCardHeight),
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

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(RainBodyHeight)) {
                val cw = maxWidth
                val ch = maxHeight

                // clipToBounds es lo que mantiene el grafico oculto: los cubos esperan
                // por encima del lienzo y no se dibujan hasta que empiezan a caer.
                Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val tNow = progress.value
                    val count = series.size
                    if (count == 0) return@Canvas

                    val cell = size.width / count

                    series.forEachIndexed { index, fraction ->
                        val start = (index.toFloat() / count) * RAIN_STAGGER
                        val span = 1f - RAIN_STAGGER
                        val local = ((tNow - start) / span).coerceIn(0f, 1f)

                        val targetH = size.height * (RAIN_MIN_H + fraction * RAIN_MAX_H)
                        val targetW = cell * 0.60f
                        val targetX = index * cell
                        val targetY = size.height * RAIN_BASELINE - targetH

                        // Punto de partida: justo por encima del lienzo, fuera de vista.
                        val startY = -targetH - size.height * 0.06f

                        val fallT = (local / RAIN_FALL_END).coerceAtMost(1f)
                        val settleT = ((local - RAIN_FALL_END) / (1f - RAIN_FALL_END)).coerceIn(0f, 1f)

                        // Gravedad: cae despacio al principio y acelera.
                        val y = startY + (targetY - startY) * (fallT * fallT)

                        // Impacto: se aplasta y se recupera.
                        val impact = sin(settleT * PI.toFloat())
                        val h = targetH * (1f - RAIN_SQUASH * impact)
                        val w = targetW * (1f + RAIN_WIDEN * impact)
                        val x = targetX - (w - targetW) / 2f
                        val yDraw = size.height * RAIN_BASELINE - h

                        // Volteretas mientras cae: la profundidad oscila.
                        val baseDepth = targetW * 0.30f
                        val spin = if (fallT < 1f) sin(fallT * PI.toFloat() * 2f) else 0f
                        val depth = baseDepth * (1f + 0.9f * spin)

                        drawCube(
                            cube = Cube(
                                x = x,
                                y = if (fallT < 1f) y else yDraw,
                                w = w,
                                h = if (fallT < 1f) h else h,
                            ),
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
                    // Pista: el hueco de abajo es donde va a caer la grafica.
                    Text(
                        text = hint,
                        modifier = Modifier
                            .offset(x = 0.dp, y = ch * (RAIN_BASELINE + 0.02f))
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
                            .offset(x = 0.dp, y = ch * (RAIN_BASELINE + 0.03f))
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
