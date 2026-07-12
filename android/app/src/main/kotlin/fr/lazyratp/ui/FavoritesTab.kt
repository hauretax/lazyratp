package fr.lazyratp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.lazyratp.data.Favorite
import fr.lazyratp.data.FavoriteDraft
import fr.lazyratp.data.NavitiaApi
import fr.lazyratp.data.Prefs
import fr.lazyratp.data.Station
import fr.lazyratp.data.TargetTime
import fr.lazyratp.rules.PinRule
import fr.lazyratp.rules.Rule
import fr.lazyratp.rules.countForFavorite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** L'index qui designe la creation plutot que l'edition d'un favori existant. */
private const val NEW = -1

@Composable
internal fun FavoritesTab(
    apiKey: String,
    favorites: List<Favorite>,
    selected: Int,
    rules: List<Rule>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = la liste. NEW = creation. >= 0 = edition du favori a cet index.
    var form by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }

    val open = form
    if (open != null) {
        // Creation et edition partagent l'ecran, comme l'editeur de regles : meme
        // formulaire, meme place, seuls le titre et le bouton changent.
        val initial = favorites.getOrNull(open)
        FavoriteForm(
            apiKey = apiKey,
            initial = initial,
            title = if (initial == null) "Nouveau favori" else "Modifier le favori",
            submitLabel = if (initial == null) "Ajouter" else "Enregistrer",
            onCancel = { form = null },
            onSubmit = { favorite ->
                scope.launch {
                    if (initial == null) {
                        Prefs.addFavorite(context, favorite)
                    } else {
                        Prefs.replaceFavorite(context, open, favorite)
                    }
                    refreshWidget(context)
                }
                form = null
            },
        )
        return
    }

    confirmDelete?.let { index ->
        favorites.getOrNull(index)?.let { favorite ->
            DeleteFavoriteDialog(
                favorite = favorite,
                ruleCount = rules.countForFavorite(favorite.id),
                onDismiss = { confirmDelete = null },
                onConfirm = {
                    scope.launch {
                        Prefs.removeFavorite(context, index)
                        refreshWidget(context)
                    }
                    confirmDelete = null
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Un favori est un trajet : d'ou, vers ou, et comment on calcule les horaires. " +
                "Ce qui decide de l'afficher, c'est une regle — onglet Regles.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (apiKey.isBlank()) {
            Text(
                "Enregistre d'abord ta cle API dans l'onglet Parametres : sans elle, " +
                    "impossible de chercher une gare.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (favorites.isEmpty()) {
            Text("Aucun favori.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Le bouton radio designe le trajet de repli : celui affiche quand aucune regle ne matche.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        favorites.forEachIndexed { index, favorite ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = index == selected,
                            onClick = {
                                scope.launch {
                                    Prefs.setSelected(context, index)
                                    refreshWidget(context)
                                }
                            },
                        )
                        Text(favorite.label, modifier = Modifier.weight(1f))
                    }

                    // Un rendez-vous passe ne s'affichera plus jamais. Le dire ici, sinon la
                    // carte reste dans la liste sans qu'on comprenne pourquoi le widget l'ignore.
                    val expired = favorite.effectiveExpiry?.let { it <= System.currentTimeMillis() } == true
                    if (expired) {
                        Text(
                            text = "Passe : le widget ne l'affiche plus",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }

                    val ruleCount = rules.countForFavorite(favorite.id)
                    if (ruleCount > 0) {
                        Text(
                            text = if (ruleCount == 1) "1 regle l'affiche" else "$ruleCount regles l'affichent",
                            style = MaterialTheme.typography.bodySmall,
                            // Aligne sous le libelle, pas sous le bouton radio.
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Bascule idempotente : l'identifiant de l'epingle est derive
                        // du favori, donc reappuyer la retire au lieu d'en creer une seconde.
                        val pinned = PinRule.isActive(rules, favorite.id, System.currentTimeMillis())
                        TextButton(onClick = {
                            scope.launch {
                                Prefs.setRules(
                                    context,
                                    PinRule.toggle(rules, favorite.id, System.currentTimeMillis()),
                                )
                                refreshWidget(context)
                            }
                        }) { Text(if (pinned) "Desepingler" else "Epingler 24 h") }

                        Spacer(Modifier.weight(1f))

                        TextButton(onClick = { form = index }) { Text("Modifier") }
                        TextButton(onClick = { confirmDelete = index }) { Text("Supprimer") }
                    }
                }
            }
        }

        Text(
            "Epingler cree une regle sans condition, en tete de liste, qui expire au bout " +
                "de 24 h : le trajet passe devant toutes les autres jusque-la.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Button(
            onClick = { form = NEW },
            enabled = apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Nouveau favori") }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Supprimer un favori emporte ses regles : elles ne designeraient plus rien. On le dit
 * avant, pas apres — c'est la seule suppression de l'app qui en entraine d'autres.
 */
@Composable
private fun DeleteFavoriteDialog(
    favorite: Favorite,
    ruleCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer ce favori ?") },
        text = {
            Text(
                buildString {
                    append(favorite.label)
                    if (ruleCount > 0) {
                        append("\n\n")
                        append(
                            if (ruleCount == 1) {
                                "1 regle le designe : elle sera supprimee aussi, "
                            } else {
                                "$ruleCount regles le designent : elles seront supprimees aussi, "
                            }
                        )
                        append("faute de trajet a afficher.")
                    }
                }
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Formulaire d'un favori, partage entre l'ajout et l'edition. */
@Composable
private fun FavoriteForm(
    apiKey: String,
    initial: Favorite?,
    title: String,
    submitLabel: String,
    onCancel: () -> Unit,
    onSubmit: (Favorite) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(FavoriteDraft.of(initial)) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)

        CheckRow("Partir de ma position", draft.fromHere) { draft = draft.copy(fromHere = it) }
        if (draft.fromHere) {
            Text(
                "Navitia calcule lui-meme la marche jusqu'au premier arret. " +
                    "Autorise la position dans l'onglet Parametres.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            StationField("Depart", apiKey, draft.from) { draft = draft.copy(from = it) }
        }
        StationField("Arrivee", apiKey, draft.to) { draft = draft.copy(to = it) }

        // Les deux modes se contredisent : "le dernier du jour" et "arriver a 19h00" ne
        // designent pas le meme trajet. Cocher l'un decoche l'autre, plutot que de laisser
        // une combinaison qu'il faudrait ensuite arbitrer en silence.
        CheckRow("Dernier trajet du jour", draft.lastJourney) {
            draft = draft.copy(lastJourney = it, arriveBy = if (it) false else draft.arriveBy)
        }
        CheckRow("Arriver a une heure precise", draft.arriveBy) {
            draft = draft.copy(arriveBy = it, lastJourney = if (it) false else draft.lastJourney)
        }

        if (draft.arriveBy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Chaque champ ne signale que sa propre erreur : une date juste ne doit pas
                // rougir parce que l'heure d'a cote est encore vide.
                OutlinedTextField(
                    value = draft.targetDate,
                    onValueChange = { draft = draft.copy(targetDate = it) },
                    singleLine = true,
                    isError = draft.targetDate.isNotBlank() && !TargetTime.isValidDate(draft.targetDate),
                    label = { Text("Date (JJ/MM/AAAA)") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.targetTime,
                    onValueChange = { draft = draft.copy(targetTime = it) },
                    singleLine = true,
                    isError = draft.targetTime.isNotBlank() && !TargetTime.isValidTime(draft.targetTime),
                    label = { Text("Arrivee (HH:MM)") },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Le widget affichera l'heure a laquelle il faut etre au point de depart, " +
                    "et non les prochains departs. Passe l'heure visee, le widget cesse " +
                    "d'afficher ce trajet, mais le favori reste ici : a toi de le supprimer.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        CheckRow("Sans bus (exclut aussi le Noctilien)", draft.noBus) { draft = draft.copy(noBus = it) }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Annuler") }
            Button(
                onClick = { draft.toFavorite(initial)?.let(onSubmit) },
                enabled = draft.isComplete,
            ) { Text(submitLabel) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StationField(
    label: String,
    apiKey: String,
    picked: Station?,
    onPick: (Station) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Station>>(emptyList()) }
    // "Changer" ouvre la recherche mais ne vide pas la gare : sinon, ouvrir la recherche par
    // curiosite desactivait Enregistrer et forceait a tout re-saisir, sans retour possible.
    var searching by remember(picked) { mutableStateOf(false) }

    // Debounce : Navitia est appele 300 ms apres la derniere frappe, pas a chaque caractere.
    LaunchedEffect(query, apiKey) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        results = NavitiaApi.searchStations(apiKey, query)
    }

    if (picked != null && !searching) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$label : ${picked.name}", modifier = Modifier.weight(1f))
            TextButton(onClick = {
                query = ""
                results = emptyList()
                searching = true
            }) { Text("Changer") }
        }
        return
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )

    // La gare actuelle reste sous les yeux pendant la recherche, et on peut y renoncer.
    if (picked != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Actuel : ${picked.name}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                query = ""
                results = emptyList()
                searching = false
            }) { Text("Garder") }
        }
    }

    results.take(5).forEach { station ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    query = ""
                    results = emptyList()
                    searching = false
                    onPick(station)
                },
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(station.name, style = MaterialTheme.typography.bodyMedium)
                val subtitle = listOfNotNull(
                    station.city.takeIf { it.isNotBlank() },
                    station.modes.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
