package fr.lazyratp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import fr.lazyratp.rules.Rule

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lazyratp")

/**
 * Le telephone est la source de verite. Aucune synchronisation avec le config.json du CLI :
 * ce fichier est gitignore et vit sur la machine de dev, hors de portee du widget.
 */
object Prefs {

    private val KEY_API = stringPreferencesKey("api_key")
    private val KEY_FAVORITES = stringPreferencesKey("favorites")
    private val KEY_SELECTED = intPreferencesKey("selected")
    private val KEY_CACHE = stringPreferencesKey("cache")
    private val KEY_RULES = stringPreferencesKey("rules")
    private val KEY_DISPLAY = stringPreferencesKey("display")

    private val json = Json { ignoreUnknownKeys = true }

    fun apiKeyFlow(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_API].orEmpty() }

    fun favoritesFlow(context: Context): Flow<List<Favorite>> =
        context.dataStore.data.map { decodeFavorites(it[KEY_FAVORITES]) }

    fun selectedFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_SELECTED] ?: 0 }

    suspend fun apiKey(context: Context): String = apiKeyFlow(context).first()

    suspend fun favorites(context: Context): List<Favorite> = favoritesFlow(context).first()

    suspend fun selected(context: Context): Int = selectedFlow(context).first()

    suspend fun setApiKey(context: Context, value: String) {
        context.dataStore.edit { it[KEY_API] = value.trim() }
    }

    suspend fun addFavorite(context: Context, favorite: Favorite) {
        context.dataStore.edit { prefs ->
            val current = decodeFavorites(prefs[KEY_FAVORITES])
            // L'identifiant couvre gares, mode et exclusions : deux requetes distinctes
            // sur les memes gares coexistent.
            if (current.none { it.id == favorite.id }) {
                prefs[KEY_FAVORITES] = json.encodeToString(current + favorite)
            }
        }
    }

    suspend fun removeFavorite(context: Context, index: Int) {
        context.dataStore.edit { prefs ->
            val current = decodeFavorites(prefs[KEY_FAVORITES]).toMutableList()
            if (index !in current.indices) return@edit
            current.removeAt(index)
            prefs[KEY_FAVORITES] = json.encodeToString(current.toList())

            val selected = prefs[KEY_SELECTED] ?: 0
            prefs[KEY_SELECTED] = selected.coerceAtMost((current.size - 1).coerceAtLeast(0))
        }
    }

    suspend fun setSelected(context: Context, index: Int) {
        context.dataStore.edit { it[KEY_SELECTED] = index }
    }

    fun displayFlow(context: Context): Flow<Display> =
        context.dataStore.data.map { decodeDisplay(it[KEY_DISPLAY]) }

    suspend fun display(context: Context): Display = displayFlow(context).first()

    suspend fun setDisplay(context: Context, value: Display) {
        context.dataStore.edit { it[KEY_DISPLAY] = json.encodeToString(value) }
    }

    private fun decodeDisplay(raw: String?): Display {
        if (raw.isNullOrBlank()) return Display()
        return runCatching { json.decodeFromString<Display>(raw) }.getOrDefault(Display())
    }

    /** L'ordre de la liste est la priorite : la premiere regle qui matche gagne. */
    fun rulesFlow(context: Context): Flow<List<Rule>> =
        context.dataStore.data.map { decodeRules(it[KEY_RULES]) }

    suspend fun rules(context: Context): List<Rule> = rulesFlow(context).first()

    suspend fun setRules(context: Context, value: List<Rule>) {
        context.dataStore.edit { it[KEY_RULES] = json.encodeToString(value) }
    }

    private fun decodeRules(raw: String?): List<Rule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Rule>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun cache(context: Context): WidgetCache? =
        context.dataStore.data.map { it[KEY_CACHE] }.first()?.let {
            runCatching { json.decodeFromString<WidgetCache>(it) }.getOrNull()
        }

    suspend fun setCache(context: Context, value: WidgetCache) {
        context.dataStore.edit { it[KEY_CACHE] = json.encodeToString(value) }
    }

    private fun decodeFavorites(raw: String?): List<Favorite> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Favorite>>(raw) }.getOrDefault(emptyList())
    }
}
