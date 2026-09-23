package com.example.morphdemo.morph

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp

/** Un cubo ya resuelto en pixeles dentro del lienzo. */
internal data class Cube(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

/**
 * Dibuja un cubo en perspectiva isometrica: cara superior mas clara, cara lateral mas
 * oscura y cara frontal del color base.
 *
 * Es lo que hace que las piezas se lean como cubos y no como barras planas. La profundidad
 * [depth] es lo que se anima para dar la sensacion de que el cubo gira en el aire.
 */
internal fun DrawScope.drawCube(cube: Cube, depth: Float, color: Color) {
    if (cube.w <= 0f || cube.h <= 0f) return
    val dx = depth
    val dy = -depth * 0.55f
    val radius = (cube.w * 0.16f).coerceAtMost(6f)

    // Cara superior.
    drawPath(
        path = Path().apply {
            moveTo(cube.x, cube.y)
            lineTo(cube.x + dx, cube.y + dy)
            lineTo(cube.x + cube.w + dx, cube.y + dy)
            lineTo(cube.x + cube.w, cube.y)
            close()
        },
        color = lerp(color, Color.White, 0.34f),
    )

    // Cara lateral derecha.
    drawPath(
        path = Path().apply {
            moveTo(cube.x + cube.w, cube.y)
            lineTo(cube.x + cube.w + dx, cube.y + dy)
            lineTo(cube.x + cube.w + dx, cube.y + cube.h + dy)
            lineTo(cube.x + cube.w, cube.y + cube.h)
            close()
        },
        color = lerp(color, Color.Black, 0.32f),
    )

    // Cara frontal.
    drawRoundRect(
        color = color,
        topLeft = Offset(cube.x, cube.y),
        size = Size(cube.w, cube.h),
        cornerRadius = CornerRadius(radius, radius),
    )
}
