package fr.lazyratp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiriMonitoringRefTest {

    @Test
    fun `un identifiant Navitia devient une reference STIF de zone d'arret`() {
        assertEquals("STIF:StopArea:SP:71517:", NavitiaApi.siriMonitoringRef("stop_area:IDFM:71517"))
    }

    @Test
    fun `un identifiant sans numero exploitable ne donne aucune reference`() {
        // Coordonnees "lon;lat" d'un depart depuis la position : pas de gare a interroger.
        assertNull(NavitiaApi.siriMonitoringRef("2.34%3B48.86"))
        assertNull(NavitiaApi.siriMonitoringRef("stop_area:IDFM:monomodalStopPlace"))
        assertNull(NavitiaApi.siriMonitoringRef(""))
    }
}
