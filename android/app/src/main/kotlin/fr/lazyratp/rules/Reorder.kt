package fr.lazyratp.rules

/**
 * Deplace un element, en rendant une nouvelle liste.
 * Tout index hors bornes rend la liste inchangee : reordonner ne doit jamais faire perdre une regle.
 */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}
