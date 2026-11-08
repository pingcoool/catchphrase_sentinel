package com.audiokit.catchphrase.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.audiokit.catchphrase.ui.theme.CatchphraseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 息屏提醒卡片：亮屏展示，不强制解锁进入 App。
 */
class WakeCardActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PHRASE = "phrase"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val phrase = intent.getStringExtra(EXTRA_PHRASE).orEmpty()

        setContent {
            CatchphraseTheme {
                WakeCard(phrase) { finish() }
            }
        }

        lifecycleScope.launch {
            delay(6000)
            finish()
        }
    }

    @Composable
    private fun WakeCard(phrase: String, onDismiss: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111823))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "口头禅提醒",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF3DDC97)
                    )
                    Text(
                        text = "又说了「$phrase」",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3)
                    )
                    Text(
                        text = "刚刚 · 已记入时间线，3 秒内不重复提醒",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8B98A5)
                    )
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("知道了", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
