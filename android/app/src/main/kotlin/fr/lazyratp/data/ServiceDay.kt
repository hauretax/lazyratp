package fr.lazyratp.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Le jour de service des transports ne bascule pas a minuit.
 * A 01h30, le dernier metro appartient encore au service de la veille.
 */
object ServiceDay {

    /** Heure a laquelle on considere qu'un nouveau jour de service commence. */
    const val ROLLOVER_HOUR = 4

    fun of(nowMillis: Long, zone: ZoneId): LocalDate {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return if (now.hour < ROLLOVER_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    /**
     * Premiere borne d'arrivee sondee : 23h59 du jour de service.
     * On part volontairement bas, quitte a monter : une borne trop haute
     * fait repondre no_solution a Navitia.
     */
    fun firstArrivalBound(nowMillis: Long, zone: ZoneId): Long =
        of(nowMillis, zone).atTime(LocalTime.of(23, 59)).atZone(zone).toInstant().toEpochMilli()

    /**
     * Fin du jour de service : le lendemain a [ROLLOVER_HOUR].
     *
     * Sans cette borne, monter la borne d'arrivee finit par ramener les *premiers*
     * trains du matin suivant, qu'on prendrait pour le dernier trajet de la nuit.
     */
    fun endOfService(nowMillis: Long, zone: ZoneId): Long =
        of(nowMillis, zone).plusDays(1).atTime(LocalTime.of(ROLLOVER_HOUR, 0))
            .atZone(zone).toInstant().toEpochMilli()
}
