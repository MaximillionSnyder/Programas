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
import kotlin.math.cos
import kotlin.math.sin

val FlipCardHeight: Dp = 268.dp

private val FlipBodyHeight: Dp = 168.dp

private const val FLIP_BASELINE = 0.88f
private const val FLIP_MAX_H = 0.76f
private const val FLIP_MIN_H = 0.05f

/** Escalonado: mas amplio que en las otras variantes porque el giro es rapido. */
private const val FLIP_STAGGER = 0.55f

/** Cuanto del recorrido propio dura el giro; el resto es el asentamiento. */
private const val FLIP_TURN_END = 0.70f

/**
 * VARIANTE 3 - Cubos que se ponen de pie.
 *
 * Mecanica distinta a las anteriores: aqui los cubos **no viajan**. Cada uno esta ya en su
 * sitio, pero **girado 90 grados sobre su eje vertical**, asi que de perfil no ocupa nada y
 * no se ve. Al tocar, cada cubo **gira hasta quedar de frente** y crece a su altura,
 * escalonado de izquierda a derecha: la grafica se levanta como una fila de fichas.
 *
 * El giro se proyecta a 2D encogiendo el ancho (`|cos(angulo)|`), que es exactamente lo que
 * hace un objeto al rotar sobre su eje vertical visto de frente.
 *
 * @param series valores normalizados 0f..1f, uno por cubo.
 */
@Composable
fun CubeFlipCard(
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
        animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing),
        label = "cubeFlip",
    )
    val t by progress

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    val infoAlpha = (1f - t / 0.18f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.75f) / 0.25f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(FlipCardHeight),
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

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(FlipBodyHeight)) {
                val cw = maxWidth
                val ch = maxHeight

                Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val tNow = progress.value
                    val count = series.size
                    if (count == 0) return@Canvas

                    val cell = size.width / count

                    series.forEachIndexed { index, fraction ->
                        val start = (index.toFloat() / count) * FLIP_STAGGER
                        val span = 1f - FLIP_STAGGER
                        val local = ((tNow - start) / span).coerceIn(0f, 1f)

                        val turnT = (local / FLIP_TURN_END).coerceAtMost(1f)
                        val settleT = ((local - FLIP_TURN_END) / (1f - FLIP_TURN_END)).coerceIn(0f, 1f)

                        // De perfil (90 grados) a de frente (0 grados).
                        val eased = FastOutSlowInEasing.transform(turnT)
                        val angle = (1f - eased) * (PI.toFloat() / 2f)

                        // Proyeccion del giro: el ancho se encoge con el coseno.
                        val targetW = cell * 0.60f
                        val w = targetW * cos(angle)

                        // La altura crece con el mismo progreso.
                        val targetH = size.height * (FLIP_MIN_H + fraction * FLIP_MAX_H)
                        val h = targetH * eased

                        // Centrado en su celda: al girar debe quedarse en el sitio.
                        val x = index * cell + (targetW - w) / 2f
                        val y = size.height * FLIP_BASELINE - h

                        // Un respiro al final: se asienta sin llegar a rebotar.
                        val settle = sin(settleT * PI.toFloat()) * size.height * 0.012f

                        // Mientras gira esta mas grueso: se esta viendo de canto.
                        val baseDepth = targetW * 0.30f
                        val depth = baseDepth * (1f + 1.1f * (1f - eased))

                        drawCube(
                            cube = Cube(x = x, y = y - settle, w = w, h = h),
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
                            .offset(x = 0.dp, y = ch * (FLIP_BASELINE + 0.02f))
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
                            .offset(x = 0.dp, y = ch * (FLIP_BASELINE + 0.03f))
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
