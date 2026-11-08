package com.audiokit.catchphrase.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiokit.catchphrase.data.DailyCount
import com.audiokit.catchphrase.ui.theme.Mint
import com.audiokit.catchphrase.ui.theme.SurfaceVariantDark
import com.audiokit.catchphrase.ui.theme.TextSecondary
import com.audiokit.catchphrase.util.TimeUtils
import kotlin.math.sin

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun StatusRing(count: Int, running: Boolean, amplitude: Float) {
    val infinite = rememberInfiniteTransition(label = "status-ring")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )
    val ringColor = if (running) Mint else MaterialTheme.colorScheme.outline
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val numberColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier = Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 18.dp.toPx()
            drawCircle(color = trackColor, radius = radius, center = center, style = Stroke(width = 10.dp.toPx()))
            if (running) {
                drawCircle(
                    color = ringColor.copy(alpha = (1f - pulse) * 0.22f),
                    radius = radius * (1f + pulse * 0.14f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                val sweep = 54f + amplitude * 120f
                drawArc(
                    color = ringColor,
                    startAngle = -90f + pulse * 360f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$count",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = numberColor
            )
            Text(
                text = if (running) "今日捕获" else "今日捕获 · 未监听",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor
            )
        }
    }
}

@Composable
fun LiveWaveform(amplitude: Float, running: Boolean, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "waveform")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val color = if (running) Mint else MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val barCount = 34
        val step = size.width / barCount
        for (i in 0 until barCount) {
            val wave = (sin(phase + i * 0.62f) + 1f) / 2f
            val available = if (running) (0.1f + amplitude * 3.2f * wave) else 0.08f
            val height = (size.height * available).coerceIn(3f, size.height)
            val width = step * 0.55f
            drawRoundRect(
                color = color.copy(alpha = if (running) 0.85f else 0.25f),
                topLeft = Offset(i * step + step * 0.2f, (size.height - height) / 2f),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2f)
            )
        }
    }
}

@Composable
fun TrendChart(counts: List<DailyCount>) {
    val days = remember { TimeUtils.lastSevenDays() }
    val countMap = remember(counts) { counts.associate { it.day to it.count } }
    val values = days.map { countMap[TimeUtils.dayKey(it)] ?: 0 }
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, date ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${values[index]}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (values[index] > 0) Mint else TextSecondary
                )
                Box(
                    modifier = Modifier.height(88.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height((88.dp * (values[index].toFloat() / max)).coerceAtLeast(4.dp))
                            .background(
                                color = if (values[index] > 0) Mint else SurfaceVariantDark,
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }
                Text(
                    text = TimeUtils.weekdayShort(date),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun PhraseBarRow(text: String, count: Int, maxCount: Int) {
    val fraction = if (maxCount <= 0) 0f else (count.toFloat() / maxCount).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            modifier = Modifier.width(60.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(SurfaceVariantDark, RoundedCornerShape(4.dp))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .background(Mint, RoundedCornerShape(4.dp))
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$count",
            modifier = Modifier.width(34.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = if (count > 0) Mint else TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
fun EmptyHint(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
