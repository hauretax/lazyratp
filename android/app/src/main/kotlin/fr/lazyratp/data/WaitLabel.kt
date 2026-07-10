package fr.lazyratp.data

/**
 * Le temps restant avant un depart.
 *
 * En mode "dernier trajet" l'attente se compte en heures : "720 min" ne se lit pas.
 */
object WaitLabel {

    /** Au-dela, on bascule en heures. */
    private const val MINUTES_THRESHOLD = 90

    fun of(minutes: Int): String = when {
        minutes < 0 -> "parti"
        minutes == 0 -> "a quai"
        minutes < MINUTES_THRESHOLD -> "$minutes min"
        else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')}"
    }
}
