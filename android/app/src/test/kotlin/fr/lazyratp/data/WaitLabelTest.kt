package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WaitLabelTest {

    @Test
    fun `un train deja parti se dit parti`() {
        assertEquals("parti", WaitLabel.of(-1))
        assertEquals("parti", WaitLabel.of(-90))
    }

    @Test
    fun `zero minute se dit a quai`() {
        assertEquals("a quai", WaitLabel.of(0))
    }

    @Test
    fun `en dessous d'une heure et demie on compte en minutes`() {
        assertEquals("1 min", WaitLabel.of(1))
        assertEquals("45 min", WaitLabel.of(45))
        assertEquals("89 min", WaitLabel.of(89))
    }

    @Test
    fun `au dela on bascule en heures`() {
        assertEquals("1 h 30", WaitLabel.of(90))
        assertEquals("2 h 00", WaitLabel.of(120))
        assertEquals("12 h 05", WaitLabel.of(725))
    }

    @Test
    fun `les minutes des heures sont sur deux chiffres`() {
        assertEquals("1 h 31", WaitLabel.of(91))
        assertEquals("3 h 09", WaitLabel.of(189))
    }
}
