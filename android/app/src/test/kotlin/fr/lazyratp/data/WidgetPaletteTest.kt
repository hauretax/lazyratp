package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPaletteTest {

    @Test
    fun `le mode systeme ne calcule aucune couleur`() {
        assertNull(WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.SYSTEM)))
    }

    @Test
    fun `le mode clair impose un fond clair et un texte sombre`() {
        val c = WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.LIGHT))!!
        assertTrue("fond clair", !WidgetPalette.isDark(c.background))
        assertTrue("texte sombre", WidgetPalette.isDark(c.text))
    }

    @Test
    fun `le mode sombre impose un fond sombre et un texte clair`() {
        val c = WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.DARK))!!
        assertTrue("fond sombre", WidgetPalette.isDark(c.background))
        assertTrue("texte clair", !WidgetPalette.isDark(c.text))
    }

    @Test
    fun `clair et sombre partagent l'accent choisi`() {
        val accent = "2196F3"
        val light = WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.LIGHT, accent = accent))!!
        val dark = WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.DARK, accent = accent))!!
        assertEquals(light.accent, dark.accent)
        assertEquals(0xFF2196F3.toInt(), light.accent)
    }

    @Test
    fun `le mode terminal est noir avec du texte et un accent vert`() {
        val c = WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.TERMINAL))!!
        assertEquals(0xFF000000.toInt(), c.background)
        assertEquals(c.text, c.accent)
        // Vert : canal vert au maximum, rouge et bleu faibles.
        assertTrue("dominante verte", (c.text shr 8 and 0xFF) > (c.text shr 16 and 0xFF))
        assertTrue("dominante verte", (c.text shr 8 and 0xFF) > (c.text and 0xFF))
    }

    @Test
    fun `le mode terminal ignore l'accent choisi`() {
        // Dans un terminal, tout est de la meme encre : l'accent utilisateur ne s'applique pas.
        val c = WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.TERMINAL, accent = "FF0000"))!!
        assertNotEquals(0xFFFF0000.toInt(), c.accent)
    }

    @Test
    fun `le mode personnalise reprend les trois couleurs telles quelles`() {
        val c = WidgetPalette.resolve(
            WidgetTheme(mode = ThemeMode.CUSTOM, background = "000000", text = "FFFFFF", accent = "FF9800"),
        )!!
        assertEquals(0xFF000000.toInt(), c.background)
        assertEquals(0xFFFFFFFF.toInt(), c.text)
        assertEquals(0xFFFF9800.toInt(), c.accent)
    }

    @Test
    fun `le texte secondaire se situe entre le texte et le fond`() {
        // Fondu : chaque canal est strictement entre celui du fond (0) et celui du texte (255).
        val c = WidgetPalette.resolve(
            WidgetTheme(mode = ThemeMode.CUSTOM, background = "000000", text = "FFFFFF", accent = "FF9800"),
        )!!
        assertNotEquals(c.text, c.textVariant)
        assertNotEquals(c.background, c.textVariant)
        val channel = c.textVariant and 0xFF
        assertTrue("entre le fond et le texte", channel in 1..254)
    }

    @Test
    fun `l'erreur est un rouge clair sur fond sombre, fonce sur fond clair`() {
        val onDark = WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.DARK))!!.error
        val onLight = WidgetPalette.resolve(WidgetTheme(mode = ThemeMode.LIGHT))!!.error
        assertNotEquals(onDark, onLight)
        assertTrue("lisible sur fond sombre", !WidgetPalette.isDark(onDark))
        assertTrue("lisible sur fond clair", WidgetPalette.isDark(onLight))
    }

    @Test
    fun `une couleur a six chiffres recoit une opacite pleine`() {
        assertEquals(0xFFCC30FF.toInt(), WidgetPalette.parseHex("CC30FF", 0))
    }

    @Test
    fun `le croisillon en tete est tolere`() {
        assertEquals(0xFF7C4DFF.toInt(), WidgetPalette.parseHex("#7C4DFF", 0))
    }

    @Test
    fun `une saisie invalide retombe sur le repli, jamais sur du transparent`() {
        val fallback = 0xFF123456.toInt()
        assertEquals(fallback, WidgetPalette.parseHex("pas une couleur", fallback))
        assertEquals(fallback, WidgetPalette.parseHex("", fallback))
        assertEquals(fallback, WidgetPalette.parseHex("FFF", fallback))
    }

    @Test
    fun `onColor choisit du noir sur une couleur claire et du blanc sur une sombre`() {
        assertEquals(0xFF000000.toInt(), WidgetPalette.onColor(0xFFFFCC30.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), WidgetPalette.onColor(0xFF1C1B1F.toInt()))
    }
}
