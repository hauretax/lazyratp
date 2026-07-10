package fr.lazyratp.rules

/**
 * Redirige les regles d'un favori vers un autre identifiant.
 *
 * L'id d'un favori derive de son contenu : le modifier change l'id, et les regles
 * qui le visaient pointeraient dans le vide. On les recable ici.
 *
 * L'epingle est un cas special : son propre id est "pin:<favId>", donc il faut
 * aussi renommer la regle, pas seulement son favoriteId, sinon la bascule ne la
 * retrouverait plus.
 */
fun List<Rule>.repointFavorite(oldId: String, newId: String): List<Rule> {
    if (oldId == newId) return this
    return map { rule ->
        if (rule.favoriteId != oldId) {
            rule
        } else {
            val renamed = if (rule.id == PinRule.id(oldId)) PinRule.id(newId) else rule.id
            rule.copy(id = renamed, favoriteId = newId)
        }
    }
}
