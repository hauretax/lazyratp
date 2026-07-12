package fr.lazyratp.data

import android.content.Context
import fr.lazyratp.rules.LatLon
import fr.lazyratp.rules.RuleEngine
import java.time.ZoneId

sealed interface WidgetState {
    /** Pas de cle API saisie : le widget invite a ouvrir l'app. */
    data object NeedsKey : WidgetState

    /** Cle presente mais aucun favori actif (jamais cree, ou tous expires). */
    data object NeedsFavorite : WidgetState

    data class Ready(
        val label: String,
        /** Nom de la regle qui a designe ce trajet. null quand on est sur le repli. */
        val ruleName: String?,
        val journeys: List<Journey>,
        val fetchedAt: Long,
        /** Vrai quand l'appel reseau a echoue et qu'on affiche le dernier cache connu. */
        val stale: Boolean,
        val display: Display,
        /** L'heure d'arrivee visee, quand le favori en porte une. Change ce qu'on rend. */
        val arriveBy: Long? = null,
    ) : WidgetState

    data class Error(val message: String) : WidgetState
}

object WidgetRepo {

    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    /**
     * Le dernier contenu connu, sans reseau. Sert a amorcer la composition : sans lui, le
     * widget se viderait le temps du refetch, alors qu'afficher des horaires d'il y a deux
     * minutes reste plus utile qu'un ecran de chargement.
     *
     * Pas marque stale : le "!" signale un echec reseau, or ici on n'a encore rien tente.
     * L'horodatage du header suffit a dire que le contenu date.
     */
    suspend fun seed(context: Context): WidgetState.Ready? {
        val cache = Prefs.cache(context) ?: return null
        return WidgetState.Ready(
            label = cache.favoriteLabel,
            ruleName = null,
            journeys = cache.journeys,
            fetchedAt = cache.fetchedAt,
            stale = false,
            display = Prefs.display(context),
            arriveBy = cache.arriveBy,
        )
    }

    /**
     * Choisit le favori via le moteur de regles, puis rafraichit depuis Navitia.
     * Retombe sur le dernier cache si le reseau echoue : un widget qui affiche des
     * horaires perimes reste plus utile qu'un widget vide.
     */
    suspend fun load(context: Context, forcedLocation: LatLon? = null): WidgetState {
        val apiKey = Prefs.apiKey(context)
        if (apiKey.isBlank()) return WidgetState.NeedsKey

        val rules = Prefs.rules(context)
        val favorites = Prefs.favorites(context)

        // On n'allume la localisation que si quelqu'un la reclame.
        val needsLocation = favorites.any { it.fromHere } || rules.any { it.place != null }
        val location = forcedLocation ?: if (needsLocation) LocationProvider.current(context) else null

        val resolution = RuleEngine.resolve(
            rules = rules,
            favorites = favorites,
            nowMillis = System.currentTimeMillis(),
            zone = PARIS,
            location = location,
            fallbackIndex = Prefs.selected(context),
        ) ?: return WidgetState.NeedsFavorite

        val favorite = resolution.favorite
        val ruleName = resolution.rule?.name?.takeIf { it.isNotBlank() }
        val display = Prefs.display(context)

        // Echec ferme : sans position, un trajet "depuis ma position" n'a pas de sens.
        val fromParam = favorite.fromParam(location)
            ?: return WidgetState.Error(locationRefusal(context))

        return try {
            val journeys = when (favorite.mode) {
                TripMode.NEXT_DEPARTURES ->
                    NavitiaApi.fetchJourneys(apiKey, fromParam, favorite.to.id, favorite.forbiddenModes)

                // Deja triee du plus tardif au plus tot : on lit la fin de journee a rebours.
                TripMode.LAST_JOURNEY ->
                    NavitiaApi.fetchLastJourneys(apiKey, fromParam, favorite.to.id, favorite.forbiddenModes)

                // Idem : le dernier depart qui tient la cible se lit en tete.
                TripMode.ARRIVE_BY -> favorite.arriveBy?.let { target ->
                    NavitiaApi.fetchArriveBy(
                        apiKey, fromParam, favorite.to.id, favorite.forbiddenModes, target,
                    )
                } ?: emptyList()
            }
            val cache = WidgetCache(favorite.label, journeys, System.currentTimeMillis(), favorite.arriveBy)
            Prefs.setCache(context, cache)
            WidgetState.Ready(
                cache.favoriteLabel, ruleName, cache.journeys, cache.fetchedAt, false, display, cache.arriveBy,
            )
        } catch (e: Exception) {
            val cached = Prefs.cache(context)
            when {
                cached != null && cached.favoriteLabel == favorite.label -> WidgetState.Ready(
                    cached.favoriteLabel, ruleName, cached.journeys, cached.fetchedAt, true, display, cached.arriveBy,
                )

                else -> WidgetState.Error(e.message ?: "Reseau indisponible")
            }
        }
    }

    /** Dire *pourquoi* la position manque : la permission, ou le releve lui-meme. */
    private fun locationRefusal(context: Context): String = when {
        !LocationProvider.hasForegroundPermission(context) -> "Position non autorisee"
        !LocationProvider.hasBackgroundPermission(context) -> "Autorise la position en arriere-plan"
        else -> "Position introuvable"
    }
}
