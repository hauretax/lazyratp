package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTest {

    private val minute = 60_000L

    @Test
    fun `par defaut on montre le depart, l'attente et le chemin`() {
        val d = Display()
        assertTrue(d.showDeparture)
        assertTrue(d.showWait)
        assertTrue(d.showRoute)
        assertFalse(d.showArrival)
        assertFalse(d.showDuration)
        assertFalse(d.isEmpty)
    }

    @Test
    fun `tout decocher rend l'affichage vide`() {
        val d = Display(
            showDeparture = false,
            showWait = false,
            showArrival = false,
            showDuration = false,
            showRoute = false,
        )
        assertTrue(d.isEmpty)
    }

    @Test
    fun `l'attente se compte en minutes entieres`() {
        assertEquals(10, Walk.waitMinutes(10 * minute, 0))
        assertEquals(0, Walk.waitMinutes(30_000, 0))
        assertEquals(-5, Walk.waitMinutes(0, 5 * minute))
    }

    @Test
    fun `sans temps de marche, tout ce qui n'est pas parti est attrapable`() {
        assertTrue(Walk.isReachable(waitMinutes = 0, walkDeparture = 0))
        assertTrue(Walk.isReachable(waitMinutes = 1, walkDeparture = 0))
        assertFalse(Walk.isReachable(waitMinutes = -1, walkDeparture = 0))
    }

    @Test
    fun `un train qui part dans moins que le temps de marche est hors de portee`() {
        assertFalse(Walk.isReachable(waitMinutes = 5, walkDeparture = 10))
        assertFalse(Walk.isReachable(waitMinutes = 10, walkDeparture = 10))
        assertTrue(Walk.isReachable(waitMinutes = 11, walkDeparture = 10))
    }

    @Test
    fun `la marche finale decale l'heure d'arrivee`() {
        assertEquals(5 * minute, Walk.arrivalWithWalk(0, walkArrival = 5))
        assertEquals(0L, Walk.arrivalWithWalk(0, walkArrival = 0))
    }

    @Test
    fun `la duree se lit en minutes puis en heures`() {
        assertEquals("9m", Walk.durationLabel(9 * 60))
        assertEquals("59m", Walk.durationLabel(59 * 60))
        assertEquals("1h00", Walk.durationLabel(60 * 60))
        assertEquals("1h39", Walk.durationLabel(99 * 60))
        assertEquals("2h05", Walk.durationLabel(125 * 60))
    }
}
