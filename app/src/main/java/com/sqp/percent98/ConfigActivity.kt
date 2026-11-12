package com.sqp.percent98

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.chimbori.colorpicker.ColorPickerView
import com.chimbori.colorpicker.builder.ColorPickerDialogBuilder
import java.util.Locale

// Configures one widget instance
// This activity uses a transparent full-screen window containing a centered configuration panel
// The full-screen overlay receives taps outside the panel and treats them as cancellation
class ConfigActivity : Activity() {

	// Stores the ID of the widget being configured
	private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

	// Becomes true only after the user presses Save
	// Prevents lifecycle cancellation logic from treating a successful save as an abandoned configuration session
	private var settingsWereSaved = false

	// Handles predictive Back navigation on Android 13 and newer
	private val backCallback = OnBackInvokedCallback { cancelConfiguration() }

	// Creates and initializes the configuration screen
	override fun onCreate(savedInstanceState: Bundle?) {
		// Performs the base activity initialization
		super.onCreate(savedInstanceState)

		// Loads the configuration layout
		setContentView(R.layout.activity_config)

		// Restarts battery monitoring when the restore button is pressed
		findViewById<Button>(R.id.btn_restore_notification).setOnClickListener {
			BatteryMonitorService.start(this)
		}

		// Forces the transparent activity window to fill the display
		// Ensures config_overlay receives every tap outside the visible settings panel
		window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

		// Makes the activity window background transparent
		window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

		// Registers the predictive Back callback on Android 13 and newer
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
		}

		// Gets the application information text view
		val appInfo = findViewById<TextView>(R.id.app_info)

		// Gets the application name
		val appName = getString(R.string.app_name)

		// Gets the installed application version name
		val versionName = packageManager.getPackageInfo(packageName, 0).versionName

		// Displays the application name and version
		appInfo.text = getString(R.string.app_version, appName, versionName)

		// Reads the widget ID passed by Android
		appWidgetId =
			intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

		// Closes the activity when Android did not provide a valid widget ID
		if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			finishAndRemoveTask()
			return
		}

		// Keeps the result as cancellation until Save is pressed
		// Tapping outside, pressing Back, pressing Home, or switching apps does not write configuration values
		setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

		// Handles taps outside the visible configuration panel
		findViewById<View>(R.id.config_overlay).setOnClickListener { cancelConfiguration() }

		// Requests notification permission when required on Android 13 and newer
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 98)
		}

		// Gets the widget manager
		val manager = AppWidgetManager.getInstance(this)

		// Updates the widget before loading the configuration values
		BatteryWidgetProvider.updateAppWidget(this, manager, appWidgetId)

		// Gets the preferences containing widget-specific settings
		val prefs = getSharedPreferences(BatteryWidgetProvider.PREFS, MODE_PRIVATE)

		// Gets the preferences containing default user settings
		val userPrefs = getSharedPreferences(BatteryWidgetProvider.USER_PREFS, MODE_PRIVATE)

		// Gets the border width input field
		val widthEdit = findViewById<EditText>(R.id.border_width)

		// Gets the font size input field
		val fontSizeEdit = findViewById<EditText>(R.id.font_size)

		// Gets the background color preview
		val previewBg = findViewById<View>(R.id.preview_bg_color)

		// Gets the foreground color preview
		val previewFg = findViewById<View>(R.id.preview_fg_color)

		// Gets the border color preview
		val previewBorder = findViewById<View>(R.id.preview_border_color)

		// Gets the square shape option
		val shapeSquare = findViewById<RadioButton>(R.id.shape_square)

		// Gets the circle shape option
		val shapeCircle = findViewById<RadioButton>(R.id.shape_circle)

		// Gets the background color picker button
		val previewBgButton = findViewById<View>(R.id.preview_bg)

		// Gets the foreground color picker button
		val previewFgButton = findViewById<View>(R.id.preview_fg)

		// Gets the border color picker button
		val previewBorderButton = findViewById<View>(R.id.preview_border)

		// Existing widget-specific values take priority
		// A new widget instance uses the persistent user defaults from the latest configuration
		var bg = prefs.getString("bg_$appWidgetId", userPrefs.getString("bg", "000000")) ?: "000000"
		var fg = prefs.getString("fg_$appWidgetId", userPrefs.getString("fg", "EEEEEE")) ?: "EEEEEE"
		var bc = prefs.getString("bc_$appWidgetId", userPrefs.getString("bc", "66965E")) ?: "66965E"

		// Reads the saved border width
		val savedWidth = prefs.getInt("bw_$appWidgetId", userPrefs.getInt("bw", 8))

		// Reads the saved font size
		val savedFontSize = prefs.getInt("font_$appWidgetId", userPrefs.getInt("font", 22))

		// Reads the saved widget shape
		val savedShape =
			prefs.getString("shape_$appWidgetId", userPrefs.getString("shape", "circle"))
				?: "circle"

		// Displays the saved border width
		widthEdit.setText(savedWidth.toString())

		// Displays the saved font size
		fontSizeEdit.setText(savedFontSize.toString())

		// Selects the saved widget shape
		when (savedShape) {
			"circle" -> shapeCircle.isChecked = true
			else -> shapeSquare.isChecked = true
		}

		// Displays the saved background color
		updatePreview(previewBg, bg)

		// Displays the saved foreground color
		updatePreview(previewFg, fg)

		// Displays the saved border color
		updatePreview(previewBorder, bc)

		// Opens the background color picker
		previewBgButton.setOnClickListener {
			showColorPicker("Background color", bg) { selected ->
				// Stores the selected background color
				bg = selected

				// Updates the background color preview
				updatePreview(previewBg, bg)
			}
		}

		// Opens the foreground color picker
		previewFgButton.setOnClickListener {
			showColorPicker("Text color", fg) { selected ->
				// Stores the selected foreground color
				fg = selected

				// Updates the foreground color preview
				updatePreview(previewFg, fg)
			}
		}

		// Opens the border color picker
		previewBorderButton.setOnClickListener {
			showColorPicker("Border color", bc) { selected ->
				// Stores the selected border color
				bc = selected

				// Updates the border color preview
				updatePreview(previewBorder, bc)
			}
		}

		// Saves the current configuration
		findViewById<Button>(R.id.save).setOnClickListener {
			// Reads and clamps the border width
			val bw = widthEdit.text.toString().toIntOrNull()?.coerceIn(0, 20) ?: 8

			// Reads and clamps the font size
			val fontSize = fontSizeEdit.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 22

			// Gets the selected widget shape
			val shape = if (shapeCircle.isChecked) "circle" else "square"

			// Saves settings belonging only to this exact widget instance
			// Existing separate widget instances remain unchanged
			prefs.edit {
				// Saves the widget background color
				putString("bg_$appWidgetId", bg)

				// Saves the widget foreground color
				putString("fg_$appWidgetId", fg)

				// Saves the widget border color
				putString("bc_$appWidgetId", bc)

				// Saves the widget border width
				putInt("bw_$appWidgetId", bw)

				// Saves the widget font size
				putInt("font_$appWidgetId", fontSize)

				// Saves the widget shape
				putString("shape_$appWidgetId", shape)
			}

			// Saves the same values as defaults for future widget instances
			userPrefs.edit {
				// Saves the default background color
				putString("bg", bg)

				// Saves the default foreground color
				putString("fg", fg)

				// Saves the default border color
				putString("bc", bc)

				// Saves the default border width
				putInt("bw", bw)

				// Saves the default font size
				putInt("font", fontSize)

				// Saves the default widget shape
				putString("shape", shape)
			}

			// Updates the widget with the saved settings
			BatteryWidgetProvider.updateAppWidget(this, manager, appWidgetId)

			// Starts or refreshes battery monitoring
			BatteryMonitorService.start(this)

			// Marks the configuration as successfully saved
			settingsWereSaved = true

			// Returns the configured widget ID to Android
			setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

			// Closes the configuration activity and removes its task entry
			finishAndRemoveTask()
		}
	}

	// Handles legacy Back navigation on Android 12 and earlier
	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		// Cancels the configuration
		cancelConfiguration()
	}

	// Treats Home presses and switching to another application as cancellation
	// The color picker is a dialog owned by this activity and does not normally stop ConfigActivity
	override fun onStop() {
		// Performs the base activity stop handling
		super.onStop()

		// Cancels the configuration when it was not saved and the activity is not finishing
		if (settingsWereSaved == false && isFinishing == false) {
			cancelConfiguration()
		}
	}

	// Unregisters the predictive Back callback during activity destruction
	override fun onDestroy() {
		// Unregisters the callback on Android 13 and newer
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backCallback)
		}

		// Performs the base activity destruction handling
		super.onDestroy()
	}

	// Closes the activity as cancellation and removes its task entry
	private fun cancelConfiguration() {
		// Finishes the activity only when it is not already finishing
		if (isFinishing == false) {
			finishAndRemoveTask()
		}
	}

	// Opens a color-picker dialog and returns an uppercase six-digit RGB value after confirmation
	private fun showColorPicker(title: String, currentHex: String, onColorPicked: (String) -> Unit) {
		// Attempts to parse the current color
		val currentColor = try {
			"#${currentHex.trim().removePrefix("#")}".toColorInt()
		} catch (_: Exception) {
			// Uses white when the current color cannot be parsed
			Color.WHITE
		}

		// Builds and displays the color-picker dialog
		ColorPickerDialogBuilder
			.with(this)
			.setTitle(title)
			.initialColor(currentColor)
			.wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
			.showColorEdit(true)
			.setPositiveButton(android.R.string.ok) { _, selectedColor, _ ->
				// Converts the selected color to an uppercase six-digit RGB value
				val hex = String.format(Locale.US, "%06X", 0xFFFFFF and selectedColor)

				// Returns the selected color to the caller
				onColorPicked(hex)
			}
			.setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
			.build()
			.show()
	}

	// Applies a hexadecimal color value to one configuration preview square
	private fun updatePreview(view: View, hex: String) {
		// Attempts to apply the configured color
		try {
			view.setBackgroundColor("#${hex.trim().removePrefix("#")}".toColorInt())
		} catch (_: Exception) {
			// Uses gray when the configured color cannot be parsed
			view.setBackgroundColor(Color.GRAY)
		}
	}
}