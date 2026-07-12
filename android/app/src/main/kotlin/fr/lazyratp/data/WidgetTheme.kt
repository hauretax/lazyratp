package fr.lazyratp.data

import kotlinx.serialization.Serializable

enum class ThemeMode {
    /** Couleurs dynamiques du systeme (Material You). Le defaut, comme avant ce reglage. */
    SYSTEM,
    LIGHT,
    DARK,

    /** Noir et vert phosphore, clin d'oeil au terminal dont LazyRATP est ne. */
    TERMINAL,

    /** Fond, texte et accent choisis a la main. */
    CUSTOM,
}

/**
 * L'apparence du widget. En mode SYSTEM, les trois couleurs sont ignorees : on suit le
 * theme dynamique. En LIGHT/DARK, seul l'accent compte (fond et texte sont imposes). En
 * CUSTOM, les trois sont utilisees. Hex sans '#'.
 */
@Serializable
data class WidgetTheme(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val accent: String = "7C4DFF",
    val background: String = "1C1B1F",
    val text: String = "ECE6F0",
)
