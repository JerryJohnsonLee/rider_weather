package com.example.hellowear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.wear.tiles.TileService
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class EditableStop(
    val container: LinearLayout,
    val badge: TextView,
    val title: TextView,
    val place: EditText,
    val outbound: EditText,
    val returning: EditText
)

class SettingsActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var stopList: LinearLayout
    private lateinit var celsiusButton: Button
    private lateinit var fahrenheitButton: Button
    private val stops = mutableListOf<EditableStop>()
    private var useCelsius = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        val prefs = getSharedPreferences("ride_weather", MODE_PRIVATE)
        useCelsius = prefs.getBoolean("use_celsius", false)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val density = resources.displayMetrics.density
        val widthDp = resources.displayMetrics.widthPixels / density
        val round = resources.configuration.isScreenRound
        val horizontal = if (round) (widthDp * 0.13f).toInt() else 18
        val vertical = if (round) (widthDp * 0.17f).toInt() else 18
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((horizontal * density).toInt(), (vertical * density).toInt(), (horizontal * density).toInt(), (horizontal * density).toInt())
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(root)
        setContentView(scroll)

        addText("TODAY’S ROUTE", 10f, 0xFF83D6BE.toInt(), bold = true, bottom = 2)
        addText("Stops & times", 23f, Color.WHITE, bold = true, bottom = 3)
        addText("Add places in riding order. Enter local times for both legs.", 12f, 0xFFB9C4BF.toInt(), bottom = 9)
        addSectionHeading("ROUTE STOPS", "Times are local to each place")
        stopList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(stopList)
        val saved = prefs.getString("stops", null)
        if (saved != null) {
            try {
                val array = JSONArray(saved)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    addStop(item.getString("place"), item.getString("out"), item.getString("back"), i > 0)
                }
            } catch (_: Exception) { stops.clear(); stopList.removeAllViews() }
        }
        if (stops.isEmpty()) {
            addStop("Start (home)", "08:00", "17:00", false)
            addStop("Destination", "10:00", "15:00", true)
        }
        val addStopButton = Button(this).apply {
            text = "+  Add waypoint"
            setOnClickListener { addStop("", "09:00", "16:00", true) }
        }
        styleButton(addStopButton, outlined = true)
        root.addView(addStopButton, fullWidth().apply { bottomMargin = dp(6) })
        addTemperatureUnits()
        actionButton("Save route") { saveSettings() }
    }

    private fun addTemperatureUnits() {
        addSectionHeading("TEMPERATURE UNIT", "Wind speed is shown in mph")
        val unitChoices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = panel(0xFF151C19.toInt(), 18)
        }
        celsiusButton = Button(this).apply {
            text = "°C"
            setOnClickListener { useCelsius = true; updateUnitToggle() }
        }
        fahrenheitButton = Button(this).apply {
            text = "°F"
            setOnClickListener { useCelsius = false; updateUnitToggle() }
        }
        unitChoices.addView(celsiusButton, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(3) })
        unitChoices.addView(fahrenheitButton, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(3) })
        root.addView(unitChoices, fullWidth().apply { bottomMargin = dp(12) })
        updateUnitToggle()
    }

    private fun updateUnitToggle() {
        styleUnitButton(celsiusButton, selected = useCelsius)
        styleUnitButton(fahrenheitButton, selected = !useCelsius)
    }

    private fun styleUnitButton(button: Button, selected: Boolean) {
        button.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(if (selected) 0xFFB7F2D9.toInt() else 0xFF263A35.toInt())
            cornerRadius = dp(18).toFloat()
        }
        button.setTextColor(if (selected) 0xFF08251C.toInt() else 0xFFE4FFF3.toInt())
        button.textSize = 14f
        button.isAllCaps = false
        button.minHeight = dp(46)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun addText(value: String, size: Float, color: Int, bold: Boolean = false, bottom: Int = 6) {
        root.addView(TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(bottom))
        }, fullWidth())
    }

    private fun editField(hint: String, initial: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(0xFF77837D.toInt())
        setTextColor(Color.WHITE)
        textSize = 12f
        setSingleLine(true)
        setText(initial)
        imeOptions = EditorInfo.IME_ACTION_NEXT
        minHeight = dp(40)
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = panel(0xFF101714.toInt(), 12, 0xFF35433D.toInt())
    }

    private fun addStop(placeName: String, outTime: String, backTime: String, removable: Boolean) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(10))
            background = panel(0xFF151C19.toInt(), 18, 0xFF26332D.toInt())
        }
        val title = TextView(this).apply {
            textSize = 12f; setTextColor(0xFFEAF2EE.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        val badge = TextView(this).apply {
            textSize = 10f; setTextColor(0xFF08251C.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER; background = panel(0xFF83D6BE.toInt(), 30)
        }
        val role = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        role.addView(title, LinearLayout.LayoutParams(-1, -2))
        val removeButton = if (removable) Button(this).apply {
            text = "×"
            contentDescription = "Remove stop"
            textSize = 18f; isAllCaps = false; gravity = Gravity.CENTER
            minHeight = dp(32); minimumHeight = dp(32); minWidth = dp(32); minimumWidth = dp(32)
            setPadding(0, 0, 0, 0)
            setTextColor(0xFFFFB4AB.toInt())
            background = panel(0xFF35211F.toInt(), 16)
        } else null
        header.addView(badge, LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(8) })
        header.addView(role, LinearLayout.LayoutParams(0, -2, 1f))
        if (removeButton != null) header.addView(removeButton)
        card.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val placeLabel = fieldLabel("PLACE")
        val place = editField("City, state or region", placeName)
        card.addView(placeLabel, fullWidth())
        card.addView(place, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val outboundColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val returnColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val outboundLabel = fieldLabel("OUTBOUND")
        val returnLabel = fieldLabel("RETURN")
        val outbound = editField("08:00", outTime)
        val returning = editField("17:00", backTime)
        outboundColumn.addView(outboundLabel); outboundColumn.addView(outbound)
        returnColumn.addView(returnLabel); returnColumn.addView(returning)
        timeRow.addView(outboundColumn, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        timeRow.addView(returnColumn, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        card.addView(timeRow)

        val item = EditableStop(card, badge, title, place, outbound, returning)
        stops += item
        stopList.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        removeButton?.setOnClickListener { stopList.removeView(card); stops.remove(item); refreshStopTitles() }
        refreshStopTitles()
    }

    private fun actionButton(label: String, action: () -> Unit) {
        val primary = label == "Save route"
        root.addView(Button(this).apply {
            text = label
            styleButton(this, primary = primary)
            setOnClickListener { action() }
        })
    }

    private fun styleButton(button: Button, primary: Boolean = false, danger: Boolean = false) {
        val backgroundColor = when { primary -> 0xFFB7F2D9.toInt(); danger -> 0xFF4B2928.toInt(); else -> 0xFF263A35.toInt() }
        val foregroundColor = if (primary) 0xFF08251C.toInt() else if (danger) 0xFFFFDAD5.toInt() else 0xFFE4FFF3.toInt()
        button.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(backgroundColor); cornerRadius = dp(18).toFloat()
        }
        button.setTextColor(foregroundColor)
        button.textSize = 13f
        button.isAllCaps = false
        button.minHeight = dp(46)
    }

    private fun styleButton(button: Button, outlined: Boolean) {
        button.background = panel(0xFF101714.toInt(), 16, 0xFF52796C.toInt())
        button.setTextColor(0xFFB7F2D9.toInt())
        button.textSize = 13f
        button.isAllCaps = false
        button.minHeight = dp(44)
    }

    private fun addSectionHeading(title: String, subtitle: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        row.addView(TextView(this).apply {
            text = title; textSize = 10f; setTextColor(0xFF9FADA6.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        })
        row.addView(TextView(this).apply { text = subtitle; textSize = 10f; setTextColor(0xFF718078.toInt()) })
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
    }

    private fun fieldLabel(value: String) = TextView(this).apply {
        text = value; textSize = 9f; setTextColor(0xFF9FADA6.toInt())
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        setPadding(dp(2), 0, 0, dp(3))
    }

    private fun refreshStopTitles() {
        stops.forEachIndexed { index, stop ->
            stop.badge.text = (index + 1).toString().padStart(2, '0')
            stop.title.text = when {
                index == 0 -> "START"
                index == stops.lastIndex -> "DESTINATION"
                else -> "WAYPOINT ${index}"
            }
        }
    }

    private fun panel(color: Int, radius: Int, stroke: Int? = null) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun fullWidth() = LinearLayout.LayoutParams(-1, -2)

    private fun saveSettings() {
        if (stops.size < 2) { addText("Add a start and destination first.", 13f, 0xFFFF8A80.toInt()); return }
        val array = JSONArray()
        for ((index, stop) in stops.withIndex()) {
            val place = stop.place.text.toString().trim()
            if (place.isBlank()) { addText("Enter a place for stop ${index + 1}.", 13f, 0xFFFF8A80.toInt()); return }
            try {
                LocalTime.parse(stop.outbound.text.toString().trim(), DateTimeFormatter.ofPattern("H:mm"))
                LocalTime.parse(stop.returning.text.toString().trim(), DateTimeFormatter.ofPattern("H:mm"))
            } catch (_: Exception) { addText("Enter times as HH:MM, for example 08:30.", 13f, 0xFFFF8A80.toInt()); return }
            array.put(JSONObject().put("place", place).put("out", stop.outbound.text.toString().trim()).put("back", stop.returning.text.toString().trim()))
        }
        getSharedPreferences("ride_weather", MODE_PRIVATE).edit()
            .putString("stops", array.toString())
            .putBoolean("use_celsius", useCelsius)
            .putString("recommendation", "NO FORECAST")
            .putString("updated_label", "Not updated")
            .remove("today_snapshot")
            .apply()
        DailyForecastScheduler.ensureScheduled(applicationContext)
        TileService.getUpdater(this).requestUpdate(HelloWorldTileService::class.java)
        finish()
    }
}
