package fr.lazyratp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NavitiaException(message: String, val code: Int = 0) : Exception(message)

object NavitiaApi {

    private const val BASE = "https://prim.iledefrance-mobilites.fr/marketplace/v2/navitia"

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
                add(
                    Station(
                        id = sa.optString("id"),
                        name = sa.optString("name"),
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

    suspend fun fetchJourneys(apiKey: String, from: String, to: String): List<Journey> = withContext(Dispatchers.IO) {
        val dt = LocalDateTime.now(PARIS).format(NAVITIA_DT)
        val body = httpGet("$BASE/journeys?from=$from&to=$to&datetime=$dt&count=8&min_nb_journeys=5", apiKey)
        val journeys = JSONObject(body).optJSONArray("journeys") ?: return@withContext emptyList()

        buildList {
            for (i in 0 until journeys.length()) {
                val j = journeys.getJSONObject(i)
                val sections = j.optJSONArray("sections") ?: JSONArray()

                val steps = mutableListOf<Step>()
                var pendingWalk = 0

                for (k in 0 until sections.length()) {
                    val s = sections.getJSONObject(k)
                    if (s.optString("type") == "public_transport") {
                        val info = s.optJSONObject("display_informations") ?: JSONObject()
                        steps += Step(
                            mode = info.optString("commercial_mode", "?"),
                            code = info.optString("code", ""),
                            direction = info.optString("direction", ""),
                            from = s.optJSONObject("from")?.optJSONObject("stop_point")?.optString("name") ?: "?",
                            to = s.optJSONObject("to")?.optJSONObject("stop_point")?.optString("name") ?: "?",
                            duration = s.optInt("duration"),
                            walkBefore = pendingWalk,
                        )
                        pendingWalk = 0
                    } else {
                        pendingWalk += s.optInt("duration")
                    }
                }

                add(
                    Journey(
                        departure = parseNavitiaTime(j.optString("departure_date_time")),
                        arrival = parseNavitiaTime(j.optString("arrival_date_time")),
                        duration = j.optInt("duration"),
                        transfers = j.optInt("nb_transfers"),
                        steps = steps,
                        walkAfterLast = pendingWalk,
                        cancelled = j.optString("status") == "NO_SERVICE",
                    )
                )
            }
        }
    }

    /** "20260709T101800" -> epoch millis. */
    private fun parseNavitiaTime(raw: String): Long {
        if (raw.length < 15) return 0L
        return LocalDateTime.parse(raw, NAVITIA_DT).atZone(PARIS).toInstant().toEpochMilli()
    }

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
