package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformMatcherTest {

    private val base = 1_752_338_280_000L // une heure de depart de reference, en epoch millis
    private fun min(n: Long) = n * 60_000L

    private fun step(
        departure: Long = base,
        headsign: String = "",
        trainNumber: String = "",
        platform: String = "",
    ) = Step(
        mode = "RER", code = "C", direction = "Versailles", from = "Les Ardoines", to = "BFM",
        duration = 1290, walkBefore = 0, departure = departure, headsign = headsign,
        trainNumber = trainNumber, platform = platform,
    )

    private fun departure(
        mission: String = "",
        trainNumber: String = "",
        expected: Long = base,
        platform: String = "2",
    ) = StopDeparture(
        lineRef = "STIF:Line::C01727:", mission = mission, trainNumber = trainNumber,
        destination = "Versailles", aimedDeparture = expected, expectedDeparture = expected,
        platform = platform,
    )

    @Test
    fun `le numero de train, meme noye dans une reference, emporte l'appariement`() {
        // L'heure SIRI est volontairement fausse (30 min d'ecart) : le numero prime tout.
        val step = step(trainNumber = "148254")
        val departures = listOf(
            departure(trainNumber = "SNCF:2026-07-12:148254:1187", expected = base + min(30), platform = "5"),
            departure(trainNumber = "999999", expected = base, platform = "1"),
        )
        assertEquals("5", PlatformMatcher.platformFor(step, departures))
    }

    @Test
    fun `sans numero de train, la mission departage deux departs de meme minute`() {
        val step = step(headsign = "VACK")
        val departures = listOf(
            departure(mission = "NOVA", expected = base, platform = "1"),
            departure(mission = "VACK", expected = base, platform = "3"),
        )
        assertEquals("3", PlatformMatcher.platformFor(step, departures))
    }

    @Test
    fun `sans mission ni numero, le depart le plus proche dans le temps l'emporte`() {
        val step = step()
        val departures = listOf(
            departure(expected = base + min(4), platform = "1"),
            departure(expected = base + min(1), platform = "2"),
        )
        assertEquals("2", PlatformMatcher.platformFor(step, departures))
    }

    @Test
    fun `une mission identique prime sur une meilleure heure d'un autre train`() {
        val step = step(headsign = "VACK")
        val departures = listOf(
            departure(mission = "NOVA", expected = base, platform = "1"), // pile a l'heure, mais pas la mission
            departure(mission = "VACK", expected = base + min(3), platform = "4"), // la bonne mission, 3 min apres
        )
        assertEquals("4", PlatformMatcher.platformFor(step, departures))
    }

    @Test
    fun `aucun depart dans la fenetre ne rend aucune voie`() {
        val step = step()
        val departures = listOf(departure(expected = base + min(20), platform = "1"))
        assertEquals("", PlatformMatcher.platformFor(step, departures))
    }

    @Test
    fun `un depart sans voie n'est jamais choisi`() {
        val step = step(trainNumber = "148254")
        val departures = listOf(departure(trainNumber = "148254", platform = ""))
        assertEquals("", PlatformMatcher.platformFor(step, departures))
    }

    @Test
    fun `enrich recopie la voie sur le premier troncon`() {
        val journey = Journey(
            departure = base, arrival = base + min(25), duration = 1530, transfers = 0,
            steps = listOf(step(headsign = "VACK")), walkAfterLast = 0, cancelled = false,
        )
        val out = PlatformMatcher.enrich(listOf(journey), listOf(departure(mission = "VACK", platform = "2")))
        assertEquals("2", out.single().steps.single().platform)
    }

    @Test
    fun `enrich ne remplace pas une voie que Navitia connaissait deja`() {
        val journey = Journey(
            departure = base, arrival = base + min(25), duration = 1530, transfers = 0,
            steps = listOf(step(platform = "A")), walkAfterLast = 0, cancelled = false,
        )
        val out = PlatformMatcher.enrich(listOf(journey), listOf(departure(platform = "9")))
        assertEquals("A", out.single().steps.single().platform)
    }

    @Test
    fun `enrich laisse intact un trajet qu'on n'apparie pas`() {
        val journey = Journey(
            departure = base, arrival = base + min(25), duration = 1530, transfers = 0,
            steps = listOf(step()), walkAfterLast = 0, cancelled = false,
        )
        val out = PlatformMatcher.enrich(listOf(journey), listOf(departure(expected = base + min(30))))
        assertEquals("", out.single().steps.single().platform)
    }
}
