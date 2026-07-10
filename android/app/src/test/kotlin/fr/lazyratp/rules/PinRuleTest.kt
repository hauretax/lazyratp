package fr.lazyratp.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinRuleTest {

    private val now = 1_000_000L
    private val fav = "sa:A>sa:B"
    private val autre = Rule(id = "r1", favoriteId = "sa:C>sa:D", name = "matin")

    @Test
    fun `epingler ajoute une regle sans condition en tete`() {
        val rules = PinRule.toggle(listOf(autre), fav, now)
        assertEquals(2, rules.size)
        val pin = rules.first()
        assertEquals(PinRule.id(fav), pin.id)
        assertEquals(fav, pin.favoriteId)
        assertTrue(pin.days.isEmpty())
        assertEquals(null, pin.fromMinutes)
        assertEquals(null, pin.place)
        assertEquals(now + PinRule.DURATION_MILLIS, pin.expiresAt)
    }

    @Test
    fun `epingler deux fois ne cree pas deux epingles`() {
        val once = PinRule.toggle(listOf(autre), fav, now)
        val twice = PinRule.toggle(once, fav, now)
        assertEquals(listOf(autre), twice)
    }

    @Test
    fun `la bascule desepingle puis reepingle`() {
        val pinned = PinRule.toggle(emptyList(), fav, now)
        assertTrue(PinRule.isActive(pinned, fav, now))

        val unpinned = PinRule.toggle(pinned, fav, now)
        assertFalse(PinRule.isActive(unpinned, fav, now))
        assertTrue(unpinned.isEmpty())

        val repinned = PinRule.toggle(unpinned, fav, now)
        assertTrue(PinRule.isActive(repinned, fav, now))
    }

    @Test
    fun `une epingle expiree n'est plus active`() {
        val pinned = PinRule.toggle(emptyList(), fav, now)
        val later = now + PinRule.DURATION_MILLIS + 1
        assertFalse(PinRule.isActive(pinned, fav, later))
    }

    @Test
    fun `basculer une epingle expiree la remplace au lieu de la retirer`() {
        val stale = PinRule.toggle(emptyList(), fav, now)
        val later = now + PinRule.DURATION_MILLIS + 1

        val refreshed = PinRule.toggle(stale, fav, later)
        assertEquals(1, refreshed.size)
        assertEquals(later + PinRule.DURATION_MILLIS, refreshed.first().expiresAt)
        assertTrue(PinRule.isActive(refreshed, fav, later))
    }

    @Test
    fun `l'epingle d'un favori n'affecte pas celle d'un autre`() {
        val a = PinRule.toggle(emptyList(), "sa:A>sa:B", now)
        val ab = PinRule.toggle(a, "sa:C>sa:D", now)
        assertEquals(2, ab.size)
        assertTrue(PinRule.isActive(ab, "sa:A>sa:B", now))
        assertTrue(PinRule.isActive(ab, "sa:C>sa:D", now))

        val onlyC = PinRule.toggle(ab, "sa:A>sa:B", now)
        assertFalse(PinRule.isActive(onlyC, "sa:A>sa:B", now))
        assertTrue(PinRule.isActive(onlyC, "sa:C>sa:D", now))
    }

    @Test
    fun `les autres regles gardent leur ordre relatif`() {
        val r1 = Rule(id = "1", favoriteId = "x")
        val r2 = Rule(id = "2", favoriteId = "y")
        val pinned = PinRule.toggle(listOf(r1, r2), fav, now)
        assertEquals(listOf("1", "2"), pinned.drop(1).map { it.id })
    }
}
