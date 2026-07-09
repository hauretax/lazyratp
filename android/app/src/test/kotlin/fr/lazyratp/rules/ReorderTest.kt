package fr.lazyratp.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {

    private val list = listOf("a", "b", "c", "d")

    @Test
    fun `monter un element d'un cran`() {
        assertEquals(listOf("a", "c", "b", "d"), list.moved(2, 1))
    }

    @Test
    fun `descendre un element d'un cran`() {
        assertEquals(listOf("b", "a", "c", "d"), list.moved(0, 1))
    }

    @Test
    fun `deplacer du debut a la fin`() {
        assertEquals(listOf("b", "c", "d", "a"), list.moved(0, 3))
    }

    @Test
    fun `un index hors bornes ne perd aucun element`() {
        assertEquals(list, list.moved(-1, 2))
        assertEquals(list, list.moved(0, 9))
        assertEquals(list, list.moved(9, 0))
    }

    @Test
    fun `une liste vide reste vide`() {
        assertEquals(emptyList<String>(), emptyList<String>().moved(0, 0))
    }

    @Test
    fun `deplacer sur soi-meme ne change rien`() {
        assertEquals(list, list.moved(2, 2))
    }

    @Test
    fun `la liste d'origine n'est pas modifiee`() {
        val original = listOf("a", "b", "c")
        original.moved(0, 2)
        assertEquals(listOf("a", "b", "c"), original)
    }
}
