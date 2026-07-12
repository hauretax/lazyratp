package fr.lazyratp.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.lazyratp.data.ApiKey
import fr.lazyratp.data.Display
import fr.lazyratp.data.LocationProvider
import fr.lazyratp.data.NavitiaApi
import fr.lazyratp.data.Prefs
import fr.lazyratp.data.WidgetTheme
import kotlinx.coroutines.launch

@Composable
internal fun SettingsTab(apiKey: String, display: Display) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun save(next: Display) = scope.launch {
        Prefs.setDisplay(context, next)
        refreshWidget(context)
    }

    val theme by Prefs.widgetThemeFlow(context).collectAsState(initial = WidgetTheme())
    fun saveTheme(next: WidgetTheme) = scope.launch {
        Prefs.setWidgetTheme(context, next)
        refreshWidget(context)
    }

    // Sans cle enregistree, on ouvre directement le formulaire.
    var changing by remember(apiKey.isBlank()) { mutableStateOf(apiKey.isBlank()) }
    var input by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cle API PRIM", style = MaterialTheme.typography.titleMedium)

        if (!changing) {
            Text("Enregistree : ${ApiKey.mask(apiKey)}")
            Button(onClick = {
                input = ""
                error = null
                changing = true
            }) { Text("Changer la cle") }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = error != null,
                label = { Text(if (apiKey.isBlank()) "Cle API" else "Nouvelle cle") },
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (apiKey.isNotBlank()) {
                    TextButton(onClick = {
                        changing = false
                        error = null
                    }) { Text("Annuler") }
                }
                Button(
                    enabled = input.isNotBlank() && !checking,
                    onClick = {
                        scope.launch {
                            checking = true
                            error = null
                            // On eprouve la cle contre l'API avant de toucher a celle qui marche.
                            val refusal = NavitiaApi.validateKey(input.trim())
                            if (refusal == null) {
                                Prefs.setApiKey(context, input.trim())
                                input = ""
                                changing = false
                                refreshWidget(context)
                            } else {
                                error = "Cle refusee : $refusal. L'ancienne est conservee."
                            }
                            checking = false
                        }
                    },
                ) { Text(if (checking) "Verification..." else "Verifier et enregistrer") }
            }
        }

        HorizontalDivider()
        Text(
            "Une fois enregistree, la cle n'est plus jamais reaffichee en clair. " +
                "Une nouvelle cle n'est adoptee qu'apres un appel reel a Navitia : " +
                "si elle est refusee, l'ancienne reste en place.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "La sauvegarde systeme est desactivee pour cette app, afin que la cle " +
                "ne parte pas dans un backup adb.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        Text("Affichage", style = MaterialTheme.typography.titleMedium)
        CheckRow("Heure de depart", display.showDeparture) { save(display.copy(showDeparture = it)) }
        CheckRow("Temps d'attente", display.showWait) { save(display.copy(showWait = it)) }
        CheckRow("Heure d'arrivee", display.showArrival) { save(display.copy(showArrival = it)) }
        CheckRow("Duree", display.showDuration) { save(display.copy(showDuration = it)) }
        CheckRow("Chemin (lignes empruntees)", display.showRoute) { save(display.copy(showRoute = it)) }

        if (display.isEmpty) {
            Text(
                "Toutes les colonnes sont masquees : le widget n'affichera rien.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()

        LocationSection()

        HorizontalDivider()

        Text("Temps de marche", style = MaterialTheme.typography.titleMedium)
        MinutesField(
            label = "Marche jusqu'a la gare de depart",
            value = display.walkDeparture,
        ) { save(display.copy(walkDeparture = it)) }
        MinutesField(
            label = "Marche depuis la gare d'arrivee",
            value = display.walkArrival,
        ) { save(display.copy(walkArrival = it)) }
        Text(
            "Un train qui part dans moins que la marche au depart est estompe : " +
                "tu ne peux plus l'attraper. La marche a l'arrivee est ajoutee a l'heure affichee.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        WidgetThemeSection(theme = theme, display = display, onChange = ::saveTheme)

        HorizontalDivider()

        Text("Widget", style = MaterialTheme.typography.titleMedium)
        Text(
            "Le widget se rafraichit tout seul toutes les 15 minutes, et a chaque " +
                "changement fait ici. Ce bouton force un rafraichissement immediat.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { scope.launch { refreshWidget(context) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rafraichir le widget") }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Deux permissions distinctes. Celle du premier plan s'obtient par un dialogue ;
 * celle d'arriere-plan, exigee par le widget, ne s'obtient que dans les Reglages
 * depuis Android 11. On ne peut donc pas la demander, seulement y conduire.
 */
@Composable
private fun LocationSection() {
    val context = LocalContext.current
    var foreground by remember { mutableStateOf(LocationProvider.hasForegroundPermission(context)) }
    var background by remember { mutableStateOf(LocationProvider.hasBackgroundPermission(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                foreground = LocationProvider.hasForegroundPermission(context)
                background = LocationProvider.hasBackgroundPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        foreground = LocationProvider.hasForegroundPermission(context)
        background = LocationProvider.hasBackgroundPermission(context)
    }

    Text("Position", style = MaterialTheme.typography.titleMedium)
    Text(
        "Necessaire seulement pour les favoris \"depuis ma position\" et les regles " +
            "declenchees par un lieu.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("Premier plan : ${if (foreground) "autorisee" else "refusee"}")
    Text("Arriere-plan : ${if (background) "autorisee" else "refusee"}")

    if (!foreground) {
        Button(onClick = {
            request.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }) { Text("Autoriser la position") }
    }

    if (foreground && !background) {
        Text(
            "Le widget lit la position hors du premier plan. Android ne permet pas " +
                "de l'accorder par un dialogue : choisis \"Toujours autoriser\" dans les Reglages.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
        }) { Text("Ouvrir les Reglages") }
    }

    if (foreground && background) {
        Text(
            "Sur Xiaomi, active aussi \"Demarrage auto\" et passe la batterie a " +
                "\"Aucune restriction\" pour LazyRATP, sans quoi le rafraichissement " +
                "de fond ne s'executera pas.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MinutesField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            val minutes = it.toIntOrNull()
            if (minutes != null && minutes >= 0) onChange(minutes)
        },
        singleLine = true,
        isError = parsed == null || parsed < 0,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        label = { Text("$label (min)") },
        modifier = Modifier.fillMaxWidth(),
    )
}
