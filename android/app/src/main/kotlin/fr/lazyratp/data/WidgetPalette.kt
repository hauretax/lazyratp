package fr.lazyratp.data

/**
 * Resout un [WidgetTheme] en couleurs concretes. Kotlin pur, en ARGB entier : ni Compose ni
 * Glance ici, pour que le meme calcul serve le widget et l'apercu, et se teste sur la JVM.
 *
 * Les couleurs sont des Int 0xAARRGGBB. L'appelant les enveloppe en Color/ColorProvider.
 */
object WidgetPalette {

    data class Colors(
        val background: Int,
        val text: Int,
        /** Texte secondaire : le texte fondu vers le fond, pour les infos de second plan. */
        val textVariant: Int,
        val accent: Int,
        /** Toujours un rouge lisible sur le fond : une annulation doit alarmer, quel que soit le theme. */
        val error: Int,
    )

    private const val LIGHT_BG = 0xFFFFFBFE.toInt()
    private const val DARK_BG = 0xFF1C1B1F.toInt()
    private const val LIGHT_TEXT = 0xFF1C1B1F.toInt()
    private const val DARK_TEXT = 0xFFECE6F0.toInt()

    /**
     * Rend null en mode SYSTEM : il n'y a alors rien a calculer, le widget garde les
     * couleurs dynamiques de la plateforme. Sinon, les cinq roles resolus.
     */
    private const val TERMINAL_BG = 0xFF000000.toInt()
    private const val TERMINAL_GREEN = 0xFF33FF33.toInt()

    fun resolve(theme: WidgetTheme): Colors? {
        val userAccent = parseHex(theme.accent, 0xFF7C4DFF.toInt())
        val (bg, text, accent) = when (theme.mode) {
            ThemeMode.SYSTEM -> return null
            ThemeMode.LIGHT -> Triple(LIGHT_BG, LIGHT_TEXT, userAccent)
            ThemeMode.DARK -> Triple(DARK_BG, DARK_TEXT, userAccent)
            // Vert unique : dans un terminal, tout est de la meme encre.
            ThemeMode.TERMINAL -> Triple(TERMINAL_BG, TERMINAL_GREEN, TERMINAL_GREEN)
            ThemeMode.CUSTOM -> Triple(
                parseHex(theme.background, DARK_BG),
                parseHex(theme.text, DARK_TEXT),
                userAccent,
            )
        }

        return Colors(
            background = bg,
            text = text,
            textVariant = blend(text, bg, 0.45),
            accent = accent,
            error = if (isDark(bg)) 0xFFF2B8B5.toInt() else 0xFFB3261E.toInt(),
        )
    }

    /** Vrai quand le fond est sombre : sert a choisir un rouge d'erreur qui s'y detache. */
    fun isDark(argb: Int): Boolean = luminance(argb) < 0.5

    /** Noir ou blanc, selon ce qui se lit sur cette couleur. Pour le texte pose sur l'accent. */
    fun onColor(argb: Int): Int = if (luminance(argb) > 0.6) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    /**
     * "RRGGBB" ou "AARRGGBB" -> Int ARGB. Toute entree invalide rend [fallback], plutot que
     * de laisser une couleur illisible s'installer : l'apercu et le widget ne se retrouvent
     * jamais transparents ou noirs par accident de saisie.
     */
    fun parseHex(hex: String, fallback: Int): Int {
        val clean = hex.trim().removePrefix("#")
        val value = clean.toLongOrNull(16) ?: return fallback
        return when (clean.length) {
            6 -> (0xFF000000 or value).toInt()
            8 -> value.toInt()
            else -> fallback
        }
    }

    private fun blend(a: Int, b: Int, t: Double): Int {
        fun mix(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca * (1 - t) + cb * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    private fun luminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
    }
}
