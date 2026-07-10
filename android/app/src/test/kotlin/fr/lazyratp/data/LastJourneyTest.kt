package fr.lazyratp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastJourneyTest {

    private val hour = 3_600_000L

    private fun journey(departure: Long) = Journey(
        departure = departure,
        arrival = departure + 30 * 60_000,
        duration = 1800,
        transfers = 0,
        steps = emptyList(),
        walkAfterLast = 0,
        cancelled = false,
    )

    @Test
    fun `s'arrete des que le dernier depart cesse de monter`() = runBlocking {
        val probes = mutableListOf<Long>()
        // Le depart monte, puis sature : imite Chatelet -> La Ferte.
        val answers = listOf(1000L, 2000L, 2000L, 9999L)
        var i = 0

        val result = LastJourney.find(firstBound = 0, stepMillis = hour) { bound ->
            probes += bound
            listOf(journey(answers[i++]))
        }

        assertEquals(2000L, result!!.departure)
        // 3 sondages : 1000 -> 2000 -> 2000 (saturation, on s'arrete sans consommer 9999).
        assertEquals(listOf(0L, hour, 2 * hour), probes)
    }

    @Test
    fun `continue de monter tant que le depart recule`() = runBlocking {
        // Imite Chatelet -> Gare du Nord : 23h56, 00h17, 00h47, 01h17, 01h48, puis sature.
        val answers = listOf(100L, 200L, 300L, 400L, 500L, 500L)
        var i = 0
        val result = LastJourney.find(firstBound = 0, stepMillis = hour, maxProbes = 6) {
            listOf(journey(answers[i++]))
        }
        assertEquals(500L, result!!.departure)
    }

    @Test
    fun `une absence de solution des le premier palier ne rend rien`() = runBlocking {
        val result = LastJourney.find(firstBound = 0) { emptyList() }
        assertNull(result)
    }

    @Test
    fun `une absence de solution a un palier ulterieur garde le meilleur trouve`() = runBlocking {
        var i = 0
        val result = LastJourney.find(firstBound = 0) {
            if (i++ == 0) listOf(journey(4242L)) else emptyList()
        }
        assertEquals(4242L, result!!.departure)
    }

    @Test
    fun `le nombre de sondages est borne`() = runBlocking {
        var calls = 0
        // Le depart monte indefiniment : seul maxProbes arrete la boucle.
        LastJourney.find(firstBound = 0, maxProbes = 3) {
            calls++
            listOf(journey(calls * 1000L))
        }
        assertEquals(3, calls)
    }

    @Test
    fun `retient le depart le plus tardif parmi ceux d'un meme palier`() = runBlocking {
        var i = 0
        val result = LastJourney.find(firstBound = 0) {
            if (i++ == 0) listOf(journey(100L), journey(900L), journey(500L)) else emptyList()
        }
        assertEquals(900L, result!!.departure)
    }

    @Test
    fun `la borne haute arrete la montee avant les trains du lendemain`() = runBlocking {
        val probes = mutableListOf<Long>()
        var i = 0
        // Sans lastBound, le depart monterait indefiniment : c'est le cas du metro,
        // ou passe 4h on recolte les premiers trains du matin suivant.
        val result = LastJourney.find(
            firstBound = 0,
            lastBound = 2 * hour,
            stepMillis = hour,
        ) { bound ->
            probes += bound
            listOf(journey(++i * 1000L))
        }

        // Paliers 0, 1h, 2h sondes ; 3h > lastBound, on s'arrete.
        assertEquals(listOf(0L, hour, 2 * hour), probes)
        assertEquals(3000L, result!!.departure)
    }

    @Test
    fun `une borne haute deja depassee ne declenche aucun appel`() = runBlocking {
        var calls = 0
        val result = LastJourney.find(firstBound = 10 * hour, lastBound = hour) {
            calls++
            listOf(journey(1L))
        }
        assertEquals(0, calls)
        assertNull(result)
    }

    @Test
    fun `un palier qui recule ne remplace pas le meilleur`() = runBlocking {
        var i = 0
        val result = LastJourney.find(firstBound = 0) {
            if (i++ == 0) listOf(journey(800L)) else listOf(journey(300L))
        }
        assertEquals(800L, result!!.departure)
    }
}
