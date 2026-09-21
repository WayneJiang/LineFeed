package com.waynejiang.linefeed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Placeholder content here proves the Compose/Hilt/KSP toolchain compiles
 * end to end; [com.waynejiang.linefeed.app] gains the real NavHost + bottom navigation once the
 * feature modules exist (step 9+).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    Text(text = "LineFeed")
                }
            }
        }
    }
}
