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
import kotlin.math.abs
import kotlin.math.sin

val ConvergeCardHeight: Dp = 268.dp

private val ConvergeBodyHeight: Dp = 168.dp

private const val CONV_BASELINE = 0.88f
private const val CONV_MAX_H = 0.76f
private const val CONV_MIN_H = 0.05f

/** Cuanto del recorrido se dedica al escalonado: arrancan desde el centro hacia fuera. */
private const val CONV_STAGGER = 0.45f

/** Arco vertical que describe cada cubo mientras entra. */
private const val CONV_ARC = 0.14f

/**
 * VARIANTE 5 - Convergencia desde los dos bordes.
 *
 * Movimiento horizontal, distinto de caer (variante 1) o nacer del dato (variante 2): los
 * cubos **entran por los lados** —la mitad izquierda desde la izquierda, la mitad derecha
 * desde la derecha— y se deslizan hasta su sitio describiendo un arco.
 *
 * El escalonado va **de dentro hacia fuera**: los cubos centrales aterrizan primero y la
 * grafica se cierra desde el medio hacia los extremos.
 *
 * @param series valores normalizados 0f..1f, uno por cubo.
 */
@Composable
fun CubeConvergeCard(
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
        label = "cubeConverge",
    )
    val t by progress

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    val infoAlpha = (1f - t / 0.18f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.76f) / 0.24f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(ConvergeCardHeight),
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

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(ConvergeBodyHeight)) {
                val cw = maxWidth
                val ch = maxHeight

                Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val tNow = progress.value
                    val count = series.size
                    if (count == 0) return@Canvas

                    val cell = size.width / count
                    val middle = (count - 1) / 2f

                    series.forEachIndexed { index, fraction ->
                        // Escalonado de dentro hacia fuera: el centro primero.
                        val distance = abs(index - middle) / middle   // 0 en el centro, 1 en los bordes
                        val start = distance * CONV_STAGGER
                        val span = 1f - CONV_STAGGER
                        val local = ((tNow - start) / span).coerceIn(0f, 1f)
                        val eased = FastOutSlowInEasing.transform(local)

                        val targetH = size.height * (CONV_MIN_H + fraction * CONV_MAX_H)
                        val targetW = cell * 0.60f
                        val targetX = index * cell
                        val targetY = size.height * CONV_BASELINE - targetH

                        // Entra por el lado que le toca.
                        val fromLeft = index < count / 2
                        val startX = if (fromLeft) -targetW - size.width * 0.06f
                                     else size.width + size.width * 0.06f

                        val x = startX + (targetX - startX) * eased
                        val y = targetY - sin(eased * PI.toFloat()) * size.height * CONV_ARC

                        // Se estira un poco al entrar: viene lanzado.
                        val stretch = 1f + 0.35f * sin(eased * PI.toFloat())
                        val w = targetW * stretch
                        val h = targetH

                        val baseDepth = targetW * 0.30f
                        val depth = baseDepth * (1f + 0.6f * sin(eased * PI.toFloat()))

                        drawCube(
                            cube = Cube(
                                x = x - (w - targetW) / 2f,
                                y = y,
                                w = w,
                                h = h,
                            ),
                            depth = depth,
                            color = cubeColor,
                        )
                    }
                }

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
                            .offset(x = 0.dp, y = ch * (CONV_BASELINE + 0.02f))
                            .alpha(infoAlpha),
                        fontSize = 10.sp,
                        color = muted,
                        maxLines = 1,
                    )
                }

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
                            .offset(x = 0.dp, y = ch * (CONV_BASELINE + 0.03f))
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
