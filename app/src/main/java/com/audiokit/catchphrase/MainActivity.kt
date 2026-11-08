package com.audiokit.catchphrase

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audiokit.catchphrase.ui.AppRoot
import com.audiokit.catchphrase.ui.AppViewModel
import com.audiokit.catchphrase.ui.theme.CatchphraseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CatchphraseTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.factory(application as CatchphraseApp))
                AppRoot(vm)
            }
        }
    }
}
