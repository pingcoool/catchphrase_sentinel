package com.audiokit.catchphrase.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiokit.catchphrase.data.DetectionEntity
import com.audiokit.catchphrase.ui.AppViewModel
import com.audiokit.catchphrase.ui.components.LiveWaveform
import com.audiokit.catchphrase.ui.components.PhraseBarRow
import com.audiokit.catchphrase.ui.components.SectionCard
import com.audiokit.catchphrase.ui.components.SectionLabel
import com.audiokit.catchphrase.ui.components.StatusRing
import com.audiokit.catchphrase.ui.components.TrendChart
import com.audiokit.catchphrase.ui.theme.Mint
import com.audiokit.catchphrase.ui.theme.TextSecondary
import com.audiokit.catchphrase.util.TimeUtils
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onToggleMonitoring: () -> Unit,
    onOpenPhrases: () -> Unit
) {
    val running by vm.running.collectAsStateWithLifecycle()
    val total by vm.todayTotal.collectAsStateWithLifecycle()
    val amplitude by vm.amplitude.collectAsStateWithLifecycle()
    val partial by vm.partialText.collectAsStateWithLifecycle()
    val phrases by vm.phrases.collectAsStateWithLifecycle()
    val counts by vm.todayByPhrase.collectAsStateWithLifecycle()
    val week by vm.weekCounts.collectAsStateWithLifecycle()
    val detections by vm.detections.collectAsStateWithLifecycle()

    val countMap = remember(counts) { counts.associate { it.phraseId to it.count } }
    val activePhrases = remember(phrases) { phrases.filter { it.enabled } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "口头禅哨兵",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (running) "正在监听 · 息屏也会继续" else "已停止 · 点下方按钮开始",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (running) Mint else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }

        SectionCard {
            StatusRing(count = total, running = running, amplitude = amplitude)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (running) "监听中" else "已停止",
                    color = if (running) Mint else TextSecondary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "今日 $total 次",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            LiveWaveform(amplitude = amplitude, running = running)
            if (running && partial.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "听到：$partial",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Mint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Button(
                onClick = onToggleMonitoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) MaterialTheme.colorScheme.surfaceVariant else Mint,
                    contentColor = if (running) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = if (running) "停止监听" else "开始监听",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        SectionCard {
            SectionLabel("今日各口头禅")
            if (activePhrases.isEmpty()) {
                Text(
                    text = "还没有启用任何口头禅",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                TextButton(onClick = onOpenPhrases) { Text("去词库添加") }
            } else {
                val maxCount = activePhrases.maxOfOrNull { countMap[it.id] ?: 0 }?.coerceAtLeast(1) ?: 1
                activePhrases.forEach { phrase ->
                    PhraseBarRow(
                        text = phrase.text,
                        count = countMap[phrase.id] ?: 0,
                        maxCount = maxCount
                    )
                }
            }
        }

        SectionCard {
            SectionLabel("近 7 天趋势")
            TrendChart(week)
        }

        SectionCard {
            SectionLabel("最近触发")
            if (detections.isEmpty()) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                detections.take(3).forEach { detection ->
                    RecentRow(detection)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun RecentRow(detection: DetectionEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = detection.phraseText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = if (detection.confidence > 0f) {
                "${(detection.confidence * 100).roundToInt()}%"
            } else {
                "实时"
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = TimeUtils.formatClock(detection.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )
    }
}
