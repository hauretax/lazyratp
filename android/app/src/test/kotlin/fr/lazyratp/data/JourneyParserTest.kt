package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyParserTest {

    /**
     * Fragment calque sur une vraie reponse PRIM : un RER C touche par deux perturbations
     * ascenseur (une par quai), plus une perturbation passee qui ne doit pas apparaitre.
     */
    private val body = """
    {
      "disruptions": [
        {
          "id": "imp-active-1", "status": "active", "cause": "perturbation",
          "severity": {"name": "perturbée", "effect": "SIGNIFICANT_DELAYS"},
          "messages": [
            {"text": "Panne d'un ascenseur", "channel": {"types": ["title"]}},
            {"text": "<p>Ascenseur Hall &lt;&gt; Quais C/D en panne</p>", "channel": {"types": ["web"], "content_type": "text/html"}}
          ]
        },
        {
          "id": "imp-active-2", "status": "active", "cause": "perturbation",
          "severity": {"name": "perturbée", "effect": "SIGNIFICANT_DELAYS"},
          "messages": [
            {"text": "Panne d'un ascenseur", "channel": {"types": ["title"]}},
            {"text": "Ascenseur Hall Quais E/F en panne", "channel": {"types": ["web"]}}
          ]
        },
        {
          "id": "imp-past", "status": "past",
          "severity": {"name": "bloquante"},
          "messages": [{"text": "Travaux termines", "channel": {"types": ["title"]}}]
        }
      ],
      "journeys": [
        {
          "departure_date_time": "20260712T173126",
          "arrival_date_time": "20260712T175656",
          "duration": 1530, "nb_transfers": 0, "status": "",
          "sections": [
            {"type": "street_network", "duration": 180},
            {
              "type": "public_transport", "duration": 1290,
              "departure_date_time": "20260712T173126",
              "arrival_date_time": "20260712T175256",
              "from": {"stop_point": {"name": "Les Ardoines"}},
              "to": {"stop_point": {"name": "Bibliothèque François Mitterrand"}},
              "display_informations": {
                "commercial_mode": "RER", "code": "C", "color": "FFCC30",
                "direction": "Versailles Château Rive Gauche",
                "links": [
                  {"type": "stop_area", "id": "sa-1"},
                  {"type": "disruption", "id": "imp-active-1"},
                  {"type": "disruption", "id": "imp-active-2"},
                  {"type": "disruption", "id": "imp-past"},
                  {"type": "disruption", "id": "imp-unknown"}
                ]
              },
              "stop_date_times": [
                {"stop_point": {"name": "Les Ardoines", "platform_code": "2"}},
                {"stop_point": {"name": "Bibliothèque François Mitterrand"}}
              ]
            }
          ]
        }
      ]
    }
    """.trimIndent()

    private val journey get() = JourneyParser.parse(body).single()

    @Test
    fun `le troncon porte la ligne, sa couleur et sa direction`() {
        val step = journey.steps.single()
        assertEquals("RER", step.mode)
        assertEquals("C", step.code)
        assertEquals("FFCC30", step.color)
        assertEquals("Versailles Château Rive Gauche", step.direction)
        assertEquals("Les Ardoines", step.from)
        assertEquals("Bibliothèque François Mitterrand", step.to)
    }

    @Test
    fun `la marche precedant le troncon est reportee`() {
        assertEquals(180, journey.steps.single().walkBefore)
    }

    @Test
    fun `la voie est lue quand PRIM la fournit`() {
        assertEquals("2", journey.steps.single().platform)
    }

    @Test
    fun `seules les perturbations actives et referencees sont retenues`() {
        // imp-past est passee, imp-unknown n'existe pas dans le tableau racine : ni l'une ni
        // l'autre ne doit apparaitre.
        val severities = journey.disruptions.map { it.severity }
        assertTrue("aucune perturbation passee", journey.disruptions.none { it.title == "Travaux termines" })
        assertEquals(listOf("perturbée", "perturbée"), severities)
    }

    @Test
    fun `deux quais distincts restent deux perturbations`() {
        // Meme titre, messages differents : la deduplication porte sur le message, pas le titre.
        assertEquals(2, journey.disruptions.size)
        assertEquals("Panne d'un ascenseur", journey.disruptions.first().title)
    }

    @Test
    fun `le message web est nettoye de son HTML et de ses entites`() {
        val first = journey.disruptions.first { it.message.contains("C/D") }
        assertEquals("Ascenseur Hall <> Quais C/D en panne", first.message)
    }

    @Test
    fun `un depart identique dedupe ne casse pas le parsing`() {
        // Le compteur de correspondances lit nb_transfers, pas le nombre de troncons.
        assertEquals(0, journey.transfers)
        assertEquals("Versailles Château Rive Gauche", journey.dest)
    }

    @Test
    fun `une perturbation sans contenu humain est ecartee`() {
        // Navitia rend des perturbations structurelles ("trip modified") sans message ni
        // titre : leur seul libelle est le nom de severite, en anglais. Elles n'apprennent
        // rien et ne doivent pas apparaitre.
        val withEmpty = """
        {
          "disruptions": [
            {"id": "d1", "status": "active", "severity": {"name": "trip modified"}, "messages": []}
          ],
          "journeys": [{
            "departure_date_time":"20260712T080000","arrival_date_time":"20260712T081000",
            "duration":600,"nb_transfers":0,
            "sections":[{"type":"public_transport","from":{"stop_point":{"name":"A"}},
              "to":{"stop_point":{"name":"B"}},
              "display_informations":{"commercial_mode":"RER","code":"C",
                "links":[{"type":"disruption","id":"d1"}]}}]
          }]
        }
        """.trimIndent()
        assertTrue(JourneyParser.parse(withEmpty).single().disruptions.isEmpty())
    }

    @Test
    fun `un corps sans perturbations rend une liste vide, pas une erreur`() {
        val minimal = """{"journeys":[{"departure_date_time":"20260712T080000","arrival_date_time":"20260712T081000","duration":600,"nb_transfers":0,"sections":[{"type":"public_transport","from":{"stop_point":{"name":"A"}},"to":{"stop_point":{"name":"B"}},"display_informations":{"commercial_mode":"Metro","code":"4"}}]}]}"""
        val j = JourneyParser.parse(minimal).single()
        assertTrue(j.disruptions.isEmpty())
        assertEquals("", j.steps.single().platform)
        assertEquals("4", j.steps.single().code)
    }

    @Test
    fun `un corps vide ou sans journeys ne plante pas`() {
        assertTrue(JourneyParser.parse("{}").isEmpty())
        assertTrue(JourneyParser.parse("""{"journeys":[]}""").isEmpty())
    }

    @Test
    fun `un trajet NO_SERVICE est marque supprime`() {
        val cancelled = """{"journeys":[{"status":"NO_SERVICE","departure_date_time":"20260712T080000","arrival_date_time":"20260712T081000","duration":600,"nb_transfers":0,"sections":[{"type":"public_transport","from":{"stop_point":{"name":"A"}},"to":{"stop_point":{"name":"B"}},"display_informations":{"commercial_mode":"Metro","code":"4"}}]}]}"""
        assertTrue(JourneyParser.parse(cancelled).single().cancelled)
    }
}
