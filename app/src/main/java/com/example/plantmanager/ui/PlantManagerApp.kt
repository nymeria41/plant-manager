package com.example.plantmanager.ui

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.plantmanager.PhotoStore
import com.example.plantmanager.PlantsState
import com.example.plantmanager.domain.PlantRepository

/** Écran affiché : la liste, le formulaire d'une nouvelle plante, ou l'id d'une plante à modifier. */
private const val SCREEN_LIST = -1L
private const val SCREEN_NEW = 0L

@Composable
fun PlantTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun PlantManagerApp(repository: PlantRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember {
        PlantsState(repository, PhotoStore.get(context), scope) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // rememberSaveable : l'écran ouvert survit à une rotation de l'écran.
    var screen by rememberSaveable { mutableStateOf(SCREEN_LIST) }

    BackHandler(enabled = screen != SCREEN_LIST) { screen = SCREEN_LIST }

    when (screen) {
        SCREEN_LIST -> PlantListScreen(
            state = state,
            onAdd = { screen = SCREEN_NEW },
            onOpen = { id -> screen = id },
        )

        SCREEN_NEW -> PlantFormScreen(
            plant = null,
            onSave = { plant ->
                state.save(plant)
                screen = SCREEN_LIST
            },
            onDelete = null,
            onBack = { screen = SCREEN_LIST },
        )

        else -> {
            val plant = state.plants.firstOrNull { it.id == screen }
            when {
                plant != null -> key(plant.id) {
                    PlantFormScreen(
                        plant = plant,
                        onSave = { updated ->
                            state.save(updated)
                            screen = SCREEN_LIST
                        },
                        onDelete = {
                            state.delete(plant.id)
                            screen = SCREEN_LIST
                        },
                        onBack = { screen = SCREEN_LIST },
                    )
                }

                // Plante introuvable (supprimée entre-temps) : retour à la liste.
                state.isLoaded -> LaunchedEffect(Unit) { screen = SCREEN_LIST }

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}