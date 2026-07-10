package fr.lazyratp.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.glance.appwidget.updateAll
import fr.lazyratp.data.Display
import fr.lazyratp.data.Prefs
import fr.lazyratp.widget.NextTrainsWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Depuis Android 15, cibler l'API 35+ impose le bord a bord. Sans cela le
        // systeme dessine par-dessus, mais le contenu passe sous la barre d'etat.
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface { ConfigScreen() }
            }
        }
    }
}

internal suspend fun refreshWidget(context: Context) {
    NextTrainsWidget().updateAll(context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen() {
    val context = LocalContext.current

    val apiKey by Prefs.apiKeyFlow(context).collectAsState(initial = "")
    val favorites by Prefs.favoritesFlow(context).collectAsState(initial = emptyList())
    val selected by Prefs.selectedFlow(context).collectAsState(initial = 0)
    val rules by Prefs.rulesFlow(context).collectAsState(initial = emptyList())
    val display by Prefs.displayFlow(context).collectAsState(initial = Display())

    var tab by remember { mutableIntStateOf(0) }

    // safeDrawing couvre la barre d'etat, la barre de navigation ET la decoupe de
    // la camera : sur un Pixel, les onglets passaient sous le poincon.
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Favoris") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Regles") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Parametres") })
        }

        when (tab) {
            0 -> FavoritesTab(apiKey = apiKey, favorites = favorites, selected = selected, rules = rules)
            1 -> RulesTab(apiKey = apiKey, favorites = favorites, rules = rules)
            else -> SettingsTab(apiKey = apiKey, display = display)
        }
    }
}
