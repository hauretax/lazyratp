package fr.lazyratp.data

import android.content.Context

sealed interface WidgetState {
    /** Pas de cle API saisie : le widget invite a ouvrir l'app. */
    data object NeedsKey : WidgetState

    /** Cle presente mais aucun favori enregistre. */
    data object NeedsFavorite : WidgetState

    data class Ready(
        val label: String,
        val journeys: List<Journey>,
        val fetchedAt: Long,
        /** Vrai quand l'appel reseau a echoue et qu'on affiche le dernier cache connu. */
        val stale: Boolean,
    ) : WidgetState

    data class Error(val message: String) : WidgetState
}

object WidgetRepo {

    /**
     * Rafraichit depuis Navitia, et retombe sur le dernier cache si le reseau echoue.
     * Un widget qui affiche des horaires perimes reste plus utile qu'un widget vide.
     */
    suspend fun load(context: Context): WidgetState {
        val apiKey = Prefs.apiKey(context)
        if (apiKey.isBlank()) return WidgetState.NeedsKey

        val favorites = Prefs.favorites(context)
        if (favorites.isEmpty()) return WidgetState.NeedsFavorite

        val favorite = favorites.getOrElse(Prefs.selected(context)) { favorites.first() }

        return try {
            val journeys = NavitiaApi.fetchJourneys(apiKey, favorite.from.id, favorite.to.id)
            val cache = WidgetCache(favorite.label, journeys, System.currentTimeMillis())
            Prefs.setCache(context, cache)
            WidgetState.Ready(cache.favoriteLabel, cache.journeys, cache.fetchedAt, stale = false)
        } catch (e: Exception) {
            val cached = Prefs.cache(context)
            when {
                cached != null && cached.favoriteLabel == favorite.label ->
                    WidgetState.Ready(cached.favoriteLabel, cached.journeys, cached.fetchedAt, stale = true)

                else -> WidgetState.Error(e.message ?: "Reseau indisponible")
            }
        }
    }
}
