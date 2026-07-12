package fr.lazyratp.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Transforme la reponse /journeys de Navitia en modeles. Fonction pure : aucun reseau,
 * elle se teste sur la JVM avec un corps JSON fige.
 *
 * Les perturbations vivent dans un tableau `disruptions` a la racine ; chaque troncon les
 * reference par identifiant dans display_informations.links. On resout ce lien ici, une
 * bonne fois, pour que la fiche detaillee n'ait pas a relire la reponse entiere.
 */
object JourneyParser {

    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
    private val NAVITIA_DT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(body: String): List<Journey> {
        val root = JSONObject(body)
        val disruptionsById = indexDisruptions(root.optJSONArray("disruptions"))
        val journeys = root.optJSONArray("journeys") ?: return emptyList()

        return (0 until journeys.length())
            .mapNotNull { journeys.optJSONObject(it) }
            .map { parseJourney(it, disruptionsById) }
    }

    private fun parseJourney(j: JSONObject, disruptionsById: Map<String, ActiveDisruption>): Journey {
        val sections = j.optJSONArray("sections") ?: JSONArray()
        val steps = mutableListOf<Step>()
        val disruptionIds = linkedSetOf<String>()
        var pendingWalk = 0

        for (k in 0 until sections.length()) {
            val s = sections.optJSONObject(k) ?: continue
            if (s.optString("type") == "public_transport") {
                val info = s.optJSONObject("display_informations") ?: JSONObject()
                steps += Step(
                    mode = info.optString("commercial_mode", "?"),
                    code = info.optString("code", ""),
                    direction = info.optString("direction", ""),
                    from = stopName(s.optJSONObject("from")),
                    to = stopName(s.optJSONObject("to")),
                    duration = s.optInt("duration"),
                    walkBefore = pendingWalk,
                    departure = parseTime(s.optString("departure_date_time")),
                    arrival = parseTime(s.optString("arrival_date_time")),
                    platform = platformOf(s),
                    color = info.optString("color"),
                    headsign = info.optString("headsign"),
                    trainNumber = info.optString("trip_short_name"),
                )
                collectDisruptionIds(info, disruptionIds)
                pendingWalk = 0
            } else {
                pendingWalk += s.optInt("duration")
            }
        }

        // Actives seulement (on ecarte le passe et le futur), et sans doublon de message :
        // une meme panne d'ascenseur revient une fois par quai touche.
        val disruptions = disruptionIds
            .mapNotNull { disruptionsById[it] }
            .filter { it.active }
            .map { it.disruption }
            // On ecarte les perturbations sans contenu humain : celles dont le titre n'est
            // que le nom de severite recopie et qui n'ont aucun message. Navitia en rend
            // ("trip modified", nom anglais non traduit) qui n'apprennent rien a l'usager.
            .filter { it.message.isNotBlank() || !it.title.equals(it.severity, ignoreCase = true) }
            .distinctBy { it.message.ifBlank { it.title } }

        return Journey(
            departure = parseTime(j.optString("departure_date_time")),
            arrival = parseTime(j.optString("arrival_date_time")),
            duration = j.optInt("duration"),
            transfers = j.optInt("nb_transfers"),
            steps = steps,
            walkAfterLast = pendingWalk,
            cancelled = j.optString("status") == "NO_SERVICE",
            disruptions = disruptions,
        )
    }

    private fun stopName(obj: JSONObject?): String =
        obj?.optJSONObject("stop_point")?.optString("name")?.takeIf { it.isNotBlank() } ?: "?"

    /**
     * La voie de depart, quand PRIM l'expose. Elle est presque toujours absente pour
     * l'Ile-de-France : on la lit la ou Navitia la met parfois, et on rend une chaine vide
     * quand elle manque, plutot que d'inventer un quai.
     */
    private fun platformOf(section: JSONObject): String {
        val firstStop = section.optJSONArray("stop_date_times")?.optJSONObject(0)
        val sp = firstStop?.optJSONObject("stop_point")
            ?: section.optJSONObject("from")?.optJSONObject("stop_point")
        return sp?.optString("platform_code").orEmpty()
    }

    private fun collectDisruptionIds(displayInfo: JSONObject, into: MutableSet<String>) {
        val links = displayInfo.optJSONArray("links") ?: return
        for (i in 0 until links.length()) {
            val link = links.optJSONObject(i) ?: continue
            if (link.optString("type") == "disruption") {
                link.optString("id").takeIf { it.isNotBlank() }?.let { into += it }
            }
        }
    }

    private fun indexDisruptions(arr: JSONArray?): Map<String, ActiveDisruption> {
        if (arr == null) return emptyMap()
        val map = HashMap<String, ActiveDisruption>()
        for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            val id = d.optString("id")
            if (id.isBlank()) continue
            map[id] = toDisruption(d)
        }
        return map
    }

    /** L'etat "active" ne sert qu'au filtrage, il n'a pas a survivre dans le modele affiche. */
    private class ActiveDisruption(val disruption: Disruption, val active: Boolean)

    private fun toDisruption(d: JSONObject): ActiveDisruption {
        val severity = d.optJSONObject("severity")
        val messages = d.optJSONArray("messages") ?: JSONArray()

        var title = ""
        var body = ""
        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val text = m.optString("text")
            val types = m.optJSONObject("channel")?.optJSONArray("types")
            val typeSet = (0 until (types?.length() ?: 0)).map { types!!.optString(it) }
            when {
                "title" in typeSet && title.isEmpty() -> title = text
                "web" in typeSet && body.isEmpty() -> body = stripHtml(text)
            }
        }
        if (body.isEmpty()) body = stripHtml(messages.optJSONObject(0)?.optString("text").orEmpty())
        if (title.isEmpty()) title = severity?.optString("name").orEmpty().ifBlank { d.optString("cause") }

        return ActiveDisruption(
            disruption = Disruption(
                severity = severity?.optString("name").orEmpty(),
                title = title,
                message = body,
            ),
            active = d.optString("status") == "active",
        )
    }

    private val HTML_TAG = Regex("<[^>]+>")
    private val HEX_ENTITY = Regex("&#[xX]([0-9a-fA-F]+);")
    private val DEC_ENTITY = Regex("&#(\\d+);")

    /** Retire les balises d'abord, decode les entites ensuite : l'inverse fabriquerait de fausses balises. */
    private fun stripHtml(s: String): String =
        s.replace(HTML_TAG, "")
            .replace(HEX_ENTITY) { it.groupValues[1].toInt(16).toChar().toString() }
            // Les perturbations PRIM arrivent en entites numeriques ("P&#233;riode", "ao&#251;t").
            .replace(DEC_ENTITY) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .trim()

    /** "20260709T101800" -> epoch millis. */
    private fun parseTime(raw: String): Long {
        if (raw.length < 15) return 0L
        return LocalDateTime.parse(raw, NAVITIA_DT).atZone(PARIS).toInstant().toEpochMilli()
    }
}
