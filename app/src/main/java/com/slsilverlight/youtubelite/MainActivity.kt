package com.slsilverlight.youtubelite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.slsilverlight.youtubelite.ui.App
import com.slsilverlight.youtubelite.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val red = Color(0xFFE53935)
            val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = red) else lightColorScheme(primary = red)
            MaterialTheme(colorScheme = colors) {
                Surface { App(viewModel) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveWatchTime()
    }
}
