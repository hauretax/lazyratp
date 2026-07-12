package fr.lazyratp.rules

/** Mise en forme et analyse des champs d'une regle. Kotlin pur, testable sur la JVM. */
object RuleFormat {

    private val SHORT = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

    fun days(days: Set<Int>): String = when {
        days.isEmpty() || days.size == 7 -> "Tous les jours"
        days == setOf(1, 2, 3, 4, 5) -> "Lun-Ven"
        days == setOf(6, 7) -> "Week-end"
        else -> days.sorted().joinToString(" ") { SHORT[it - 1] }
    }

    fun dayLabel(day: Int): String = SHORT[day - 1]

    fun minutesToHhMm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** "07:30" -> 450. Rend null sur toute entree invalide, plutot que de deviner. */
    fun parseHhMm(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val hours = parts[0].toIntOrNull() ?: return null
        val minutes = parts[1].toIntOrNull() ?: return null
        if (hours !in 0..23 || minutes !in 0..59) return null
        return hours * 60 + minutes
    }

    fun window(from: Int?, to: Int?): String = when {
        from == null || to == null -> "Toute la journee"
        from > to -> "${minutesToHhMm(from)}-${minutesToHhMm(to)} (nuit)"
        else -> "${minutesToHhMm(from)}-${minutesToHhMm(to)}"
    }

    fun place(place: PlaceCondition?): String {
        if (place == null) return ""

        // "a plus de" / "a moins de" : la distinction doit sauter aux yeux sur la carte,
        // deux regles inverses ne differant que par la.
        val distance = if (place.inverted) "a plus de" else "a moins de"
        val target = when (place) {
            is PlaceCondition.NearDeparture -> "du depart"
            is PlaceCondition.NearPoint -> "de ${place.name}"
        }
        return "$distance ${place.radiusMeters} m $target"
    }

    /** "a moins de 2 h du rendez-vous". Les heures rondes se lisent mieux que 120 min. */
    fun approach(beforeTargetMinutes: Int?): String = when {
        beforeTargetMinutes == null -> ""
        beforeTargetMinutes % 60 == 0 -> "a moins de ${beforeTargetMinutes / 60} h du rendez-vous"
        else -> "a moins de $beforeTargetMinutes min du rendez-vous"
    }

    /** Ligne de resume affichee sous le nom d'une regle. */
    fun summary(rule: Rule): String = listOf(
        days(rule.days),
        window(rule.fromMinutes, rule.toMinutes),
        approach(rule.beforeTargetMinutes),
        place(rule.place),
    ).filter { it.isNotEmpty() }.joinToString(" · ")
}
