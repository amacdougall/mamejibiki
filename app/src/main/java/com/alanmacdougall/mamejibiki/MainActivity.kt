package com.alanmacdougall.mamejibiki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.alanmacdougall.mamejibiki.ui.MamejibikiTheme
import com.alanmacdougall.mamejibiki.ui.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MamejibikiTheme {
                Scaffold { padding ->
                    SearchScreen(Modifier.padding(padding).imePadding())
                }
            }
        }
    }
}
