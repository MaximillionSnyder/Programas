package com.example.morphdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morphdemo.data.SampleData
import com.example.morphdemo.morph.RectMorphCard
import com.example.morphdemo.morph.SharedMorphCard
import com.example.morphdemo.theme.MorphDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Modo auto-demo, se activa con:  am start ... --ez auto true
        // Hace falta porque HyperOS bloquea inyectar eventos de entrada desde adb.
        val auto = intent.getBooleanExtra("auto", false)
        setContent {
            MorphDemoTheme {
                Screen(auto)
            }
        }
    }
}

@Composable
private fun Screen(auto: Boolean = false) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = "Card -> Grafica",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Mismo tamano de card en ambos estados. Toca cada card para transformarla.",
            fontSize = 12.sp,
            color = muted,
        )

        Spacer(Modifier.height(28.dp))

        SectionHeader(
            number = "1",
            name = "Cuadrados animados",
            detail = "Rectangulos interpolados a mano con animateFloatAsState",
        )
        Spacer(Modifier.height(10.dp))
        RectMorphCard(
            title = SampleData.TITLE,
            amount = SampleData.AMOUNT,
            subtitle = SampleData.SUBTITLE,
            categories = SampleData.categories,
            autoToggleMs = if (auto) 2000L else null,
        )

        Spacer(Modifier.height(32.dp))

        SectionHeader(
            number = "2",
            name = "SharedTransitionLayout",
            detail = "animateBounds sobre layouts reales, sin calcular geometria",
        )
        Spacer(Modifier.height(10.dp))
        SharedMorphCard(
            title = SampleData.TITLE,
            amount = SampleData.AMOUNT,
            subtitle = SampleData.SUBTITLE,
            categories = SampleData.categories,
            autoToggleMs = if (auto) 2000L else null,
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionHeader(number: String, name: String, detail: String) {
    val primary = MaterialTheme.colorScheme.primary
    val onBackground = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "$number - $name",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = onBackground,
        )
        Text(
            text = detail,
            fontSize = 11.sp,
            color = muted,
        )
        Spacer(Modifier.height(2.dp))
        Spacer(
            Modifier
                .height(2.dp)
                .fillMaxWidth(0.18f)
                .background(primary)
        )
    }
}
