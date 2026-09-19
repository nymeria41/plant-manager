package com.example.plantmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val TODAY = LocalDate.of(2026, 9, 21)

private fun at(hour: Int, minute: Int = 0, dayOffset: Long = 0): LocalDateTime =
    LocalDateTime.of(TODAY.plusDays(dayOffset), LocalTime.of(hour, minute))

/**
 * Crée une plante dont l'arrosage est dû dans [waterDueIn] jours à partir d'aujourd'hui
 * (négatif = en retard). Même principe pour l'engrais avec [fertDueIn].
 */
private fun plant(
    name: String,
    waterDueIn: Long,
    every: Int = 7,
    fertDueIn: Long? = null,
    fertEvery: Int = 30,
) = Plant(
    id = name.hashCode().toLong(),
    name = name,
    acquiredOn = TODAY.plusDays(waterDueIn - every),
    wateringEveryDays = every,
    fertilizingEveryDays = fertDueIn?.let { fertEvery },
    lastFertilizedOn = fertDueIn?.let { TODAY.plusDays(it - fertEvery) },
)

class PlantTest {

    @Test
    fun nextWateringStartsFromLastWatering() {
        val p = Plant(
            id = 1, name = "Ficus",
            acquiredOn = LocalDate.of(2026, 1, 1),
            wateringEveryDays = 7,
            lastWateredOn = LocalDate.of(2026, 9, 10),
        )
        assertEquals(LocalDate.of(2026, 9, 17), p.nextDue(Care.WATERING))
    }

    @Test
    fun nextWateringFallsBackOnAcquisitionDate() {
        val p = Plant(
            id = 1, name = "Ficus",
            acquiredOn = LocalDate.of(2026, 9, 1),
            wateringEveryDays = 7,
        )
        assertEquals(LocalDate.of(2026, 9, 8), p.nextDue(Care.WATERING))
    }

    @Test
    fun noFertilizingWithoutFrequency() {
        val p = Plant(id = 1, name = "Cactus", acquiredOn = TODAY, wateringEveryDays = 15)
        assertNull(p.nextDue(Care.FERTILIZING))
    }

    @Test
    fun fertilizingUsesItsOwnLastDate() {
        val p = Plant(
            id = 1, name = "Pothos",
            acquiredOn = LocalDate.of(2026, 1, 1),
            wateringEveryDays = 7,
            fertilizingEveryDays = 30,
            lastFertilizedOn = LocalDate.of(2026, 9, 1),
        )
        assertEquals(LocalDate.of(2026, 10, 1), p.nextDue(Care.FERTILIZING))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNameIsRejected() {
        Plant(id = 1, name = "  ", acquiredOn = TODAY, wateringEveryDays = 7)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroFrequencyIsRejected() {
        Plant(id = 1, name = "Ficus", acquiredOn = TODAY, wateringEveryDays = 0)
    }
}

class NotificationPlannerTest {

    private val settings = NotificationSettings() // 9 h 00, 30 jours

    @Test
    fun emptyLibraryPlansNothing() {
        assertTrue(NotificationPlanner.plan(emptyList(), settings, at(7)).isEmpty())
    }

    @Test
    fun plantDueToday_beforeNotificationTime() {
        val plan = NotificationPlanner.plan(listOf(plant("Monstera", waterDueIn = 0)), settings, at(7))

        // Jour J : 3 notifications (9 h, 10 h, 11 h)
        assertEquals(listOf(at(9), at(10), at(11)), plan.take(3).map { it.at })
        plan.take(3).forEach {
            assertEquals(NotificationKind.DUE_DAY, it.kind)
            assertEquals("🌱 C'est l'heure d'arroser", it.title)
            assertEquals("Monstera", it.body)
        }

        // Jours suivants : une notification par jour, texte de rappel
        assertEquals(at(9, dayOffset = 1), plan[3].at)
        assertEquals(NotificationKind.FOLLOW_UP, plan[3].kind)
        assertEquals("💧 Toujours en attente", plan[3].title)
        assertEquals("Monstera (1 j de retard)", plan[3].body)

        // 3 (jour J) + 29 (jours 1 à 29) + 1 (notification de fin de fenêtre)
        assertEquals(33, plan.size)
        assertEquals(NotificationKind.REOPEN_APP, plan.last().kind)
        assertEquals(at(9, dayOffset = 30), plan.last().at)
    }

    @Test
    fun planIsInChronologicalOrder() {
        val plan = NotificationPlanner.plan(listOf(plant("Monstera", 0)), settings, at(7))
        assertEquals(plan.map { it.at }.sorted(), plan.map { it.at })
    }

    @Test
    fun passedRemindersAreSkipped() {
        val plan = NotificationPlanner.plan(listOf(plant("Monstera", 0)), settings, at(10, 30))
        assertEquals(at(11), plan.first().at)
        assertEquals(31, plan.size)
    }

    @Test
    fun allRemindersOfTodayPassed_nextIsTomorrowFollowUp() {
        val plan = NotificationPlanner.plan(listOf(plant("Monstera", 0)), settings, at(12))
        assertEquals(at(9, dayOffset = 1), plan.first().at)
        assertEquals("Monstera (1 j de retard)", plan.first().body)
        assertEquals(30, plan.size)
    }

    @Test
    fun futureDueDate_nothingBeforeIt() {
        val plan = NotificationPlanner.plan(listOf(plant("Ficus", waterDueIn = 10)), settings, at(7))
        assertEquals(at(9, dayOffset = 10), plan.first().at)
        assertEquals(NotificationKind.DUE_DAY, plan.first().kind)
        // 3 (jour 10) + 19 (jours 11 à 29) + 1 (fin de fenêtre)
        assertEquals(23, plan.size)
    }

    @Test
    fun plantDueAfterTheWindow_onlyGetsTheReopenNotification() {
        val plan = NotificationPlanner.plan(listOf(plant("Cactus", waterDueIn = 45)), settings, at(7))
        assertEquals(1, plan.size)
        assertEquals(NotificationKind.REOPEN_APP, plan.single().kind)
    }

    @Test
    fun validatingWateringRebuildsThePlan() {
        val due = plant("Monstera", 0)
        val watered = due.copy(lastWateredOn = TODAY)

        val plan = NotificationPlanner.plan(listOf(watered), settings, at(9, 30))

        // Prochaine échéance = aujourd'hui + 7 jours ; plus rien avant
        assertEquals(at(9, dayOffset = 7), plan.first().at)
        assertEquals(NotificationKind.DUE_DAY, plan.first().kind)
    }

    @Test
    fun customNotificationTimeIsUsed() {
        val custom = NotificationSettings(time = LocalTime.of(18, 30))
        val plan = NotificationPlanner.plan(listOf(plant("Monstera", 0)), custom, at(7))
        assertEquals(listOf(at(18, 30), at(19, 30), at(20, 30)), plan.take(3).map { it.at })
    }

    @Test
    fun windowLengthIsRespected() {
        val short = NotificationSettings(windowDays = 10)
        val plan = NotificationPlanner.plan(listOf(plant("Monstera", 0)), short, at(7))
        // 3 + 9 + 1
        assertEquals(13, plan.size)
        assertEquals(at(9, dayOffset = 10), plan.last().at)
    }

    @Test
    fun dueTodayAndOverdueOnTheSameDay() {
        val plants = listOf(plant("Ficus", 0), plant("Monstera", -2))
        val plan = NotificationPlanner.plan(plants, settings, at(7))

        assertEquals("Aujourd'hui : Ficus · En retard : Monstera (2 j)", plan[0].body)
        // Le lendemain, tout est en retard : le plus en retard d'abord
        assertEquals("Monstera (3 j de retard), Ficus (1 j de retard)", plan[3].body)
    }

    @Test
    fun wateringAndFertilizingShareTheSameNotification() {
        val plants = listOf(
            plant("Ficus", waterDueIn = 0),
            plant("Pothos", waterDueIn = 20, fertDueIn = 0),
        )
        val plan = NotificationPlanner.plan(plants, settings, at(7))

        assertEquals("🌱 C'est l'heure des soins", plan[0].title)
        assertEquals("Arrosage : Ficus\nEngrais : Pothos", plan[0].body)
        assertEquals(
            "Arrosage : Ficus (1 j de retard)\nEngrais : Pothos (1 j de retard)",
            plan[3].body,
        )
    }

    @Test
    fun notificationIdsAreUnique() {
        val plants = listOf("A", "B", "C", "D", "E").mapIndexed { i, n -> plant(n, waterDueIn = i.toLong()) }
        val plan = NotificationPlanner.plan(plants, settings, at(7))
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
    }

    @Test
    fun sameInputsGiveSameIds() {
        val plants = listOf(plant("Monstera", 0))
        val first = NotificationPlanner.plan(plants, settings, at(7)).map { it.id }
        val second = NotificationPlanner.plan(plants, settings, at(8)).map { it.id }
        // Les ids sont liés au jour et au créneau : le plan de 8 h retombe sur les mêmes ids.
        assertEquals(first, second)
    }
}
