package fr.lazyratp.data

import fr.lazyratp.rules.LatLon
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

    /**
     * Arriver avant une heure precise, un jour precis. On ne lit plus les prochains
     * departs mais les derniers qui tiennent encore la cible : la question n'est pas
     * "quand puis-je partir" mais "jusqu'a quand puis-je attendre".
     */
    ARRIVE_BY,
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
    /** Le depart est la position courante. [from] est alors ignore. */
    val fromHere: Boolean = false,
    /** Epoch millis. null = permanent. Un favori temporaire disparait de lui-meme. */
    val expiresAt: Long? = null,

    /**
     * Epoch millis. L'heure a laquelle il faut etre arrive. N'a de sens qu'avec
     * [TripMode.ARRIVE_BY], et lui est indispensable : sans cible, pas de trajet a calculer.
     */
    val arriveBy: Long? = null,
) {
    /**
     * L'identifiant derive de la requete entiere, cible comprise : deux rendez-vous a la
     * meme adresse a deux heures differentes sont deux favoris, pas un seul.
     */
    val id: String
        get() = buildString {
            append(if (fromHere) HERE else from.id).append('>').append(to.id)
            if (mode != TripMode.NEXT_DEPARTURES) append('#').append(mode.name)
            if (forbiddenModes.isNotEmpty()) append('#').append(forbiddenModes.sorted().joinToString(","))
            if (arriveBy != null) append('#').append(arriveBy)
        }

    val label: String
        get() = buildString {
            append(if (fromHere) "Ma position" else from.name).append(" → ").append(to.name)
            val tags = buildList {
                if (mode == TripMode.LAST_JOURNEY) add("dernier")
                if (mode == TripMode.ARRIVE_BY && arriveBy != null) add("pour ${TargetTime.format(arriveBy)}")
                if (PhysicalMode.BUS in forbiddenModes) add("sans bus")
            }
            if (tags.isNotEmpty()) append(tags.joinToString(", ", prefix = " (", postfix = ")"))
        }

    /**
     * Un rendez-vous passe n'a plus rien a afficher : le favori s'eteint de lui-meme a
     * l'heure cible, sans quoi il faudrait le supprimer a la main le lendemain.
     */
    val effectiveExpiry: Long?
        get() = if (mode == TripMode.ARRIVE_BY) arriveBy ?: expiresAt else expiresAt

    /**
     * Ce qu'on envoie a Navitia comme point de depart : un identifiant d'arret,
     * ou des coordonnees "lon;lat" (le point-virgule doit etre encode).
     * Rend null quand la position est exigee mais inconnue.
     */
    fun fromParam(location: LatLon?): String? =
        if (fromHere) location?.let { "${it.lon}%3B${it.lat}" } else from.id

    companion object {
        const val HERE = "here"
    }
}

/** Une adresse ou un point d'interet geocode par Navitia. Jamais persiste tel quel. */
data class GeoPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    /** "address" ou "poi". */
    val kind: String,
)

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
    /** Epoch millis du depart et de l'arrivee de ce troncon. Pour la fiche detaillee. */
    val departure: Long = 0,
    val arrival: Long = 0,
    /** Voie de depart, quand PRIM la fournit — rare en Ile-de-France. Vide sinon. */
    val platform: String = "",
    /** Couleur de la ligne, hex sans '#' (ex. "FFCC30"). Vide si absente. */
    val color: String = "",
    /** Code mission du RER/Transilien (ex. "VACK") : il encode les gares desservies. Vide hors train. */
    val headsign: String = "",
    /** Numero de train (Navitia trip_short_name, ex. "148248"). Vide si absent. */
    val trainNumber: String = "",
)

/**
 * Une perturbation qui touche une ligne du trajet. Navitia les rend une fois a la racine
 * de la reponse ; on les rattache au trajet des le parsing pour que la fiche detaillee
 * n'ait pas a relire l'API.
 */
@Serializable
data class Disruption(
    /** Nom de la severite Navitia, ex. "perturbée". */
    val severity: String,
    val title: String,
    val message: String,
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
    /** Les perturbations touchant les lignes de ce trajet. Vide quand rien n'est signale. */
    val disruptions: List<Disruption> = emptyList(),
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
    /** Recopie du favori : le widget doit pouvoir rendre la cible sans relire les favoris. */
    val arriveBy: Long? = null,
)
