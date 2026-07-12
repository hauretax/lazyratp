package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteDraftTest {

    private val a = Station("sa:A", "Bureau")
    private val b = Station("sa:B", "Maison")

    @Test
    fun `un brouillon vide ne donne aucun favori`() {
        assertFalse(FavoriteDraft(from = null, to = null).isComplete)
        assertNull(FavoriteDraft(from = null, to = null).toFavorite(null))
    }

    @Test
    fun `il faut une arrivee, meme en partant de sa position`() {
        val draft = FavoriteDraft(from = null, to = null, fromHere = true)
        assertFalse(draft.isComplete)
        assertNull(draft.toFavorite(null))
    }

    @Test
    fun `partir de sa position dispense de choisir une gare de depart`() {
        val draft = FavoriteDraft(from = null, to = b, fromHere = true)
        assertTrue(draft.isComplete)

        val favorite = draft.toFavorite(null)!!
        assertTrue(favorite.fromHere)
        assertEquals(Favorite.HERE, favorite.from.id)
    }

    @Test
    fun `l'edition conserve la date d'expiration, que le formulaire n'affiche pas`() {
        // La regression : le formulaire reconstruisait le favori a partir de ses seuls
        // champs, donc modifier un favori temporaire le rendait permanent en silence.
        val initial = Favorite(a, b, expiresAt = 1_800_000_000_000L)

        val edited = FavoriteDraft.of(initial).copy(noBus = true).toFavorite(initial)!!

        assertEquals(1_800_000_000_000L, edited.expiresAt)
        assertEquals(setOf(PhysicalMode.BUS), edited.forbiddenModes)
    }

    @Test
    fun `un favori permanent le reste`() {
        val initial = Favorite(a, b)
        assertNull(FavoriteDraft.of(initial).toFavorite(initial)!!.expiresAt)
    }

    @Test
    fun `un ajout n'herite d'aucune expiration`() {
        assertNull(FavoriteDraft(from = a, to = b).toFavorite(null)!!.expiresAt)
    }

    @Test
    fun `ouvrir un favori existant reproduit ses champs a l'identique`() {
        val initial = Favorite(
            from = a,
            to = b,
            mode = TripMode.LAST_JOURNEY,
            forbiddenModes = setOf(PhysicalMode.BUS),
        )

        assertEquals(initial, FavoriteDraft.of(initial).toFavorite(initial))
    }

    @Test
    fun `le depart d'un favori depuis la position n'est pas pre-rempli`() {
        val initial = Favorite(a, b, fromHere = true)
        assertNull(FavoriteDraft.of(initial).from)
    }
}
