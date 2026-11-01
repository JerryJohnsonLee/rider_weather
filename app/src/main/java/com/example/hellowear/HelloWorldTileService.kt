package com.example.hellowear

import android.content.ComponentName
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.layout.androidImageResource
import androidx.wear.protolayout.layout.imageResource
import androidx.wear.protolayout.material3.avatarImage
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography.BODY_LARGE
import androidx.wear.protolayout.material3.Typography.LABEL_SMALL
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class HelloWorldTileService : Material3TileService() {
    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val prefs = applicationContext.getSharedPreferences("ride_weather", MODE_PRIVATE)
        val recommendation = prefs.getString("recommendation", "NO FORECAST") ?: "NO FORECAST"
        val snapshot = prefs.getString("today_snapshot", null)?.let {
            try { WeatherRepository.decode(it) } catch (_: Exception) { null }
        }
        val updateLine = if (snapshot == null) {
            "Not updated"
        } else {
            val updated = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.updatedAt))
            val isOutdated = Instant.ofEpochMilli(snapshot.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate() != LocalDate.now()
            if (isOutdated) "Outdated · $updated" else "Updated $updated"
        }
        val icon = when {
            recommendation.startsWith("NO RIDE") -> R.drawable.no_ride
            recommendation.startsWith("RIDE LOOKS") -> R.drawable.ride_ok
            else -> R.drawable.ic_tile
        }
        return Tile.Builder()
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    primaryLayout(
                        mainSlot = {
                            textButton(
                                width = expand(),
                                height = expand(),
                                onClick = clickable(action = launchAction(ComponentName(applicationContext, MainActivity::class.java))),
                                labelContent = {
                                    column(
                                        avatarImage(
                                            resource = imageResource(androidImageResource(icon)),
                                            protoLayoutResourceId = "ride_status_icon",
                                            width = dp(104f),
                                            height = dp(104f)
                                        ),
                                        text(recommendation.layoutString, typography = BODY_LARGE),
                                        text(updateLine.layoutString, typography = LABEL_SMALL),
                                        width = expand(),
                                        height = expand()
                                    )
                                }
                            )
                        }
                    )
                )
            )
            .build()
    }
}
