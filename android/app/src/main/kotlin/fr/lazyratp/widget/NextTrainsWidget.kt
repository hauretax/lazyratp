package fr.lazyratp.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
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
import fr.lazyratp.data.ArriveBy
import fr.lazyratp.data.Display
import fr.lazyratp.data.Journey
import fr.lazyratp.data.LineBadge
import fr.lazyratp.data.Prefs
import fr.lazyratp.data.WaitLabel
import fr.lazyratp.data.Walk
import fr.lazyratp.data.WidgetPalette
import fr.lazyratp.data.WidgetRepo
import fr.lazyratp.data.WidgetState
import fr.lazyratp.ui.MainActivity
import fr.lazyratp.ui.TripDetailActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NextTrainsWidget : GlanceAppWidget() {

    // Sans state definition, updateAll() n'a rien a comparer sur une session vivante et
    // ne recompose jamais. C'est par ce state que WidgetRefresh fait passer son jeton.
    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Lus hors composition, mais sans reseau et re-lus a chaque session : valeurs
        // d'amorcage, pas source de verite. Le seed evite l'ecran de chargement au refetch.
        val seed = WidgetRepo.seed(context)
        val initialTheme = Prefs.widgetTheme(context)

        provideContent {
            // Charger DANS la composition, pas avant : provideContent ne rend jamais la main,
            // donc un etat capture au-dessus resterait fige pour toute la session. Le theme est
            // lu ici aussi, avec le meme jeton : sans cela un changement de couleur n'arriverait
            // qu'a la prochaine session, comme le refetch avant sa correction.
            val token = currentState<Preferences>()[WidgetRefresh.TOKEN] ?: 0L
            val theme by produceState(initialTheme, token) { value = Prefs.widgetTheme(context) }
            val state by produceState<WidgetState?>(seed, token) { value = WidgetRepo.load(context) }

            when (val palette = WidgetPalette.resolve(theme)) {
                // Mode systeme : on garde les couleurs dynamiques de la plateforme.
                null -> GlanceTheme { Screen(state, glanceColors()) }
                else -> Screen(state, palette.toWidgetColors())
            }
        }
    }
}

/** Les cinq roles de couleur du widget, quelle qu'en soit la source (dynamique ou choisie). */
private class WidgetColors(
    val background: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val primary: ColorProvider,
    val error: ColorProvider,
)

private fun WidgetPalette.Colors.toWidgetColors() = WidgetColors(
    background = ColorProvider(Color(background)),
    onSurface = ColorProvider(Color(text)),
    onSurfaceVariant = ColorProvider(Color(textVariant)),
    primary = ColorProvider(Color(accent)),
    error = ColorProvider(Color(error)),
)

@Composable
private fun glanceColors() = WidgetColors(
    background = GlanceTheme.colors.widgetBackground,
    onSurface = GlanceTheme.colors.onSurface,
    onSurfaceVariant = GlanceTheme.colors.onSurfaceVariant,
    primary = GlanceTheme.colors.primary,
    error = GlanceTheme.colors.error,
)

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.asClock(): String = HHMM.format(Instant.ofEpochMilli(this).atZone(PARIS))

@Composable
private fun Screen(state: WidgetState?, colors: WidgetColors) {
    when (val current = state) {
        // Seulement au tout premier affichage, quand il n'existe aucun cache.
        null -> Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.background)
                .padding(12.dp),
        ) { Hint("Chargement...", colors) }

        else -> Body(current, colors)
    }
}

@Composable
private fun Body(state: WidgetState, colors: WidgetColors) {
    // Appuyer sur le widget ouvre l'app. Le rafraichissement a son propre bouton :
    // WorkManager s'en charge de toute facon toutes les 15 minutes.
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when (state) {
            WidgetState.NeedsKey -> Hint("Appuie pour ouvrir LazyRATP\net saisir ta cle API PRIM", colors)
            WidgetState.NeedsFavorite -> Hint("Aucun favori.\nAppuie pour en ajouter", colors)
            is WidgetState.Error -> Hint(state.message + "\nAppuie pour reessayer", colors)
            is WidgetState.Ready -> Ready(state, colors)
        }
    }
}

@Composable
private fun Hint(text: String, colors: WidgetColors) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}

@Composable
private fun Ready(state: WidgetState.Ready, colors: WidgetColors) {
    Header(state, colors)

    // Un favori "arriver a telle heure" ne repond pas a "quand part le prochain" mais a
    // "jusqu'a quand puis-je attendre". La reponse merite la premiere ligne, pas d'etre
    // deduite d'une liste d'horaires.
    if (state.arriveBy != null) {
        Deadline(state, colors)
    }

    Spacer(GlanceModifier.height(6.dp))

    when {
        state.display.isEmpty -> Hint("Toutes les colonnes sont masquees.\nOuvre Parametres > Affichage", colors)
        state.journeys.isEmpty() && state.arriveBy != null -> Hint("Aucun trajet n'arrive a l'heure", colors)
        state.journeys.isEmpty() -> Hint("Aucun trajet a venir", colors)
        else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            itemsIndexed(state.journeys) { index, journey -> JourneyRow(index, journey, state.display, colors) }
        }
    }
}

@Composable
private fun Deadline(state: WidgetState.Ready, colors: WidgetColors) {
    val walk = state.display.walkDeparture
    val latest = ArriveBy.latestCatchable(state.journeys, System.currentTimeMillis(), walk)

    if (latest == null) {
        Text(
            text = "Trop tard pour arriver a l'heure",
            style = TextStyle(color = colors.error, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
        return
    }

    // Sans temps de marche configure, "partir a" et "etre au depart a" sont la meme heure :
    // on n'affiche pas deux fois la meme chose.
    val leaveAt = ArriveBy.leaveAt(latest.departure, walk)
    val line = if (walk > 0) {
        "Etre au depart a ${latest.departure.asClock()} · partir a ${leaveAt.asClock()}"
    } else {
        "Etre au depart a ${latest.departure.asClock()}"
    }

    Text(
        text = line,
        style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

@Composable
private fun Header(state: WidgetState.Ready, colors: WidgetColors) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = state.label,
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = if (state.stale) "! ${state.fetchedAt.asClock()}" else state.fetchedAt.asClock(),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
            )
            // Le glyphe fait 15 sp : sans marge, la cible tactile est plus petite que le
            // doigt et le bouton passe pour mort avant meme d'avoir ete presse. La marge
            // tient lieu d'espacement, d'ou l'absence de Spacer : elle est deja prise sur
            // la largeur du libelle, qui est ce qu'on tronque en premier.
            Text(
                text = "⟳",
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 15.sp),
                modifier = GlanceModifier
                    .clickable(actionRunCallback<RefreshAction>())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }

        // Sans ca, le choix du trajet serait une boite noire.
        if (state.ruleName != null) {
            Text(
                text = state.ruleName,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun JourneyRow(index: Int, journey: Journey, display: Display, colors: WidgetColors) {
    val wait = Walk.waitMinutes(journey.departure, System.currentTimeMillis())
    val reachable = Walk.isReachable(wait, display.walkDeparture)

    // Un train qu'on ne peut plus attraper reste visible, mais estompe : le CLI le
    // colore en magenta, faute de pouvoir estomper dans un terminal.
    val primary: ColorProvider = if (reachable) colors.onSurface else colors.onSurfaceVariant
    val secondary: ColorProvider = colors.onSurfaceVariant

    Row(
        // Appuyer sur une ligne ouvre sa fiche detaillee ; le clic passe l'index du trajet,
        // que TripDetailActivity relit dans le cache du widget.
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
            .clickable(
                actionStartActivity<TripDetailActivity>(
                    actionParametersOf(ActionParameters.Key<Int>(TripDetailActivity.EXTRA_INDEX) to index),
                ),
            ),
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
                        journey.cancelled -> colors.error
                        !reachable -> secondary
                        else -> colors.primary
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
