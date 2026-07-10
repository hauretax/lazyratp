package fr.lazyratp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.lazyratp.data.ApiKey
import fr.lazyratp.data.NavitiaApi
import fr.lazyratp.data.Prefs
import kotlinx.coroutines.launch

@Composable
internal fun SettingsTab(apiKey: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    }
}
