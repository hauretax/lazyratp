package fr.lazyratp.data

/**
 * Le chemin d'un trajet, ligne par ligne.
 *
 * Le metro se lit mieux en chiffres cercles (① a ⑳, U+2460..U+2473) ; le RER et le
 * Transilien gardent leur lettre. C'est la seule facon de dessiner un rond dans un
 * RemoteViews, qui ne connait que du texte.
 */
object LineBadge {

    private const val CIRCLED_ONE = '①'
    private const val MAX_CIRCLED = 20

    fun of(mode: String, code: String): String {
        if (!mode.contains("métro", ignoreCase = true) && !mode.contains("metro", ignoreCase = true)) {
            return code
        }
        // "3bis" et "7bis" n'ont pas de chiffre cercle : on les laisse tels quels.
        val number = code.toIntOrNull() ?: return code
        if (number !in 1..MAX_CIRCLED) return code
        return (CIRCLED_ONE + number - 1).toString()
    }

    fun route(steps: List<Step>): String = steps.joinToString(" › ") { of(it.mode, it.code) }
}
