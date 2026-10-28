package com.example.hellowear

import android.content.Context
import androidx.wear.tiles.TileService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.DateFormat
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import java.util.Date
import java.util.concurrent.TimeUnit

internal object DailyForecastScheduler {
    private const val UNIQUE_WORK = "ride_weather_daily_8am"

    fun ensureScheduled(context: Context) {
        enqueue(context, ExistingWorkPolicy.KEEP)
    }

    fun scheduleNext(context: Context) {
        enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now()
        var nextRun = now.withHour(8).withMinute(0).withSecond(0).withNano(0)
        if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
        val delay = Duration.between(now, nextRun).toMillis().coerceAtLeast(1)
        val request = OneTimeWorkRequestBuilder<DailyForecastWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }
}

class DailyForecastWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("ride_weather", Context.MODE_PRIVATE)
        val stops = try { readStops(prefs.getString("stops", null)) } catch (e: Exception) {
            prefs.edit().putString("daily_update_error", e.message ?: "Invalid route settings").apply()
            DailyForecastScheduler.scheduleNext(applicationContext)
            return Result.success()
        }
        if (stops.size < 2) {
            prefs.edit().putString("daily_update_error", "Set up a start and destination in Route settings.").apply()
            DailyForecastScheduler.scheduleNext(applicationContext)
            return Result.success()
        }

        return try {
            val snapshot = withContext(Dispatchers.IO) { WeatherRepository.fetchToday(stops) }
            val timeLabel = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.updatedAt))
            prefs.edit()
                .putString("today_snapshot", WeatherRepository.encode(snapshot))
                .putString("recommendation", snapshot.recommendation)
                .putString("updated_label", timeLabel)
                .remove("daily_update_error")
                .apply()
            TileService.getUpdater(applicationContext).requestUpdate(HelloWorldTileService::class.java)
            DailyForecastScheduler.scheduleNext(applicationContext)
            Result.success()
        } catch (e: Exception) {
            prefs.edit().putString("daily_update_error", e.message ?: "Could not update weather").apply()
            DailyForecastScheduler.scheduleNext(applicationContext)
            Result.success()
        }
    }

    private fun readStops(encoded: String?): List<RouteStop> {
        if (encoded == null) return emptyList()
        val array = JSONArray(encoded)
        val format = DateTimeFormatter.ofPattern("H:mm")
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            RouteStop(
                item.getString("place").trim(),
                LocalTime.parse(item.getString("out"), format),
                LocalTime.parse(item.getString("back"), format)
            )
        }
    }
}
