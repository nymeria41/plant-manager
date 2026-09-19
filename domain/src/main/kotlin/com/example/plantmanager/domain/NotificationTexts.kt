package com.example.plantmanager.domain

data class NotificationMessage(val title: String, val body: String)

/**
 * Génère le texte des notifications.
 *
 * Deux styles :
 *  - jour d'échéance (isDueDay = true) : au moins un soin arrive à échéance ce jour-là ;
 *  - rappel (isDueDay = false) : tous les soins sont déjà en retard.
 *
 * Deux modes d'affichage :
 *  - noms : jusqu'à maxNamesListed plantes concernées ;
 *  - total : au-delà, seulement le nombre de plantes.
 */
object NotificationTexts {

    fun build(
        tasks: List<DueTask>,
        isDueDay: Boolean,
        maxNamesListed: Int = 4,
    ): NotificationMessage {
        require(tasks.isNotEmpty()) { "Aucun soin à annoncer" }

        val watering = tasks.filter { it.care == Care.WATERING }
        val fertilizing = tasks.filter { it.care == Care.FERTILIZING }
        val distinctPlants = tasks.map { it.plantId }.distinct().size
        val namesMode = distinctPlants <= maxNamesListed
        // Dès qu'il y a de l'engrais, on étiquette les lignes pour ne pas les confondre.
        val labelled = fertilizing.isNotEmpty()

        val lines = listOfNotNull(
            watering.takeIf { it.isNotEmpty() }
                ?.let { section(it, Care.WATERING, isDueDay, namesMode, labelled) },
            fertilizing.takeIf { it.isNotEmpty() }
                ?.let { section(it, Care.FERTILIZING, isDueDay, namesMode, labelled) },
        )

        return NotificationMessage(
            title = title(isDueDay, watering.isNotEmpty(), fertilizing.isNotEmpty()),
            body = lines.joinToString("\n"),
        )
    }

    private fun title(isDueDay: Boolean, hasWatering: Boolean, hasFertilizing: Boolean): String =
        when {
            !isDueDay -> "💧 Toujours en attente"
            hasWatering && hasFertilizing -> "🌱 C'est l'heure des soins"
            hasFertilizing -> "🌱 C'est l'heure de l'engrais"
            else -> "🌱 C'est l'heure d'arroser"
        }

    private fun section(
        tasks: List<DueTask>,
        care: Care,
        isDueDay: Boolean,
        namesMode: Boolean,
        labelled: Boolean,
    ): String {
        if (!namesMode) return summary(tasks, care, isDueDay)
        val text = names(tasks, isDueDay)
        return if (labelled) "${label(care)} : $text" else text
    }

    private fun label(care: Care): String = when (care) {
        Care.WATERING -> "Arrosage"
        Care.FERTILIZING -> "Engrais"
    }

    /** Mode « noms ». */
    private fun names(tasks: List<DueTask>, isDueDay: Boolean): String {
        if (!isDueDay) {
            return tasks.joinToString(", ") { "${it.plantName} (${it.overdueDays} j de retard)" }
        }

        val dueToday = tasks.filter { it.isNew }
        val late = tasks.filter { !it.isNew }
        if (late.isEmpty()) return dueToday.joinToString(", ") { it.plantName }

        val parts = listOfNotNull(
            dueToday.takeIf { it.isNotEmpty() }
                ?.let { list -> "Aujourd'hui : " + list.joinToString(", ") { it.plantName } },
            "En retard : " + late.joinToString(", ") { "${it.plantName} (${it.overdueDays} j)" },
        )
        return parts.joinToString(" · ")
    }

    /** Mode « total ». */
    private fun summary(tasks: List<DueTask>, care: Care, isDueDay: Boolean): String {
        val n = tasks.size
        val plants = if (n == 1) "1 plante" else "$n plantes"

        if (!isDueDay) {
            val waiting = if (n == 1) "attend toujours son" else "attendent toujours leur"
            val noun = if (care == Care.WATERING) "arrosage" else "engrais"
            return "$plants $waiting $noun"
        }

        val action = if (care == Care.WATERING) "à arroser" else "à fertiliser"
        val late = tasks.count { !it.isNew }
        return if (late == 0) "$plants $action aujourd'hui" else "$plants $action, dont $late en retard"
    }
}
