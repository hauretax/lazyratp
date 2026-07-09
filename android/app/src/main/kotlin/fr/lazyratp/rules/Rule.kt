package fr.lazyratp.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PlaceCondition {

    val radiusMeters: Int

    /**
     * "Je suis pres du point de depart de ce trajet."
     * Ne demande aucune configuration : les coordonnees viennent de Navitia.
     */
    @Serializable
    @SerialName("near_departure")
    data class NearDeparture(override val radiusMeters: Int = 600) : PlaceCondition

    /** Un lieu pose a la main, quand le domicile est loin de sa gare. */
    @Serializable
    @SerialName("near_point")
    data class NearPoint(
        val name: String,
        val lat: Double,
        val lon: Double,
        override val radiusMeters: Int = 600,
    ) : PlaceCondition
}

/**
 * Une regle designe le favori a afficher quand ses conditions sont reunies.
 *
 * Les regles sont evaluees dans l'ordre de la liste : la premiere qui matche gagne.
 * L'ordre est donc la priorite, et il appartient a l'utilisateur, pas a un calcul
 * de specificite implicite.
 *
 * Toutes les conditions sont facultatives. Une regle sans aucune condition matche
 * toujours : c'est ainsi qu'on epingle un trajet ("pendant 24 h", via expiresAt).
 */
@Serializable
data class Rule(
    val id: String,
    val favoriteId: String,
    /** Affiche dans le widget pour expliquer pourquoi ce trajet est la. */
    val name: String = "",
    val enabled: Boolean = true,

    /**
     * Vide = tous les jours. 1 = lundi ... 7 = dimanche, comme java.time.DayOfWeek.value.
     *
     * Attention : le jour est celui de l'instant d'evaluation. Une regle
     * "vendredi 23h00-02h00" ne matchera pas a 01h00, puisqu'on est samedi.
     */
    val days: Set<Int> = emptySet(),

    /**
     * Minutes depuis minuit, bornes incluses. null = toute la journee.
     * fromMinutes > toMinutes decrit une fenetre a cheval sur minuit (22h00 -> 02h00).
     */
    val fromMinutes: Int? = null,
    val toMinutes: Int? = null,

    /** null = n'importe ou. Sinon exige une position connue, sans quoi la regle ne matche pas. */
    val place: PlaceCondition? = null,

    /** Epoch millis. null = permanente. */
    val expiresAt: Long? = null,
)
