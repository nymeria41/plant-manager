package com.example.plantmanager.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Un soin à faire pour une plante, avec son retard en jours (0 = dû ce jour-là). */
data class DueTask(
    val plantId: Long,
    val plantName: String,
    val care: Care,
    val overdueDays: Int,
) {
    val isNew: Boolean get() = overdueDays == 0
}

enum class NotificationKind {
    /** Jour d'échéance : notification principale et rappels à +1 h et +2 h. */
    DUE_DAY,

    /** Jours suivants : une seule notification par jour. */
    FOLLOW_UP,

    /** Dernière notification de la fenêtre : demande d'ouvrir l'app. */
    REOPEN_APP,
}

/** Une notification à programmer. [id] sert de requestCode pour l'alarme Android. */
data class PlannedNotification(
    val id: Int,
    val at: LocalDateTime,
    val title: String,
    val body: String,
    val kind: NotificationKind,
)

/**
 * Calcule le plan complet des notifications.
 *
 * Fonction pure : aucun accès à Android, à la base ou à l'horloge système.
 * L'adaptateur Android annule toutes les alarmes existantes puis programme celles-ci,
 * à chaque ouverture de l'app, validation d'un soin, modification d'une plante ou d'un réglage.
 *
 * Hypothèse du plan : rien n'est validé dans le futur. Une plante due reste donc due
 * (et en retard croissant) jusqu'à ce que l'utilisateur ouvre l'app et valide.
 */
object NotificationPlanner {

    private val DUE_DAY_HOUR_OFFSETS = listOf(0L, 1L, 2L)
    private const val REOPEN_SLOT = 9

    fun plan(
        plants: List<Plant>,
        settings: NotificationSettings,
        now: LocalDateTime,
    ): List<PlannedNotification> {
        if (plants.isEmpty()) return emptyList()

        val today = now.toLocalDate()
        val planned = mutableListOf<PlannedNotification>()

        for (dayIndex in 0 until settings.windowDays) {
            val day = today.plusDays(dayIndex.toLong())
            val tasks = dueTasks(plants, day)
            if (tasks.isEmpty()) continue

            val isDueDay = tasks.any { it.isNew }
            val message = NotificationTexts.build(tasks, isDueDay, settings.maxNamesListed)
            val hourOffsets = if (isDueDay) DUE_DAY_HOUR_OFFSETS else listOf(0L)
            val kind = if (isDueDay) NotificationKind.DUE_DAY else NotificationKind.FOLLOW_UP

            hourOffsets.forEachIndexed { slot, hours ->
                val at = LocalDateTime.of(day, settings.time).plusHours(hours)
                if (at.isAfter(now)) {
                    planned += PlannedNotification(
                        id = notificationId(day, slot),
                        at = at,
                        title = message.title,
                        body = message.body,
                        kind = kind,
                    )
                }
            }
        }

        val reopenDay = today.plusDays(settings.windowDays.toLong())
        planned += PlannedNotification(
            id = notificationId(reopenDay, REOPEN_SLOT),
            at = LocalDateTime.of(reopenDay, settings.time),
            title = "🌿 Tes rappels vont s'arrêter",
            body = "Ouvre l'app pour réactiver tes rappels.",
            kind = NotificationKind.REOPEN_APP,
        )

        return planned
    }

    /**
     * Soins dus (échéance <= [day]) ce jour-là, les plus en retard d'abord,
     * puis par ordre alphabétique.
     */
    fun dueTasks(plants: List<Plant>, day: LocalDate): List<DueTask> =
        plants
            .flatMap { plant ->
                Care.values().mapNotNull { care ->
                    val due = plant.nextDue(care) ?: return@mapNotNull null
                    if (due.isAfter(day)) {
                        null
                    } else {
                        DueTask(
                            plantId = plant.id,
                            plantName = plant.name,
                            care = care,
                            overdueDays = ChronoUnit.DAYS.between(due, day).toInt(),
                        )
                    }
                }
            }
            .sortedWith(
                compareByDescending<DueTask> { it.overdueDays }
                    .thenBy { it.plantName.lowercase() },
            )

    /**
     * Identifiant stable et unique par (jour, créneau) : 10 créneaux par jour.
     * Le même identifiant est recalculé à chaque replanification, ce qui permet
     * d'annuler proprement les anciennes alarmes.
     */
    fun notificationId(day: LocalDate, slot: Int): Int =
        (day.toEpochDay() * 10 + slot).toInt()
}
