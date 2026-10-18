package com.bestcut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bestcut.app.ui.BestCutTheme
import com.bestcut.app.ui.EditorScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BestCutTheme {
                val vm: EditorViewModel = viewModel()
                EditorScreen(vm)
            }
        }
    }
}
