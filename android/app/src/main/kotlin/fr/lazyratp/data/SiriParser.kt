package fr.lazyratp.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * Un depart surveille, tel que SIRI le rend. On garde ce qu'il faut pour le rapprocher d'un
 * trajet Navitia — ligne, mission, numero de train, destination, heure — et ce que Navitia
 * ne donne pas : la voie.
 */
data class StopDeparture(
    val lineRef: String,
    /** Code mission (ex. "VACK") quand SIRI l'expose. Vide sinon. */
    val mission: String,
    /** Numero ou reference de course : sert de cle sure quand il recoupe le trip_short_name. */
    val trainNumber: String,
    val destination: String,
    /** Epoch millis theorique et temps reel. 0 quand absent. */
    val aimedDeparture: Long,
    val expectedDeparture: Long,
    val platform: String,
) {
    /** L'heure a confronter au trajet : le temps reel s'il existe, sinon le theorique. */
    val departure: Long get() = if (expectedDeparture > 0) expectedDeparture else aimedDeparture
}

/**
 * Parse la reponse SIRI Lite StopMonitoring de PRIM. Fonction pure : aucun reseau, testable
 * sur la JVM avec un corps fige.
 *
 * SIRI expose ce que /journeys tait en Ile-de-France : la voie (quai) de depart. La reponse
 * est profondement imbriquee et melange objets uniques ({"value": x}) et tableaux
 * ([{"value": x}]) selon les champs — d'ou [firstValue], qui tolere les deux.
 */
object SiriParser {

    fun parse(body: String): List<StopDeparture> {
        val deliveries = JSONObject(body)
            .optJSONObject("Siri")
            ?.optJSONObject("ServiceDelivery")
            ?.optJSONArray("StopMonitoringDelivery")
            ?: return emptyList()
        return collectVisits(deliveries).mapNotNull { toDeparture(it) }
    }

    private fun collectVisits(deliveries: JSONArray): List<JSONObject> = buildList {
        for (i in 0 until deliveries.length()) {
            val visits = deliveries.optJSONObject(i)?.optJSONArray("MonitoredStopVisit") ?: continue
            for (j in 0 until visits.length()) visits.optJSONObject(j)?.let { add(it) }
        }
    }

    private fun toDeparture(visit: JSONObject): StopDeparture? {
        val mvj = visit.optJSONObject("MonitoredVehicleJourney") ?: return null
        val call = mvj.optJSONObject("MonitoredCall") ?: return null
        val platform = firstValue(call.opt("DeparturePlatformName"))
            .ifBlank { firstValue(call.opt("ArrivalPlatformName")) }
        return StopDeparture(
            lineRef = firstValue(mvj.opt("LineRef")),
            mission = firstValue(mvj.opt("JourneyNote")),
            trainNumber = trainNumberOf(mvj),
            destination = firstValue(mvj.opt("DestinationName"))
                .ifBlank { firstValue(call.opt("DestinationDisplay")) },
            aimedDeparture = parseTime(call.optString("AimedDepartureTime")),
            expectedDeparture = parseTime(call.optString("ExpectedDepartureTime")),
            platform = platform,
        )
    }

    /** Le numero de train, la ou SIRI le range : le nom de course, sinon la reference datee. */
    private fun trainNumberOf(mvj: JSONObject): String {
        val name = firstValue(mvj.opt("VehicleJourneyName"))
        if (name.isNotBlank()) return name
        return firstValue(mvj.optJSONObject("FramedVehicleJourneyRef")?.opt("DatedVehicleJourneyRef"))
    }

    /**
     * SIRI enrobe ses valeurs de facon inegale : {"value": x}, [{"value": x}], ou la chaine
     * nue. On rend la premiere valeur utile quelle que soit la forme.
     */
    private fun firstValue(node: Any?): String = when (node) {
        is JSONArray -> if (node.length() > 0) firstValue(node.opt(0)) else ""
        is JSONObject -> node.optString("value")
        is String -> node
        else -> ""
    }

    /** "2026-07-12T18:18:00.000Z" ou avec decalage "+02:00" -> epoch millis. */
    private fun parseTime(raw: String): Long {
        if (raw.isBlank()) return 0L
        return runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrDefault(0L)
    }
}
