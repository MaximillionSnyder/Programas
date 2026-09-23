package com.example.morphdemo.morph

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morphdemo.data.Category

/**
 * CARD B - morph con SharedTransitionLayout + Modifier.sharedElement.
 *
 * Aqui NO se interpolan rectangulos a mano. Se escriben dos layouts reales y normales
 * (Column/Row/Text/Box) y se le dice a Compose que elementos son "el mismo" en ambos
 * estados mediante una key. Compose calcula las bounds de cada uno en los dos layouts
 * y las anima.
 *
 * La card mantiene el mismo tamano porque ambos estados viven dentro de un
 * AnimatedContent con fillMaxSize() y la Card tiene altura fija: lo que se mueve
 * son las piezas, nunca el contenedor.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedMorphCard(
    title: String,
    amount: String,
    subtitle: String,
    categories: List<Category>,
    modifier: Modifier = Modifier,
) {
    var showChart by remember { mutableStateOf(false) }

    Card(
        onClick = { showChart = !showChart },
        modifier = modifier.fillMaxWidth().height(MorphCardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        SharedTransitionLayout(
            modifier = Modifier.fillMaxSize().padding(CardPadding)
        ) {
            // `this` es SharedTransitionScope: se captura para pasarlo a los estados.
            val shared = this

            AnimatedContent(
                targetState = showChart,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(tween(durationMillis = 260, delayMillis = 170)) togetherWith
                        fadeOut(tween(durationMillis = 170))
                },
                label = "sharedMorph",
            ) { chart ->
                // `this` es AnimatedContentScope, que implementa AnimatedVisibilityScope.
                val visibility: AnimatedContentScope = this

                Box(Modifier.fillMaxSize()) {
                    if (chart) {
                        shared.ChartState(visibility, amount, categories)
                    } else {
                        shared.InfoState(visibility, title, amount, subtitle, categories)
                    }
                }
            }
        }
    }
}

// --- Estado 1: la informacion ------------------------------------------------------

@Composable
private fun SharedTransitionScope.InfoState(
    avs: AnimatedVisibilityScope,
    title: String,
    amount: String,
    subtitle: String,
    categories: List<Category>,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                // El panel completo viaja hasta convertirse en la canaleta del eje Y.
                .sharedElement(rememberSharedContentState("panel"), avs)
                .clip(RoundedCornerShape(18.dp))
                .background(accent.copy(alpha = 0.10f))
                .padding(14.dp),
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = muted,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = amount,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = muted,
                maxLines = 1,
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            categories.forEachIndexed { index, category ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        // Misma key que la barra del estado grafica: son la misma pieza.
                        .sharedElement(rememberSharedContentState("bar$index"), avs)
                        .clip(RoundedCornerShape(6.dp))
                        .background(category.color)
                )
            }
        }
    }
}

// --- Estado 2: la grafica ----------------------------------------------------------

@Composable
private fun SharedTransitionScope.ChartState(
    avs: AnimatedVisibilityScope,
    amount: String,
    categories: List<Category>,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .width(84.dp)
                    .fillMaxHeight()
                    .sharedElement(rememberSharedContentState("panel"), avs)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.08f))
                    .padding(10.dp),
            ) {
                Text(text = "Total", fontSize = 8.sp, color = muted, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = amount,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(14.dp))

            Row(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                categories.forEachIndexed { index, category ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(category.fraction)
                            .sharedElement(rememberSharedContentState("bar$index"), avs)
                            .clip(RoundedCornerShape(7.dp))
                            .background(category.color),
                    ) {
                        Text(
                            text = category.amountText,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 5.dp),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(98.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                categories.forEach { category ->
                    Text(
                        text = category.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 8.sp,
                        color = muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
