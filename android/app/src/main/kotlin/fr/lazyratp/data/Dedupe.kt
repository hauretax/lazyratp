package fr.lazyratp.data

/**
 * Navitia rend plusieurs variantes d'un meme depart (best, comfort, rapid,
 * less_fallback_walk). Sur le widget, six lignes sur huit affichaient le meme
 * train de 11h27.
 *
 * On n'en garde qu'une par heure de depart : celle qui arrive le plus tot, et a
 * egalite celle qui a le moins de correspondances. C'est l'heure de depart qui
 * decide de ce qu'on fait, pas la variante.
 */
fun List<Journey>.dedupeByDeparture(): List<Journey> =
    groupBy { it.departure }
        .values
        .map { variants -> variants.minWith(compareBy({ it.arrival }, { it.transfers })) }
        .sortedBy { it.departure }
