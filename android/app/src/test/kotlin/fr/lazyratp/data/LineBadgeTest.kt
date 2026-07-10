package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LineBadgeTest {

    private fun step(mode: String, code: String) =
        Step(mode = mode, code = code, direction = "", from = "A", to = "B", duration = 0, walkBefore = 0)

    @Test
    fun `le metro se dessine en chiffre cercle`() {
        assertEquals("①", LineBadge.of("Métro", "1"))
        assertEquals("④", LineBadge.of("Métro", "4"))
        assertEquals("⑭", LineBadge.of("Métro", "14"))
    }

    @Test
    fun `l'accent du mode n'a pas d'importance`() {
        assertEquals("⑤", LineBadge.of("Metro", "5"))
        assertEquals("⑤", LineBadge.of("MÉTRO", "5"))
    }

    @Test
    fun `les lignes bis restent en clair, faute de chiffre cercle`() {
        assertEquals("3bis", LineBadge.of("Métro", "3bis"))
        assertEquals("7bis", LineBadge.of("Métro", "7bis"))
    }

    @Test
    fun `le RER et le Transilien gardent leur lettre`() {
        assertEquals("B", LineBadge.of("RER", "B"))
        assertEquals("P", LineBadge.of("Train Transilien", "P"))
        assertEquals("E", LineBadge.of("RER", "E"))
    }

    @Test
    fun `un bus ou un tram garde son code tel quel`() {
        assertEquals("7712", LineBadge.of("Bus", "7712"))
        assertEquals("T3a", LineBadge.of("Tramway", "T3a"))
        // Un bus numerote 4 ne doit surtout pas devenir le metro 4.
        assertEquals("4", LineBadge.of("Bus", "4"))
    }

    @Test
    fun `un numero de metro hors de la plage cerclee reste en clair`() {
        assertEquals("21", LineBadge.of("Métro", "21"))
        assertEquals("0", LineBadge.of("Métro", "0"))
    }

    @Test
    fun `le chemin enchaine les lignes`() {
        val steps = listOf(step("RER", "B"), step("Métro", "5"), step("Train Transilien", "P"))
        assertEquals("B › ⑤ › P", LineBadge.route(steps))
    }

    @Test
    fun `un trajet direct n'a qu'une pastille`() {
        assertEquals("④", LineBadge.route(listOf(step("Métro", "4"))))
    }

    @Test
    fun `un trajet sans transport public n'a pas de chemin`() {
        assertEquals("", LineBadge.route(emptyList()))
    }
}
