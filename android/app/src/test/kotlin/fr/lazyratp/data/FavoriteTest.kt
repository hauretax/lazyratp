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
    fun `un depart depuis la position se lit Ma position`() {
        val f = Favorite(a, b, fromHere = true)
        assertEquals("Ma position → Maison", f.label)
        assertEquals("here>sa:B", f.id)
    }

    @Test
    fun `le meme trajet depuis une gare ou depuis la position sont deux favoris`() {
        assertNotEquals(Favorite(a, b).id, Favorite(a, b, fromHere = true).id)
    }

    @Test
    fun `sans position, un favori depuis la position n'a pas de point de depart`() {
        assertEquals(null, Favorite(a, b, fromHere = true).fromParam(null))
    }

    @Test
    fun `avec une position, on envoie des coordonnees lon puis lat, point-virgule encode`() {
        // Navitia attend longitude;latitude, dans cet ordre contre-intuitif.
        val f = Favorite(a, b, fromHere = true)
        assertEquals("2.347%3B48.8617", f.fromParam(fr.lazyratp.rules.LatLon(48.8617, 2.347)))
    }

    @Test
    fun `un favori depuis une gare ignore la position`() {
        val f = Favorite(a, b)
        assertEquals("sa:A", f.fromParam(null))
        assertEquals("sa:A", f.fromParam(fr.lazyratp.rules.LatLon(48.0, 2.0)))
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
