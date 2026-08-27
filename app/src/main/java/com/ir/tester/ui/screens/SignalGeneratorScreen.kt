package com.ir.tester.ui.screens
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ir.tester.engine.ModulationMode
import com.ir.tester.viewmodel.SimpleJammerState
import kotlin.math.sin
@Composable
fun SignalGeneratorScreen(
    state: SimpleJammerState,
    onFrequencySelected: (Int) -> Unit,
    onModeSelected: (ModulationMode) -> Unit,
    onToggleGenerator: () -> Unit
) {
    val isRunning = state.isRunning
    val freqScrollState = rememberScrollState()
    val modeScrollState = rememberScrollState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 36.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text(
                    text = "джаммер",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        item {
            val btnColor by animateColorAsState(
                targetValue = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                label = "jam_btn_color"
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(14.dp))
                    if (isRunning) {
                        Text(
                            text = "время: ${state.formattedTime} • пакетов: ${state.packetsSent}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "${state.selectedFrequency / 1000} кгц • ${state.selectedMode.title.lowercase()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onToggleGenerator,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = btnColor)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isRunning) "остановить" else "заглушить",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SimpleWaveform(isRunning = isRunning, mode = state.selectedMode)
                }
            }
        }
        item {
            Column {
                Text(
                    text = "частота глушения:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(freqScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val frequencies = listOf(30000, 33000, 36000, 38000, 40000, 56000)
                    frequencies.forEach { freq ->
                        val isSelected = state.selectedFrequency == freq
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFrequencySelected(freq) },
                            label = {
                                Text(
                                    text = if (freq == 38000) "38 кгц (авто)" else "${freq / 1000} кгц",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
        item {
            Column {
                Text(
                    text = "режим работы:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(modeScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModulationMode.values().forEach { mode ->
                        val isSelected = state.selectedMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { onModeSelected(mode) },
                            label = {
                                Text(
                                    text = when (mode) {
                                        ModulationMode.SWEEP -> "свип (рекомендуется)"
                                        ModulationMode.BASIC -> "меандр"
                                        ModulationMode.RANDOM -> "случайный шум"
                                        ModulationMode.ENHANCED_BASIC -> "усиленный"
                                        ModulationMode.EMPTY -> "преамбула"
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleWaveform(
    isRunning: Boolean,
    mode: ModulationMode
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim_simple")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_simple"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2

            if (!isRunning) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.3f),
                    start = Offset(0f, midY),
                    end = Offset(width, midY),
                    strokeWidth = 2.dp.toPx()
                )
                return@Canvas
            }

            val path = Path()
            path.moveTo(0f, midY)
            var x = 0f

            when (mode) {
                ModulationMode.SWEEP -> {
                    val stepX = 4f
                    while (x < width) {
                        val angle = (x * 0.06f + Math.toRadians(phase.toDouble())).toFloat()
                        val noise = kotlin.math.sin(angle) * kotlin.math.sin(angle * 2.5f) * 18.dp.toPx()
                        path.lineTo(x, midY + noise)
                        x += stepX
                    }
                }
                ModulationMode.BASIC -> {
                    val period = 36f
                    val shift = (phase * 1.8f) % period
                    val stepX = 4f
                    while (x < width) {
                        val pos = (x + shift) % period
                        val isHigh = pos < (period / 2f)
                        val y = if (isHigh) midY - 14.dp.toPx() else midY + 14.dp.toPx()
                        path.lineTo(x, y)
                        x += stepX
                    }
                }
                ModulationMode.RANDOM -> {
                    val stepX = 4f
                    while (x < width) {
                        val rad = Math.toRadians((phase * 4.0).toDouble())
                        val noise = (kotlin.math.sin(x * 0.12f + rad) * kotlin.math.cos(x * 0.28f - rad) * 16.dp.toPx()).toFloat() +
                                    (kotlin.math.sin(x * 0.6f + rad * 2) * 5.dp.toPx()).toFloat()
                        path.lineTo(x, midY + noise)
                        x += stepX
                    }
                }
                ModulationMode.ENHANCED_BASIC -> {
                    val period = 18f
                    val shift = (phase * 3f) % period
                    val stepX = 3f
                    while (x < width) {
                        val pos = (x + shift) % period
                        val isHigh = pos < (period / 2f)
                        val y = if (isHigh) midY - 18.dp.toPx() else midY + 18.dp.toPx()
                        path.lineTo(x, y)
                        x += stepX
                    }
                }
                ModulationMode.EMPTY -> {
                    val period = 70f
                    val shift = (phase * 2.2f) % period
                    val stepX = 3f
                    while (x < width) {
                        val pos = (x + shift) % period
                        val y = if (pos < 6f) midY - 18.dp.toPx() else if (pos < 12f) midY + 10.dp.toPx() else midY
                        path.lineTo(x, y)
                        x += stepX
                    }
                }
            }

            drawPath(
                path = path,
                color = errorColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
