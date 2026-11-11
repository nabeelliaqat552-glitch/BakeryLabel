package com.foodlabel.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foodlabel.studio.ui.AppRoot
import com.foodlabel.studio.ui.theme.FoodLabelTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FoodLabelApp).container
        setContent {
            FoodLabelTheme { AppRoot(container) }
        }
    }
}
