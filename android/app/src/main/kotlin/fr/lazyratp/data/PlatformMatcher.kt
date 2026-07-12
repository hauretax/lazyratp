package fr.lazyratp.data

import kotlin.math.abs

/**
 * Rapproche les departs SIRI (qui portent la voie) des trajets Navitia (qui portent le
 * detail de la course mais pas la voie), et recopie la voie sur le premier troncon.
 *
 * Fonction pure : le rapprochement se raisonne et se teste sans reseau.
 */
object PlatformMatcher {

    /** Au-dela, deux departs de meme heure affichee sont deux trains differents. */
    private const val TIME_WINDOW_MS = 5 * 60 * 1000L

    /**
     * Recopie la voie SIRI sur le premier troncon de chaque trajet, quand on la retrouve.
     * On ne touche pas un troncon dont Navitia connaissait deja la voie, ni un trajet qu'on
     * n'arrive pas a apparier : mieux vaut pas de voie qu'une fausse.
     */
    fun enrich(journeys: List<Journey>, departures: List<StopDeparture>): List<Journey> {
        if (departures.isEmpty()) return journeys
        return journeys.map { journey ->
            val first = journey.steps.firstOrNull()
            if (first == null || first.platform.isNotBlank()) return@map journey
            val platform = platformFor(first, departures)
            if (platform.isBlank()) {
                journey
            } else {
                journey.copy(
                    steps = journey.steps.toMutableList().also { it[0] = first.copy(platform = platform) },
                )
            }
        }
    }

    /**
     * La voie du depart correspondant a [step], ou "" si aucun ne convient.
     *
     * Le numero de train est unique dans la journee : quand il recoupe une reference SIRI,
     * l'appariement est certain. A defaut, on prend le depart de l'heure la plus proche,
     * la mission departageant les egalites — plusieurs trains d'une meme minute affichee
     * n'arrivent qu'aux grandes gares, et ils ne partagent pas leur mission.
     */
    fun platformFor(step: Step, departures: List<StopDeparture>): String {
        if (step.trainNumber.isNotBlank()) {
            departures.firstOrNull {
                it.platform.isNotBlank() && it.trainNumber.isNotBlank() &&
                    it.trainNumber.contains(step.trainNumber)
            }?.let { return it.platform }
        }

        val best = departures
            .filter { it.platform.isNotBlank() && it.departure > 0 }
            .filter { abs(it.departure - step.departure) <= TIME_WINDOW_MS }
            .minByOrNull { score(it, step) }
        return best?.platform.orEmpty()
    }

    /**
     * Plus c'est bas, mieux ca correspond. Une mission identique fait gagner une fenetre
     * entiere d'avance : elle prime toujours sur la seule proximite horaire, sans jamais
     * l'ignorer pour departager deux departs de meme mission.
     */
    private fun score(departure: StopDeparture, step: Step): Long {
        val missionMatches = step.headsign.isNotBlank() && departure.mission.equals(step.headsign, ignoreCase = true)
        val missionPenalty = if (missionMatches) 0L else TIME_WINDOW_MS + 1
        return missionPenalty + abs(departure.departure - step.departure)
    }
}
