package com.example.hellowear

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal data class RouteStop(val place: String, val outbound: LocalTime, val returning: LocalTime)
internal data class WeatherPoint(
    val place: String,
    val time: String,
    val temperatureF: Double,
    val feelsLikeF: Double,
    val rainChance: Int,
    val windMph: Double,
    val gustMph: Double
)
internal data class WeatherSnapshot(
    val date: String,
    val updatedAt: Long,
    val recommendation: String,
    val reason: String,
    val clothing: String,
    val outbound: List<WeatherPoint>,
    val returning: List<WeatherPoint>
)

internal object WeatherRepository {
    fun fetchToday(stops: List<RouteStop>): WeatherSnapshot {
        val today = LocalDate.now()
        val forecasts = stops.map { stop ->
            val coords = geocode(stop.place)
            stop to forecast(coords.first, coords.second)
        }
        val outbound = forecasts.map { (stop, json) -> sample(json, stop.place, today, stop.outbound) }
        val returning = forecasts.asReversed().map { (stop, json) -> sample(json, stop.place, today, stop.returning) }
        val rainCandidate = (outbound.map { it to false } + returning.map { it to true })
            .filter { it.first.rainChance > 0 }.maxByOrNull { it.first.rainChance }
        val all = outbound + returning
        val rainPoint = rainCandidate?.first
        val recommendation = if (rainPoint == null) "RIDE LOOKS OK" else "NO RIDE TODAY"
        val reason = if (rainPoint == null) {
            val windy = all.maxByOrNull { it.gustMph }!!
            "No rain chance at planned stops. Gusts reach ${windy.gustMph.toInt()} mph near ${windy.place}; check conditions again before leaving."
        } else {
            "${rainPoint.rainChance}% rain chance at ${rainPoint.place} at ${rainPoint.time} on the ${if (rainCandidate!!.second) "return" else "outbound"} leg. Any rain chance on either leg vetoes the ride."
        }
        val coldest = all.minByOrNull { it.feelsLikeF }!!
        val hottest = all.maxByOrNull { it.feelsLikeF }!!
        val clothing = when {
            coldest.feelsLikeF < 45 -> "Insulated, windproof riding layers, warm gloves and a neck covering."
            coldest.feelsLikeF < 60 -> "A warm base layer, windproof riding jacket and gloves."
            hottest.feelsLikeF >= 85 -> "Ventilated protective gear; hydrate and keep protective coverage."
            hottest.feelsLikeF >= 70 -> "Breathable protective jacket and riding pants."
            else -> "Protective riding gear with a light layer; it may feel cool at speed."
        }
        return WeatherSnapshot(today.toString(), System.currentTimeMillis(), recommendation, reason, clothing, outbound, returning)
    }

    fun encode(snapshot: WeatherSnapshot): String = JSONObject()
        .put("date", snapshot.date)
        .put("updatedAt", snapshot.updatedAt)
        .put("recommendation", snapshot.recommendation)
        .put("reason", snapshot.reason)
        .put("clothing", snapshot.clothing)
        .put("outbound", pointsToJson(snapshot.outbound))
        .put("returning", pointsToJson(snapshot.returning))
        .toString()

    fun decode(json: String): WeatherSnapshot {
        val obj = JSONObject(json)
        return WeatherSnapshot(
            obj.getString("date"), obj.getLong("updatedAt"), obj.getString("recommendation"),
            obj.getString("reason"), obj.getString("clothing"),
            pointsFromJson(obj.getJSONArray("outbound")), pointsFromJson(obj.getJSONArray("returning"))
        )
    }

    private fun pointsToJson(points: List<WeatherPoint>) = JSONArray().apply {
        points.forEach { p -> put(JSONObject().put("place", p.place).put("time", p.time).put("temperatureF", p.temperatureF)
            .put("feelsLikeF", p.feelsLikeF).put("rainChance", p.rainChance).put("windMph", p.windMph).put("gustMph", p.gustMph)) }
    }

    private fun pointsFromJson(array: JSONArray) = (0 until array.length()).map { i ->
        val p = array.getJSONObject(i)
        WeatherPoint(p.getString("place"), p.getString("time"), p.getDouble("temperatureF"), p.getDouble("feelsLikeF"),
            p.getInt("rainChance"), p.getDouble("windMph"), p.getDouble("gustMph"))
    }

    private fun geocode(place: String): Pair<Double, Double> {
        val encoded = URLEncoder.encode(place, "UTF-8")
        val json = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json")
        val hits = json.optJSONArray("results") ?: throw IllegalArgumentException("No location found for '$place'. Try a city and region.")
        if (hits.length() == 0) throw IllegalArgumentException("No location found for '$place'. Try a city and region.")
        val hit = hits.getJSONObject(0)
        return hit.getDouble("latitude") to hit.getDouble("longitude")
    }

    private fun forecast(lat: Double, lon: Double): JSONObject = getJson(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&hourly=temperature_2m,apparent_temperature,precipitation_probability,wind_speed_10m,wind_gusts_10m" +
            "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=1"
    )

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000; connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", "RideWeatherWearOS/1.0")
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Weather service returned HTTP $code")
            return JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun sample(json: JSONObject, place: String, date: LocalDate, time: LocalTime): WeatherPoint {
        val hourly = json.getJSONObject("hourly"); val times = hourly.getJSONArray("time")
        val prefix = "${date}T${time.hour.toString().padStart(2, '0')}:00"
        var idx = -1
        for (i in 0 until times.length()) if (times.getString(i).startsWith(prefix)) { idx = i; break }
        if (idx < 0) throw IllegalStateException("No hourly forecast for $place at ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}.")
        fun number(key: String) = hourly.getJSONArray(key).optDouble(idx, 0.0)
        return WeatherPoint(place, time.format(DateTimeFormatter.ofPattern("HH:mm")), number("temperature_2m"),
            number("apparent_temperature"), number("precipitation_probability").toInt(), number("wind_speed_10m"), number("wind_gusts_10m"))
    }
}
