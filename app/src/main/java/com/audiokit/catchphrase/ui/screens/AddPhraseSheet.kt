package com.audiokit.catchphrase.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.audiokit.catchphrase.ui.AppViewModel
import com.audiokit.catchphrase.ui.CalibrationResult
import com.audiokit.catchphrase.ui.components.SectionCard
import com.audiokit.catchphrase.ui.components.SectionLabel
import com.audiokit.catchphrase.ui.theme.Danger
import com.audiokit.catchphrase.ui.theme.Mint
import com.audiokit.catchphrase.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun AddPhraseSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var samples by remember { mutableStateOf<List<File>>(emptyList()) }
    var countdown by remember { mutableIntStateOf(-1) }
    var capturing by remember { mutableStateOf(false) }
    var calibrating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<CalibrationResult?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) error = "需要录音权限才能录入样例"
    }

    fun close() {
        if (calibrating) return
        if (result != null) {
            onDismiss()
            return
        }
        val old = samples
        samples = emptyList()
        scope.launch { vm.discardSamples(old) }
        onDismiss()
    }

    fun startRecording() {
        if (countdown >= 0 || capturing || calibrating || samples.size >= 5) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        error = null
        scope.launch {
            for (i in 3 downTo 1) {
                countdown = i
                delay(650)
            }
            countdown = 0
            capturing = true
            try {
                val pcm = vm.recordSample()
                if (pcm.size < 8000) {
                    error = "录音太短，请靠近手机再说一遍"
                } else {
                    samples = samples + vm.saveSample(pcm)
                }
            } catch (t: Throwable) {
                error = t.message ?: "录音失败"
            } finally {
                capturing = false
                countdown = -1
            }
        }
    }

    fun save() {
        if (calibrating || text.isBlank() || samples.size < 3) return
        scope.launch {
            calibrating = true
            error = null
            try {
                result = vm.finalizePhrase(text, samples)
            } catch (t: Throwable) {
                error = t.message ?: "校准失败"
            } finally {
                calibrating = false
            }
        }
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "新增口头禅",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { close() }, enabled = !calibrating) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                SectionCard {
                    SectionLabel("1. 写下口头禅")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { newValue ->
                            if (newValue.length <= 4) {
                                if (newValue != text) {
                                    val old = samples
                                    samples = emptyList()
                                    if (old.isNotEmpty()) scope.launch { vm.discardSamples(old) }
                                }
                                text = newValue
                                error = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("例如：那个") },
                        supportingText = { Text("${text.length}/4 个字，越短的词越容易被日常用语误触") },
                        singleLine = true,
                        enabled = !calibrating
                    )
                }

                SectionCard {
                    SectionLabel("2. 录 3~5 遍发音")
                    Text(
                        text = "每遍 2 秒。自然说出口头禅即可，不需要刻意拖长。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("已录 ${samples.size}/5", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        val hint = when {
                            countdown > 0 -> "${countdown} 秒后开始录音"
                            countdown == 0 || capturing -> "正在录音…"
                            else -> "建议 3 遍以上"
                        }
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (countdown >= 0 || capturing) Mint else TextSecondary
                        )
                    }
                    samples.forEachIndexed { index, _ ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Mint,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "第 ${index + 1} 遍已录入",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Button(
                        onClick = { startRecording() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = countdown < 0 && !capturing && !calibrating && samples.size < 5,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(if (samples.size >= 5) "已录满 5 遍" else "录第 ${samples.size + 1} 遍")
                    }
                }

                SectionCard {
                    SectionLabel("3. 完成校准")
                    Text(
                        text = "会用离线模型听一遍你的样例，计算更严格的识别阈值，并学习可能的发音变体。",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = { save() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = text.isNotBlank() && samples.size >= 3 && !calibrating && countdown < 0
                    ) {
                        Text(if (calibrating) "正在校准…" else "完成并开始使用")
                    }
                }

                error?.let {
                    Text(text = it, color = Danger, style = MaterialTheme.typography.bodySmall)
                }

                Text(
                    text = "录入期间会暂停监听，完成后自动恢复。",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    result?.let { calibration ->
        AlertDialog(
            onDismissRequest = {
                result = null
                onDismiss()
            },
            title = { Text("校准完成") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("识别阈值：${(calibration.threshold * 100).roundToInt()}%")
                    if (calibration.variants.isNotEmpty()) {
                        Text("学习到的发音变体：${calibration.variants.joinToString("、")}")
                    } else {
                        Text("未发现额外发音变体")
                    }
                    Text(
                        text = "样例识别结果：${calibration.recognizedTexts.joinToString("、").ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    result = null
                    onDismiss()
                }) { Text("完成") }
            }
        )
    }
}
