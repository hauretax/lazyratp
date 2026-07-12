package fr.lazyratp.rules

import fr.lazyratp.data.Favorite
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** Le favori retenu, et la regle qui l'a designe. rule = null quand on est tombe sur le repli. */
data class Resolution(val favorite: Favorite, val rule: Rule?)

/**
 * Fonction pure de (regles, favoris, instant, position) vers un favori.
 * Aucune dependance Android : tout ceci se teste sur la JVM.
 */
object RuleEngine {

    fun resolve(
        rules: List<Rule>,
        favorites: List<Favorite>,
        nowMillis: Long,
        zone: ZoneId,
        location: LatLon? = null,
        fallbackIndex: Int = 0,
    ): Resolution? {
        val alive = favorites.filter { it.expiresAt == null || nowMillis < it.expiresAt }
        if (alive.isEmpty()) return null

        val byId = alive.associateBy { it.id }
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)

        for (rule in rules) {
            // Une regle qui pointe un favori supprime ou expire est ignoree, pas fatale.
            val favorite = byId[rule.favoriteId] ?: continue
            if (matches(rule, favorite, now, nowMillis, location)) return Resolution(favorite, rule)
        }

        val fallback = alive.getOrElse(fallbackIndex) { alive.first() }
        return Resolution(fallback, null)
    }

    fun matches(
        rule: Rule,
        favorite: Favorite,
        now: ZonedDateTime,
        nowMillis: Long,
        location: LatLon?,
    ): Boolean {
        if (!rule.enabled) return false
        if (rule.expiresAt != null && nowMillis >= rule.expiresAt) return false
        if (rule.days.isNotEmpty() && now.dayOfWeek.value !in rule.days) return false
        if (!inWindow(rule.fromMinutes, rule.toMinutes, now.hour * 60 + now.minute)) return false

        val place = rule.place ?: return true
        return matchesPlace(place, favorite, location)
    }

    /** Bornes incluses. from > to decrit une fenetre a cheval sur minuit. */
    internal fun inWindow(from: Int?, to: Int?, minuteOfDay: Int): Boolean {
        if (from == null || to == null) return true
        return if (from <= to) minuteOfDay in from..to else minuteOfDay >= from || minuteOfDay <= to
    }

    /**
     * Sans position connue, une regle de lieu ne matche pas, inversee ou non.
     *
     * On echoue de maniere fermee dans les deux sens. Le cas inverse est le piege : il
     * serait tentant de lire "position inconnue" comme "pas pres, donc loin", mais une
     * regle "quand je suis loin de chez moi" se declencherait alors dans son salon des
     * que le GPS tousse. Ne pas savoir ou l'on est n'est pas etre ailleurs.
     */
    private fun matchesPlace(place: PlaceCondition, favorite: Favorite, location: LatLon?): Boolean {
        if (location == null) return false

        val target = when (place) {
            is PlaceCondition.NearDeparture -> {
                val lat = favorite.from.lat ?: return false
                val lon = favorite.from.lon ?: return false
                LatLon(lat, lon)
            }

            is PlaceCondition.NearPoint -> LatLon(place.lat, place.lon)
        }

        val near = haversineMeters(location, target) <= place.radiusMeters
        return if (place.inverted) !near else near
    }

    internal fun haversineMeters(a: LatLon, b: LatLon): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadius * asin(min(1.0, sqrt(h)))
    }
}
