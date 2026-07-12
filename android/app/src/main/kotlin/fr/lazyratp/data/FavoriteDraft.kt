package fr.lazyratp.data

/** Place-tenant : quand fromHere est vrai, Favorite ignore ce champ. */
val HERE_STATION = Station(Favorite.HERE, "Ma position")

/**
 * Les champs du formulaire de favori, avant validation. Le formulaire n'expose pas tout
 * le modele : c'est [FavoriteDraft.toFavorite] qui recolle les champs absents.
 */
data class FavoriteDraft(
    val from: Station?,
    val to: Station?,
    val fromHere: Boolean = false,
    val lastJourney: Boolean = false,
    val noBus: Boolean = false,
) {

    /** Rend null tant que le trajet est incomplet : le bouton d'envoi reste desactive. */
    val isComplete: Boolean
        get() = to != null && (fromHere || from != null)

    /**
     * [initial] n'est pas qu'un jeu de valeurs par defaut a l'edition : il porte les champs
     * que le formulaire n'affiche pas. Les oublier ici republierait un favori temporaire en
     * favori permanent — une perte silencieuse, et c'est exactement ce qui arrivait.
     */
    fun toFavorite(initial: Favorite?): Favorite? {
        val destination = to ?: return null
        val origin = if (fromHere) HERE_STATION else (from ?: return null)

        return Favorite(
            from = origin,
            to = destination,
            mode = if (lastJourney) TripMode.LAST_JOURNEY else TripMode.NEXT_DEPARTURES,
            forbiddenModes = if (noBus) setOf(PhysicalMode.BUS) else emptySet(),
            fromHere = fromHere,
            expiresAt = initial?.expiresAt,
        )
    }

    companion object {
        /** Ce que le formulaire affiche a l'ouverture : vide a l'ajout, l'existant a l'edition. */
        fun of(initial: Favorite?): FavoriteDraft = FavoriteDraft(
            from = if (initial?.fromHere == true) null else initial?.from,
            to = initial?.to,
            fromHere = initial?.fromHere ?: false,
            lastJourney = initial?.mode == TripMode.LAST_JOURNEY,
            noBus = initial != null && PhysicalMode.BUS in initial.forbiddenModes,
        )
    }
}
