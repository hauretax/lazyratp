package fr.lazyratp.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepointTest {

    private val old = "sa:A>sa:B"
    private val new = "sa:A>sa:C"

    @Test
    fun `une regle qui vise le favori est recablee`() {
        val rules = listOf(Rule(id = "r1", favoriteId = old, name = "matin"))
        val out = rules.repointFavorite(old, new)
        assertEquals(new, out.single().favoriteId)
        assertEquals("r1", out.single().id)
    }

    @Test
    fun `une regle qui vise un autre favori n'est pas touchee`() {
        val rules = listOf(Rule(id = "r1", favoriteId = "sa:X>sa:Y"))
        assertEquals(rules, rules.repointFavorite(old, new))
    }

    @Test
    fun `l'epingle est renommee, pas seulement son favoriteId`() {
        val pin = Rule(id = PinRule.id(old), favoriteId = old, name = "Epingle 24 h")
        val out = pin.let { listOf(it) }.repointFavorite(old, new)
        assertEquals(PinRule.id(new), out.single().id)
        assertEquals(new, out.single().favoriteId)
        // La bascule doit retrouver l'epingle apres coup.
        assertTrue(PinRule.isActive(out, new, 0))
    }

    @Test
    fun `un id inchange ne modifie rien`() {
        val rules = listOf(Rule(id = "r1", favoriteId = old))
        assertEquals(rules, rules.repointFavorite(old, old))
    }

    @Test
    fun `l'ordre et les regles non concernees sont preserves`() {
        val rules = listOf(
            Rule(id = "r1", favoriteId = "sa:X>sa:Y"),
            Rule(id = "r2", favoriteId = old, name = "cible"),
            Rule(id = "r3", favoriteId = "sa:Z>sa:W"),
        )
        val out = rules.repointFavorite(old, new)
        assertEquals(listOf("r1", "r2", "r3"), out.map { it.id })
        assertEquals(new, out[1].favoriteId)
        assertEquals("sa:X>sa:Y", out[0].favoriteId)
    }
}
