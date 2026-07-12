package fr.lazyratp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** Tap sur le ⟳ : refetch immediat, sans attendre le cycle WorkManager. */
class RefreshAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        WidgetRefresh.request(context, glanceId)
    }
}
