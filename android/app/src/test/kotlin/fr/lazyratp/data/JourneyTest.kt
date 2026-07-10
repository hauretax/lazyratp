package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyTest {

    private fun step(code: String, direction: String = "") =
        Step(mode = "Metro", code = code, direction = direction, from = "A", to = "B", duration = 600, walkBefore = 0)

    private fun journey(steps: List<Step>, transfers: Int) = Journey(
        departure = 0,
        arrival = 0,
        duration = 0,
        transfers = transfers,
        steps = steps,
        walkAfterLast = 0,
        cancelled = false,
    )

    @Test
    fun `un trajet sans transport public n'a pas de destination`() {
        assertEquals("", journey(emptyList(), transfers = 0).dest)
    }

    @Test
    fun `un trajet direct affiche la direction de sa ligne`() {
        val j = journey(listOf(step("4", "Porte de Clignancourt")), transfers = 0)
        assertEquals("Porte de Clignancourt", j.dest)
    }

    @Test
    fun `deux lignes font une correspondance`() {
        val j = journey(listOf(step("4"), step("P")), transfers = 1)
        assertEquals("1 corresp.", j.dest)
    }

    @Test
    fun `trois lignes font deux correspondances, pas trois`() {
        // Le cas vu sur le widget : B > 5 > P, annonce a tort "3 corresp.".
        val j = journey(listOf(step("B"), step("5"), step("P")), transfers = 2)
        assertEquals("2 corresp.", j.dest)
    }

    @Test
    fun `on lit nb_transfers, pas le nombre de troncons`() {
        // Navitia peut decouper une meme ligne en deux troncons sans correspondance.
        val j = journey(listOf(step("P", "Meaux"), step("P")), transfers = 0)
        assertEquals("Meaux", j.dest)
    }

    @Test
    fun `le code affiche est celui de la premiere ligne`() {
        assertEquals("B", journey(listOf(step("B"), step("5")), transfers = 1).code)
        assertEquals("", journey(emptyList(), transfers = 0).code)
    }
}
