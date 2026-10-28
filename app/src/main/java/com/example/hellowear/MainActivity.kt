package com.example.hellowear

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.wear.tiles.TileService
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.text.DateFormat
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var statusCard: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var reasonText: TextView
    private lateinit var clothingText: TextView
    private lateinit var updatedText: TextView
    private lateinit var updateButton: Button
    private lateinit var charts: LinearLayout
    private lateinit var emptyText: TextView
    private var useCelsius = false
    private val snapshotListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "today_snapshot" && ::statusCard.isInitialized) {
            runOnUiThread {
                useCelsius = getSharedPreferences("ride_weather", MODE_PRIVATE).getBoolean("use_celsius", false)
                renderSnapshot(loadTodaySnapshot())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root = createRoot()
        scroll.addView(root)
        setContentView(scroll)
        DailyForecastScheduler.ensureScheduled(applicationContext)

        addText("TODAY · ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d"))}", 12f, 0xFF9EAAA5.toInt(), bold = true, top = 0, bottom = 3)
        addText("Ride Weather", 25f, Color.WHITE, bold = true, top = 0, bottom = 9)
        statusIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ride status"
            visibility = View.GONE
        }
        root.addView(statusIcon, LinearLayout.LayoutParams(dp(142), dp(142)).apply { bottomMargin = dp(4) })
        statusCard = TextView(this).apply {
            textSize = 18f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(statusCard, cardLayout())
        reasonText = addText("", 13f, 0xFFE5EAE7.toInt(), top = 4, bottom = 5)
        updatedText = addText("Not updated yet · daily refresh around 8 AM", 11f, 0xFF9EAAA5.toInt(), top = 1, bottom = 7)
        updateButton = Button(this).apply {
            text = "Update now"
            setOnClickListener { updateNow() }
        }
        styleButton(updateButton, primary = true)
        root.addView(updateButton, fullWidth())
        emptyText = addText("Set up your route in Settings, then update today’s forecast.", 12f, 0xFFBBC4C0.toInt(), top = 4, bottom = 8)
        clothingText = TextView(this).apply {
            textSize = 12f; setTextColor(0xFFE7EEEA.toInt()); setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(0xFF18221F.toInt(), 14)
        }
        root.addView(clothingText, cardLayout())
        charts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(charts, fullWidth())
        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(10)) }
        root.addView(spacer)
        val settings = Button(this).apply {
            text = "Route settings"
            setOnClickListener { startActivity(android.content.Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        styleButton(settings)
        root.addView(settings, fullWidth())
        useCelsius = getSharedPreferences("ride_weather", MODE_PRIVATE).getBoolean("use_celsius", false)
        renderSnapshot(loadTodaySnapshot())
    }

    override fun onResume() {
        super.onResume()
        if (::statusCard.isInitialized) {
            getSharedPreferences("ride_weather", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(snapshotListener)
            useCelsius = getSharedPreferences("ride_weather", MODE_PRIVATE).getBoolean("use_celsius", false)
            renderSnapshot(loadTodaySnapshot())
        }
    }

    override fun onPause() {
        getSharedPreferences("ride_weather", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(snapshotListener)
        super.onPause()
    }

    private fun createRoot() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val density = resources.displayMetrics.density
        val widthDp = resources.displayMetrics.widthPixels / density
        val round = resources.configuration.isScreenRound
        val horizontal = if (round) (widthDp * 0.13f).toInt() else 18
        val vertical = if (round) (widthDp * 0.18f).toInt() else 18
        setPadding(dp(horizontal), dp(vertical), dp(horizontal), dp(horizontal))
        setBackgroundColor(Color.BLACK)
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun addText(value: String, size: Float, color: Int, bold: Boolean = false, top: Int = 4, bottom: Int = 6): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding(0, dp(top), 0, dp(bottom))
        root.addView(this, fullWidth())
    }

    private fun updateNow() {
        val stops = try { readStops() } catch (e: Exception) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = e.message ?: "Add a start and destination in Route settings."
            return
        }
        updateButton.isEnabled = false
        updateButton.text = "Updating…"
        emptyText.visibility = View.GONE
        worker.execute {
            try {
                val snapshot = WeatherRepository.fetchToday(stops)
                val timeLabel = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.updatedAt))
                getSharedPreferences("ride_weather", MODE_PRIVATE).edit()
                    .putString("today_snapshot", WeatherRepository.encode(snapshot))
                    .putString("recommendation", snapshot.recommendation)
                    .putString("updated_label", timeLabel)
                    .apply()
                runOnUiThread {
                    updateButton.isEnabled = true; updateButton.text = "Update now"
                    renderSnapshot(snapshot)
                    TileService.getUpdater(this).requestUpdate(HelloWorldTileService::class.java)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    updateButton.isEnabled = true; updateButton.text = "Try again"
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "Couldn’t update: ${e.message ?: "Check the connection and place names."}"
                }
            }
        }
    }

    private fun readStops(): List<RouteStop> {
        val json = getSharedPreferences("ride_weather", MODE_PRIVATE).getString("stops", null)
            ?: throw IllegalArgumentException("Set up your start and destination in Route settings first.")
        val array = JSONArray(json)
        if (array.length() < 2) throw IllegalArgumentException("Add a start and destination in Route settings first.")
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val place = item.getString("place").trim()
            if (place.isBlank()) throw IllegalArgumentException("A route stop is missing its place name.")
            val timeFormat = DateTimeFormatter.ofPattern("H:mm")
            RouteStop(place, LocalTime.parse(item.getString("out"), timeFormat), LocalTime.parse(item.getString("back"), timeFormat))
        }
    }

    private fun loadTodaySnapshot(): WeatherSnapshot? {
        val prefs = getSharedPreferences("ride_weather", MODE_PRIVATE)
        val json = prefs.getString("today_snapshot", null) ?: return null
        return try {
            WeatherRepository.decode(json).takeIf { it.date == LocalDate.now().toString() }
        } catch (_: Exception) { null }
    }

    private fun renderSnapshot(snapshot: WeatherSnapshot?) {
        charts.removeAllViews()
        if (snapshot == null) {
            statusIcon.visibility = View.GONE
            statusCard.text = "○ NO FORECAST"
            statusCard.background = rounded(0xFF242A2D.toInt(), 18)
            statusCard.setTextColor(0xFFE0E7E3.toInt())
            reasonText.text = "Tap Update now to check the planned round trip."
            updatedText.text = "Not updated yet · daily refresh around 8 AM"
            clothingText.visibility = View.GONE
            emptyText.visibility = if (readStopsOrEmpty()) View.GONE else View.VISIBLE
            if (emptyText.visibility == View.VISIBLE) emptyText.text = "Set up your route in Settings, then update today’s forecast."
            return
        }
        val unsafe = snapshot.recommendation == "NO RIDE TODAY"
        statusIcon.setImageResource(if (unsafe) R.drawable.no_ride else R.drawable.ride_ok)
        statusIcon.contentDescription = if (unsafe) "No riding recommended today" else "Ride conditions look okay today"
        statusIcon.visibility = View.VISIBLE
        statusCard.text = snapshot.recommendation
        statusCard.background = rounded(if (unsafe) 0xFF4A201E.toInt() else 0xFF17362D.toInt(), 18)
        statusCard.setTextColor(if (unsafe) 0xFFFFB4AB.toInt() else 0xFF9AF0CE.toInt())
        reasonText.text = snapshot.reason
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.updatedAt))
        updatedText.text = "Updated $time · daily refresh around 8 AM"
        clothingText.visibility = View.VISIBLE
        clothingText.text = "WHAT TO WEAR\n${snapshot.clothing}"
        addRouteCharts(snapshot)
        emptyText.visibility = View.GONE
    }

    private fun readStopsOrEmpty(): Boolean = try { readStops(); true } catch (_: Exception) { false }

    private fun addRouteCharts(snapshot: WeatherSnapshot) {
        val first = snapshot.outbound.firstOrNull()?.place ?: "Start"
        val last = snapshot.outbound.lastOrNull()?.place ?: "Destination"
        addSectionTitle("OUTBOUND", "$first  →  $last")
        addChart(snapshot.outbound)
        addSectionTitle("RETURN", "$last  →  $first")
        addChart(snapshot.returning)
    }

    private fun addSectionTitle(title: String, subtitle: String) {
        charts.addView(TextView(this).apply {
            text = "$title\n$subtitle"; textSize = 13f; setTextColor(0xFFEEF5F1.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(15), 0, dp(5))
        }, fullWidth())
    }

    private fun addChart(points: List<WeatherPoint>) {
        charts.addView(WeatherChartView(this, points, useCelsius), LinearLayout.LayoutParams(-1, dp(264)))
    }

    private fun cardLayout() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) }
    private fun fullWidth() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun styleButton(button: Button, primary: Boolean = false) {
        button.background = rounded(if (primary) 0xFFB7F2D9.toInt() else 0xFF263A35.toInt(), 18)
        button.setTextColor(if (primary) 0xFF08251C.toInt() else 0xFFE4FFF3.toInt())
        button.textSize = 13f
        button.isAllCaps = false
        button.minHeight = dp(46)
    }

    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
