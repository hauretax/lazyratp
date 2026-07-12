package fr.lazyratp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.lazyratp.data.Display
import fr.lazyratp.data.ThemeMode
import fr.lazyratp.data.WidgetPalette
import fr.lazyratp.data.WidgetTheme

private val ACCENTS = listOf("7C4DFF", "2196F3", "4CAF50", "E91E63", "FF9800", "F44336", "009688")
private val BACKGROUNDS = listOf("1C1B1F", "000000", "0D1B2A", "14261C", "2A1A2E", "FFFBFE")
private val TEXTS = listOf("ECE6F0", "1C1B1F", "FFF8E1")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetThemeSection(theme: WidgetTheme, display: Display, onChange: (WidgetTheme) -> Unit) {
    Text("Theme du widget", style = MaterialTheme.typography.titleMedium)

    // L'apercu se met a jour a chaque changement, couleurs ET colonnes : on regle en voyant
    // le vrai rendu du widget, pas une maquette figee.
    WidgetPreview(theme, display)

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip("Systeme", theme.mode == ThemeMode.SYSTEM) { onChange(theme.copy(mode = ThemeMode.SYSTEM)) }
        ModeChip("Clair", theme.mode == ThemeMode.LIGHT) { onChange(theme.copy(mode = ThemeMode.LIGHT)) }
        ModeChip("Sombre", theme.mode == ThemeMode.DARK) { onChange(theme.copy(mode = ThemeMode.DARK)) }
        ModeChip("Terminal", theme.mode == ThemeMode.TERMINAL) { onChange(theme.copy(mode = ThemeMode.TERMINAL)) }
        ModeChip("Personnalise", theme.mode == ThemeMode.CUSTOM) { onChange(theme.copy(mode = ThemeMode.CUSTOM)) }
    }

    when (theme.mode) {
        ThemeMode.SYSTEM -> Text(
            "Le widget suit les couleurs du systeme (Material You).",
            style = MaterialTheme.typography.bodySmall,
        )

        ThemeMode.TERMINAL -> Text(
            "Noir et vert phosphore, comme le terminal dont LazyRATP est ne.",
            style = MaterialTheme.typography.bodySmall,
        )

        ThemeMode.LIGHT, ThemeMode.DARK -> SwatchRow("Accent", ACCENTS, theme.accent) {
            onChange(theme.copy(accent = it))
        }

        ThemeMode.CUSTOM -> {
            SwatchRow("Fond", BACKGROUNDS, theme.background) { onChange(theme.copy(background = it)) }
            SwatchRow("Texte", TEXTS, theme.text) { onChange(theme.copy(text = it)) }
            SwatchRow("Accent", ACCENTS, theme.accent) { onChange(theme.copy(accent = it)) }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun SwatchRow(label: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { hex ->
                val color = Color(WidgetPalette.parseHex(hex, 0xFF000000.toInt()))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        // Le contour marque la couleur choisie ; il tranche toujours, quelle
                        // que soit la teinte, en reprenant le noir ou blanc lisible dessus.
                        .border(
                            width = if (hex == selected) 3.dp else 1.dp,
                            color = if (hex == selected) {
                                Color(WidgetPalette.onColor(WidgetPalette.parseHex(hex, 0)))
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .clickable { onPick(hex) },
                )
            }
        }
    }
}

/** Deux departs fictifs pour l'apercu. Les valeurs importent peu, la mise en forme si. */
private data class SampleRow(
    val departure: String,
    val wait: String,
    val arrival: String,
    val duration: String,
    val line: String,
)

private val SAMPLE = listOf(
    SampleRow("12:36", "3 min", "→ 13:04", "28m", "C"),
    SampleRow("12:41", "8 min", "→ 13:10", "29m", "T9 › C"),
)

/**
 * Une maquette Material3 du widget, teintee par le meme calcul que le vrai widget et
 * pilotee par les memes reglages d'affichage : changer une couleur ou masquer une colonne
 * se voit ici avant de toucher le vrai widget.
 */
@Composable
private fun WidgetPreview(theme: WidgetTheme, display: Display) {
    val resolved = WidgetPalette.resolve(theme)
    val bg = resolved?.let { Color(it.background) } ?: MaterialTheme.colorScheme.surfaceVariant
    val text = resolved?.let { Color(it.text) } ?: MaterialTheme.colorScheme.onSurface
    val textVar = resolved?.let { Color(it.textVariant) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accent = resolved?.let { Color(it.accent) } ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ma position → Châtelet",
                color = text,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text("12:34  ⟳", color = textVar, style = MaterialTheme.typography.bodySmall)
        }

        if (display.isEmpty) {
            Text("Toutes les colonnes sont masquees.", color = textVar, style = MaterialTheme.typography.bodySmall)
        } else {
            SAMPLE.forEach { PreviewRow(it, display, text, accent, textVar) }
        }
    }
}

/**
 * Reproduit la logique de colonnes de JourneyRow : chaque champ n'apparait que si son
 * reglage d'affichage est actif, dans le meme ordre que le vrai widget.
 */
@Composable
private fun PreviewRow(row: SampleRow, display: Display, text: Color, accent: Color, textVar: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (display.showDeparture) {
            Text(row.departure, color = text, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        }
        if (display.showWait) {
            Text(row.wait, color = accent, style = MaterialTheme.typography.bodySmall)
        }
        if (display.showArrival) {
            Text(row.arrival, color = textVar, style = MaterialTheme.typography.bodySmall)
        }
        if (display.showDuration) {
            Text(row.duration, color = textVar, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        if (display.showRoute) {
            Text(row.line, color = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
