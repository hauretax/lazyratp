package fr.lazyratp.data

import kotlinx.serialization.Serializable

/**
 * Ce que le widget montre, et les temps de marche.
 *
 * Reprend les options du CLI qui ont un sens hors d'un terminal. Banniere, en-tete
 * de tableau et alignement n'en font pas partie : c'est du chrome de terminal.
 */
@Serializable
data class Display(
    val showDeparture: Boolean = true,
    val showWait: Boolean = true,
    val showArrival: Boolean = false,
    val showDuration: Boolean = false,
    val showRoute: Boolean = true,

    /**
     * Minutes de marche jusqu'a la gare de depart. Un train qui part dans moins que ca
     * est hors de portee : le CLI le colore, le widget l'estompe.
     */
    val walkDeparture: Int = 0,

    /** Minutes de marche depuis la gare d'arrivee, ajoutees a l'heure affichee. */
    val walkArrival: Int = 0,
) {
    /** Au moins une colonne, sans quoi le widget serait vide. */
    val isEmpty: Boolean
        get() = !showDeparture && !showWait && !showArrival && !showDuration && !showRoute
}

object Walk {

    private const val MINUTE_MILLIS = 60_000L

    fun waitMinutes(departure: Long, nowMillis: Long): Int =
        ((departure - nowMillis) / MINUTE_MILLIS).toInt()

    /**
     * Hors de portee : le train part avant qu'on ait fini de marcher jusqu'au quai.
     * A zero minute de marche, tout ce qui n'est pas deja parti reste attrapable.
     */
    fun isReachable(waitMinutes: Int, walkDeparture: Int): Boolean = when {
        waitMinutes < 0 -> false
        walkDeparture <= 0 -> true
        else -> waitMinutes > walkDeparture
    }

    /** L'heure a laquelle on est vraiment arrive, marche finale comprise. */
    fun arrivalWithWalk(arrival: Long, walkArrival: Int): Long =
        arrival + walkArrival * MINUTE_MILLIS

    fun durationLabel(seconds: Int): String {
        val minutes = seconds / 60
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
    }
}
