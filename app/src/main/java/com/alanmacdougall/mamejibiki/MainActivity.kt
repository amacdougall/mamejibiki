package com.alanmacdougall.mamejibiki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.alanmacdougall.mamejibiki.ui.App
import com.alanmacdougall.mamejibiki.ui.AppModel
import com.alanmacdougall.mamejibiki.ui.MamejibikiTheme

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Create the model here, not lazily inside the first composition.
        val model = model
        setContent {
            MamejibikiTheme {
                App(model)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refreshAnki()
    }
}
