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

val SlabCardHeight: Dp = 268.dp

private val SlabBodyHeight: Dp = 168.dp

private const val SLAB_BASELINE = 0.88f
private const val SLAB_MAX_H = 0.76f
private const val SLAB_MIN_H = 0.05f

/** Hasta que fraccion del recorrido dura la caida del bloque entero. */
private const val SLAB_FALL_END = 0.52f

/** Escalonado de la segunda fase: el bloque se resuelve en ola. */
private const val SLAB_RESOLVE_STAGGER = 0.28f

/** Aplastado del bloque contra la linea base. */
private const val SLAB_SQUASH = 0.18f

/**
 * VARIANTE 4 - El bloque que se resuelve.
 *
 * Dos fases muy marcadas:
 *
 * 1. **Cae un bloque macizo.** Todos los cubos bajan juntos, a la misma altura, formando un
 *    rectangulo solido. No hay escalonado: tiene que leerse como una sola pieza.
 * 2. **El bloque se resuelve.** Ya apoyado, cada cubo se estira o se encoge hasta su altura
 *    real, escalonado de izquierda a derecha, como si el bloque se asentara.
 *
 * La gracia es que el espectador ve llegar "la grafica entera" y luego la ve tomar forma,
 * en vez de ver aparecer barritas una a una.
 *
 * @param series valores normalizados 0f..1f, uno por cubo.
 */
@Composable
fun CubeSlabCard(
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
        animationSpec = tween(durationMillis = 1150, easing = FastOutSlowInEasing),
        label = "cubeSlab",
    )
    val t by progress

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    val infoAlpha = (1f - t / 0.16f).coerceIn(0f, 1f)
    val chartAlpha = ((t - 0.80f) / 0.20f).coerceIn(0f, 1f)

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(SlabCardHeight),
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

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(SlabBodyHeight)) {
                val cw = maxWidth
                val ch = maxHeight

                Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                    val tNow = progress.value
                    val count = series.size
                    if (count == 0) return@Canvas

                    val cell = size.width / count

                    // --- Fase 1: la caida es comun a todos (es una sola pieza) ---
                    val fallT = (tNow / SLAB_FALL_END).coerceAtMost(1f)
                    val easedFall = fallT * fallT   // gravedad
                    val slabH = size.height * SLAB_MAX_H
                    val slabTop = size.height * SLAB_BASELINE - slabH
                    val startY = -slabH - size.height * 0.05f
                    val blockTop = startY + (slabTop - startY) * easedFall

                    // Aplastado al aterrizar.
                    val impactT = ((tNow - SLAB_FALL_END) / 0.12f).coerceIn(0f, 1f)
                    val impact = sin(impactT * PI.toFloat()) * SLAB_SQUASH
                    val blockH = slabH * (1f - impact)
                    val blockBottom = size.height * SLAB_BASELINE
                    val blockTopDraw = blockBottom - blockH

                    series.forEachIndexed { index, fraction ->
                        // --- Fase 2: el bloque se resuelve, escalonado ---
                        val start = SLAB_FALL_END + (index.toFloat() / count) * SLAB_RESOLVE_STAGGER
                        val span = 1f - SLAB_FALL_END - SLAB_RESOLVE_STAGGER
                        val resolveT = ((tNow - start) / span).coerceIn(0f, 1f)
                        val eased = FastOutSlowInEasing.transform(resolveT)

                        val targetH = size.height * (SLAB_MIN_H + fraction * SLAB_MAX_H)
                        val targetW = cell * 0.60f

                        val h = blockH + (targetH - blockH) * eased
                        val x = index * cell
                        val y = blockBottom - h

                        val baseDepth = targetW * 0.30f
                        val depth = baseDepth * (1f + 0.5f * impact)

                        drawCube(
                            cube = Cube(x = x, y = y, w = targetW, h = h),
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
                            .offset(x = 0.dp, y = ch * (SLAB_BASELINE + 0.02f))
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
                            .offset(x = 0.dp, y = ch * (SLAB_BASELINE + 0.03f))
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
