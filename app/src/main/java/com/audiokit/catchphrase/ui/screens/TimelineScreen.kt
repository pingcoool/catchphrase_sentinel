package com.audiokit.catchphrase.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiokit.catchphrase.data.DetectionEntity
import com.audiokit.catchphrase.ui.AppViewModel
import com.audiokit.catchphrase.ui.components.EmptyHint
import com.audiokit.catchphrase.ui.theme.Mint
import com.audiokit.catchphrase.ui.theme.TextSecondary
import com.audiokit.catchphrase.util.TimeUtils
import kotlin.math.roundToInt

@Composable
fun TimelineScreen(vm: AppViewModel) {
    val detections by vm.detections.collectAsStateWithLifecycle()

    if (detections.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmptyHint(
                title = "还没有触发记录",
                subtitle = "开始监听后，每次说出口头禅都会出现在这里"
            )
        }
        return
    }

    val player = remember { MediaPlayer() }
    var playingId by remember { mutableLongStateOf(-1L) }
    DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }

    val grouped = remember(detections) {
        detections.groupBy { TimeUtils.dayHeader(it.timestamp) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "title") {
            Text(
                text = "时间线",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        grouped.forEach { (day, items) ->
            item(key = "header_$day") {
                Text(
                    text = day,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
            items(items = items, key = { it.id }) { detection ->
                DetectionRow(
                    detection = detection,
                    playing = playingId == detection.id,
                    onPlay = {
                        val path = detection.clipPath
                        if (path != null) {
                            runCatching {
                                player.reset()
                                player.setDataSource(path)
                                player.prepare()
                                player.start()
                                playingId = detection.id
                                player.setOnCompletionListener { playingId = -1L }
                            }.onFailure { playingId = -1L }
                        }
                    },
                    onDelete = { vm.deleteDetection(detection) }
                )
            }
        }
    }
}

@Composable
private fun DetectionRow(
    detection: DetectionEntity,
    playing: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = detection.phraseText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (detection.confidence > 0f) {
                            "${(detection.confidence * 100).roundToInt()}%"
                        } else {
                            "实时"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Mint
                    )
                }
                Text(
                    text = TimeUtils.formatClock(detection.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (detection.clipPath != null) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "回听",
                        tint = if (playing) Mint else TextSecondary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = TextSecondary
                )
            }
        }
    }
}
