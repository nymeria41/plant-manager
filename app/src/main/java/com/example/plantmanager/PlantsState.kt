package com.example.plantmanager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.plantmanager.domain.Plant
import com.example.plantmanager.domain.PlantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pont entre le dépôt (appels bloquants, sur le disque) et l'interface Compose.
 * Toutes les lectures et écritures passent sur le thread d'entrées/sorties.
 */
class PlantsState(
    private val repository: PlantRepository,
    private val photoStore: PhotoStore,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
) {
    var plants by mutableStateOf<List<Plant>>(emptyList())
        private set

    /** false tant que le premier chargement n'est pas terminé. */
    var isLoaded by mutableStateOf(false)
        private set

    /** Non null si l'ancien fichier était illisible (il a été mis de côté, pas supprimé). */
    var loadWarning by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch {
            try {
                val (list, failure) = withContext(Dispatchers.IO) {
                    val all = repository.all()
                    val loadFailure = repository.loadFailure()
                    // Ménage des photos que plus aucune plante n'utilise. On ne le fait pas si le
                    // fichier était illisible : la sauvegarde mise de côté en a encore besoin.
                    if (loadFailure == null) {
                        runCatching { photoStore.deleteOrphans(all.map { it.photoPath }) }
                    }
                    all to loadFailure
                }
                plants = list
                if (failure != null) {
                    loadWarning = "Le fichier des plantes était illisible. Il a été mis de côté " +
                            "(plants.json.corrupt-…) et la bibliothèque repart vide."
                }
            } catch (e: Exception) {
                onError("Lecture impossible : ${e.message}")
            }
            isLoaded = true
        }
    }

    /** Ajoute la plante si son id vaut 0, sinon la met à jour. */
    fun save(plant: Plant) = perform {
        if (plant.id == 0L) repository.add(plant) else repository.update(plant)
    }

    fun delete(id: Long) = perform { repository.delete(id) }

    fun dismissLoadWarning() {
        loadWarning = null
    }

    private fun perform(action: () -> Unit) {
        scope.launch {
            try {
                plants = withContext(Dispatchers.IO) {
                    action()
                    repository.all()
                }
            } catch (e: Exception) {
                onError("Enregistrement impossible : ${e.message}")
            }
        }
    }
}