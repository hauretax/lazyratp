package fr.lazyratp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lazyratp.data.Disruption
import fr.lazyratp.data.Journey
import fr.lazyratp.data.LineBadge
import fr.lazyratp.data.Prefs
import fr.lazyratp.data.Step
import fr.lazyratp.data.WidgetCache
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fiche d'un trajet : une seule page, sans onglet, rien que du renseignement. Ouverte
 * depuis le widget par appui sur une ligne. Elle lit le trajet dans le cache du widget,
 * a l'index passe en extra : c'est ce que le widget affichait, donc pas de nouvel appel reseau.
 */
class TripDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        setContent {
            MaterialTheme {
                Surface { TripDetailScreen(index) }
            }
        }
    }

    companion object {
        const val EXTRA_INDEX = "journey_index"
    }
}

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun Long.asClock(): String = HHMM.format(Instant.ofEpochMilli(this).atZone(PARIS))

private fun durationLabel(seconds: Int): String {
    val m = seconds / 60
    return if (m < 60) "${m} min" else "${m / 60} h ${(m % 60).toString().padStart(2, '0')}"
}

@Composable
private fun TripDetailScreen(index: Int) {
    val context = LocalContext.current
    val cache by produceState<WidgetCache?>(null) { value = Prefs.cache(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val journey = cache?.journeys?.getOrNull(index)
        if (journey == null) {
            Text(
                "Trajet indisponible. Rafraichis le widget et rouvre la fiche.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        Text(cache?.favoriteLabel.orEmpty(), style = MaterialTheme.typography.titleLarge)
        Text(
            "${journey.departure.asClock()} → ${journey.arrival.asClock()}  ·  ${durationLabel(journey.duration)}",
            style = MaterialTheme.typography.titleMedium,
        )
        if (journey.cancelled) {
            Text(
                "Ce trajet est supprimé.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider()

        journey.steps.forEachIndexed { i, step ->
            if (step.walkBefore > 0) WalkLine(step.walkBefore)
            StepCard(step)
        }
        if (journey.walkAfterLast > 0) WalkLine(journey.walkAfterLast)

        HorizontalDivider()

        Text("Perturbations", style = MaterialTheme.typography.titleMedium)
        if (journey.disruptions.isEmpty()) {
            Text("Aucune perturbation signalée sur ce trajet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            journey.disruptions.forEach { DisruptionCard(it) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WalkLine(walkSeconds: Int) {
    Text(
        "Marche ${walkSeconds / 60} min",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun StepCard(step: Step) {
    Card(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineChip(step)
                Spacer(Modifier.height(0.dp))
                Text(
                    "  ${step.mode}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (step.direction.isNotBlank()) {
                Text("Direction ${step.direction}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "${step.departure.asClock()}  ${step.from}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${step.arrival.asClock()}  ${step.to}",
                style = MaterialTheme.typography.bodyMedium,
            )
            // La voie n'est presque jamais fournie par PRIM : on ne montre la ligne que
            // lorsqu'elle existe vraiment, sans afficher un champ vide le reste du temps.
            if (step.platform.isNotBlank()) {
                Text(
                    "Voie ${step.platform}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LineChip(step: Step) {
    val bg = parseColor(step.color) ?: MaterialTheme.colorScheme.primary
    Text(
        text = LineBadge.of(step.mode, step.code).ifBlank { step.mode },
        color = onColor(bg),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DisruptionCard(disruption: Disruption) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // distinct : quand le titre n'est que le nom de severite, ne pas l'ecrire deux fois.
            val head = listOf(disruption.severity, disruption.title)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" · ")
            if (head.isNotBlank()) {
                Text(
                    head,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (disruption.message.isNotBlank()) {
                Text(
                    disruption.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** "FFCC30" -> Color, ou null quand la chaine n'est pas une couleur hex exploitable. */
private fun parseColor(hex: String): Color? {
    if (hex.isBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrNull()
}

/** Noir ou blanc, selon ce qui se lit le mieux sur la couleur de ligne. */
private fun onColor(bg: Color): Color {
    val luminance = 0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue
    return if (luminance > 0.6) Color.Black else Color.White
}
