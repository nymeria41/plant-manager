package com.example.plantmanager.domain

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

enum class ImportMode {
    /** La bibliothèque importée remplace entièrement l'actuelle. */
    REPLACE_ALL,

    /** Les plantes importées s'ajoutent aux plantes existantes (nouveaux identifiants). */
    ADD_TO_EXISTING,
}

/**
 * Bibliothèque de plantes stockée dans un fichier JSON.
 *
 * - Le fichier est la source de vérité ; la liste est gardée en mémoire (quelques dizaines de plantes).
 * - Chaque modification est écrite sur disque AVANT de mettre à jour la mémoire : si l'écriture
 *   échoue (exception), l'état en mémoire reste identique à celui du disque.
 * - L'écriture est atomique (fichier temporaire puis renommage) : une coupure en plein milieu
 *   ne peut pas laisser un fichier à moitié écrit.
 * - Thread-safe. Appels bloquants : à lancer hors du thread principal.
 *
 * Aucune dépendance Android : on lui donne simplement un [File] (par exemple
 * `File(context.filesDir, "plants.json")`), ce qui permet de le tester sur la JVM.
 */
class PlantRepository(private val file: File) {

    private var plants: List<Plant> = emptyList()
    private var nextId: Long = 1
    private var loaded = false
    private var loadFailure: LibraryFormatException? = null

    /**
     * Non null si le fichier existant était illisible au chargement : il a alors été
     * renommé en `plants.json.corrupt-<horodatage>` (pas supprimé) et la bibliothèque repart vide.
     * L'interface peut s'en servir pour prévenir l'utilisateur.
     */
    @Synchronized
    fun loadFailure(): LibraryFormatException? {
        ensureLoaded()
        return loadFailure
    }

    @Synchronized
    fun all(): List<Plant> {
        ensureLoaded()
        return plants
    }

    @Synchronized
    fun get(id: Long): Plant? {
        ensureLoaded()
        return plants.firstOrNull { it.id == id }
    }

    /** Ajoute une plante ; l'identifiant est attribué ici (celui de [plant] est ignoré). */
    @Synchronized
    fun add(plant: Plant): Plant {
        ensureLoaded()
        val saved = plant.copy(id = nextId)
        commit(plants + saved, nextId + 1)
        return saved
    }

    @Synchronized
    fun update(plant: Plant) {
        ensureLoaded()
        if (plants.none { it.id == plant.id }) {
            throw NoSuchElementException("Plante ${plant.id} introuvable")
        }
        commit(plants.map { if (it.id == plant.id) plant else it }, nextId)
    }

    /** Supprime la plante et la renvoie (pour que l'appelant puisse supprimer sa photo), ou null. */
    @Synchronized
    fun delete(id: Long): Plant? {
        ensureLoaded()
        val removed = plants.firstOrNull { it.id == id } ?: return null
        commit(plants.filter { it.id != id }, nextId)
        return removed
    }

    @Synchronized
    fun markWatered(id: Long, on: LocalDate): Plant = change(id) { it.copy(lastWateredOn = on) }

    @Synchronized
    fun markFertilized(id: Long, on: LocalDate): Plant = change(id) { it.copy(lastFertilizedOn = on) }

    /** Le JSON de sauvegarde : identique au contenu du fichier de la bibliothèque. */
    @Synchronized
    fun exportJson(): String {
        ensureLoaded()
        return LibraryJson.encode(plants, nextId)
    }

    /**
     * Importe une sauvegarde. Si le JSON est invalide, lève [LibraryFormatException]
     * sans rien modifier.
     */
    @Synchronized
    fun importJson(json: String, mode: ImportMode): List<Plant> {
        ensureLoaded()
        val data = LibraryJson.decode(json)
        when (mode) {
            ImportMode.REPLACE_ALL ->
                // nextId ne recule jamais : les noms de photos basés sur l'id restent uniques.
                commit(data.plants, maxOf(data.nextId, nextId))

            ImportMode.ADD_TO_EXISTING -> {
                var id = nextId
                val added = data.plants.map { it.copy(id = id++) }
                commit(plants + added, id)
            }
        }
        return plants
    }

    // ---------- interne ----------

    private fun change(id: Long, transform: (Plant) -> Plant): Plant {
        ensureLoaded()
        val current = plants.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Plante $id introuvable")
        val changed = transform(current)
        commit(plants.map { if (it.id == id) changed else it }, nextId)
        return changed
    }

    private fun commit(newPlants: List<Plant>, newNextId: Long) {
        write(LibraryJson.encode(newPlants, newNextId))
        plants = newPlants
        nextId = newNextId
    }

    private fun ensureLoaded() {
        if (loaded) return
        if (file.exists()) {
            try {
                val data = LibraryJson.decode(file.readText(Charsets.UTF_8))
                plants = data.plants
                nextId = data.nextId
            } catch (e: LibraryFormatException) {
                loadFailure = e
                quarantine()
            }
        }
        loaded = true
    }

    /** Met de côté un fichier illisible au lieu de l'écraser ou de faire planter l'app. */
    private fun quarantine() {
        val dir = file.absoluteFile.parentFile
        file.renameTo(File(dir, "${file.name}.corrupt-${System.currentTimeMillis()}"))
    }

    private fun write(text: String) {
        val dir = file.absoluteFile.parentFile
        dir.mkdirs()
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        try {
            Files.move(
                tmp.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
