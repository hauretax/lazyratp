package fr.lazyratp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import fr.lazyratp.rules.LatLon
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Un releve de position ponctuel, sans Google Play Services.
 *
 * LocationManagerCompat.getCurrentLocation gere lui-meme le repli sous API 30.
 * On prend le fournisseur reseau en premier : il se contente du wifi et des
 * antennes, la ou le GPS allume une puce pour rien.
 *
 * Depuis Android 10, lire la position hors du premier plan exige
 * ACCESS_BACKGROUND_LOCATION. Sans elle, [current] rend null : on echoue de
 * maniere fermee plutot que d'afficher un trajet depuis nulle part.
 */
object LocationProvider {

    private const val TIMEOUT_MILLIS = 10_000L

    private val executor = Executors.newSingleThreadExecutor()

    fun hasForegroundPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun current(context: Context): LatLon? {
        if (!hasForegroundPermission(context)) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = pickProvider(manager) ?: return null

        val fresh = withTimeoutOrNull(TIMEOUT_MILLIS) { requestCurrent(manager, provider) }
        return fresh ?: lastKnown(manager, provider)
    }

    private fun pickProvider(manager: LocationManager): String? = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    ).firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    private suspend fun requestCurrent(manager: LocationManager, provider: String): LatLon? =
        suspendCancellableCoroutine { continuation ->
            // Le signal leve l'ambiguite entre les deux surcharges de getCurrentLocation,
            // et permet d'annuler le releve si la coroutine meurt.
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }

            val consumer = Consumer<Location?> { location ->
                if (continuation.isActive) {
                    continuation.resume(location?.let { LatLon(it.latitude, it.longitude) })
                }
            }

            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor, consumer)
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun lastKnown(manager: LocationManager, provider: String): LatLon? = try {
        manager.getLastKnownLocation(provider)?.let { LatLon(it.latitude, it.longitude) }
    } catch (e: SecurityException) {
        null
    }
}
