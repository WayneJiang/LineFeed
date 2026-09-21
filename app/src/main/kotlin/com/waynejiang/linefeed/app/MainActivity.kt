package com.waynejiang.linefeed.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.waynejiang.linefeed.core.designsystem.theme.LineFeedTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host: everything else is `LineFeedApp`'s NavHost + bottom navigation. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LineFeedTheme {
                LineFeedApp()
            }
        }
    }
}
