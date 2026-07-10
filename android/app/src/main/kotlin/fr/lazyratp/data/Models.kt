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

enum class TripMode {
    /** Les prochains departs, comme le CLI. */
    NEXT_DEPARTURES,

    /** Le dernier trajet praticable du jour de service. */
    LAST_JOURNEY,
}

/** Identifiants Navitia des modes physiques qu'on sait exclure. */
object PhysicalMode {
    /** Le Noctilien n'est pas un objet distinct dans Navitia : ses lignes sont des bus RATP. */
    const val BUS = "physical_mode:Bus"
}

/**
 * Un favori est une requete, pas seulement une paire de gares : le mode et les exclusions
 * en font partie. Deux favoris peuvent donc viser les memes gares avec des reponses
 * differentes, d'ou un identifiant derive de la requete entiere.
 */
@Serializable
data class Favorite(
    val from: Station,
    val to: Station,
    val mode: TripMode = TripMode.NEXT_DEPARTURES,
    /** Modes physiques exclus, ex. PhysicalMode.BUS. */
    val forbiddenModes: Set<String> = emptySet(),
    /** Epoch millis. null = permanent. Un favori temporaire disparait de lui-meme. */
    val expiresAt: Long? = null,
) {
    val id: String
        get() = buildString {
            append(from.id).append('>').append(to.id)
            if (mode != TripMode.NEXT_DEPARTURES) append('#').append(mode.name)
            if (forbiddenModes.isNotEmpty()) append('#').append(forbiddenModes.sorted().joinToString(","))
        }

    val label: String
        get() = buildString {
            append(from.name).append(" → ").append(to.name)
            val tags = buildList {
                if (mode == TripMode.LAST_JOURNEY) add("dernier")
                if (PhysicalMode.BUS in forbiddenModes) add("sans bus")
            }
            if (tags.isNotEmpty()) append(tags.joinToString(", ", prefix = " (", postfix = ")"))
        }
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

    /**
     * Trois lignes empruntees font deux correspondances, pas trois.
     * On lit nb_transfers plutot que de compter les troncons : le CLI compte les
     * troncons (format.js) et se trompe donc d'un.
     */
    val dest: String
        get() = when {
            steps.isEmpty() -> ""
            transfers <= 0 -> steps.first().direction
            transfers == 1 -> "1 corresp."
            else -> "$transfers corresp."
        }
}

/** Ce que le widget rend. */
@Serializable
data class WidgetCache(
    val favoriteLabel: String,
    val journeys: List<Journey>,
    val fetchedAt: Long,
)
