package com.audiokit.catchphrase.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiokit.catchphrase.data.PhraseEntity
import com.audiokit.catchphrase.ui.AppViewModel
import com.audiokit.catchphrase.ui.components.EmptyHint
import com.audiokit.catchphrase.ui.theme.Danger
import com.audiokit.catchphrase.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun PhrasesScreen(vm: AppViewModel, onAdd: () -> Unit) {
    val phrases by vm.phrases.collectAsStateWithLifecycle()
    val counts by vm.todayByPhrase.collectAsStateWithLifecycle()
    val countMap = remember(counts) { counts.associate { it.phraseId to it.count } }
    var pendingDelete by remember { mutableStateOf<PhraseEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "词库",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "最长 4 个字 · 建议同时监测 3~5 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Button(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("新增")
            }
        }

        Text(
            text = "录入时会暂停监听；每个词录 3~5 遍发音可以校准阈值并学习发音变体。",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        if (phrases.isEmpty()) {
            EmptyHint(
                title = "先添加你的第一个口头禅",
                subtitle = "比如“那个”“就是”，输入文字并录几遍发音即可"
            )
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text("新增口头禅")
            }
        } else {
            phrases.forEach { phrase ->
                PhraseCard(
                    phrase = phrase,
                    todayCount = countMap[phrase.id] ?: 0,
                    onToggle = { vm.setPhraseEnabled(phrase.id, it) },
                    onDelete = { pendingDelete = phrase }
                )
            }
        }
    }

    pendingDelete?.let { phrase ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${phrase.text}」？") },
            text = { Text("该词的历史记录与录音片段也会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePhrase(phrase)
                    pendingDelete = null
                }) { Text("删除", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PhraseCard(
    phrase: PhraseEntity,
    todayCount: Int,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = phrase.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "阈值 ${(phrase.threshold * 100).roundToInt()}% · 今日 $todayCount 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(checked = phrase.enabled, onCheckedChange = onToggle)
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
