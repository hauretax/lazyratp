package fr.lazyratp.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.lazyratp.data.Journey
import fr.lazyratp.data.WidgetRepo
import fr.lazyratp.data.WidgetState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NextTrainsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = WidgetRepo.load(context)
        provideContent {
            GlanceTheme {
                Body(state)
            }
        }
    }
}

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.asClock(): String =
    HHMM.format(Instant.ofEpochMilli(this).atZone(PARIS))

@Composable
private fun Body(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
            .clickable(actionRunCallback<RefreshAction>()),
    ) {
        when (state) {
            WidgetState.NeedsKey -> Hint("Ouvre LazyRATP\net saisis ta cle API PRIM")
            WidgetState.NeedsFavorite -> Hint("Aucun favori.\nAjoute un trajet dans l'app")
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

    if (state.journeys.isEmpty()) {
        Hint("Aucun trajet a venir")
        return
    }

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(state.journeys) { journey -> JourneyRow(journey) }
    }
}

@Composable
private fun Header(state: WidgetState.Ready) {
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
    }
}

@Composable
private fun JourneyRow(journey: Journey) {
    val waitMinutes = ((journey.departure - System.currentTimeMillis()) / 60_000L).toInt()

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = journey.departure.asClock(),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = if (journey.cancelled) "supprime" else waitLabel(waitMinutes),
            style = TextStyle(
                color = if (journey.cancelled) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                fontSize = 13.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = journey.dest,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

private fun waitLabel(minutes: Int): String = when {
    minutes < 0 -> "parti"
    minutes == 0 -> "a quai"
    else -> "$minutes min"
}
