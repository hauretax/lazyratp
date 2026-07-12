package fr.lazyratp.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.lazyratp.data.Display
import fr.lazyratp.data.Journey
import fr.lazyratp.data.LineBadge
import fr.lazyratp.data.WaitLabel
import fr.lazyratp.data.Walk
import fr.lazyratp.data.WidgetRepo
import fr.lazyratp.data.WidgetState
import fr.lazyratp.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NextTrainsWidget : GlanceAppWidget() {

    // Sans state definition, updateAll() n'a rien a comparer sur une session vivante et
    // ne recompose jamais. C'est par ce state que WidgetRefresh fait passer son jeton.
    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Lu hors composition, mais sans reseau et re-lu a chaque session : c'est la valeur
        // d'amorcage, pas la source de verite. Elle evite l'ecran de chargement au refetch.
        val seed = WidgetRepo.seed(context)

        provideContent {
            // Charger DANS la composition, pas avant : provideContent ne rend jamais la main,
            // donc un etat capture au-dessus resterait fige pour toute la duree de la session,
            // et le bouton de rafraichissement paraitrait mort.
            val token = currentState<Preferences>()[WidgetRefresh.TOKEN] ?: 0L
            val state by produceState<WidgetState?>(seed, token) { value = WidgetRepo.load(context) }

            GlanceTheme {
                when (val current = state) {
                    // Seulement au tout premier affichage, quand il n'existe aucun cache.
                    null -> Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(GlanceTheme.colors.widgetBackground)
                            .padding(12.dp),
                    ) { Hint("Chargement...") }

                    else -> Body(current)
                }
            }
        }
    }
}

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.asClock(): String = HHMM.format(Instant.ofEpochMilli(this).atZone(PARIS))

@Composable
private fun Body(state: WidgetState) {
    // Appuyer sur le widget ouvre l'app. Le rafraichissement a son propre bouton :
    // WorkManager s'en charge de toute facon toutes les 15 minutes.
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when (state) {
            WidgetState.NeedsKey -> Hint("Appuie pour ouvrir LazyRATP\net saisir ta cle API PRIM")
            WidgetState.NeedsFavorite -> Hint("Aucun favori.\nAppuie pour en ajouter")
            is WidgetState.Error -> Hint(state.message + "\nAppuie pour reessayer")
            is WidgetState.Ready -> Ready(state)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

@Composable
private fun Ready(state: WidgetState.Ready) {
    Header(state)
    Spacer(GlanceModifier.height(6.dp))

    when {
        state.display.isEmpty -> Hint("Toutes les colonnes sont masquees.\nOuvre Parametres > Affichage")
        state.journeys.isEmpty() -> Hint("Aucun trajet a venir")
        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(state.journeys) { journey -> JourneyRow(journey, state.display) }
        }
    }
}

@Composable
private fun Header(state: WidgetState.Ready) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = state.label,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = if (state.stale) "! ${state.fetchedAt.asClock()}" else state.fetchedAt.asClock(),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
            // Le glyphe fait 15 sp : sans marge, la cible tactile est plus petite que le
            // doigt et le bouton passe pour mort avant meme d'avoir ete presse. La marge
            // tient lieu d'espacement, d'ou l'absence de Spacer : elle est deja prise sur
            // la largeur du libelle, qui est ce qu'on tronque en premier.
            Text(
                text = "⟳",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 15.sp),
                modifier = GlanceModifier
                    .clickable(actionRunCallback<RefreshAction>())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }

        // Sans ca, le choix du trajet serait une boite noire.
        if (state.ruleName != null) {
            Text(
                text = state.ruleName,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun JourneyRow(journey: Journey, display: Display) {
    val wait = Walk.waitMinutes(journey.departure, System.currentTimeMillis())
    val reachable = Walk.isReachable(wait, display.walkDeparture)

    // Un train qu'on ne peut plus attraper reste visible, mais estompe : le CLI le
    // colore en magenta, faute de pouvoir estomper dans un terminal.
    val primary: ColorProvider =
        if (reachable) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant
    val secondary: ColorProvider = GlanceTheme.colors.onSurfaceVariant

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (display.showDeparture) {
            Text(
                text = journey.departure.asClock(),
                style = TextStyle(color = primary, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.width(8.dp))
        }

        if (display.showWait) {
            Text(
                text = if (journey.cancelled) "supprime" else WaitLabel.of(wait),
                style = TextStyle(
                    color = when {
                        journey.cancelled -> GlanceTheme.colors.error
                        !reachable -> secondary
                        else -> GlanceTheme.colors.primary
                    },
                    fontSize = 13.sp,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
        }

        if (display.showArrival) {
            Text(
                text = "→ ${Walk.arrivalWithWalk(journey.arrival, display.walkArrival).asClock()}",
                style = TextStyle(color = secondary, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
        }

        if (display.showDuration) {
            Text(
                text = Walk.durationLabel(journey.duration),
                style = TextStyle(color = secondary, fontSize = 12.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
        }

        Spacer(GlanceModifier.defaultWeight())

        if (display.showRoute) {
            // Lettre pour le RER et le Transilien, chiffre cercle pour le metro.
            Text(
                text = LineBadge.route(journey.steps).ifEmpty { journey.dest },
                style = TextStyle(color = primary, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}
