package com.example.plantmanager.domain

import java.time.LocalDate
import java.time.LocalTime

/** Les deux types de soin suivis par l'app. */
enum class Care { WATERING, FERTILIZING }

/**
 * Une plante de la bibliothèque.
 *
 * Les prochaines échéances ne sont jamais stockées : elles se calculent à partir
 * du dernier soin réel (ou de la date d'acquisition si aucun soin n'a été enregistré).
 */
data class Plant(
    val id: Long,
    val name: String,
    val species: String = "",
    val sunlight: String = "",
    val location: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    val acquiredOn: LocalDate,
    val wateringEveryDays: Int,
    /** null = cette plante n'a pas besoin d'engrais. */
    val fertilizingEveryDays: Int? = null,
    val lastWateredOn: LocalDate? = null,
    val lastFertilizedOn: LocalDate? = null,
) {
    init {
        require(name.isNotBlank()) { "Le nom de la plante est obligatoire" }
        require(wateringEveryDays > 0) { "La fréquence d'arrosage doit être d'au moins 1 jour" }
        require(fertilizingEveryDays == null || fertilizingEveryDays > 0) {
            "La fréquence d'engrais doit être d'au moins 1 jour"
        }
    }

    /** Date de la prochaine échéance pour ce soin, ou null si le soin n'est pas suivi. */
    fun nextDue(care: Care): LocalDate? = when (care) {
        Care.WATERING ->
            (lastWateredOn ?: acquiredOn).plusDays(wateringEveryDays.toLong())
        Care.FERTILIZING ->
            fertilizingEveryDays?.let { (lastFertilizedOn ?: acquiredOn).plusDays(it.toLong()) }
    }
}

/** Réglages qui pilotent le planificateur de notifications. */
data class NotificationSettings(
    /** Heure de la notification principale (réglable dans l'app). */
    val time: LocalTime = LocalTime.of(9, 0),
    /** Nombre de jours planifiés à l'avance. */
    val windowDays: Int = 30,
    /** Au-delà de ce nombre de plantes, la notification affiche un total au lieu des noms. */
    val maxNamesListed: Int = 4,
) {
    init {
        require(windowDays > 0) { "La fenêtre de planification doit être d'au moins 1 jour" }
        require(maxNamesListed > 0) { "maxNamesListed doit être positif" }
    }
}
