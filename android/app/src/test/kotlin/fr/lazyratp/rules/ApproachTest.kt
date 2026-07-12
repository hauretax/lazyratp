package fr.lazyratp.rules

import fr.lazyratp.data.Favorite
import fr.lazyratp.data.Station
import fr.lazyratp.data.TripMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ApproachTest {

    private val paris: ZoneId = ZoneId.of("Europe/Paris")

    private fun clock(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 7, 15, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    private fun at(hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 7, 15, hour, minute, 0, 0, paris)

    /** "Etre a l'aeroport pour 19h00." */
    private val rendezVous = Favorite(
        from = Station("sa:A", "Chez moi"),
        to = Station("sa:B", "Aeroport"),
        mode = TripMode.ARRIVE_BY,
        arriveBy = clock(19, 0),
    )

    /** "Affiche-le des qu'on est a moins de deux heures." */
    private val approche = Rule(
        id = "r1",
        favoriteId = rendezVous.id,
        name = "Rendez-vous",
        beforeTargetMinutes = 120,
    )

    private fun matches(rule: Rule, favorite: Favorite, now: ZonedDateTime): Boolean =
        RuleEngine.matches(rule, favorite, now, now.toInstant().toEpochMilli(), location = null)

    @Test
    fun `a 17h00 pile, la fenetre s'ouvre`() {
        assertTrue(matches(approche, rendezVous, at(17, 0)))
    }

    @Test
    fun `a 16h59, il est encore trop tot`() {
        assertFalse(matches(approche, rendezVous, at(16, 59)))
    }

    @Test
    fun `a 18h30, on est en plein dedans`() {
        assertTrue(matches(approche, rendezVous, at(18, 30)))
    }

    @Test
    fun `passe l'heure du rendez-vous, la regle se tait`() {
        assertFalse(matches(approche, rendezVous, at(19, 1)))
    }

    @Test
    fun `un favori sans cible ne declenche pas une regle d'approche`() {
        // "Deux heures avant" ne designe aucun instant sans rendez-vous : echec ferme,
        // plutot que d'afficher un trajet pour un rendez-vous qui n'existe pas.
        val sansCible = Favorite(Station("sa:A", "Chez moi"), Station("sa:B", "Bureau"))
        val rule = approche.copy(favoriteId = sansCible.id)

        assertFalse(matches(rule, sansCible, at(18, 0)))
    }

    @Test
    fun `l'approche se combine avec les autres conditions`() {
        // Bonne heure relative, mais mauvais jour : le 15 juillet 2026 est un mercredi.
        val leLundi = approche.copy(days = setOf(1))

        assertFalse(matches(leLundi, rendezVous, at(18, 0)))
        assertTrue(matches(approche.copy(days = setOf(3)), rendezVous, at(18, 0)))
    }

    @Test
    fun `le favori s'eteint a l'heure du rendez-vous, sans expiresAt`() {
        // effectiveExpiry, pas expiresAt : sinon un rendez-vous d'hier trainerait.
        assertEquals(clock(19, 0), rendezVous.effectiveExpiry)

        val resolvedAvant = RuleEngine.resolve(
            rules = listOf(approche),
            favorites = listOf(rendezVous),
            nowMillis = clock(18, 0),
            zone = paris,
        )
        assertEquals(rendezVous, resolvedAvant?.favorite)

        val resolvedApres = RuleEngine.resolve(
            rules = listOf(approche),
            favorites = listOf(rendezVous),
            nowMillis = clock(19, 30),
            zone = paris,
        )
        assertEquals(null, resolvedApres)
    }

    @Test
    fun `deux rendez-vous au meme endroit a deux heures sont deux favoris`() {
        val soir = rendezVous.copy(arriveBy = clock(21, 0))

        assertEquals(false, rendezVous.id == soir.id)
    }

    @Test
    fun `le resume dit l'approche en heures rondes`() {
        assertEquals("a moins de 2 h du rendez-vous", RuleFormat.approach(120))
        assertEquals("a moins de 90 min du rendez-vous", RuleFormat.approach(90))
        assertEquals("", RuleFormat.approach(null))
    }

    @Test
    fun `sans condition d'approche, rien ne change`() {
        val sansApproche = approche.copy(beforeTargetMinutes = null)

        assertTrue(matches(sansApproche, rendezVous, at(6, 0)))
        assertTrue(RuleEngine.inApproach(null, arriveBy = null, nowMillis = clock(6, 0)))
    }
}
