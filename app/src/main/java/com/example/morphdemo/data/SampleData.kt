package com.example.morphdemo.data

import androidx.compose.ui.graphics.Color

/**
 * Una categoria de gasto.
 *
 * @param fraction altura relativa de la barra en el estado "grafica" (0f..1f).
 */
data class Category(
    val name: String,
    val amountText: String,
    val fraction: Float,
    val color: Color,
)

object SampleData {
    const val TITLE = "Presupuesto mensual"
    const val AMOUNT = "S/ 1,240"
    const val SUBTITLE = "de S/ 2,000 - 62%"

    val categories = listOf(
        Category("Comida", "520", 0.55f, Color(0xFF6C5CE7)),
        Category("Transp.", "310", 0.80f, Color(0xFF00B894)),
        Category("Ocio", "190", 0.35f, Color(0xFFE17055)),
        Category("Otros", "220", 0.65f, Color(0xFF0984E3)),
    )
}
