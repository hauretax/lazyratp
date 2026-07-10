package fr.lazyratp.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastJourneyTest {

    private val hour = 3_600_000L

    private fun journey(departure: Long, cancelled: Boolean = false) = Journey(
        departure = departure,
        arrival = departure + 30 * 60_000,
        duration = 1800,
        transfers = 0,
        steps = emptyList(),
        walkAfterLast = 0,
        cancelled = cancelled,
    )

    private fun departures(js: List<Journey>) = js.map { it.departure }

    @Test
    fun `s'arrete des que le dernier depart cesse de monter`() = runBlocking {
        val probes = mutableListOf<Long>()
        val answers = listOf(1000L, 2000L, 2000L, 9999L)
        var i = 0

        val result = LastJourney.find(firstBound = 0, stepMillis = hour) { bound ->
            probes += bound
            listOf(journey(answers[i++]))
        }

        assertEquals(listOf(2000L), departures(result))
        // Trois sondages : 1000 -> 2000 -> 2000 (saturation), sans jamais atteindre 9999.
        assertEquals(listOf(0L, hour, 2 * hour), probes)
    }

    @Test
    fun `rend toute la queue de journee, du plus tardif au plus tot`() = runBlocking {
        var i = 0
        val result = LastJourney.find(firstBound = 0) {
            if (i++ == 0) listOf(journey(300L), journey(900L), journey(600L)) else emptyList()
        }
        assertEquals(listOf(900L, 600L, 300L), departures(result))
    }

    @Test
    fun `un dernier trajet supprime n'arrete pas la montee`() = runBlocking {
        var i = 0
        // Palier 1 : le plus tardif praticable est 500. Palier 2 : 800, ca monte encore.
        // Le 9000 supprime ne doit tromper personne.
        val result = LastJourney.find(firstBound = 0) { _ ->
            when (i++) {
                0 -> listOf(journey(9000L, cancelled = true), journey(500L))
                1 -> listOf(journey(9000L, cancelled = true), journey(800L))
                else -> emptyList()
            }
        }
        assertEquals(listOf(9000L, 800L), departures(result))
    }

    @Test
    fun `le trajet supprime reste affiche, en tete`() = runBlocking {
        var i = 0
        val result = LastJourney.find(firstBound = 0) {
            if (i++ == 0) listOf(journey(700L), journey(900L, cancelled = true)) else emptyList()
        }
        assertEquals(listOf(900L, 700L), departures(result))
        assertTrue(result.first().cancelled)
        // Celui d'avant est bien la, juste derriere.
        assertTrue(!result[1].cancelled)
    }

    @Test
    fun `un palier entierement supprime equivaut a une absence de solution`() = runBlocking {
        var i = 0
        val result = LastJourney.find(firstBound = 0) {
            if (i++ == 0) listOf(journey(400L)) else listOf(journey(9000L, cancelled = true))
        }
        assertEquals(listOf(400L), departures(result))
    }

    @Test
    fun `une absence de solution des le premier palier ne rend rien`() = runBlocking {
        assertTrue(LastJourney.find(firstBound = 0) { emptyList() }.isEmpty())
    }

    @Test
    fun `une absence de solution a un palier ulterieur garde le meilleur trouve`() = runBlocking {
        var i = 0
        val result = LastJourney.find(firstBound = 0) {
            if (i++ == 0) listOf(journey(4242L)) else emptyList()
        }
        assertEquals(listOf(4242L), departures(result))
    }

    @Test
    fun `la borne haute arrete la montee avant les trains du lendemain`() = runBlocking {
        val probes = mutableListOf<Long>()
        var i = 0
        val result = LastJourney.find(firstBound = 0, lastBound = 2 * hour, stepMillis = hour) { bound ->
            probes += bound
            listOf(journey(++i * 1000L))
        }
        assertEquals(listOf(0L, hour, 2 * hour), probes)
        assertEquals(listOf(3000L), departures(result))
    }

    @Test
    fun `une borne haute deja depassee ne declenche aucun appel`() = runBlocking {
        var calls = 0
        val result = LastJourney.find(firstBound = 10 * hour, lastBound = hour) {
            calls++
            listOf(journey(1L))
        }
        assertEquals(0, calls)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `le nombre de sondages est borne`() = runBlocking {
        var calls = 0
        LastJourney.find(firstBound = 0, maxProbes = 3) {
            calls++
            listOf(journey(calls * 1000L))
        }
        assertEquals(3, calls)
    }

    @Test
    fun `un palier qui recule ne remplace pas le meilleur`() = runBlocking {
        var i = 0
        val result = LastJourney.find(firstBound = 0) {
            if (i++ == 0) listOf(journey(800L)) else listOf(journey(300L))
        }
        assertEquals(listOf(800L), departures(result))
    }
}
