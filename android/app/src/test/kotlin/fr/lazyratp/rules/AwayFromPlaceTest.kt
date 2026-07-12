package fr.lazyratp.rules

import fr.lazyratp.data.Favorite
import fr.lazyratp.data.Station
import fr.lazyratp.data.TripMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AwayFromPlaceTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    /** Le domicile, et un point a ~1,6 km de la (un centieme de degre de latitude ~ 1,1 km). */
    private val home = LatLon(48.8600, 2.3400)
    private val nearHome = LatLon(48.8605, 2.3405)
    private val farFromHome = LatLon(48.8750, 2.3600)

    private val station = Station("sa:A", "Gare", lat = 48.88, lon = 2.36)
    private val lastTrainHome = Favorite(
        from = station,
        to = Station("sa:B", "Maison"),
        mode = TripMode.LAST_JOURNEY,
    )

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 7, 15, hour, minute, 0, 0, paris)

    /** "Quand je suis a plus de 1 km de chez moi, apres 22 h : le dernier train pour rentrer." */
    private val rentrer = Rule(
        id = "r1",
        favoriteId = lastTrainHome.id,
        name = "Rentrer",
        fromMinutes = 22 * 60,
        toMinutes = 3 * 60, // a cheval sur minuit
        place = PlaceCondition.NearPoint("Chez moi", home.lat, home.lon, radiusMeters = 1000, inverted = true),
    )

    private fun matches(rule: Rule, now: ZonedDateTime, location: LatLon?): Boolean =
        RuleEngine.matches(rule, lastTrainHome, now, now.toInstant().toEpochMilli(), location)

    @Test
    fun `il est 22h30 et je suis loin de chez moi, le dernier train s'affiche`() {
        assertTrue(matches(rentrer, at(22, 30), farFromHome))
    }

    @Test
    fun `il est 22h30 mais je suis deja chez moi, rien a afficher`() {
        assertFalse(matches(rentrer, at(22, 30), nearHome))
    }

    @Test
    fun `je suis loin de chez moi mais il est 15h, trop tot`() {
        assertFalse(matches(rentrer, at(15, 0), farFromHome))
    }

    @Test
    fun `position inconnue, la regle ne matche pas, meme inversee`() {
        // Le piege du cas inverse : "je ne sais pas ou je suis" ne doit surtout pas se lire
        // "je ne suis pas pres, donc je suis loin", sinon la regle se declenche dans le salon.
        assertFalse(matches(rentrer, at(22, 30), null))
    }

    @Test
    fun `la condition non inversee garde son sens`() {
        val chezMoi = rentrer.copy(
            place = PlaceCondition.NearPoint("Chez moi", home.lat, home.lon, radiusMeters = 1000),
        )
        assertTrue(matches(chezMoi, at(22, 30), nearHome))
        assertFalse(matches(chezMoi, at(22, 30), farFromHome))
    }

    @Test
    fun `pile sur le rayon, on est pres, donc pas loin`() {
        // Bornes : la distance est comparee avec <=, donc le point sur le cercle est "pres".
        // Les deux conditions doivent rester exactement complementaires.
        val radius = 1000
        val near = PlaceCondition.NearPoint("Chez moi", home.lat, home.lon, radius)
        val far = near.copy(inverted = true)

        listOf(nearHome, farFromHome).forEach { where ->
            val isNear = matches(rentrer.copy(place = near, fromMinutes = null, toMinutes = null), at(12, 0), where)
            val isFar = matches(rentrer.copy(place = far, fromMinutes = null, toMinutes = null), at(12, 0), where)
            assertEquals("les deux conditions doivent etre complementaires", isNear, !isFar)
        }
    }

    @Test
    fun `sans coordonnees sur la gare de depart, une regle NearDeparture inversee ne matche pas`() {
        // Meme echec ferme : une gare sans coordonnees n'est pas une gare lointaine.
        val sansCoord = Favorite(from = Station("sa:X", "Inconnue"), to = Station("sa:B", "Maison"))
        val rule = Rule(
            id = "r2",
            favoriteId = sansCoord.id,
            place = PlaceCondition.NearDeparture(radiusMeters = 600, inverted = true),
        )
        val now = at(12, 0)

        assertFalse(RuleEngine.matches(rule, sansCoord, now, now.toInstant().toEpochMilli(), farFromHome))
    }

    @Test
    fun `le resume distingue pres et loin`() {
        assertEquals(
            "a plus de 1000 m de Chez moi",
            RuleFormat.place(PlaceCondition.NearPoint("Chez moi", home.lat, home.lon, 1000, inverted = true)),
        )
        assertEquals(
            "a moins de 1000 m de Chez moi",
            RuleFormat.place(PlaceCondition.NearPoint("Chez moi", home.lat, home.lon, 1000)),
        )
        assertEquals(
            "a plus de 600 m du depart",
            RuleFormat.place(PlaceCondition.NearDeparture(600, inverted = true)),
        )
    }

    @Test
    fun `une regle enregistree avant l'inversion se relit comme une regle de proximite`() {
        // Compatibilite : inverted est absent du JSON des regles deja stockees.
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val stored = """{"type":"near_point","name":"Chez moi","lat":48.86,"lon":2.34,"radiusMeters":600}"""

        val place = json.decodeFromString<PlaceCondition>(stored)

        assertFalse(place.inverted)
    }

    @Test
    fun `Instant sert de repere, le fuseau de Paris est bien celui du test`() {
        assertEquals(at(22, 30).toInstant(), Instant.parse("2026-07-15T20:30:00Z"))
    }
}
