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
import fr.lazyratp.data.NavitiaApi
import fr.lazyratp.data.PhysicalMode
import fr.lazyratp.data.Prefs
import fr.lazyratp.data.Station
import fr.lazyratp.data.TripMode
import fr.lazyratp.rules.PinRule
import fr.lazyratp.rules.Rule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Place-tenant : quand fromHere est vrai, Favorite ignore ce champ. */
private val HERE_STATION = Station(Favorite.HERE, "Ma position")

@Composable
internal fun FavoritesTab(
    apiKey: String,
    favorites: List<Favorite>,
    selected: Int,
    rules: List<Rule>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var from by remember { mutableStateOf<Station?>(null) }
    var to by remember { mutableStateOf<Station?>(null) }
    var fromHere by remember { mutableStateOf(false) }
    var lastJourney by remember { mutableStateOf(false) }
    var noBus by remember { mutableStateOf(false) }

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
            CheckRow("Partir de ma position", fromHere) { fromHere = it }
            if (fromHere) {
                Text(
                    "Navitia calcule lui-meme la marche jusqu'au premier arret. " +
                        "Autorise la position dans l'onglet Parametres.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                StationField("Depart", apiKey, from) { from = it }
            }
            StationField("Arrivee", apiKey, to) { to = it }

            CheckRow("Dernier trajet du jour", lastJourney) { lastJourney = it }
            CheckRow("Sans bus (exclut aussi le Noctilien)", noBus) { noBus = it }

            Button(
                onClick = {
                    val t = to ?: return@Button
                    val f = if (fromHere) HERE_STATION else (from ?: return@Button)
                    scope.launch {
                        Prefs.addFavorite(
                            context,
                            Favorite(
                                from = f,
                                to = t,
                                mode = if (lastJourney) TripMode.LAST_JOURNEY else TripMode.NEXT_DEPARTURES,
                                forbiddenModes = if (noBus) setOf(PhysicalMode.BUS) else emptySet(),
                                fromHere = fromHere,
                            ),
                        )
                        from = null
                        to = null
                        fromHere = false
                        lastJourney = false
                        noBus = false
                        refreshWidget(context)
                    }
                },
                enabled = to != null && (fromHere || from != null),
            ) { Text("Ajouter aux favoris") }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

@Composable
private fun StationField(
    label: String,
    apiKey: String,
    picked: Station?,
    onPick: (Station?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Station>>(emptyList()) }

    // Debounce : Navitia est appele 300 ms apres la derniere frappe, pas a chaque caractere.
    LaunchedEffect(query, apiKey) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        results = NavitiaApi.searchStations(apiKey, query)
    }

    if (picked != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$label : ${picked.name}", modifier = Modifier.weight(1f))
            TextButton(onClick = {
                query = ""
                results = emptyList()
                onPick(null)
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

    results.take(5).forEach { station ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    query = ""
                    results = emptyList()
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
