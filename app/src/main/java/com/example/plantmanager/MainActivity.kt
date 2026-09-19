package com.example.plantmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.plantmanager.ui.PlantManagerApp
import com.example.plantmanager.ui.PlantTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = PlantRepositoryProvider.get(applicationContext)

        setContent {
            PlantTheme {
                PlantManagerApp(repository)
            }
        }
    }
}
