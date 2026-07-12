package fr.lazyratp.data

/** Place-tenant : quand fromHere est vrai, Favorite ignore ce champ. */
val HERE_STATION = Station(Favorite.HERE, "Ma position")

/**
 * Les champs du formulaire de favori, avant validation. Le formulaire n'expose pas tout
 * le modele : c'est [FavoriteDraft.toFavorite] qui recolle les champs absents.
 *
 * La cible d'arrivee est gardee en texte brut, pas en millis : une saisie a moitie tapee
 * ("15/0") n'est pas une date, et la convertir trop tot forcerait a inventer une valeur.
 */
data class FavoriteDraft(
    val from: Station?,
    val to: Station?,
    val fromHere: Boolean = false,
    val lastJourney: Boolean = false,
    val noBus: Boolean = false,
    val arriveBy: Boolean = false,
    val targetDate: String = "",
    val targetTime: String = "",
) {

    /** La cible saisie, ou null si elle est vide ou invalide. */
    val target: Long?
        get() = if (arriveBy) TargetTime.parse(targetDate, targetTime) else null

    /** Rend faux tant que le trajet est incomplet : le bouton d'envoi reste desactive. */
    val isComplete: Boolean
        get() {
            if (to == null) return false
            if (!fromHere && from == null) return false
            // Un ARRIVE_BY sans cible lisible n'a rien a calculer : mieux vaut bloquer
            // l'enregistrement que sauver un favori qui n'affichera jamais rien.
            if (arriveBy && target == null) return false
            return true
        }

    /**
     * [initial] n'est pas qu'un jeu de valeurs par defaut a l'edition : il porte les champs
     * que le formulaire n'affiche pas. Les oublier ici republierait un favori temporaire en
     * favori permanent — une perte silencieuse, et c'est exactement ce qui arrivait.
     */
    fun toFavorite(initial: Favorite?): Favorite? {
        if (!isComplete) return null
        val destination = to ?: return null
        val origin = if (fromHere) HERE_STATION else (from ?: return null)

        return Favorite(
            from = origin,
            to = destination,
            mode = when {
                arriveBy -> TripMode.ARRIVE_BY
                lastJourney -> TripMode.LAST_JOURNEY
                else -> TripMode.NEXT_DEPARTURES
            },
            forbiddenModes = if (noBus) setOf(PhysicalMode.BUS) else emptySet(),
            fromHere = fromHere,
            expiresAt = initial?.expiresAt,
            arriveBy = target,
        )
    }

    companion object {
        /** Ce que le formulaire affiche a l'ouverture : vide a l'ajout, l'existant a l'edition. */
        fun of(initial: Favorite?, nowMillis: Long = System.currentTimeMillis()): FavoriteDraft {
            val target = initial?.arriveBy

            return FavoriteDraft(
                from = if (initial?.fromHere == true) null else initial?.from,
                to = initial?.to,
                fromHere = initial?.fromHere ?: false,
                lastJourney = initial?.mode == TripMode.LAST_JOURNEY,
                noBus = initial != null && PhysicalMode.BUS in initial.forbiddenModes,
                arriveBy = initial?.mode == TripMode.ARRIVE_BY,
                targetDate = target?.let { TargetTime.formatDate(it) } ?: TargetTime.defaultDate(nowMillis),
                targetTime = target?.let { TargetTime.formatTime(it) } ?: "",
            )
        }
    }
}
