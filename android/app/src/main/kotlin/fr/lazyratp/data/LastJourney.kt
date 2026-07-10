package fr.lazyratp.data

/**
 * Trouve la fin de journee : les derniers trajets praticables du jour de service,
 * du plus tardif au plus tot.
 *
 * Navitia n'a pas de "dernier trajet". Avec datetime_represents=arrival il rend les
 * trajets arrivant *pres de* la borne demandee. Une borne trop basse sous-estime le
 * dernier depart, une borne trop haute rapporte n'importe quoi.
 *
 * On monte donc la borne par paliers jusqu'a saturation. Trois pieges, tous observes
 * sur l'API reelle :
 *
 *  - Quand plus rien ne circule, Navitia propose une **marche a pied** (une section
 *    street_network, zero transport public). Ces trajets-la existent a toute heure,
 *    donc la saturation n'arrive jamais. A l'appelant de les ecarter.
 *  - Passe 4h du matin, on recolte les **premiers trains du lendemain**, qu'on
 *    prendrait pour le dernier de la nuit. D'ou [lastBound].
 *  - Un dernier train **supprime** ne doit pas arreter la montee : sinon on cale sur
 *    un trajet fantome. Il reste affiche, mais ne compte pas.
 *
 * La requete est injectee, donc cette logique se teste sans reseau.
 */
object LastJourney {

    const val STEP_MILLIS: Long = 60 * 60 * 1000
    const val MAX_PROBES: Int = 6

    /** Depart du dernier trajet reellement praticable, ou null s'il n'y en a aucun. */
    private fun latestUsable(journeys: List<Journey>): Long? =
        journeys.filterNot { it.cancelled }.maxOfOrNull { it.departure }

    /**
     * @param query rend les trajets arrivant avant la borne. Liste vide = no_solution.
     *        A charge de l'appelant d'en avoir deja retire les trajets sans transport public.
     * @param lastBound borne d'arrivee a ne pas depasser, typiquement [ServiceDay.endOfService].
     * @return les derniers trajets, du plus tardif au plus tot. Vide si aucun.
     */
    suspend fun find(
        firstBound: Long,
        lastBound: Long = Long.MAX_VALUE,
        stepMillis: Long = STEP_MILLIS,
        maxProbes: Int = MAX_PROBES,
        query: suspend (arrivalBound: Long) -> List<Journey>,
    ): List<Journey> {
        var best: List<Journey> = emptyList()
        var bestDeparture: Long? = null
        var bound = firstBound

        repeat(maxProbes) {
            if (bound > lastBound) return best

            val found = query(bound)

            // Ni solution, ni trajet praticable : on a depasse la fin de service.
            val latest = latestUsable(found) ?: return best

            // Saturation : monter la borne ne fait plus reculer le dernier depart praticable.
            val previous = bestDeparture
            if (previous != null && latest <= previous) return best

            best = found.sortedByDescending { it.departure }
            bestDeparture = latest
            bound += stepMillis
        }

        return best
    }
}
