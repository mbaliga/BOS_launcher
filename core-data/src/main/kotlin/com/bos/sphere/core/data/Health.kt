package com.bos.sphere.core.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** Today's activity totals read from Health Connect. Null fields = no data recorded today. */
data class HealthToday(
    val steps: Long?,
    val floors: Double?,
    val elevationM: Double?,
    val distanceM: Double?,
    val activeKcal: Double?,
    val fetchedAtMillis: Long,
)

/** Pure display formatting for the numbers above (testable). */
object HealthFormat {
    fun steps(v: Long?): String = v?.toString() ?: "—"
    fun floors(v: Double?): String = v?.let { it.roundToInt().toString() } ?: "—"
    fun kcal(v: Double?): String = v?.let { "${it.roundToInt()} kcal" } ?: "—"

    fun elevation(m: Double?, imperial: Boolean): String = when {
        m == null -> "—"
        imperial -> "${(m * 3.28084).roundToInt()} ft"
        else -> "${m.roundToInt()} m"
    }

    fun distance(m: Double?, imperial: Boolean): String = when {
        m == null -> "—"
        imperial -> "%.1f mi".format(Locale.US, m / 1609.344)
        else -> "%.1f km".format(Locale.US, m / 1000.0)
    }

    /** Countries that use miles / feet day to day. */
    fun usesImperial(country: String): Boolean = country.uppercase() in setOf("US", "LR", "MM", "GB")
}

/**
 * Reads steps, floors climbed, elevation gained, distance and active calories for today from
 * Health Connect — where Fitbit, Samsung Health, Garmin, the Google Fit app and others write on
 * Android — merged by Health Connect's own source priority so nothing is double-counted. Needs the
 * read permissions in [PERMISSIONS], requested through Health Connect's own contract.
 */
class HealthSource(context: Context) {
    private val ctx = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _today = MutableStateFlow<HealthToday?>(null)
    val today: StateFlow<HealthToday?> = _today.asStateFlow()
    @Volatile private var inFlight = false

    /** HealthConnectClient.SDK_AVAILABLE, SDK_UNAVAILABLE or SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(ctx)
    fun available(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    /** True once every permission in [PERMISSIONS] is granted. */
    suspend fun hasAllPermissions(): Boolean = runCatching {
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    /** Reads today's totals if permission is granted; otherwise leaves [today] null. */
    fun refresh() {
        if (inFlight || !available()) return
        inFlight = true
        scope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(ctx)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(PERMISSIONS)) { _today.value = null; return@launch }
                val zone = ZoneId.systemDefault()
                val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
                val r = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            StepsRecord.COUNT_TOTAL,
                            FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL,
                            ElevationGainedRecord.ELEVATION_GAINED_TOTAL,
                            DistanceRecord.DISTANCE_TOTAL,
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        ),
                        timeRangeFilter = TimeRangeFilter.between(start, java.time.Instant.now()),
                    ),
                )
                _today.value = HealthToday(
                    steps = r[StepsRecord.COUNT_TOTAL],
                    floors = r[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL],
                    elevationM = r[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters,
                    distanceM = r[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                    activeKcal = r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                    fetchedAtMillis = System.currentTimeMillis(),
                )
            } catch (_: Exception) {
                // Health Connect busy / revoked: keep the last numbers.
            } finally {
                inFlight = false
            }
        }
    }

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(FloorsClimbedRecord::class),
            HealthPermission.getReadPermission(ElevationGainedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
    }
}
