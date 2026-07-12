package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiriParserTest {

    /**
     * Fragment calque sur une vraie reponse SIRI Lite StopMonitoring de PRIM : deux departs
     * a quai, un troisieme sans arret surveille (a ignorer). On y melange a dessein les deux
     * formes que SIRI donne a ses valeurs — objet {"value": x} et tableau [{"value": x}] —
     * ainsi qu'une heure en UTC (Z) et une avec decalage (+02:00).
     */
    private val body = """
    {
      "Siri": {
        "ServiceDelivery": {
          "StopMonitoringDelivery": [
            {
              "MonitoredStopVisit": [
                {
                  "MonitoredVehicleJourney": {
                    "LineRef": {"value": "STIF:Line::C01727:"},
                    "JourneyNote": [{"value": "VACK"}],
                    "VehicleJourneyName": [{"value": "148254"}],
                    "DestinationName": [{"value": "Versailles Château Rive Gauche"}],
                    "MonitoredCall": {
                      "StopPointName": [{"value": "Les Ardoines"}],
                      "AimedDepartureTime": "2026-07-12T18:18:00.000Z",
                      "ExpectedDepartureTime": "2026-07-12T18:19:00.000Z",
                      "DeparturePlatformName": {"value": "2"}
                    }
                  }
                },
                {
                  "MonitoredVehicleJourney": {
                    "LineRef": {"value": "STIF:Line::C01727:"},
                    "JourneyNote": {"value": "NOVA"},
                    "FramedVehicleJourneyRef": {"DatedVehicleJourneyRef": {"value": "SNCF:2026-07-12:148300:1187"}},
                    "DestinationName": {"value": "Pontoise"},
                    "MonitoredCall": {
                      "AimedDepartureTime": "2026-07-12T18:22:00+02:00",
                      "ArrivalPlatformName": {"value": "1"}
                    }
                  }
                },
                {
                  "MonitoredVehicleJourney": {
                    "LineRef": {"value": "STIF:Line::C01727:"}
                  }
                }
              ]
            }
          ]
        }
      }
    }
    """.trimIndent()

    private val departures get() = SiriParser.parse(body)

    @Test
    fun `un depart sans arret surveille est ignore`() {
        // Trois visites, mais la troisieme n'a pas de MonitoredCall : deux departs exploitables.
        assertEquals(2, departures.size)
    }

    @Test
    fun `la voie de depart est lue, sinon celle d'arrivee`() {
        assertEquals("2", departures[0].platform) // DeparturePlatformName
        assertEquals("1", departures[1].platform) // repli sur ArrivalPlatformName
    }

    @Test
    fun `la mission et le numero de train sont lus sous leurs deux formes`() {
        assertEquals("VACK", departures[0].mission)
        assertEquals("148254", departures[0].trainNumber) // VehicleJourneyName
        assertEquals("NOVA", departures[1].mission) // objet, pas tableau
        assertEquals("SNCF:2026-07-12:148300:1187", departures[1].trainNumber) // reference datee
    }

    @Test
    fun `la destination est lue sous ses deux formes`() {
        assertEquals("Versailles Château Rive Gauche", departures[0].destination)
        assertEquals("Pontoise", departures[1].destination)
    }

    @Test
    fun `l'heure retenue est le temps reel s'il existe, sinon le theorique`() {
        // Premier depart : ExpectedDepartureTime (18:19) prime sur Aimed (18:18).
        assertTrue(departures[0].expectedDeparture > 0)
        assertEquals(departures[0].expectedDeparture, departures[0].departure)
        // Second depart : pas de temps reel, on retombe sur le theorique.
        assertEquals(0L, departures[1].expectedDeparture)
        assertEquals(departures[1].aimedDeparture, departures[1].departure)
    }

    @Test
    fun `le decalage horaire est correctement ramene a l'epoch`() {
        // 18:22+02:00 == 16:22 UTC. On verifie l'ecart avec 18:18 UTC du premier (Aimed) :
        // 18:22+02:00 = 16:22Z, soit 116 min avant 18:18Z.
        val secondAimed = departures[1].aimedDeparture
        val firstAimedUtc = departures[0].aimedDeparture
        assertEquals(-116L, (secondAimed - firstAimedUtc) / 60_000L)
    }

    @Test
    fun `un corps vide ou sans SIRI ne plante pas`() {
        assertTrue(SiriParser.parse("{}").isEmpty())
        assertTrue(SiriParser.parse("""{"Siri":{"ServiceDelivery":{}}}""").isEmpty())
    }
}
