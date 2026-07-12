package fr.lazyratp.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class DropFavoriteTest {

    private fun rule(id: String, favoriteId: String) = Rule(id = id, favoriteId = favoriteId)

    private val rules = listOf(
        rule("r1", "sa:A>sa:B"),
        rule("r2", "sa:C>sa:D"),
        rule(PinRule.id("sa:A>sa:B"), "sa:A>sa:B"),
        rule("r3", "sa:A>sa:B"),
    )

    @Test
    fun `supprimer un favori emporte toutes ses regles, epingle comprise`() {
        val left = rules.dropFavorite("sa:A>sa:B")

        assertEquals(listOf("r2"), left.map { it.id })
    }

    @Test
    fun `les regles des autres favoris sont intactes`() {
        assertEquals(rules, rules.dropFavorite("sa:X>sa:Y"))
    }

    @Test
    fun `on sait dire combien de regles tomberont avant de supprimer`() {
        assertEquals(3, rules.countForFavorite("sa:A>sa:B"))
        assertEquals(1, rules.countForFavorite("sa:C>sa:D"))
        assertEquals(0, rules.countForFavorite("sa:X>sa:Y"))
    }

    @Test
    fun `une liste vide reste vide`() {
        assertEquals(emptyList<Rule>(), emptyList<Rule>().dropFavorite("sa:A>sa:B"))
    }
}
