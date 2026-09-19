package com.example.plantmanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LibraryJsonTest {

    private val full = Plant(
        id = 3,
        name = "Monstera",
        species = "Monstera deliciosa",
        sunlight = "Lumière indirecte",
        location = "Salon",
        notes = "Tourner le pot chaque semaine",
        photoPath = "photos/3.jpg",
        acquiredOn = LocalDate.of(2026, 3, 14),
        wateringEveryDays = 7,
        fertilizingEveryDays = 30,
        lastWateredOn = LocalDate.of(2026, 9, 18),
        lastFertilizedOn = LocalDate.of(2026, 9, 1),
    )

    private val minimal = Plant(
        id = 4,
        name = "Cactus",
        acquiredOn = LocalDate.of(2026, 5, 2),
        wateringEveryDays = 15,
    )

    @Test
    fun roundTripKeepsEveryField() {
        val text = LibraryJson.encode(listOf(full, minimal), nextId = 5)
        val data = LibraryJson.decode(text)

        assertEquals(listOf(full, minimal), data.plants)
        assertEquals(5L, data.nextId)
    }

    @Test
    fun emptyLibraryRoundTrips() {
        val data = LibraryJson.decode(LibraryJson.encode(emptyList(), nextId = 1))
        assertTrue(data.plants.isEmpty())
        assertEquals(1L, data.nextId)
    }

    @Test
    fun encodedTextCarriesTheFormatVersion() {
        val text = LibraryJson.encode(emptyList(), nextId = 1)
        assertTrue(text.contains("\"version\": ${LibraryJson.CURRENT_VERSION}"))
    }

    @Test
    fun optionalFieldsMayBeMissing() {
        val text = """
            {"version":1,"nextId":2,"plants":[
              {"id":1,"name":"Ficus","acquiredOn":"2026-09-01","wateringEveryDays":7}
            ]}
        """.trimIndent()

        val plant = LibraryJson.decode(text).plants.single()
        assertEquals("Ficus", plant.name)
        assertEquals(null, plant.photoPath)
        assertEquals("", plant.species)
        assertEquals("", plant.sunlight)
        assertEquals("", plant.location)
        assertEquals("", plant.notes)
        assertEquals(null, plant.fertilizingEveryDays)
        assertEquals(null, plant.lastWateredOn)
    }

    @Test
    fun encodedTextCarriesPlantDetails() {
        val text = LibraryJson.encode(listOf(full), nextId = 4)

        assertTrue(text.contains("\"species\": \"Monstera deliciosa\""))
        assertTrue(text.contains("\"sunlight\": \"Lumière indirecte\""))
        assertTrue(text.contains("\"location\": \"Salon\""))
        assertTrue(text.contains("\"notes\": \"Tourner le pot chaque semaine\""))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val text = """
            {"version":1,"nextId":2,"futureField":true,"plants":[
              {"id":1,"name":"Ficus","acquiredOn":"2026-09-01","wateringEveryDays":7,"color":"green"}
            ]}
        """.trimIndent()

        assertEquals(1, LibraryJson.decode(text).plants.size)
    }

    @Test
    fun nextIdNeverFallsBelowAnExistingId() {
        val text = """
            {"version":1,"nextId":1,"plants":[
              {"id":5,"name":"Ficus","acquiredOn":"2026-09-01","wateringEveryDays":7}
            ]}
        """.trimIndent()

        assertEquals(6L, LibraryJson.decode(text).nextId)
    }

    @Test(expected = LibraryFormatException::class)
    fun garbageIsRejected() {
        LibraryJson.decode("ceci n'est pas du json")
    }

    @Test(expected = LibraryFormatException::class)
    fun emptyTextIsRejected() {
        LibraryJson.decode("")
    }

    @Test(expected = LibraryFormatException::class)
    fun newerFormatVersionIsRejected() {
        LibraryJson.decode("""{"version":99,"nextId":1,"plants":[]}""")
    }

    @Test(expected = LibraryFormatException::class)
    fun duplicateIdsAreRejected() {
        val text = """
            {"version":1,"nextId":3,"plants":[
              {"id":1,"name":"A","acquiredOn":"2026-09-01","wateringEveryDays":7},
              {"id":1,"name":"B","acquiredOn":"2026-09-01","wateringEveryDays":7}
            ]}
        """.trimIndent()
        LibraryJson.decode(text)
    }

    @Test(expected = LibraryFormatException::class)
    fun invalidDateIsRejected() {
        val text = """
            {"version":1,"nextId":2,"plants":[
              {"id":1,"name":"A","acquiredOn":"pas-une-date","wateringEveryDays":7}
            ]}
        """.trimIndent()
        LibraryJson.decode(text)
    }

    @Test(expected = LibraryFormatException::class)
    fun invalidFrequencyIsRejected() {
        val text = """
            {"version":1,"nextId":2,"plants":[
              {"id":1,"name":"A","acquiredOn":"2026-09-01","wateringEveryDays":0}
            ]}
        """.trimIndent()
        LibraryJson.decode(text)
    }
}
