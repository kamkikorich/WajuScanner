package com.example.wajuscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.wajuscanner.ui.navigation.WajuScannerNavGraph
import com.example.wajuscanner.ui.theme.WajuScannerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity host. Everything else — every screen, every route — lives
 * inside [WajuScannerNavGraph]. This file's only job is to:
 *   1. enable edge-to-edge so the system bars stay transparent;
 *   2. apply the app theme (incl. Material 3 dynamic color, dark mode, etc.);
 *   3. hand off to the navigation graph.
 *
 * Keeping the activity this small means adding a new screen does not touch this
 * file at all — only the nav graph.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WajuScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    WajuScannerNavGraph()
                }
            }
        }
    }
}