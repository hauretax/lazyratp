package fr.lazyratp.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import fr.lazyratp.data.Favorite
import fr.lazyratp.data.NavitiaApi
import fr.lazyratp.data.Prefs
import fr.lazyratp.data.Station
import fr.lazyratp.widget.NextTrainsWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { ConfigScreen() }
            }
        }
    }
}

private suspend fun refreshWidget(context: Context) {
    NextTrainsWidget().updateAll(context)
}

@Composable
private fun ConfigScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val apiKey by Prefs.apiKeyFlow(context).collectAsState(initial = "")
    val favorites by Prefs.favoritesFlow(context).collectAsState(initial = emptyList())
    val selected by Prefs.selectedFlow(context).collectAsState(initial = 0)

    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var from by remember { mutableStateOf<Station?>(null) }
    var to by remember { mutableStateOf<Station?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LazyRATP", style = MaterialTheme.typography.headlineSmall)

        Text("Cle API PRIM", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            singleLine = true,
            label = { Text("apiKey") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                scope.launch {
                    Prefs.setApiKey(context, keyInput)
                    refreshWidget(context)
                }
            },
            enabled = keyInput.isNotBlank() && keyInput != apiKey,
        ) { Text("Enregistrer la cle") }

        HorizontalDivider()

        Text("Nouveau favori", style = MaterialTheme.typography.titleMedium)
        if (apiKey.isBlank()) {
            Text(
                "Saisis d'abord ta cle API pour rechercher des stations.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            StationField("Depart", apiKey, from) { from = it }
            StationField("Arrivee", apiKey, to) { to = it }
            Button(
                onClick = {
                    val f = from ?: return@Button
                    val t = to ?: return@Button
                    scope.launch {
                        Prefs.addFavorite(context, Favorite(f, t))
                        from = null
                        to = null
                        refreshWidget(context)
                    }
                },
                enabled = from != null && to != null,
            ) { Text("Ajouter aux favoris") }
        }

        HorizontalDivider()

        Text("Favoris", style = MaterialTheme.typography.titleMedium)
        if (favorites.isEmpty()) {
            Text("Aucun favori.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Le widget affiche le favori selectionne.",
                style = MaterialTheme.typography.bodySmall,
            )
            favorites.forEachIndexed { index, favorite ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    TextButton(onClick = {
                        scope.launch {
                            Prefs.removeFavorite(context, index)
                            refreshWidget(context)
                        }
                    }) { Text("Supprimer") }
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
