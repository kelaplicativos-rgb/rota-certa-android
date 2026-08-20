package br.com.mapeiaia.rotacerta

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rastreador leve e independente da leitura dos cards.
 *
 * Usa GPS em alta frequência quando existem alertas ativos e combina o rumo do
 * deslocamento com a bússola. O rumo do GPS tem prioridade enquanto o veículo
 * está em movimento, pois sofre menos interferência magnética dentro do carro.
 */
class PreciseNavigationTracker(context: Context) : LocationListener, SensorEventListener {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val started = AtomicBoolean(false)

    @Volatile
    private var latestFix: PreciseNavigationFix? = null

    @Volatile
    private var magneticHeadingDegrees: Double? = null

    @Volatile
    private var compassAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE

    private var lastAcceptedLocation: Location? = null
    private var smoothedHeadingDegrees: Double? = null
    private val rotationMatrix = FloatArray(9)
    private val adjustedRotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun currentFix(nowMillis: Long = System.currentTimeMillis()): PreciseNavigationFix? {
        val fix = latestFix ?: return null
        return fix.takeIf { nowMillis - it.timestampMillis in 0L..MAX_FIX_AGE_MILLIS }
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!started.compareAndSet(false, true)) return true
        if (!hasLocationPermission()) {
            started.set(false)
            return false
        }

        val gpsRegistered = runCatching {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_MIN_TIME_MILLIS,
                    GPS_MIN_DISTANCE_METERS,
                    this,
                    Looper.getMainLooper(),
                )
                true
            } else false
        }.getOrDefault(false)

        val networkRegistered = runCatching {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    NETWORK_MIN_TIME_MILLIS,
                    NETWORK_MIN_DISTANCE_METERS,
                    this,
                    Looper.getMainLooper(),
                )
                true
            } else false
        }.getOrDefault(false)

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        bootstrapLastKnownLocation()
        val active = gpsRegistered || networkRegistered || latestFix != null
        if (!active) {
            runCatching { sensorManager.unregisterListener(this) }
            started.set(false)
        }
        return active
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { locationManager.removeUpdates(this) }
        runCatching { sensorManager.unregisterListener(this) }
    }

    override fun onLocationChanged(location: Location) {
        if (!started.get()) return
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        if (!location.hasAccuracy() || location.accuracy <= 0f || location.accuracy > ABSOLUTE_MAX_ACCURACY_METERS) return

        val previous = lastAcceptedLocation
        val movementBearing = previous
            ?.takeIf { location.time > it.time && it.distanceTo(location) >= MIN_DISTANCE_FOR_MOVEMENT_BEARING_METERS }
            ?.bearingTo(location)
            ?.toDouble()
            ?.let(GeoDistance::normalizeDegrees)

        val gpsBearing = location
            .takeIf { it.hasBearing() && it.speed >= MIN_SPEED_FOR_GPS_BEARING_MPS }
            ?.bearing
            ?.toDouble()
            ?.let(GeoDistance::normalizeDegrees)

        val compassTrueHeading = magneticHeadingDegrees
            ?.takeIf { compassAccuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM }
            ?.let { magnetic ->
                val declination = GeomagneticField(
                    location.latitude.toFloat(),
                    location.longitude.toFloat(),
                    location.altitude.toFloat(),
                    location.time,
                ).declination.toDouble()
                GeoDistance.normalizeDegrees(magnetic + declination)
            }

        val headingCandidate = when {
            gpsBearing != null && compassTrueHeading != null -> circularBlend(gpsBearing, compassTrueHeading, GPS_HEADING_WEIGHT)
            gpsBearing != null -> gpsBearing
            movementBearing != null && location.speed >= MIN_SPEED_FOR_MOVEMENT_HEADING_MPS -> movementBearing
            compassTrueHeading != null -> compassTrueHeading
            else -> null
        }
        val headingSource = when {
            gpsBearing != null && compassTrueHeading != null -> NavigationHeadingSource.GpsAndCompass
            gpsBearing != null -> NavigationHeadingSource.GpsBearing
            movementBearing != null && location.speed >= MIN_SPEED_FOR_MOVEMENT_HEADING_MPS -> NavigationHeadingSource.Movement
            compassTrueHeading != null -> NavigationHeadingSource.Compass
            else -> NavigationHeadingSource.Unavailable
        }
        val smoothed = headingCandidate?.let(::smoothHeading)

        latestFix = PreciseNavigationFix(
            coordinate = Coordinate(location.latitude, location.longitude),
            accuracyMeters = location.accuracy.toDouble(),
            speedMetersPerSecond = location.speed.takeIf { location.hasSpeed() }?.toDouble() ?: 0.0,
            headingDegrees = smoothed,
            headingSource = headingSource,
            timestampMillis = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            provider = location.provider.orEmpty(),
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
        )
        lastAcceptedLocation = Location(location)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started.get() || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return
        compassAccuracy = event.accuracy
        runCatching {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val matrix = remapForDisplayRotation(rotationMatrix)
            SensorManager.getOrientation(matrix, orientation)
            val azimuth = Math.toDegrees(orientation[0].toDouble())
            magneticHeadingDegrees = GeoDistance.normalizeDegrees(azimuth)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) compassAccuracy = accuracy
    }

    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    @Suppress("DEPRECATION")
    private fun remapForDisplayRotation(source: FloatArray): FloatArray {
        val axes = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> return source
        }
        return if (SensorManager.remapCoordinateSystem(source, axes.first, axes.second, adjustedRotationMatrix)) {
            adjustedRotationMatrix
        } else {
            source
        }
    }

    @SuppressLint("MissingPermission")
    private fun bootstrapLastKnownLocation() {
        if (!hasLocationPermission()) return
        val now = System.currentTimeMillis()
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { location ->
                location.hasAccuracy() &&
                    location.accuracy <= BOOTSTRAP_MAX_ACCURACY_METERS &&
                    now - location.time in 0L..BOOTSTRAP_MAX_AGE_MILLIS
            }
            .minWithOrNull(compareBy<Location> { it.accuracy }.thenByDescending { it.time })
            ?.let(::onLocationChanged)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun smoothHeading(nextDegrees: Double): Double {
        val previous = smoothedHeadingDegrees
        if (previous == null) {
            smoothedHeadingDegrees = nextDegrees
            return nextDegrees
        }
        val result = circularBlend(previous, nextDegrees, HEADING_SMOOTHING_NEW_WEIGHT)
        smoothedHeadingDegrees = result
        return result
    }

    private fun circularBlend(firstDegrees: Double, secondDegrees: Double, firstWeight: Double): Double {
        val secondWeight = 1.0 - firstWeight
        val firstRadians = Math.toRadians(firstDegrees)
        val secondRadians = Math.toRadians(secondDegrees)
        val x = cos(firstRadians) * firstWeight + cos(secondRadians) * secondWeight
        val y = sin(firstRadians) * firstWeight + sin(secondRadians) * secondWeight
        if (abs(x) < 1e-9 && abs(y) < 1e-9) return firstDegrees
        return GeoDistance.normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    private companion object {
        const val GPS_MIN_TIME_MILLIS = 250L
        const val NETWORK_MIN_TIME_MILLIS = 750L
        const val GPS_MIN_DISTANCE_METERS = 0f
        const val NETWORK_MIN_DISTANCE_METERS = 1f
        const val MAX_FIX_AGE_MILLIS = 5_000L
        const val ABSOLUTE_MAX_ACCURACY_METERS = 80f
        const val BOOTSTRAP_MAX_ACCURACY_METERS = 35f
        const val BOOTSTRAP_MAX_AGE_MILLIS = 10_000L
        const val MIN_SPEED_FOR_GPS_BEARING_MPS = 2.2f
        const val MIN_SPEED_FOR_MOVEMENT_HEADING_MPS = 1.2
        const val MIN_DISTANCE_FOR_MOVEMENT_BEARING_METERS = 3f
        const val GPS_HEADING_WEIGHT = 0.82
        const val HEADING_SMOOTHING_NEW_WEIGHT = 0.38
    }
}

data class PreciseNavigationFix(
    val coordinate: Coordinate,
    val accuracyMeters: Double,
    val speedMetersPerSecond: Double,
    val headingDegrees: Double?,
    val headingSource: NavigationHeadingSource,
    val timestampMillis: Long,
    val provider: String,
    val altitudeMeters: Double? = null,
) {
    val speedKilometersPerHour: Double
        get() = speedMetersPerSecond * 3.6
}

enum class NavigationHeadingSource {
    GpsAndCompass,
    GpsBearing,
    Movement,
    Compass,
    Unavailable,
}
