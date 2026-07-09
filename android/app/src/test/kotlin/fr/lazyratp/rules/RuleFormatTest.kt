package fr.lazyratp.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleFormatTest {

    @Test
    fun `jours vides ou complets se lisent tous les jours`() {
        assertEquals("Tous les jours", RuleFormat.days(emptySet()))
        assertEquals("Tous les jours", RuleFormat.days(setOf(1, 2, 3, 4, 5, 6, 7)))
    }

    @Test
    fun `la semaine et le week-end ont un nom`() {
        assertEquals("Lun-Ven", RuleFormat.days(setOf(1, 2, 3, 4, 5)))
        assertEquals("Week-end", RuleFormat.days(setOf(6, 7)))
    }

    @Test
    fun `les jours epars sont listes dans l'ordre`() {
        assertEquals("Lun Mer Ven", RuleFormat.days(setOf(5, 1, 3)))
    }

    @Test
    fun `les minutes se rendent en HH mm sur deux chiffres`() {
        assertEquals("07:30", RuleFormat.minutesToHhMm(450))
        assertEquals("00:00", RuleFormat.minutesToHhMm(0))
        assertEquals("23:59", RuleFormat.minutesToHhMm(1439))
    }

    @Test
    fun `parseHhMm accepte les formats valides`() {
        assertEquals(450, RuleFormat.parseHhMm("07:30"))
        assertEquals(450, RuleFormat.parseHhMm("7:30"))
        assertEquals(0, RuleFormat.parseHhMm("00:00"))
        assertEquals(1439, RuleFormat.parseHhMm("23:59"))
        assertEquals(450, RuleFormat.parseHhMm("  07:30  "))
    }

    @Test
    fun `parseHhMm refuse tout le reste plutot que de deviner`() {
        assertNull(RuleFormat.parseHhMm("24:00"))
        assertNull(RuleFormat.parseHhMm("12:60"))
        assertNull(RuleFormat.parseHhMm("-1:00"))
        assertNull(RuleFormat.parseHhMm("12"))
        assertNull(RuleFormat.parseHhMm("12:30:00"))
        assertNull(RuleFormat.parseHhMm("abc"))
        assertNull(RuleFormat.parseHhMm(""))
    }

    @Test
    fun `parseHhMm et minutesToHhMm font l'aller-retour`() {
        for (m in listOf(0, 1, 59, 60, 450, 1200, 1439)) {
            assertEquals(m, RuleFormat.parseHhMm(RuleFormat.minutesToHhMm(m)))
        }
    }

    @Test
    fun `une fenetre sans bornes est toute la journee`() {
        assertEquals("Toute la journee", RuleFormat.window(null, null))
        assertEquals("Toute la journee", RuleFormat.window(60, null))
        assertEquals("Toute la journee", RuleFormat.window(null, 60))
    }

    @Test
    fun `une fenetre normale s'affiche telle quelle`() {
        assertEquals("19:00-23:00", RuleFormat.window(19 * 60, 23 * 60))
    }

    @Test
    fun `une fenetre a cheval sur minuit est signalee`() {
        assertEquals("22:00-02:00 (nuit)", RuleFormat.window(22 * 60, 2 * 60))
    }
}
