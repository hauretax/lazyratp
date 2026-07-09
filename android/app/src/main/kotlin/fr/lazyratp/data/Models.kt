package fr.lazyratp.data

import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val id: String,
    val name: String,
    val modes: String = "",
    val city: String = "",
    /** Fournies par Navitia (stop_area.coord). Absentes des favoris enregistres avant leur ajout. */
    val lat: Double? = null,
    val lon: Double? = null,
)

/**
 * Meme paire from/to que les favoris du CLI. L'identifiant est derive plutot que stocke,
 * ce qui evite toute migration des donnees deja ecrites en DataStore.
 */
@Serializable
data class Favorite(
    val from: Station,
    val to: Station,
    /** Epoch millis. null = permanent. Un favori temporaire disparait de lui-meme. */
    val expiresAt: Long? = null,
) {
    val id: String get() = "${from.id}>${to.id}"
    val label: String get() = "${from.name} → ${to.name}"
}

@Serializable
data class Step(
    val mode: String,
    val code: String,
    val direction: String,
    val from: String,
    val to: String,
    /** Secondes. */
    val duration: Int,
    /** Secondes de marche precedant cette etape. */
    val walkBefore: Int,
)

@Serializable
data class Journey(
    /** Epoch millis. */
    val departure: Long,
    val arrival: Long,
    /** Secondes. */
    val duration: Int,
    val transfers: Int,
    val steps: List<Step>,
    val walkAfterLast: Int,
    val cancelled: Boolean,
) {
    val code: String get() = steps.firstOrNull()?.code.orEmpty()

    val dest: String
        get() = when {
            steps.isEmpty() -> ""
            steps.size == 1 -> steps.first().direction
            else -> "${steps.size} corresp."
        }
}

/** Ce que le widget rend. */
@Serializable
data class WidgetCache(
    val favoriteLabel: String,
    val journeys: List<Journey>,
    val fetchedAt: Long,
)
