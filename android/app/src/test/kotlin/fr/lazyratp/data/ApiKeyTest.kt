package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApiKeyTest {

    @Test
    fun `une cle absente se lit aucune`() {
        assertEquals("aucune", ApiKey.mask(""))
        assertEquals("aucune", ApiKey.mask("   "))
    }

    @Test
    fun `seuls les quatre derniers caracteres restent lisibles`() {
        assertEquals("••••••••EAQ1", ApiKey.mask("0llQCcL3cHN5cd16PlvnE5qK1sL4EAQ1"))
    }

    @Test
    fun `le masque ne laisse jamais fuir le debut de la cle`() {
        val key = "0llQCcL3cHN5cd16PlvnE5qK1sL4EAQ1"
        val masked = ApiKey.mask(key)
        assertFalse(masked.contains(key.take(8)))
        assertEquals(12, masked.length)
    }

    @Test
    fun `une cle tres courte est entierement masquee`() {
        assertEquals("••••", ApiKey.mask("abcd"))
        assertEquals("••", ApiKey.mask("ab"))
    }
}
