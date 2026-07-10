package fr.lazyratp.data

object ApiKey {

    /**
     * Une cle enregistree n'est jamais reaffichee en clair : on n'en montre que la fin,
     * assez pour la reconnaitre, pas assez pour la recopier.
     */
    fun mask(key: String): String = when {
        key.isBlank() -> "aucune"
        key.length <= 4 -> "•".repeat(key.length)
        else -> "•".repeat(8) + key.takeLast(4)
    }
}
