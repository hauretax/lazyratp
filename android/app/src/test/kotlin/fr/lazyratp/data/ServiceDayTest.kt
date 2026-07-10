package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ServiceDayTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(paris).toInstant().toEpochMilli()

    @Test
    fun `apres le basculement, le jour de service est celui du calendrier`() {
        assertEquals(LocalDate.of(2026, 7, 10), ServiceDay.of(at(2026, 7, 10, 4, 0), paris))
        assertEquals(LocalDate.of(2026, 7, 10), ServiceDay.of(at(2026, 7, 10, 12, 0), paris))
        assertEquals(LocalDate.of(2026, 7, 10), ServiceDay.of(at(2026, 7, 10, 23, 59), paris))
    }

    @Test
    fun `avant le basculement, on appartient encore a la veille`() {
        assertEquals(LocalDate.of(2026, 7, 9), ServiceDay.of(at(2026, 7, 10, 0, 1), paris))
        assertEquals(LocalDate.of(2026, 7, 9), ServiceDay.of(at(2026, 7, 10, 1, 30), paris))
        assertEquals(LocalDate.of(2026, 7, 9), ServiceDay.of(at(2026, 7, 10, 3, 59), paris))
    }

    @Test
    fun `le dernier metro de 1h30 compte pour la veille, pas pour le jour naissant`() {
        val lateNight = at(2026, 7, 10, 1, 30)
        assertEquals(LocalDate.of(2026, 7, 9), ServiceDay.of(lateNight, paris))
    }

    @Test
    fun `la premiere borne d'arrivee est 23h59 du jour de service`() {
        val bound = ServiceDay.firstArrivalBound(at(2026, 7, 10, 1, 30), paris)
        val expected = LocalDate.of(2026, 7, 9).atTime(LocalTime.of(23, 59))
            .atZone(paris).toInstant().toEpochMilli()
        assertEquals(expected, bound)
    }

    @Test
    fun `la fin de service est le lendemain a 4h`() {
        val end = ServiceDay.endOfService(at(2026, 7, 10, 22, 0), paris)
        val expected = LocalDate.of(2026, 7, 11).atTime(LocalTime.of(4, 0))
            .atZone(paris).toInstant().toEpochMilli()
        assertEquals(expected, end)
    }

    @Test
    fun `a 1h du matin la fin de service est encore le jour meme a 4h`() {
        // On est le 10 a 01h30, donc jour de service du 9 : la nuit s'acheve le 10 a 4h.
        val end = ServiceDay.endOfService(at(2026, 7, 10, 1, 30), paris)
        val expected = LocalDate.of(2026, 7, 10).atTime(LocalTime.of(4, 0))
            .atZone(paris).toInstant().toEpochMilli()
        assertEquals(expected, end)
    }

    @Test
    fun `en pleine journee la borne est le soir meme`() {
        val bound = ServiceDay.firstArrivalBound(at(2026, 7, 10, 10, 0), paris)
        val expected = LocalDate.of(2026, 7, 10).atTime(LocalTime.of(23, 59))
            .atZone(paris).toInstant().toEpochMilli()
        assertEquals(expected, bound)
    }
}
