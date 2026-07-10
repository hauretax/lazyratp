package fr.lazyratp.rules

/**
 * L'epingle est une regle sans condition, en tete de liste, qui expire d'elle-meme.
 *
 * Son identifiant est derive du favori vise, ce qui la rend idempotente : epingler
 * deux fois ne cree pas deux epingles, et desepingler la retrouve a coup sur.
 */
object PinRule {

    const val DURATION_MILLIS: Long = 24L * 60 * 60 * 1000

    fun id(favoriteId: String): String = "pin:$favoriteId"

    /** Une epingle expiree ne compte pas : la bascule doit pouvoir la remplacer. */
    fun isActive(rules: List<Rule>, favoriteId: String, nowMillis: Long): Boolean {
        val pin = rules.firstOrNull { it.id == id(favoriteId) } ?: return false
        return pin.expiresAt == null || nowMillis < pin.expiresAt
    }

    fun toggle(
        rules: List<Rule>,
        favoriteId: String,
        nowMillis: Long,
        durationMillis: Long = DURATION_MILLIS,
    ): List<Rule> {
        val without = rules.filterNot { it.id == id(favoriteId) }
        if (isActive(rules, favoriteId, nowMillis)) return without

        val pin = Rule(
            id = id(favoriteId),
            favoriteId = favoriteId,
            name = "Epingle 24 h",
            expiresAt = nowMillis + durationMillis,
        )
        // En tete : sans condition, elle gagne sur toutes les autres jusqu'a expiration.
        return listOf(pin) + without
    }
}
