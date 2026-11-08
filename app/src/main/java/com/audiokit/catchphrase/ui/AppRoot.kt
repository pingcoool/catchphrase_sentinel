package com.audiokit.catchphrase.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.audiokit.catchphrase.R
import com.audiokit.catchphrase.audio.ModelManager
import com.audiokit.catchphrase.service.MonitorState
import com.audiokit.catchphrase.ui.screens.AddPhraseSheet
import com.audiokit.catchphrase.ui.screens.DashboardScreen
import com.audiokit.catchphrase.ui.screens.PhrasesScreen
import com.audiokit.catchphrase.ui.screens.SettingsScreen
import com.audiokit.catchphrase.ui.screens.TimelineScreen
import com.audiokit.catchphrase.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(ModelManager.isReady(context)) }
    var attempt by remember { mutableIntStateOf(0) }
    val progress by MonitorState.modelProgress.collectAsStateWithLifecycle()
    val modelError by MonitorState.modelError.collectAsStateWithLifecycle()

    LaunchedEffect(ready, attempt) {
        if (!ready) {
            MonitorState.modelError.value = null
            MonitorState.modelProgress.value = 0f
            runCatching {
                ModelManager.ensureReady(context) { value ->
                    MonitorState.modelProgress.value = value
                }
            }.onSuccess {
                ready = true
            }.onFailure {
                MonitorState.modelError.value = it.message ?: "模型初始化失败"
            }
        }
    }

    if (ready) {
        MainScaffold(vm)
    } else {
        ModelSetupScreen(
            progress = progress,
            error = modelError,
            onRetry = { attempt++ }
        )
    }
}

@Composable
private fun ModelSetupScreen(progress: Float, error: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = "口头禅哨兵",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "首次启动：正在解压离线中文识别模型",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            if (error == null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "模型约 44MB，只在手机本地运行",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }
    var wasRunning by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val running by vm.running.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    LaunchedEffect(error) {
        val message = error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            vm.clearError()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        if (micGranted) vm.startMonitoring() else showPermissionDialog = true
    }

    fun toggleMonitoring() {
        if (running) {
            vm.stopMonitoring()
            return
        }
        val missing = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) {
            vm.startMonitoring()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "概览") },
                    label = { Text("概览") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "时间线") },
                    label = { Text("时间线") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Create, contentDescription = "词库") },
                    label = { Text("词库") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> DashboardScreen(
                    vm = vm,
                    onToggleMonitoring = { toggleMonitoring() },
                    onOpenPhrases = { tab = 2 }
                )

                1 -> TimelineScreen(vm)
                2 -> PhrasesScreen(
                    vm = vm,
                    onAdd = {
                        wasRunning = running
                        if (running) vm.stopMonitoring()
                        showAddSheet = true
                    }
                )

                else -> SettingsScreen(vm)
            }
        }
    }

    if (showAddSheet) {
        AddPhraseSheet(vm = vm) {
            showAddSheet = false
            if (wasRunning) vm.startMonitoring()
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要麦克风权限") },
            text = {
                Text("监听口头禅必须使用麦克风，音频只在手机本地处理、不会上传。请允许录音权限后重试。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    runCatching { context.startActivity(intent) }
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("稍后") }
            }
        )
    }
}
