package fr.lazyratp.data

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * L'heure d'arrivee visee par un favori ARRIVE_BY : sa saisie et son affichage.
 * Kotlin pur, testable sur la JVM.
 */
object TargetTime {

    val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    private val LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    fun format(millis: Long, zone: ZoneId = PARIS): String =
        LABEL.format(Instant.ofEpochMilli(millis).atZone(zone))

    /**
     * "15/07/2026" + "19:00" -> epoch millis. Rend null sur toute entree invalide plutot
     * que de deviner : une cible mal lue ferait rater un rendez-vous, silencieusement.
     *
     * Le 31/02 est refuse par java.time, pas arrondi au 28. Idem pour une heure qui
     * n'existe pas le jour du passage a l'heure d'ete : on refuse au lieu de decaler.
     */
    fun parse(date: String, time: String, zone: ZoneId = PARIS): Long? {
        val day = parseDate(date) ?: return null
        val clock = parseTime(time) ?: return null

        return try {
            LocalDateTime.of(day, clock).atZone(zone).toInstant().toEpochMilli()
        } catch (e: DateTimeException) {
            null
        }
    }

    /**
     * Validite de chaque champ pris isolement. Le formulaire en a besoin : signaler la date
     * en rouge parce que l'heure est vide accuserait un champ correct, et on chercherait
     * l'erreur la ou elle n'est pas.
     */
    fun isValidDate(text: String): Boolean = parseDate(text) != null

    fun isValidTime(text: String): Boolean = parseTime(text) != null

    private fun parseDate(text: String): LocalDate? {
        val parts = text.trim().split("/")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null

        return try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun parseTime(text: String): LocalTime? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null
        return LocalTime.of(hours, minutes)
    }

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Les deux champs du formulaire, relus depuis une cible existante. */
    fun formatDate(millis: Long, zone: ZoneId = PARIS): String =
        DATE.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun formatTime(millis: Long, zone: ZoneId = PARIS): String =
        TIME.format(Instant.ofEpochMilli(millis).atZone(zone))

    /** Ce qu'on pre-remplit a l'ajout : aujourd'hui. L'heure, elle, n'a pas de defaut sense. */
    fun defaultDate(nowMillis: Long, zone: ZoneId = PARIS): String =
        DATE.format(Instant.ofEpochMilli(nowMillis).atZone(zone))
}
