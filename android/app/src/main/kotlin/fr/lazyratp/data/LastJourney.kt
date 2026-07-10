package fr.lazyratp.data

/**
 * Trouve le dernier trajet praticable du jour de service.
 *
 * Navitia n'a pas de "dernier trajet" : avec datetime_represents=arrival il rend les
 * trajets arrivant *pres de* la borne demandee. Une borne trop basse sous-estime le
 * dernier depart, une borne trop haute rapporte n'importe quoi.
 *
 * On monte donc la borne par paliers jusqu'a saturation. Deux pieges, tous deux
 * observes sur l'API reelle :
 *
 *  - Quand plus rien ne circule, Navitia propose une **marche a pied** (une section
 *    street_network, zero transport public). Ces trajets-la existent a toute heure,
 *    donc la saturation n'arrive jamais. L'appelant doit les ecarter.
 *  - Passe 4h du matin, on recolte les **premiers trains du lendemain**, qu'on
 *    prendrait pour le dernier de la nuit. D'ou [lastBound].
 *
 * La requete est injectee, donc cette logique se teste sans reseau.
 */
object LastJourney {

    const val STEP_MILLIS: Long = 60 * 60 * 1000
    const val MAX_PROBES: Int = 6

    /**
     * @param query rend les trajets arrivant avant la borne. Liste vide = no_solution.
     *        A charge de l'appelant d'en avoir deja retire les trajets sans transport public.
     * @param lastBound borne d'arrivee a ne pas depasser, typiquement [ServiceDay.endOfService].
     * @return le trajet au depart le plus tardif, ou null si aucun.
     */
    suspend fun find(
        firstBound: Long,
        lastBound: Long = Long.MAX_VALUE,
        stepMillis: Long = STEP_MILLIS,
        maxProbes: Int = MAX_PROBES,
        query: suspend (arrivalBound: Long) -> List<Journey>,
    ): Journey? {
        var best: Journey? = null
        var bound = firstBound

        repeat(maxProbes) {
            if (bound > lastBound) return best

            val found = query(bound)

            // Plus de solution : on a depasse la fin de service.
            val latest = found.maxByOrNull { it.departure } ?: return best

            // Saturation : monter la borne ne fait plus reculer le depart.
            val previous = best
            if (previous != null && latest.departure <= previous.departure) return previous

            best = latest
            bound += stepMillis
        }

        return best
    }
}
