package com.sqp.percent98

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.BatteryManager
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt

// Renders and manages individual battery widget instances
class BatteryWidgetProvider : AppWidgetProvider() {

	// Updates Android-supplied widget IDs immediately and starts foreground monitoring
	override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
		// Updates every widget instance supplied by Android
		for (id in appWidgetIds) {
			// Draws the current state of the widget
			updateAppWidget(context, appWidgetManager, id)
		}

		// Starts foreground monitoring for future battery-state changes
		BatteryMonitorService.start(context)
	}

	// Removes settings that belong only to deleted widget instances
	override fun onDeleted(context: Context, appWidgetIds: IntArray) {
		// Gets the preferences that store widget-specific settings
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

		// Removes settings for every deleted widget instance
		prefs.edit {
			// Processes each deleted widget ID
			appWidgetIds.forEach { id ->
				// Removes the saved background color
				remove("bg_$id")

				// Removes the saved foreground color
				remove("fg_$id")

				// Removes the saved border color
				remove("bc_$id")

				// Removes the saved border width
				remove("bw_$id")

				// Removes the saved font size
				remove("font_$id")

				// Removes the saved widget shape
				remove("shape_$id")
			}
		}
	}

	// Stops foreground monitoring after Android removes the final widget
	override fun onDisabled(context: Context) {
		// Stops the battery-monitoring service
		BatteryMonitorService.stop(context)
	}

	companion object {

		// Identifies the preferences containing widget-specific settings
		const val PREFS = "battery_widget_prefs"

		// Identifies the preferences containing default user settings
		const val USER_PREFS = "battery_user_prefs"

		// Defines the width and height of the generated widget bitmap
		const val SIZE = 128

		// Draws one widget instance
		// When supplied by BatteryMonitorService, batteryLevel is the exact percentage from ACTION_BATTERY_CHANGED
		// When null, the current system capacity is queried for direct widget updates
		fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, batteryLevel: Int? = null) {
			// Gets the preferences containing widget-specific settings
			val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

			// Gets the preferences containing default user settings
			val userPrefs = context.getSharedPreferences(USER_PREFS, Context.MODE_PRIVATE)

			// Reads and parses the widget background color
			val bg =
				parseColor(prefs.getString("bg_$appWidgetId", userPrefs.getString("bg", "000000"))!!)

			// Reads and parses the widget text color
			val fg =
				parseColor(prefs.getString("fg_$appWidgetId", userPrefs.getString("fg", "EEEEEE"))!!)

			// Reads and parses the widget border color
			val bc =
				parseColor(prefs.getString("bc_$appWidgetId", userPrefs.getString("bc", "66965E"))!!)

			// Reads the widget border width
			val bw = prefs.getInt("bw_$appWidgetId", userPrefs.getInt("bw", 8))

			// Reads the widget text size
			val fontSize = prefs.getInt("font_$appWidgetId", userPrefs.getInt("font", 22))

			// Reads the widget shape and uses circle as the fallback shape
			val shape =
				prefs.getString("shape_$appWidgetId", userPrefs.getString("shape", "circle"))
					?: "circle"

			// Uses the supplied battery level or queries the current system level
			val level = batteryLevel ?: getBatteryLevel(context)

			// Creates the RemoteViews object for the widget layout
			val views = RemoteViews(context.packageName, R.layout.widget_battery)

			// Displays the battery percentage
			views.setTextViewText(R.id.battery_text, "$level%")

			// Applies the configured text color
			views.setTextColor(R.id.battery_text, fg)

			// Applies the configured text size
			views.setTextViewTextSize(R.id.battery_text, android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())

			// Generates and displays the widget background bitmap
			views.setImageViewBitmap(R.id.widget_background, createRoundedBitmap(bg = bg, stroke = bc, strokeWidth = bw, isCircle = shape == "circle", batteryLevel = level))

			// Creates the intent used to open the widget configuration screen
			val configIntent = Intent(context, ConfigActivity::class.java).apply {
				// Passes the widget ID to the configuration screen
				putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

				// Reuses an existing configuration activity when possible
				flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
			}

			// Creates the PendingIntent used to open the configuration screen
			val configPendingIntent =
				PendingIntent.getActivity(context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

			// Creates the intent used to refresh the foreground service
			val serviceIntent = Intent(context, BatteryMonitorService::class.java)

			// Creates the PendingIntent used to refresh the foreground service
			val refreshPendingIntent =
				PendingIntent.getForegroundService(context, appWidgetId + 10000, serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

			// Refreshes monitoring when the widget background is tapped
			views.setOnClickPendingIntent(R.id.widget_background, refreshPendingIntent)

			// Opens configuration when the battery text is tapped
			views.setOnClickPendingIntent(R.id.battery_text, configPendingIntent)

			// Publishes the updated widget views
			appWidgetManager.updateAppWidget(appWidgetId, views)
		}

		// Reads the current percentage directly
		// Retains the sticky battery intent as a compatibility fallback
		private fun getBatteryLevel(context: Context): Int {
			// Gets the system BatteryManager instance
			val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

			// Reads the battery capacity reported by BatteryManager
			val capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

			// Returns the direct capacity when it is valid
			if (capacity in 0..100) {
				return capacity
			}

			// Reads Android's sticky battery-state intent as a fallback
			val batteryIntent =
				context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

			// Reads the current battery level from the fallback intent
			val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1

			// Reads the battery scale from the fallback intent
			val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

			// Calculates and clamps the percentage when the fallback values are valid
			return if (level >= 0 && scale > 0) {
				(level * 100 / scale).coerceIn(0, 100)
			} else {
				// Returns an invalid value when Android does not provide usable values
				-1
			}
		}

		// Parses a six-digit RGB hexadecimal color value
		private fun parseColor(hex: String): Int {
			// Attempts to convert the hexadecimal value into an Android color
			return try {
				"#${hex.trim().removePrefix("#")}".toColorInt()
			} catch (_: Exception) {
				// Uses gray when the configured color cannot be parsed
				Color.GRAY
			}
		}

		// Draws the widget fill, border, and missing-charge border segment
		private fun createRoundedBitmap(bg: Int, stroke: Int, strokeWidth: Int, isCircle: Boolean, batteryLevel: Int): Bitmap {
			// Creates the bitmap used as the widget background
			val bmp = createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

			// Creates the canvas used to draw the widget
			val canvas = Canvas(bmp)

			// Creates an anti-aliased paint object
			val paint = Paint(Paint.ANTI_ALIAS_FLAG)

			// Converts the configured border width to a non-negative float
			val width = strokeWidth.coerceAtLeast(0).toFloat()

			// Keeps the border inside the bitmap bounds
			val inset = width / 2f + 1f

			// Defines the drawable widget bounds
			val rect = RectF(inset, inset, SIZE - inset, SIZE - inset)

			// Calculates the radius for circular and rounded widgets
			val radius = if (isCircle) {
				// Uses half the smallest dimension for a circle
				minOf(rect.width(), rect.height()) / 2f
			} else {
				// Limits the rounded-corner radius to 28 pixels
				28f.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
			}

			// Selects fill drawing mode
			paint.style = Paint.Style.FILL

			// Applies the configured background color
			paint.color = bg

			// Draws the filled widget shape
			canvas.drawRoundRect(rect, radius, radius, paint)

			// Skips border drawing when the configured width is zero
			if (width <= 0f) {
				return bmp
			}

			// Creates the path used for the widget outline
			val outline = Path()

			// Builds the outline for a circular widget
			if (isCircle) {
				// Adds a complete circular arc to the outline
				outline.addArc(rect, -90f, 360f)
			} else {
				// Gets the horizontal centre of the rounded rectangle
				val cx = rect.centerX()

				// Starts the outline at the top centre
				outline.moveTo(cx, rect.top)

				// Draws the top edge toward the upper-right corner
				outline.lineTo(rect.right - radius, rect.top)

				// Draws the upper-right rounded corner
				outline.quadTo(rect.right, rect.top, rect.right, rect.top + radius)

				// Draws the right edge toward the lower-right corner
				outline.lineTo(rect.right, rect.bottom - radius)

				// Draws the lower-right rounded corner
				outline.quadTo(rect.right, rect.bottom, rect.right - radius, rect.bottom)

				// Draws the bottom edge toward the lower-left corner
				outline.lineTo(rect.left + radius, rect.bottom)

				// Draws the lower-left rounded corner
				outline.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - radius)

				// Draws the left edge toward the upper-left corner
				outline.lineTo(rect.left, rect.top + radius)

				// Draws the upper-left rounded corner
				outline.quadTo(rect.left, rect.top, rect.left + radius, rect.top)

				// Draws the top edge back to the centre
				outline.lineTo(cx, rect.top)

				// Closes the outline path
				outline.close()
			}

			// Selects outline drawing mode
			paint.style = Paint.Style.STROKE

			// Applies the configured border width
			paint.strokeWidth = width

			// Uses flat ends for the border segments
			paint.strokeCap = Paint.Cap.BUTT

			// Applies the configured border color
			paint.color = stroke

			// Draws the complete widget outline
			canvas.drawPath(outline, paint)

			// Clamps the battery level to a valid percentage
			val level = batteryLevel.coerceIn(0, 100)

			// Calculates the fraction of the border representing missing charge
			val missingFraction = 1f - level / 100f

			// Draws the missing-charge segment when the battery is not full
			if (missingFraction > 0f) {
				// Measures the length of the widget outline
				val measure = PathMeasure(outline, false)

				// Creates the path for the missing-charge segment
				val missingPath = Path()

				// Extracts the missing-charge section from the outline
				measure.getSegment(0f, measure.length * missingFraction, missingPath, true)

				// Applies the background color to hide the missing-charge section
				paint.color = bg

				// Draws the missing-charge section over the border
				canvas.drawPath(missingPath, paint)
			}

			// Returns the completed widget bitmap
			return bmp
		}
	}
}