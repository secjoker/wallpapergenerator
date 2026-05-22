package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.WallpaperApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.WallpaperViewModel
import com.example.viewmodel.WallpaperViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core central viewmodel instantiation supplying applicationContext
        val factory = WallpaperViewModelFactory(applicationContext)
        val viewModel: WallpaperViewModel by viewModels { factory }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WallpaperApp(viewModel = viewModel)
                }
            }
        }
    }
}
