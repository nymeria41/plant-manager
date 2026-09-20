package com.example.plantmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.plantmanager.PlantsState
import com.example.plantmanager.domain.Care
import com.example.plantmanager.domain.NotificationPlanner
import com.example.plantmanager.domain.Plant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

internal fun formatDate(date: LocalDate): String = date.format(DATE_FORMAT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantListScreen(
    state: PlantsState,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenToday: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val filteredPlants = state.plants
        .sortedBy { it.name.lowercase() }
        .filter { it.name.contains(search.trim(), ignoreCase = true) }

    // Tout ce dont l'échéance est aujourd'hui OU déjà passée (même règle que les notifications).
    val dueTasks = NotificationPlanner.dueTasks(state.plants, today)
    val wateringCount = dueTasks.count { it.care == Care.WATERING }
    val fertilizingCount = dueTasks.count { it.care == Care.FERTILIZING }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = "Verdant",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = "Ton astuce main verte",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            SpacerHeight(18.dp)

            state.loadWarning?.let { warning ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(warning)
                        TextButton(onClick = state::dismissLoadWarning) { Text("OK") }
                    }
                }
                SpacerHeight(12.dp)
            }

            when {
                !state.isLoaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.plants.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aucune plante pour l'instant.\nAppuie sur + pour ajouter la première.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                else -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = dueTasks.isNotEmpty(), onClick = onOpenToday),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                text = when {
                                    wateringCount > 0 -> "Il est temps d'arroser"
                                    fertilizingCount > 0 -> "Un coup d'engrais s'impose"
                                    else -> "Tout est à jour"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            val details = listOfNotNull(
                                if (wateringCount > 0) {
                                    "$wateringCount plante${if (wateringCount > 1) "s" else ""} à arroser"
                                } else {
                                    null
                                },
                                if (fertilizingCount > 0) "$fertilizingCount à fertiliser" else null,
                            ).joinToString(" · ")
                            Text(
                                text = details.ifEmpty { "Rien à faire aujourd'hui" },
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            if (dueTasks.isNotEmpty()) {
                                Text(
                                    text = "Voir la liste ›",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                    SpacerHeight(16.dp)
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Rechercher dans ton jardin...") },
                        leadingIcon = { Text("⌕", style = MaterialTheme.typography.headlineSmall) },
                    )
                    SpacerHeight(20.dp)
                    Text(
                        text = "Ma collection",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    SpacerHeight(10.dp)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 104.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(filteredPlants, key = { it.id }) { plant ->
                            PlantCard(plant = plant, onClick = { onOpen(plant.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpacerHeight(height: Dp) {
    Spacer(Modifier.height(height))
}

@Composable
private fun PlantCard(plant: Plant, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            PlantPhoto(
                photoPath = plant.photoPath,
                size = 140.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            SpacerHeight(8.dp)
            Text(
                text = plant.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            if (plant.species.isNotBlank()) {
                Text(
                    text = plant.species,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                    ),
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                )
            }
            Text(
                text = summary(plant).lineSequence().firstOrNull() ?: "Aucun rappel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

private fun summary(plant: Plant): String {
    val lines = mutableListOf<String>()
    plant.nextDue(Care.WATERING)?.let {
        lines.add("Arrosage : ${formatDate(it)}")
    }
    plant.nextDue(Care.FERTILIZING)?.let {
        lines.add("Engrais : ${formatDate(it)}")
    }
    return lines.joinToString("\n")
}