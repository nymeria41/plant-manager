package com.example.plantmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

private fun newPlant(name: String) = Plant(
    id = 0, // ignoré : le dépôt attribue l'identifiant
    name = name,
    acquiredOn = LocalDate.of(2026, 9, 1),
    wateringEveryDays = 7,
    fertilizingEveryDays = 30,
)

class PlantRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file: File get() = File(tmp.root, "plants.json")

    @Test
    fun freshRepositoryIsEmpty() {
        val repo = PlantRepository(file)
        assertTrue(repo.all().isEmpty())
        assertNull(repo.loadFailure())
    }

    @Test
    fun addAssignsSequentialIdsAndPersists() {
        val repo = PlantRepository(file)
        val a = repo.add(newPlant("Ficus"))
        val b = repo.add(newPlant("Monstera"))

        assertEquals(1L, a.id)
        assertEquals(2L, b.id)
        // Une nouvelle instance relit le fichier : tout est identique.
        assertEquals(listOf(a, b), PlantRepository(file).all())
    }

    @Test
    fun idsAreNeverReusedAfterADelete() {
        val repo = PlantRepository(file)
        repo.add(newPlant("A"))
        val b = repo.add(newPlant("B"))
        repo.delete(b.id)

        assertEquals(3L, repo.add(newPlant("C")).id)
        // Le compteur survit à un redémarrage.
        assertEquals(4L, PlantRepository(file).add(newPlant("D")).id)
    }

    @Test
    fun deleteReturnsTheRemovedPlant() {
        val repo = PlantRepository(file)
        val a = repo.add(newPlant("Ficus"))

        assertEquals("Ficus", repo.delete(a.id)?.name)
        assertTrue(repo.all().isEmpty())
        assertNull(repo.delete(a.id))
    }

    @Test
    fun updateReplacesThePlant() {
        val repo = PlantRepository(file)
        val a = repo.add(newPlant("Ficus"))

        repo.update(a.copy(name = "Ficus lyrata", wateringEveryDays = 10))

        val reloaded = PlantRepository(file).get(a.id)
        assertEquals("Ficus lyrata", reloaded?.name)
        assertEquals(10, reloaded?.wateringEveryDays)
    }

    @Test(expected = NoSuchElementException::class)
    fun updatingAnUnknownPlantFails() {
        PlantRepository(file).update(newPlant("Fantôme").copy(id = 42))
    }

    @Test
    fun markWateredAndFertilizedArePersisted() {
        val repo = PlantRepository(file)
        val a = repo.add(newPlant("Ficus"))

        repo.markWatered(a.id, LocalDate.of(2026, 9, 20))
        repo.markFertilized(a.id, LocalDate.of(2026, 9, 19))

        val reloaded = PlantRepository(file).get(a.id)
        assertEquals(LocalDate.of(2026, 9, 20), reloaded?.lastWateredOn)
        assertEquals(LocalDate.of(2026, 9, 19), reloaded?.lastFertilizedOn)
        // Et l'échéance suivante en découle directement.
        assertEquals(LocalDate.of(2026, 9, 27), reloaded?.nextDue(Care.WATERING))
    }

    @Test
    fun corruptFileIsSetAsideNotOverwritten() {
        file.writeText("ceci n'est pas du json")

        val repo = PlantRepository(file)

        assertTrue(repo.all().isEmpty())
        assertNotNull(repo.loadFailure())
        assertFalse(file.exists())
        assertTrue(tmp.root.listFiles()!!.any { it.name.startsWith("plants.json.corrupt-") })

        // L'app reste utilisable : on peut ajouter une plante normalement.
        assertEquals(1L, repo.add(newPlant("Ficus")).id)
    }

    @Test
    fun importAddKeepsExistingPlantsAndReassignsIds() {
        val source = PlantRepository(File(tmp.root, "source.json"))
        source.add(newPlant("Ficus"))
        source.add(newPlant("Monstera"))
        val backup = source.exportJson()

        val target = PlantRepository(File(tmp.root, "target.json"))
        target.add(newPlant("Cactus"))
        val result = target.importJson(backup, ImportMode.ADD_TO_EXISTING)

        assertEquals(listOf("Cactus", "Ficus", "Monstera"), result.map { it.name })
        assertEquals(listOf(1L, 2L, 3L), result.map { it.id })
        assertEquals(result, PlantRepository(File(tmp.root, "target.json")).all())
    }

    @Test
    fun importReplaceOverwritesEverything() {
        val source = PlantRepository(File(tmp.root, "source.json"))
        source.add(newPlant("Ficus"))
        val backup = source.exportJson()

        val target = PlantRepository(File(tmp.root, "target.json"))
        target.add(newPlant("Cactus"))
        target.add(newPlant("Aloe"))
        val result = target.importJson(backup, ImportMode.REPLACE_ALL)

        assertEquals(listOf("Ficus"), result.map { it.name })
        // Le compteur d'ids ne recule pas : le prochain id n'a jamais servi.
        assertEquals(3L, target.add(newPlant("Nouveau")).id)
    }

    @Test
    fun invalidImportChangesNothing() {
        val repo = PlantRepository(file)
        repo.add(newPlant("Ficus"))

        try {
            repo.importJson("pas du json", ImportMode.REPLACE_ALL)
            throw AssertionError("LibraryFormatException attendue")
        } catch (e: LibraryFormatException) {
            // attendu
        }

        assertEquals(listOf("Ficus"), repo.all().map { it.name })
        assertEquals(listOf("Ficus"), PlantRepository(file).all().map { it.name })
    }

    @Test
    fun exportIsTheSameFormatAsTheLibraryFile() {
        val repo = PlantRepository(file)
        repo.add(newPlant("Ficus"))

        assertEquals(file.readText(), repo.exportJson())
    }

    @Test
    fun noTemporaryFileIsLeftBehind() {
        val repo = PlantRepository(file)
        repo.add(newPlant("Ficus"))

        assertFalse(File(tmp.root, "plants.json.tmp").exists())
    }
}
