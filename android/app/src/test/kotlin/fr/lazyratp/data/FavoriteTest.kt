package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FavoriteTest {

    private val a = Station("sa:A", "Bureau")
    private val b = Station("sa:B", "Maison")

    @Test
    fun `l'identifiant d'un favori simple ne tient qu'aux gares`() {
        assertEquals("sa:A>sa:B", Favorite(a, b).id)
    }

    @Test
    fun `deux requetes differentes sur les memes gares ont des identifiants differents`() {
        val next = Favorite(a, b)
        val last = Favorite(a, b, mode = TripMode.LAST_JOURNEY)
        val noBus = Favorite(a, b, forbiddenModes = setOf(PhysicalMode.BUS))

        assertNotEquals(next.id, last.id)
        assertNotEquals(next.id, noBus.id)
        assertNotEquals(last.id, noBus.id)
    }

    @Test
    fun `l'identifiant ne depend pas de l'ordre des exclusions`() {
        val x = Favorite(a, b, forbiddenModes = setOf("physical_mode:Bus", "physical_mode:Metro"))
        val y = Favorite(a, b, forbiddenModes = setOf("physical_mode:Metro", "physical_mode:Bus"))
        assertEquals(x.id, y.id)
    }

    @Test
    fun `le libelle annonce le mode et les exclusions`() {
        assertEquals("Bureau → Maison", Favorite(a, b).label)
        assertEquals("Bureau → Maison (dernier)", Favorite(a, b, mode = TripMode.LAST_JOURNEY).label)
        assertEquals(
            "Bureau → Maison (sans bus)",
            Favorite(a, b, forbiddenModes = setOf(PhysicalMode.BUS)).label,
        )
        assertEquals(
            "Bureau → Maison (dernier, sans bus)",
            Favorite(a, b, mode = TripMode.LAST_JOURNEY, forbiddenModes = setOf(PhysicalMode.BUS)).label,
        )
    }
}
