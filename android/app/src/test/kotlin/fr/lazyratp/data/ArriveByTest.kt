package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ArriveByTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun clock(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 7, 15, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    private fun journey(departure: Long, cancelled: Boolean = false) = Journey(
        departure = departure,
        arrival = departure + 30 * 60_000L,
        duration = 1800,
        transfers = 0,
        steps = listOf(Step("RER", "C", "Versailles", "A", "B", 1800, 0)),
        walkAfterLast = 0,
        cancelled = cancelled,
    )

    private val departures = listOf(
        journey(clock(17, 40)),
        journey(clock(18, 10)),
        journey(clock(18, 30)),
    )

    @Test
    fun `le dernier depart attrapable est le plus tardif, pas le premier`() {
        // Toute la nuance du mode : la question est "jusqu'a quand puis-je attendre".
        val latest = ArriveBy.latestCatchable(departures, clock(17, 0), walkDeparture = 0)

        assertEquals(clock(18, 30), latest?.departure)
    }

    @Test
    fun `un depart deja passe n'est plus attrapable`() {
        val latest = ArriveBy.latestCatchable(
            listOf(journey(clock(17, 40)), journey(clock(18, 10))),
            nowMillis = clock(18, 20),
            walkDeparture = 0,
        )

        assertNull(latest)
    }

    @Test
    fun `le temps de marche retire les departs trop proches`() {
        // 20 min de marche a 18h15 : le 18h30 est hors de portee, il reste le... rien.
        val latest = ArriveBy.latestCatchable(departures, clock(18, 15), walkDeparture = 20)

        assertNull(latest)
    }

    @Test
    fun `avec la marche, on retombe sur un depart plus lointain quand il existe`() {
        val latest = ArriveBy.latestCatchable(departures, clock(17, 55), walkDeparture = 20)

        assertEquals(clock(18, 30), latest?.departure)
    }

    @Test
    fun `un train supprime ne compte pas comme une solution`() {
        val avecAnnule = listOf(journey(clock(18, 10)), journey(clock(18, 30), cancelled = true))

        val latest = ArriveBy.latestCatchable(avecAnnule, clock(17, 0), walkDeparture = 0)

        assertEquals(clock(18, 10), latest?.departure)
    }

    @Test
    fun `l'heure de depart de chez soi retire le temps de marche`() {
        assertEquals(clock(18, 10), ArriveBy.leaveAt(clock(18, 30), walkDeparture = 20))
    }

    @Test
    fun `sans temps de marche, partir et etre au depart sont la meme heure`() {
        assertEquals(clock(18, 30), ArriveBy.leaveAt(clock(18, 30), walkDeparture = 0))
    }

    @Test
    fun `aucun trajet, aucune solution`() {
        assertNull(ArriveBy.latestCatchable(emptyList(), clock(17, 0), walkDeparture = 0))
    }
}
