package fr.lazyratp.data

/**
 * Un favori ARRIVE_BY ne repond pas a "quand part le prochain" mais a "jusqu'a quand
 * puis-je attendre". Ce qui compte n'est donc pas le premier depart, c'est le dernier
 * qui tient encore la cible.
 */
object ArriveBy {

    private const val MINUTE_MILLIS = 60_000L

    /**
     * Le dernier depart encore attrapable, marche jusqu'a la gare comprise.
     * null quand ils sont tous partis : le rendez-vous ne tient plus, et il vaut mieux
     * le dire que d'afficher un horaire qu'on ne peut plus prendre.
     */
    fun latestCatchable(journeys: List<Journey>, nowMillis: Long, walkDeparture: Int): Journey? =
        journeys
            .filterNot { it.cancelled }
            .filter { Walk.isReachable(Walk.waitMinutes(it.departure, nowMillis), walkDeparture) }
            .maxByOrNull { it.departure }

    /**
     * L'heure a laquelle il faut quitter son point de depart pour attraper ce train.
     * Sans temps de marche configure, c'est l'heure du depart elle-meme.
     */
    fun leaveAt(departure: Long, walkDeparture: Int): Long =
        departure - walkDeparture * MINUTE_MILLIS
}
