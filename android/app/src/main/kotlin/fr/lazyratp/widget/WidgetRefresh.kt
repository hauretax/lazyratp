package fr.lazyratp.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll

/**
 * Rafraichir un widget Glance ne consiste pas a appeler updateAll : sur une session
 * deja vivante, updateAll relit le Glance state du widget et ne recompose *que* s'il a
 * change. Un widget dont les donnees vivent ailleurs que dans ce state n'est donc jamais
 * refetche tant que la session tient (~45 s), et le bouton parait mort.
 *
 * On fait donc transiter un jeton par le Glance state. Il ne porte aucune donnee : il
 * change, la composition le lit, et le refetch se declenche depuis la composition.
 */
object WidgetRefresh {

    val TOKEN = longPreferencesKey("refresh_token")

    /** Un widget precis : le ⟳ du widget sait sur quelle instance il a ete presse. */
    suspend fun request(context: Context, id: GlanceId) {
        updateAppWidgetState(context, id) { it[TOKEN] = System.currentTimeMillis() }
        NextTrainsWidget().update(context, id)
    }

    /** Toutes les instances : l'app et le worker ne visent personne en particulier. */
    suspend fun requestAll(context: Context) {
        val now = System.currentTimeMillis()
        GlanceAppWidgetManager(context)
            .getGlanceIds(NextTrainsWidget::class.java)
            .forEach { id -> updateAppWidgetState(context, id) { it[TOKEN] = now } }
        NextTrainsWidget().updateAll(context)
    }
}
