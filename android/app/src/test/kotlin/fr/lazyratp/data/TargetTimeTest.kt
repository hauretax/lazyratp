package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TargetTimeTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun clock(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    @Test
    fun `une date et une heure valides donnent l'instant attendu`() {
        assertEquals(
            clock(2026, 7, 15, 19, 0),
            TargetTime.parse("15/07/2026", "19:00", paris),
        )
    }

    @Test
    fun `un 31 fevrier est refuse, pas arrondi`() {
        // java.time refuse, la ou un parseur naif ramenerait au 28 et ferait rater le train.
        assertNull(TargetTime.parse("31/02/2026", "19:00", paris))
    }

    @Test
    fun `une heure hors bornes est refusee`() {
        assertNull(TargetTime.parse("15/07/2026", "25:00", paris))
        assertNull(TargetTime.parse("15/07/2026", "19:60", paris))
    }

    @Test
    fun `une saisie a moitie tapee n'est pas une date`() {
        assertNull(TargetTime.parse("15/0", "19:00", paris))
        assertNull(TargetTime.parse("15/07/2026", "19", paris))
        assertNull(TargetTime.parse("", "", paris))
    }

    @Test
    fun `du texte n'est pas une date`() {
        assertNull(TargetTime.parse("demain", "ce soir", paris))
    }

    @Test
    fun `l'aller-retour entre la saisie et l'affichage est stable`() {
        val target = clock(2026, 12, 3, 8, 5)

        val date = TargetTime.formatDate(target, paris)
        val time = TargetTime.formatTime(target, paris)

        assertEquals("03/12/2026", date)
        assertEquals("08:05", time)
        assertEquals(target, TargetTime.parse(date, time, paris))
    }

    @Test
    fun `le libelle reste court, l'annee n'y est pas`() {
        assertEquals("15/07 19:00", TargetTime.format(clock(2026, 7, 15, 19, 0), paris))
    }

    @Test
    fun `chaque champ se valide seul`() {
        // La regression : une date juste rougissait parce que l'heure d'a cote etait vide,
        // et l'on cherchait l'erreur dans le champ qui n'en avait pas.
        assertEquals(true, TargetTime.isValidDate("12/07/2026"))
        assertEquals(false, TargetTime.isValidDate("31/02/2026"))
        assertEquals(false, TargetTime.isValidDate(""))

        assertEquals(true, TargetTime.isValidTime("19:00"))
        assertEquals(false, TargetTime.isValidTime("25:00"))
        assertEquals(false, TargetTime.isValidTime(""))
    }

    @Test
    fun `l'heure d'ete est prise en compte`() {
        // 15 juillet : Paris est a UTC+2. Sans fuseau, la cible partirait deux heures a cote.
        assertEquals(
            java.time.Instant.parse("2026-07-15T17:00:00Z").toEpochMilli(),
            TargetTime.parse("15/07/2026", "19:00", paris),
        )
    }
}
