package com.example.plantmanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.plantmanager.PhotoStore
import com.example.plantmanager.PlantsState
import com.example.plantmanager.domain.PlantRepository

/**
 * Écran affiché : la liste, « À faire aujourd'hui », le formulaire d'une nouvelle plante,
 * ou l'id (toujours positif) d'une plante à modifier.
 */
private const val SCREEN_LIST = -1L
private const val SCREEN_TODAY = -2L
private const val SCREEN_NEW = 0L

@Composable
fun PlantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ForestGreen,
            onPrimary = Color.White,
            primaryContainer = SageGreen,
            onPrimaryContainer = DeepForest,
            secondary = WarmBrown,
            onSecondary = Color.White,
            secondaryContainer = Caramel,
            onSecondaryContainer = DarkBrown,
            tertiary = OliveGreen,
            background = Beige,
            onBackground = DarkBrown,
            surface = SoftBeige,
            onSurface = DarkBrown,
            surfaceVariant = Cream,
            onSurfaceVariant = BrownGrey,
            error = ErrorRed,
        ),
        content = content,
    )
}

private val ForestGreen = Color(0xFF2F5D3A)
private val DeepForest = Color(0xFF17351F)
private val SageGreen = Color(0xFFC4D6B8)
private val OliveGreen = Color(0xFF687A3D)
private val WarmBrown = Color(0xFF825A3C)
private val DarkBrown = Color(0xFF3D2B20)
private val Caramel = Color(0xFFE5C6A1)
private val BrownGrey = Color(0xFF6E6258)
private val Beige = Color(0xFFF5EBDD)
private val SoftBeige = Color(0xFFFFF9F1)
private val Cream = Color(0xFFEDE0D0)
private val ErrorRed = Color(0xFF9E3F32)

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
            onOpenToday = { screen = SCREEN_TODAY },
        )

        SCREEN_TODAY -> TodayScreen(
            state = state,
            onBack = { screen = SCREEN_LIST },
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