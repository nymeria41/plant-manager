package com.example.plantmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.plantmanager.PlantsState
import com.example.plantmanager.domain.Care
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
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Mes plantes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.loadWarning?.let { warning ->
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(warning)
                        TextButton(onClick = state::dismissLoadWarning) { Text("OK") }
                    }
                }
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

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Laisse de la place sous la dernière ligne pour que le bouton + ne la cache pas.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(state.plants.sortedBy { it.name.lowercase() }, key = { it.id }) { plant ->
                        PlantRow(plant = plant, onClick = { onOpen(plant.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantRow(plant: Plant, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { PlantPhoto(photoPath = plant.photoPath, size = 56.dp) },
        headlineContent = { Text(plant.name) },
        supportingContent = { Text(summary(plant)) },
    )
}

private fun summary(plant: Plant): String {
    val lines = mutableListOf<String>()

    val nextWatering = plant.nextDue(Care.WATERING)
    if (nextWatering != null) {
        lines.add("Arrosage tous les ${plant.wateringEveryDays} j · prochain : ${formatDate(nextWatering)}")
    }

    val nextFertilizing = plant.nextDue(Care.FERTILIZING)
    if (nextFertilizing != null) {
        lines.add("Engrais tous les ${plant.fertilizingEveryDays} j · prochain : ${formatDate(nextFertilizing)}")
    }

    return lines.joinToString("\n")
}