package fr.lazyratp.rules

import fr.lazyratp.data.Favorite
import fr.lazyratp.data.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RuleEngineTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private val maison = Station("sa:maison", "Maison", lat = 48.8620, lon = 2.3465)
    private val bureau = Station("sa:bureau", "Bureau", lat = 48.8738, lon = 2.2950)
    private val chatelet = Station("sa:chatelet", "Chatelet")

    private val maisonBureau = Favorite(maison, bureau)
    private val bureauMaison = Favorite(bureau, maison)
    private val bureauChatelet = Favorite(bureau, chatelet)
    private val favorites = listOf(maisonBureau, bureauMaison, bureauChatelet)

    /** 2026-07-06 doit bien etre un lundi, sinon les tests de jours ne prouvent rien. */
    private val monday: LocalDate = LocalDate.of(2026, 7, 6)
    private val saturday: LocalDate = LocalDate.of(2026, 7, 11)

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(paris)
            .toInstant()
            .toEpochMilli()

    private fun rule(
        favorite: Favorite,
        name: String = "r",
        days: Set<Int> = emptySet(),
        from: Int? = null,
        to: Int? = null,
        place: PlaceCondition? = null,
        expiresAt: Long? = null,
        enabled: Boolean = true,
    ) = Rule(
        id = name,
        favoriteId = favorite.id,
        name = name,
        enabled = enabled,
        days = days,
        fromMinutes = from,
        toMinutes = to,
        place = place,
        expiresAt = expiresAt,
    )

    @Test
    fun `les dates de reference sont bien lundi et samedi`() {
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
    }

    @Test
    fun `sans regle on retombe sur le favori de repli`() {
        val r = RuleEngine.resolve(emptyList(), favorites, at(monday, 8, 0), paris, fallbackIndex = 2)
        assertEquals(bureauChatelet, r!!.favorite)
        assertNull(r.rule)
    }

    @Test
    fun `un index de repli hors bornes retombe sur le premier favori`() {
        val r = RuleEngine.resolve(emptyList(), favorites, at(monday, 8, 0), paris, fallbackIndex = 99)
        assertEquals(maisonBureau, r!!.favorite)
    }

    @Test
    fun `la premiere regle qui matche gagne, meme si une suivante matcherait aussi`() {
        val rules = listOf(
            rule(bureauChatelet, name = "chatelet", from = 8 * 60, to = 9 * 60),
            rule(maisonBureau, name = "matin", from = 8 * 60, to = 9 * 60),
        )
        val r = RuleEngine.resolve(rules, favorites, at(monday, 8, 30), paris)
        assertEquals(bureauChatelet, r!!.favorite)
        assertEquals("chatelet", r.rule!!.name)
    }

    @Test
    fun `hors de la fenetre horaire, on passe a la regle suivante`() {
        val rules = listOf(
            rule(bureauChatelet, name = "soir", from = 19 * 60, to = 23 * 60),
            rule(maisonBureau, name = "matin", from = 6 * 60, to = 10 * 60),
        )
        val r = RuleEngine.resolve(rules, favorites, at(monday, 8, 30), paris)
        assertEquals("matin", r!!.rule!!.name)
    }

    @Test
    fun `les bornes de la fenetre sont incluses`() {
        assertTrue(RuleEngine.inWindow(600, 660, 600))
        assertTrue(RuleEngine.inWindow(600, 660, 660))
        assertTrue(!RuleEngine.inWindow(600, 660, 599))
        assertTrue(!RuleEngine.inWindow(600, 660, 661))
    }

    @Test
    fun `une fenetre a cheval sur minuit matche des deux cotes`() {
        val from = 22 * 60 // 22h00
        val to = 2 * 60 //  02h00
        assertTrue(RuleEngine.inWindow(from, to, 23 * 60))
        assertTrue(RuleEngine.inWindow(from, to, 1 * 60))
        assertTrue(RuleEngine.inWindow(from, to, from))
        assertTrue(RuleEngine.inWindow(from, to, to))
        assertTrue(!RuleEngine.inWindow(from, to, 3 * 60))
        assertTrue(!RuleEngine.inWindow(from, to, 12 * 60))
    }

    @Test
    fun `une fenetre sans bornes matche toujours`() {
        assertTrue(RuleEngine.inWindow(null, null, 0))
        assertTrue(RuleEngine.inWindow(null, 120, 800))
        assertTrue(RuleEngine.inWindow(120, null, 800))
    }

    @Test
    fun `le filtre de jours exclut le week-end`() {
        val semaine = setOf(1, 2, 3, 4, 5)
        val rules = listOf(rule(maisonBureau, name = "semaine", days = semaine, from = 6 * 60, to = 10 * 60))

        assertEquals("semaine", RuleEngine.resolve(rules, favorites, at(monday, 8, 0), paris)!!.rule!!.name)
        assertNull(RuleEngine.resolve(rules, favorites, at(saturday, 8, 0), paris)!!.rule)
    }

    @Test
    fun `une regle expiree est ignoree`() {
        val now = at(monday, 8, 0)
        val rules = listOf(
            rule(bureauChatelet, name = "epingle", expiresAt = now - 1),
            rule(maisonBureau, name = "matin"),
        )
        assertEquals("matin", RuleEngine.resolve(rules, favorites, now, paris)!!.rule!!.name)
    }

    @Test
    fun `une regle epinglee sans condition gagne jusqu'a son expiration`() {
        val now = at(monday, 8, 0)
        val rules = listOf(
            rule(bureauChatelet, name = "epingle24h", expiresAt = now + 3_600_000),
            rule(maisonBureau, name = "matin", from = 6 * 60, to = 10 * 60),
        )
        assertEquals("epingle24h", RuleEngine.resolve(rules, favorites, now, paris)!!.rule!!.name)
    }

    @Test
    fun `une regle desactivee est ignoree`() {
        val rules = listOf(
            rule(bureauChatelet, name = "off", enabled = false),
            rule(maisonBureau, name = "on"),
        )
        assertEquals("on", RuleEngine.resolve(rules, favorites, at(monday, 8, 0), paris)!!.rule!!.name)
    }

    @Test
    fun `un favori expire disparait, et les regles qui le visent avec`() {
        val now = at(monday, 8, 0)
        val temporaire = Favorite(maison, chatelet, expiresAt = now - 1)
        val rules = listOf(rule(temporaire, name = "temporaire"))

        val r = RuleEngine.resolve(rules, listOf(temporaire, maisonBureau), now, paris)
        assertEquals(maisonBureau, r!!.favorite)
        assertNull(r.rule)
    }

    @Test
    fun `si tous les favoris ont expire, il n'y a rien a afficher`() {
        val now = at(monday, 8, 0)
        val expire = Favorite(maison, chatelet, expiresAt = now - 1)
        assertNull(RuleEngine.resolve(emptyList(), listOf(expire), now, paris))
    }

    @Test
    fun `une regle de lieu ne matche pas sans position connue`() {
        val rules = listOf(rule(maisonBureau, name = "pres", place = PlaceCondition.NearDeparture(600)))
        val r = RuleEngine.resolve(rules, favorites, at(monday, 8, 0), paris, location = null)
        assertNull(r!!.rule)
    }

    @Test
    fun `NearDeparture matche quand on est dans le rayon de la station de depart`() {
        val rules = listOf(rule(maisonBureau, name = "pres", place = PlaceCondition.NearDeparture(600)))
        val presDeMaison = LatLon(48.8622, 2.3470) // ~40 m de la station Maison
        val r = RuleEngine.resolve(rules, favorites, at(monday, 8, 0), paris, location = presDeMaison)
        assertEquals("pres", r!!.rule!!.name)
    }

    @Test
    fun `NearDeparture ne matche pas hors du rayon`() {
        val rules = listOf(rule(maisonBureau, name = "pres", place = PlaceCondition.NearDeparture(600)))
        val presDuBureau = LatLon(48.8738, 2.2950)
        val r = RuleEngine.resolve(rules, favorites, at(monday, 8, 0), paris, location = presDuBureau)
        assertNull(r!!.rule)
    }

    @Test
    fun `NearDeparture ne matche pas si la station n'a pas de coordonnees`() {
        val sansCoord = Favorite(chatelet, maison) // chatelet n'a ni lat ni lon
        val rules = listOf(rule(sansCoord, name = "pres", place = PlaceCondition.NearDeparture(600)))
        val r = RuleEngine.resolve(rules, listOf(sansCoord), at(monday, 8, 0), paris, location = LatLon(48.86, 2.34))
        assertNull(r!!.rule)
    }

    @Test
    fun `NearPoint matche autour d'un lieu pose a la main`() {
        val chezMoi = PlaceCondition.NearPoint("Chez moi", 48.8500, 2.3000, radiusMeters = 300)
        val rules = listOf(rule(bureauChatelet, name = "domicile", place = chezMoi))
        val r = RuleEngine.resolve(rules, favorites, at(monday, 8, 0), paris, location = LatLon(48.8501, 2.3001))
        assertEquals("domicile", r!!.rule!!.name)
    }

    @Test
    fun `haversine donne environ 111 km par degre de latitude`() {
        val d = RuleEngine.haversineMeters(LatLon(48.0, 2.0), LatLon(49.0, 2.0))
        assertTrue("distance = $d", d in 111_000.0..111_500.0)
    }

    @Test
    fun `haversine est nulle entre un point et lui-meme`() {
        assertEquals(0.0, RuleEngine.haversineMeters(LatLon(48.86, 2.34), LatLon(48.86, 2.34)), 0.001)
    }
}
