package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DedupeTest {

    private fun journey(departure: Long, arrival: Long = departure + 1000, transfers: Int = 0) = Journey(
        departure = departure,
        arrival = arrival,
        duration = ((arrival - departure) / 1000).toInt(),
        transfers = transfers,
        steps = emptyList(),
        walkAfterLast = 0,
        cancelled = false,
    )

    @Test
    fun `un seul trajet par heure de depart`() {
        val js = listOf(journey(100), journey(100), journey(100), journey(200))
        assertEquals(listOf(100L, 200L), js.dedupeByDeparture().map { it.departure })
    }

    @Test
    fun `a depart egal on garde celui qui arrive le plus tot`() {
        val slow = journey(100, arrival = 900)
        val fast = journey(100, arrival = 500)
        assertEquals(listOf(fast), listOf(slow, fast).dedupeByDeparture())
    }

    @Test
    fun `a depart et arrivee egaux on garde le moins de correspondances`() {
        val complique = journey(100, arrival = 500, transfers = 3)
        val simple = journey(100, arrival = 500, transfers = 1)
        assertEquals(listOf(simple), listOf(complique, simple).dedupeByDeparture())
    }

    @Test
    fun `le resultat est trie par heure de depart`() {
        val js = listOf(journey(300), journey(100), journey(200))
        assertEquals(listOf(100L, 200L, 300L), js.dedupeByDeparture().map { it.departure })
    }

    @Test
    fun `des departs distincts sont tous conserves`() {
        val js = listOf(journey(100), journey(200), journey(300))
        assertEquals(3, js.dedupeByDeparture().size)
    }

    @Test
    fun `une liste vide reste vide`() {
        assertEquals(emptyList<Journey>(), emptyList<Journey>().dedupeByDeparture())
    }
}
