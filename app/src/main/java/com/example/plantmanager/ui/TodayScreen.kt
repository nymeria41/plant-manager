package com.example.plantmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.plantmanager.PlantsState
import com.example.plantmanager.domain.Care
import com.example.plantmanager.domain.DueTask
import com.example.plantmanager.domain.NotificationPlanner
import com.example.plantmanager.domain.Plant
import java.time.LocalDate

/**
 * Les soins à faire maintenant : tout ce dont l'échéance est aujourd'hui ou déjà passée,
 * les plus en retard d'abord. Un appui sur le bouton enregistre la date du jour, la tâche
 * disparaît de la liste et la prochaine échéance repart de là.
 */
@Composable
fun TodayScreen(
    state: PlantsState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val today = LocalDate.now()
    val plantsById = state.plants.associateBy { it.id }
    val tasks = NotificationPlanner.dueTasks(state.plants, today)

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            TextButton(onClick = onBack) { Text("‹ Retour") }
            Text(
                text = "À faire aujourd'hui",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = when (tasks.size) {
                    0 -> "Rien à faire pour le moment"
                    1 -> "1 soin à faire"
                    else -> "${tasks.size} soins à faire"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(16.dp))

            if (tasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Tout est à jour 🌿\nReviens quand une plante aura soif.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(tasks, key = { "${it.plantId}-${it.care}" }) { task ->
                        val plant = plantsById[task.plantId]
                        if (plant != null) {
                            TaskCard(
                                task = task,
                                plant = plant,
                                onOpen = { onOpen(plant.id) },
                                onDone = {
                                    if (task.care == Care.WATERING) {
                                        state.markWatered(plant.id)
                                    } else {
                                        state.markFertilized(plant.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: DueTask,
    plant: Plant,
    onOpen: () -> Unit,
    onDone: () -> Unit,
) {
    val what = if (task.care == Care.WATERING) "Arroser" else "Engrais"
    val delay = if (task.isNew) "aujourd'hui" else "en retard de ${task.overdueDays} j"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlantPhoto(photoPath = plant.photoPath, size = 64.dp)

            Column(Modifier.weight(1f)) {
                Text(
                    text = plant.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = "$what · $delay",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.isNew) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (plant.location.isNotBlank()) {
                    Text(
                        text = plant.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Button(onClick = onDone) {
                Text(if (task.care == Care.WATERING) "Arrosée" else "Fertilisée")
            }
        }
    }
}