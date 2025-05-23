package de.westnordost.streetcomplete.util.location

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.LocationManager.GPS_PROVIDER
import android.location.LocationManager.NETWORK_PROVIDER
import android.os.CancellationSignal
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer

/** Convenience wrapper around the location manager with easier API, making use of both the GPS
 *  and Network provider */
class FineLocationManager(context: Context, locationUpdateCallback: (Location) -> Unit) {
    private val locationManager = context.getSystemService<LocationManager>()!!
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val currentLocationConsumer = Consumer<Location?> {
        if (it != null) {
            if (!networkCancellationSignal.isCanceled && !gpsCancellationSignal.isCanceled) {
                locationUpdateCallback(it)
            }
        }
    }
    private var gpsCancellationSignal = CancellationSignal()
    private var networkCancellationSignal = CancellationSignal()
    private var lastLocation: Location? = null

    private val deviceHasGPS: Boolean get() = locationManager.allProviders.contains(GPS_PROVIDER)
    private val deviceHasNetworkLocationProvider: Boolean get() = locationManager.allProviders.contains(NETWORK_PROVIDER)

    private val locationListener = LocationListenerCompat { location ->
        if (location.isBetterThan(lastLocation)) {
            lastLocation = location
            locationUpdateCallback(location)
        }
    }

    // Both signals are refreshed regardless of whether the device has both providers, because
    // they are both canceled in removeUpdates and both checked in the locationListener
    private fun refreshCancellationSignals() {
        if (gpsCancellationSignal.isCanceled) {
            gpsCancellationSignal = CancellationSignal()
        }
        if (networkCancellationSignal.isCanceled) {
            networkCancellationSignal = CancellationSignal()
        }
    }

    @RequiresPermission(ACCESS_FINE_LOCATION)
    fun requestUpdates(minGpsTime: Long, minNetworkTime: Long, minDistance: Float) {
        if (deviceHasGPS) {
            locationManager.requestLocationUpdates(GPS_PROVIDER, minGpsTime, minDistance, locationListener, Looper.getMainLooper())
        }
        if (deviceHasNetworkLocationProvider) {
            locationManager.requestLocationUpdates(NETWORK_PROVIDER, minNetworkTime, minDistance, locationListener, Looper.getMainLooper())
        }
    }

    @RequiresPermission(ACCESS_FINE_LOCATION)
    @Synchronized fun getCurrentLocation() {
        refreshCancellationSignals()
        if (deviceHasGPS) {
            LocationManagerCompat.getCurrentLocation(
                locationManager, GPS_PROVIDER, gpsCancellationSignal, mainExecutor, currentLocationConsumer
            )
        }
        if (deviceHasNetworkLocationProvider) {
            LocationManagerCompat.getCurrentLocation(
                locationManager, NETWORK_PROVIDER, networkCancellationSignal, mainExecutor, currentLocationConsumer
            )
        }
    }

    @Synchronized fun removeUpdates() {
        locationManager.removeUpdates(locationListener)
        gpsCancellationSignal.cancel()
        networkCancellationSignal.cancel()
    }
}
