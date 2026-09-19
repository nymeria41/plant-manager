package com.example.plantmanager.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Le fichier est illisible, invalide, ou écrit par une version plus récente de l'app. */
class LibraryFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Contenu décodé d'un fichier de bibliothèque. */
data class LibraryData(val nextId: Long, val plants: List<Plant>)

@Serializable
internal data class PlantDto(
    val id: Long,
    val name: String,
    /** Nom de fichier relatif au dossier de l'app, jamais un chemin absolu. */
    val photoPath: String? = null,
    val acquiredOn: String,
    val wateringEveryDays: Int,
    val fertilizingEveryDays: Int? = null,
    val lastWateredOn: String? = null,
    val lastFertilizedOn: String? = null,
)

@Serializable
internal data class LibraryDto(
    val version: Int = LibraryJson.CURRENT_VERSION,
    val nextId: Long = 1,
    val plants: List<PlantDto> = emptyList(),
)

/**
 * Format de stockage ET de sauvegarde : le fichier de la bibliothèque et l'export
 * sont exactement le même JSON.
 *
 * Les dates sont écrites en ISO (2026-09-21). Les photos ne sont pas incluses,
 * seulement leur nom de fichier.
 */
object LibraryJson {
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true   // lire un fichier écrit par une version future compatible
        encodeDefaults = true
    }

    fun encode(plants: List<Plant>, nextId: Long): String {
        val dto = LibraryDto(
            version = CURRENT_VERSION,
            nextId = nextId,
            plants = plants.map { it.toDto() },
        )
        return json.encodeToString(LibraryDto.serializer(), dto)
    }

    /** @throws LibraryFormatException si le texte n'est pas une bibliothèque valide. */
    fun decode(text: String): LibraryData {
        val dto = try {
            json.decodeFromString(LibraryDto.serializer(), text)
        } catch (e: RuntimeException) {
            throw LibraryFormatException("Fichier illisible : ${e.message}", e)
        }

        if (dto.version > CURRENT_VERSION) {
            throw LibraryFormatException(
                "Ce fichier a été créé par une version plus récente de l'app (format ${dto.version}).",
            )
        }

        val plants = try {
            dto.plants.map { it.toPlant() }
        } catch (e: RuntimeException) {
            // Date invalide, nom vide, fréquence <= 0...
            throw LibraryFormatException("Plante invalide : ${e.message}", e)
        }

        if (plants.map { it.id }.toSet().size != plants.size) {
            throw LibraryFormatException("Identifiants de plantes en double")
        }

        // Garde-fou : nextId ne doit jamais retomber sur un id déjà utilisé.
        val nextId = maxOf(dto.nextId, (plants.maxOfOrNull { it.id } ?: 0L) + 1)
        return LibraryData(nextId, plants)
    }
}

private fun Plant.toDto() = PlantDto(
    id = id,
    name = name,
    photoPath = photoPath,
    acquiredOn = acquiredOn.toString(),
    wateringEveryDays = wateringEveryDays,
    fertilizingEveryDays = fertilizingEveryDays,
    lastWateredOn = lastWateredOn?.toString(),
    lastFertilizedOn = lastFertilizedOn?.toString(),
)

private fun PlantDto.toPlant() = Plant(
    id = id,
    name = name,
    photoPath = photoPath,
    acquiredOn = LocalDate.parse(acquiredOn),
    wateringEveryDays = wateringEveryDays,
    fertilizingEveryDays = fertilizingEveryDays,
    lastWateredOn = lastWateredOn?.let { LocalDate.parse(it) },
    lastFertilizedOn = lastFertilizedOn?.let { LocalDate.parse(it) },
)
