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
    ) : WidgetState

    data class Error(val message: String) : WidgetState
}

object WidgetRepo {

    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    /**
     * Choisit le favori via le moteur de regles, puis rafraichit depuis Navitia.
     * Retombe sur le dernier cache si le reseau echoue : un widget qui affiche des
     * horaires perimes reste plus utile qu'un widget vide.
     */
    suspend fun load(context: Context, location: LatLon? = null): WidgetState {
        val apiKey = Prefs.apiKey(context)
        if (apiKey.isBlank()) return WidgetState.NeedsKey

        val resolution = RuleEngine.resolve(
            rules = Prefs.rules(context),
            favorites = Prefs.favorites(context),
            nowMillis = System.currentTimeMillis(),
            zone = PARIS,
            location = location,
            fallbackIndex = Prefs.selected(context),
        ) ?: return WidgetState.NeedsFavorite

        val favorite = resolution.favorite
        val ruleName = resolution.rule?.name?.takeIf { it.isNotBlank() }

        return try {
            val journeys = when (favorite.mode) {
                TripMode.NEXT_DEPARTURES ->
                    NavitiaApi.fetchJourneys(apiKey, favorite.from.id, favorite.to.id, favorite.forbiddenModes)

                // Deja triee du plus tardif au plus tot : on lit la fin de journee a rebours.
                TripMode.LAST_JOURNEY ->
                    NavitiaApi.fetchLastJourneys(apiKey, favorite.from.id, favorite.to.id, favorite.forbiddenModes)
            }
            val cache = WidgetCache(favorite.label, journeys, System.currentTimeMillis())
            Prefs.setCache(context, cache)
            WidgetState.Ready(cache.favoriteLabel, ruleName, cache.journeys, cache.fetchedAt, stale = false)
        } catch (e: Exception) {
            val cached = Prefs.cache(context)
            when {
                cached != null && cached.favoriteLabel == favorite.label ->
                    WidgetState.Ready(cached.favoriteLabel, ruleName, cached.journeys, cached.fetchedAt, stale = true)

                else -> WidgetState.Error(e.message ?: "Reseau indisponible")
            }
        }
    }
}
