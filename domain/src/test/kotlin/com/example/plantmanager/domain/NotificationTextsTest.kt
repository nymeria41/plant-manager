package com.example.plantmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Test

private fun task(name: String, overdue: Int = 0, care: Care = Care.WATERING) = DueTask(
    plantId = name.hashCode().toLong(),
    plantName = name,
    care = care,
    overdueDays = overdue,
)

class NotificationTextsTest {

    // ---------- Jour d'échéance ----------

    @Test
    fun dueDay_listsNamesWhenFewerThanFivePlants() {
        val msg = NotificationTexts.build(listOf(task("Monstera"), task("Ficus")), isDueDay = true)
        assertEquals("🌱 C'est l'heure d'arroser", msg.title)
        assertEquals("Monstera, Ficus", msg.body)
    }

    @Test
    fun dueDay_fourPlantsStillListNames() {
        val tasks = listOf("A", "B", "C", "D").map { task(it) }
        assertEquals("A, B, C, D", NotificationTexts.build(tasks, isDueDay = true).body)
    }

    @Test
    fun dueDay_fivePlantsShowOnlyTheCount() {
        val tasks = listOf("A", "B", "C", "D", "E").map { task(it) }
        assertEquals("5 plantes à arroser aujourd'hui", NotificationTexts.build(tasks, isDueDay = true).body)
    }

    @Test
    fun dueDay_mixedNames() {
        val tasks = listOf(task("Ficus"), task("Monstera", overdue = 2))
        val msg = NotificationTexts.build(tasks, isDueDay = true)
        assertEquals("Aujourd'hui : Ficus · En retard : Monstera (2 j)", msg.body)
    }

    @Test
    fun dueDay_mixedSummary() {
        val tasks = listOf("A", "B", "C", "D").map { task(it) } +
            listOf("E", "F", "G").map { task(it, overdue = 1) }
        val msg = NotificationTexts.build(tasks, isDueDay = true)
        assertEquals("7 plantes à arroser, dont 3 en retard", msg.body)
    }

    // ---------- Jours suivants ----------

    @Test
    fun followUp_listsNamesWithDelay() {
        val tasks = listOf(task("Ficus", overdue = 2), task("Pothos", overdue = 1))
        val msg = NotificationTexts.build(tasks, isDueDay = false)
        assertEquals("💧 Toujours en attente", msg.title)
        assertEquals("Ficus (2 j de retard), Pothos (1 j de retard)", msg.body)
    }

    @Test
    fun followUp_summaryWhenFiveOrMore() {
        val tasks = (1..7).map { task("P$it", overdue = 1) }
        assertEquals(
            "7 plantes attendent toujours leur arrosage",
            NotificationTexts.build(tasks, isDueDay = false).body,
        )
    }

    // ---------- Engrais ----------

    @Test
    fun dueDay_fertilizingOnly() {
        val msg = NotificationTexts.build(listOf(task("Pothos", care = Care.FERTILIZING)), isDueDay = true)
        assertEquals("🌱 C'est l'heure de l'engrais", msg.title)
        assertEquals("Engrais : Pothos", msg.body)
    }

    @Test
    fun dueDay_wateringAndFertilizingAreOnSeparateLines() {
        val tasks = listOf(task("Ficus"), task("Pothos", care = Care.FERTILIZING))
        val msg = NotificationTexts.build(tasks, isDueDay = true)
        assertEquals("🌱 C'est l'heure des soins", msg.title)
        assertEquals("Arrosage : Ficus\nEngrais : Pothos", msg.body)
    }

    @Test
    fun followUp_summaryHandlesSingularAndPlural() {
        val tasks = listOf("A", "B", "C", "D").map { task(it, overdue = 1) } +
            task("E", overdue = 1, care = Care.FERTILIZING)
        val msg = NotificationTexts.build(tasks, isDueDay = false)
        assertEquals(
            "4 plantes attendent toujours leur arrosage\n1 plante attend toujours son engrais",
            msg.body,
        )
    }

    // ---------- Robustesse ----------

    @Test(expected = IllegalArgumentException::class)
    fun emptyTaskListIsRejected() {
        NotificationTexts.build(emptyList(), isDueDay = true)
    }
}
