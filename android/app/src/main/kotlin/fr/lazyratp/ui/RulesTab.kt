package fr.lazyratp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.lazyratp.data.Favorite
import fr.lazyratp.data.GeoPlace
import fr.lazyratp.data.NavitiaApi
import fr.lazyratp.data.Prefs
import fr.lazyratp.rules.PlaceCondition
import fr.lazyratp.rules.Rule
import fr.lazyratp.rules.RuleFormat
import fr.lazyratp.rules.moved
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.ceil

private val EXPIRY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM 'a' HH:mm")

@Composable
internal fun RulesTab(apiKey: String, favorites: List<Favorite>, rules: List<Rule>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Rule?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    fun persist(next: List<Rule>) = scope.launch {
        Prefs.setRules(context, next)
        refreshWidget(context)
    }

    if (favorites.isEmpty()) {
        Column(Modifier.padding(16.dp)) {
            Text("Ajoute d'abord un favori : une regle designe un trajet.")
        }
        return
    }

    if (editorOpen) {
        RuleEditor(
            apiKey = apiKey,
            favorites = favorites,
            initial = editing,
            onCancel = { editorOpen = false },
            onSave = { rule ->
                val next = if (editing == null) {
                    // En queue : priorite la plus basse. A l'utilisateur de la remonter.
                    rules + rule
                } else {
                    rules.map { if (it.id == rule.id) rule else it }
                }
                persist(next)
                editorOpen = false
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
        Text(
            "La premiere regle qui matche gagne. L'ordre de la liste est la priorite.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (rules.isEmpty()) {
            Text("Aucune regle. Le widget affiche le trajet de repli.", style = MaterialTheme.typography.bodySmall)
        }

        rules.forEachIndexed { index, rule ->
            val favorite = favorites.firstOrNull { it.id == rule.favoriteId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}. ${rule.name.ifBlank { "(sans nom)" }}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { on ->
                                persist(rules.map { if (it.id == rule.id) it.copy(enabled = on) else it })
                            },
                        )
                    }

                    Text(RuleFormat.summary(rule), style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = favorite?.label ?: "trajet introuvable, regle inerte",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    rule.expiresAt?.let {
                        Text("expire le ${EXPIRY_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}",
                            style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            onClick = { persist(rules.moved(index, index - 1)) },
                            enabled = index > 0,
                        ) { Text("↑") }
                        TextButton(
                            onClick = { persist(rules.moved(index, index + 1)) },
                            enabled = index < rules.size - 1,
                        ) { Text("↓") }

                        Spacer(Modifier.weight(1f))

                        TextButton(onClick = {
                            editing = rule
                            editorOpen = true
                        }) { Text("Modifier") }

                        TextButton(onClick = {
                            persist(rules.filterNot { it.id == rule.id })
                        }) { Text("Supprimer") }
                    }
                }
            }
        }

        HorizontalDivider()
        Button(
            onClick = {
                editing = null
                editorOpen = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Nouvelle regle") }

        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(
    apiKey: String,
    favorites: List<Favorite>,
    initial: Rule?,
    onCancel: () -> Unit,
    onSave: (Rule) -> Unit,
) {
    val initialPoint = initial?.place as? PlaceCondition.NearPoint
    var usePlace by remember { mutableStateOf(initialPoint != null) }
    var point by remember { mutableStateOf(initialPoint) }
    var radiusText by remember { mutableStateOf((initialPoint?.radiusMeters ?: 600).toString()) }
    val radius = radiusText.toIntOrNull()
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var favoriteId by remember { mutableStateOf(initial?.favoriteId ?: favorites.first().id) }
    var days by remember { mutableStateOf(initial?.days ?: emptySet()) }
    var allDay by remember { mutableStateOf(initial?.fromMinutes == null) }
    var fromText by remember { mutableStateOf(initial?.fromMinutes?.let(RuleFormat::minutesToHhMm) ?: "07:00") }
    var toText by remember { mutableStateOf(initial?.toMinutes?.let(RuleFormat::minutesToHhMm) ?: "10:00") }
    var expires by remember { mutableStateOf(initial?.expiresAt != null) }
    var hoursText by remember { mutableStateOf(initial?.expiresAt?.let { remainingHours(it).toString() } ?: "24") }

    val fromMinutes = RuleFormat.parseHhMm(fromText)
    val toMinutes = RuleFormat.parseHhMm(toText)
    val hours = hoursText.toIntOrNull()

    val timeValid = allDay || (fromMinutes != null && toMinutes != null)
    val expiryValid = !expires || (hours != null && hours > 0)
    val placeValid = !usePlace || (point != null && radius != null && radius > 0)
    val canSave = timeValid && expiryValid && placeValid && favorites.any { it.id == favoriteId }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (initial == null) "Nouvelle regle" else "Modifier la regle",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            label = { Text("Nom (affiche sur le widget)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Trajet", style = MaterialTheme.typography.titleSmall)
        favorites.forEach { favorite ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = favorite.id == favoriteId,
                    onClick = { favoriteId = favorite.id },
                )
                Text(favorite.label)
            }
        }

        Text("Jours (aucun coche = tous les jours)", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).forEach { day ->
                FilterChip(
                    selected = day in days,
                    onClick = { days = if (day in days) days - day else days + day },
                    label = { Text(RuleFormat.dayLabel(day)) },
                )
            }
        }

        CheckRow("Toute la journee", allDay) { allDay = it }
        if (!allDay) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    singleLine = true,
                    isError = fromMinutes == null,
                    label = { Text("De (HH:MM)") },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    singleLine = true,
                    isError = toMinutes == null,
                    label = { Text("A (HH:MM)") },
                    modifier = Modifier.weight(1f),
                )
            }
            if (fromMinutes != null && toMinutes != null && fromMinutes > toMinutes) {
                Text(
                    "Fenetre a cheval sur minuit. Attention : le filtre de jours porte sur " +
                        "l'instant d'evaluation, donc 01h00 compte comme le lendemain.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        CheckRow("Expire", expires) { expires = it }
        if (expires) {
            OutlinedTextField(
                value = hoursText,
                onValueChange = { hoursText = it },
                singleLine = true,
                isError = hours == null || hours <= 0,
                label = { Text("Dans combien d'heures") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        CheckRow("Seulement pres d'un lieu", usePlace) { usePlace = it }
        if (usePlace) {
            val current = point
            if (current == null) {
                if (apiKey.isBlank()) {
                    Text(
                        "Saisis ta cle API dans Parametres pour chercher une adresse.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    AddressField(apiKey) { found ->
                        point = PlaceCondition.NearPoint(found.name, found.lat, found.lon, radius ?: 600)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(current.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = { point = null }) { Text("Changer") }
                }
            }

            OutlinedTextField(
                value = radiusText,
                onValueChange = { radiusText = it },
                singleLine = true,
                isError = radius == null || radius <= 0,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text("Rayon (metres)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Sans position connue, une regle de lieu ne matche pas : elle echoue de " +
                    "maniere fermee, sinon elle se declencherait partout. Autorise la " +
                    "position dans Parametres.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel) { Text("Annuler") }
            Button(
                enabled = canSave,
                onClick = {
                    val computedFrom = if (allDay) null else fromMinutes
                    val computedTo = if (allDay) null else toMinutes
                    onSave(
                        Rule(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            favoriteId = favoriteId,
                            name = name.ifBlank {
                                "${RuleFormat.days(days)} · ${RuleFormat.window(computedFrom, computedTo)}"
                            },
                            enabled = initial?.enabled ?: true,
                            days = days,
                            fromMinutes = computedFrom,
                            toMinutes = computedTo,
                            place = if (usePlace) point?.copy(radiusMeters = radius ?: 600) else null,
                            expiresAt = if (expires && hours != null) {
                                System.currentTimeMillis() + hours * 3_600_000L
                            } else {
                                null
                            },
                        )
                    )
                },
            ) { Text("Enregistrer") }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Cherche une adresse ou un point d'interet. Navitia geocode, pas besoin de carte. */
@Composable
private fun AddressField(apiKey: String, onPick: (GeoPlace) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }

    LaunchedEffect(query, apiKey) {
        if (query.length < 3) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        results = NavitiaApi.searchPlaces(apiKey, query)
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        label = { Text("Adresse ou lieu") },
        modifier = Modifier.fillMaxWidth(),
    )

    results.take(5).forEach { found ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    query = ""
                    results = emptyList()
                    onPick(found)
                },
        ) {
            Column(Modifier.padding(8.dp)) {
                Text(found.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (found.kind == "poi") "point d'interet" else "adresse",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun remainingHours(expiresAt: Long): Int {
    val remaining = expiresAt - System.currentTimeMillis()
    if (remaining <= 0) return 1
    return ceil(remaining / 3_600_000.0).toInt().coerceAtLeast(1)
}
