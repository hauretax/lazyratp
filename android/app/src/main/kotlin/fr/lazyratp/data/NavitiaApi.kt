package fr.lazyratp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NavitiaException(message: String, val code: Int = 0) : Exception(message)

object NavitiaApi {

    private const val BASE = "https://prim.iledefrance-mobilites.fr/marketplace/v2/navitia"

    /** SIRI Lite : les departs temps reel d'un arret, avec la voie que /journeys tait. */
    private const val SIRI = "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring"

    /** Navitia rend les horaires dans le fuseau de la couverture, ici Paris. */
    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    private val NAVITIA_DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    suspend fun searchStations(apiKey: String, query: String): List<Station> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        val body = runCatching { httpGet("$BASE/places?q=$q&type[]=stop_area&count=15", apiKey) }
            .getOrElse { return@withContext emptyList() }

        val places = JSONObject(body).optJSONArray("places") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until places.length()) {
                val p = places.getJSONObject(i)
                if (p.optString("embedded_type") != "stop_area") continue
                val sa = p.optJSONObject("stop_area") ?: continue
                // Navitia rend les coordonnees en chaines : {"coord": {"lat": "48.86", "lon": "2.34"}}.
                val coord = sa.optJSONObject("coord")
                add(
                    Station(
                        id = sa.optString("id"),
                        name = sa.optString("name"),
                        lat = coord?.optString("lat")?.toDoubleOrNull(),
                        lon = coord?.optString("lon")?.toDoubleOrNull(),
                        modes = sa.optJSONArray("commercial_modes")
                            ?.mapObjects { it.optString("name") }
                            ?.joinToString(", ")
                            .orEmpty(),
                        city = sa.optJSONArray("administrative_regions")
                            ?.mapObjects { it }
                            ?.firstOrNull { it.optInt("level") == 8 }
                            ?.optString("label")
                            .orEmpty(),
                    )
                )
            }
        }
    }

    /**
     * Adresses et points d'interet, avec leurs coordonnees. Navitia geocode lui-meme :
     * aucune dependance a un service de cartes.
     */
    suspend fun searchPlaces(apiKey: String, query: String): List<GeoPlace> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query, "UTF-8")
        val body = runCatching { httpGet("$BASE/places?q=$q&type[]=address&type[]=poi&count=10", apiKey) }
            .getOrElse { return@withContext emptyList() }

        val places = JSONObject(body).optJSONArray("places") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until places.length()) {
                val p = places.getJSONObject(i)
                val kind = p.optString("embedded_type")
                val obj = p.optJSONObject(kind) ?: continue
                val coord = obj.optJSONObject("coord") ?: continue
                val lat = coord.optString("lat").toDoubleOrNull() ?: continue
                val lon = coord.optString("lon").toDoubleOrNull() ?: continue
                add(GeoPlace(name = obj.optString("name"), lat = lat, lon = lon, kind = kind))
            }
        }
    }

    /**
     * Eprouve une cle contre l'API reelle. Rend null si elle marche, le motif du refus sinon.
     * On ne remplace jamais une cle valide par une cle non verifiee.
     */
    suspend fun validateKey(apiKey: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "Cle vide"
        try {
            httpGet("$BASE/places?q=chatelet&type[]=stop_area&count=1", apiKey)
            null
        } catch (e: NavitiaException) {
            e.message
        } catch (e: Exception) {
            "Reseau indisponible"
        }
    }

    /** Ce qui tient sur le widget. */
    private const val DISPLAY_COUNT = 6

    /**
     * On demande large : Navitia rend plusieurs variantes du meme depart
     * (best, comfort, rapid...), et la deduplication en supprime la plupart.
     */
    private const val FETCH_COUNT = 12

    /**
     * Temps reel plutot que theorique. Sans ca, Navitia repond en base_schedule et
     * marque NO_SERVICE tout trajet touche par un avis de travaux planifie, meme quand
     * le train circule reellement : pendant les travaux d'ete de la ligne P, chaque
     * depart s'affichait "supprime" alors qu'il etait bien la. En realtime, seuls les
     * trajets reellement annules restent NO_SERVICE, et les horaires refletent les retards.
     */
    private const val REALTIME = "&data_freshness=realtime"

    /** Les prochains trajets au depart de maintenant. */
    suspend fun fetchJourneys(
        apiKey: String,
        from: String,
        to: String,
        forbiddenModes: Set<String> = emptySet(),
    ): List<Journey> = withContext(Dispatchers.IO) {
        val dt = LocalDateTime.now(PARIS).format(NAVITIA_DT)
        val all = journeys(
            "$BASE/journeys?from=$from&to=$to&datetime=$dt" +
                "&count=$FETCH_COUNT&min_nb_journeys=$DISPLAY_COUNT${forbidden(forbiddenModes)}$REALTIME",
            apiKey,
        )
        // Navitia propose une marche a pied quand rien ne circule. Le widget ne sait pas
        // l'afficher, mais mieux vaut la montrer que rien.
        all.filter { it.steps.isNotEmpty() }
            .ifEmpty { all }
            .dedupeByDeparture()
            .take(DISPLAY_COUNT)
    }

    /**
     * La fin du jour de service : les derniers trajets, du plus tardif au plus tot.
     * Voir [LastJourney] : Navitia n'expose pas cette notion, on la reconstruit en
     * montant la borne d'arrivee par paliers.
     */
    suspend fun fetchLastJourneys(
        apiKey: String,
        from: String,
        to: String,
        forbiddenModes: Set<String> = emptySet(),
        nowMillis: Long = System.currentTimeMillis(),
    ): List<Journey> = withContext(Dispatchers.IO) {
        LastJourney.find(
            firstBound = ServiceDay.firstArrivalBound(nowMillis, PARIS),
            lastBound = ServiceDay.endOfService(nowMillis, PARIS),
        ) { bound ->
            val dt = NAVITIA_DT.format(Instant.ofEpochMilli(bound).atZone(PARIS))
            try {
                journeys(
                    "$BASE/journeys?from=$from&to=$to&datetime=$dt&datetime_represents=arrival" +
                        "&count=$FETCH_COUNT${forbidden(forbiddenModes)}$REALTIME",
                    apiKey,
                )
                    // Une marche a pied n'est pas un trajet : elle existe a toute heure et
                    // empecherait la saturation de jamais survenir.
                    .filter { it.steps.isNotEmpty() }
                    .dedupeByDeparture()
            } catch (e: NavitiaException) {
                // 404 no_solution : la borne depasse la fin de service. C'est une reponse, pas une panne.
                if (e.code == 404) emptyList() else throw e
            }
        }
            .take(DISPLAY_COUNT)
    }

    /**
     * Les trajets qui arrivent au plus tard a [targetMillis], du plus tardif au plus tot.
     *
     * Meme requete que le dernier trajet du jour, mais la borne d'arrivee est donnee au
     * lieu d'etre cherchee : c'est le rendez-vous. On les trie a rebours parce que la
     * question posee est "jusqu'a quand puis-je attendre", pas "quand puis-je partir".
     */
    suspend fun fetchArriveBy(
        apiKey: String,
        from: String,
        to: String,
        forbiddenModes: Set<String> = emptySet(),
        targetMillis: Long,
    ): List<Journey> = withContext(Dispatchers.IO) {
        val dt = NAVITIA_DT.format(Instant.ofEpochMilli(targetMillis).atZone(PARIS))
        try {
            journeys(
                "$BASE/journeys?from=$from&to=$to&datetime=$dt&datetime_represents=arrival" +
                    "&count=$FETCH_COUNT${forbidden(forbiddenModes)}$REALTIME",
                apiKey,
            )
                // Une marche a pied n'est pas un trajet : elle "arrive" a n'importe quelle heure.
                .filter { it.steps.isNotEmpty() }
                .dedupeByDeparture()
                .sortedByDescending { it.departure }
                .take(DISPLAY_COUNT)
        } catch (e: NavitiaException) {
            // 404 no_solution : rien ne permet d'arriver a l'heure. C'est une reponse, pas une panne.
            if (e.code == 404) emptyList() else throw e
        }
    }

    /**
     * Les departs temps reel d'une gare, avec leur voie. Sert a completer ce que /journeys
     * ne donne pas en Ile-de-France. Silencieux en cas d'echec : la voie est un plus.
     */
    suspend fun fetchDepartures(apiKey: String, stopAreaId: String): List<StopDeparture> =
        withContext(Dispatchers.IO) {
            val ref = siriMonitoringRef(stopAreaId) ?: return@withContext emptyList()
            val url = "$SIRI?MonitoringRef=${URLEncoder.encode(ref, "UTF-8")}"
            val body = runCatching { httpGet(url, apiKey) }.getOrElse { return@withContext emptyList() }
            SiriParser.parse(body)
        }

    /**
     * "stop_area:IDFM:71517" -> "STIF:StopArea:SP:71517:". SIRI ne connait pas l'identifiant
     * Navitia, seulement la reference STIF de la zone d'arret ; on en extrait le numero.
     */
    internal fun siriMonitoringRef(stopAreaId: String): String? {
        val ref = stopAreaId.substringAfterLast(':')
        if (ref.isBlank() || !ref.all { it.isDigit() }) return null
        return "STIF:StopArea:SP:$ref:"
    }

    private fun forbidden(modes: Set<String>): String =
        modes.joinToString("") { "&forbidden_uris[]=$it" }

    /** Le parsing vit dans [JourneyParser], teste sur la JVM. Ici, seulement le reseau. */
    private fun journeys(url: String, apiKey: String): List<Journey> =
        JourneyParser.parse(httpGet(url, apiKey))

    private fun httpGet(url: String, apiKey: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("apiKey", apiKey)
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            val status = conn.responseCode
            if (status != 200) {
                throw NavitiaException(
                    when (status) {
                        401, 403 -> "Cle API refusee"
                        429 -> "Quota PRIM depasse"
                        else -> "Erreur API ($status)"
                    },
                    status,
                )
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { optJSONObject(it) }.map(transform)
}
