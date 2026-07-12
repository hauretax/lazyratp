package fr.lazyratp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.lazyratp.data.Favorite
import fr.lazyratp.data.FavoriteDraft
import fr.lazyratp.data.NavitiaApi
import fr.lazyratp.data.Prefs
import fr.lazyratp.data.Station
import fr.lazyratp.rules.PinRule
import fr.lazyratp.rules.Rule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun FavoritesTab(
    apiKey: String,
    favorites: List<Favorite>,
    selected: Int,
    rules: List<Rule>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Int?>(null) }

    // Edition : le formulaire remplit l'ecran, comme l'editeur de regles.
    val editIndex = editing
    if (editIndex != null && editIndex in favorites.indices) {
        FavoriteForm(
            apiKey = apiKey,
            initial = favorites[editIndex],
            submitLabel = "Enregistrer",
            onCancel = { editing = null },
            onSubmit = { favorite ->
                scope.launch {
                    Prefs.replaceFavorite(context, editIndex, favorite)
                    refreshWidget(context)
                }
                editing = null
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nouveau favori", style = MaterialTheme.typography.titleMedium)
        if (apiKey.isBlank()) {
            Text(
                "Enregistre d'abord ta cle API dans l'onglet Parametres.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            FavoriteForm(
                apiKey = apiKey,
                initial = null,
                submitLabel = "Ajouter aux favoris",
                onCancel = null,
                onSubmit = { favorite ->
                    scope.launch {
                        Prefs.addFavorite(context, favorite)
                        refreshWidget(context)
                    }
                },
            )
        }

        HorizontalDivider()

        Text("Favoris", style = MaterialTheme.typography.titleMedium)
        if (favorites.isEmpty()) {
            Text("Aucun favori.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Le bouton radio designe le trajet de repli : celui affiche quand aucune regle ne matche.",
                style = MaterialTheme.typography.bodySmall,
            )
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

                            TextButton(onClick = { editing = index }) { Text("Modifier") }
                            TextButton(onClick = {
                                scope.launch {
                                    Prefs.removeFavorite(context, index)
                                    refreshWidget(context)
                                }
                            }) { Text("Supprimer") }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Button(
            onClick = { scope.launch { refreshWidget(context) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rafraichir le widget") }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Formulaire d'un favori, partage entre l'ajout et l'edition. Pre-rempli depuis
 * [initial] a l'edition, vide a l'ajout.
 */
@Composable
private fun FavoriteForm(
    apiKey: String,
    initial: Favorite?,
    submitLabel: String,
    onCancel: (() -> Unit)?,
    onSubmit: (Favorite) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(FavoriteDraft.of(initial)) }

    val body: @Composable () -> Unit = {
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

        CheckRow("Dernier trajet du jour", draft.lastJourney) { draft = draft.copy(lastJourney = it) }
        CheckRow("Sans bus (exclut aussi le Noctilien)", draft.noBus) { draft = draft.copy(noBus = it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onCancel != null) {
                TextButton(onClick = onCancel) { Text("Annuler") }
            }
            Button(
                onClick = { draft.toFavorite(initial)?.let(onSubmit) },
                enabled = draft.isComplete,
            ) { Text(submitLabel) }
        }
    }

    // A l'edition, le formulaire occupe l'ecran et defile ; a l'ajout, il s'insere
    // dans la colonne deja defilante de l'onglet.
    if (onCancel != null) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Modifier le favori", style = MaterialTheme.typography.titleMedium)
            body()
            Spacer(Modifier.height(24.dp))
        }
    } else {
        body()
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
