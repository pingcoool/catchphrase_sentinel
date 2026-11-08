package com.audiokit.catchphrase.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiokit.catchphrase.ui.AppViewModel
import com.audiokit.catchphrase.ui.components.SectionCard
import com.audiokit.catchphrase.ui.components.SectionLabel
import com.audiokit.catchphrase.ui.components.SwitchRow
import com.audiokit.catchphrase.ui.theme.Danger
import com.audiokit.catchphrase.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        SectionCard {
            SectionLabel("识别灵敏度")
            Text(
                text = "当前 ${settings.sensitivity} · 越高越严格，越不容易误报",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Slider(
                value = settings.sensitivity.toFloat(),
                onValueChange = { vm.setSensitivity(it.roundToInt()) },
                valueRange = 0f..100f
            )
            Text(
                text = "默认 70，按“宁愿漏报”偏好设置；调低会更容易触发。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        SectionCard {
            SectionLabel("提醒方式")
            SwitchRow(
                title = "实时边听边提醒",
                subtitle = "关闭后只在停顿成句后计数与提醒",
                checked = settings.realtimeAlert,
                onCheckedChange = vm::setRealtimeAlert
            )
            SwitchRow(
                title = "震动",
                subtitle = "检测到口头禅时轻震一下",
                checked = settings.vibration,
                onCheckedChange = vm::setVibration
            )
            SwitchRow(
                title = "提示音",
                subtitle = "播放一声很轻的提示音",
                checked = settings.sound,
                onCheckedChange = vm::setSound
            )
            SwitchRow(
                title = "息屏时亮屏提醒",
                subtitle = "锁屏状态下点亮屏幕并显示提醒卡片",
                checked = settings.wakeScreen,
                onCheckedChange = vm::setWakeScreen
            )
        }

        SectionCard {
            SectionLabel("记录")
            SwitchRow(
                title = "保存 2 秒录音片段",
                subtitle = "可在时间线回听，全部存储在手机本地",
                checked = settings.saveClips,
                onCheckedChange = vm::setSaveClips
            )
            TextButton(onClick = { confirmClear = true }) {
                Text(text = "清空所有触发记录", color = Danger)
            }
        }

        SectionCard {
            SectionLabel("离线模型")
            Text(
                text = "vosk-model-small-cn-0.22 · Apache-2.0",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                text = "模型约 44MB，内置在 App 中，识别与录音全部在本地完成，音频不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        SectionCard {
            SectionLabel("使用须知")
            Note("监听会持续占用麦克风，期间无法使用其他录音或通话类功能。")
            Note("手机重启后，Android 不允许后台直接启动麦克风，点击通知即可恢复监听。")
            Note("建议把本应用加入电池优化白名单，避免息屏后被系统回收。")
            Note("识别短词本身存在误差，默认策略是宁可漏报，减少误提醒。")
        }

        Text(
            text = "口头禅哨兵 · v1.0",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有记录？") },
            text = { Text("会删除全部触发记录和录音片段，口头禅词库不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearDetections()
                    confirmClear = false
                }) { Text("清空", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = "· $text",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary
    )
}
