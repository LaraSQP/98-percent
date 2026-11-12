package com.sqp.percent98

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.DecimalFormat

// Keeps all installed battery widgets synchronized through a foreground service
// Receives live ACTION_BATTERY_CHANGED broadcasts and performs a two-minute fallback refresh
// BatteryMonitorWatchdog monitors the service and can request a restart if this service is unexpectedly terminated
class BatteryMonitorService : Service() {

	// Runs periodic battery updates on the main application thread
	private val handler = Handler(Looper.getMainLooper())

	// Stores the last battery percentage displayed in the widgets
	private var lastDisplayedBatteryLevel: Int? = null

	// Refreshes the battery state from Android's sticky battery intent every two minutes
	// This fallback runs while the foreground service is alive and complements live broadcasts
	private val fallbackRefresh = object : Runnable {
		// Runs one fallback battery refresh and schedules the next refresh
		override fun run() {
			// Updates the battery information without forcing a widget redraw
			updateFromCurrentBatteryIntent(forceUpdate = false)

			// Schedules the next fallback refresh
			handler.postDelayed(this, FALLBACK_REFRESH_MILLIS)
		}
	}

	// Receives the current battery state directly from Android
	private val batteryChangedReceiver = object : BroadcastReceiver() {
		// Processes a battery-state broadcast
		override fun onReceive(context: Context, intent: Intent) {
			// Processes only ACTION_BATTERY_CHANGED broadcasts
			if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
				// Updates the battery information without forcing a widget redraw
				updateFromBatteryIntent(intent, forceUpdate = false)
			}
		}
	}

	// Initializes the foreground service and starts battery monitoring
	override fun onCreate() {
		// Completes the base service initialization
		super.onCreate()

		// Creates the notification channel required by the foreground service
		createNotificationChannel()

		// Starts the service in foreground mode
		startAsForegroundService()

		// Registers the receiver for battery-state changes
		ContextCompat.registerReceiver(this, batteryChangedReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)

		// Performs an immediate forced battery update
		updateFromCurrentBatteryIntent(forceUpdate = true)

		// Records that the service is active
		BatteryMonitorWatchdog.recordHeartbeat(this)

		// Schedules the fallback battery refresh
		handler.postDelayed(fallbackRefresh, FALLBACK_REFRESH_MILLIS)
	}

	// Handles requests to start or refresh the foreground service
	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		// Performs an immediate forced battery update
		updateFromCurrentBatteryIntent(forceUpdate = true)

		// Records that the service is active
		BatteryMonitorWatchdog.recordHeartbeat(this)

		// Requests that Android recreate the service after termination
		return START_STICKY
	}

	// This service does not support binding
	override fun onBind(intent: Intent?): IBinder? {
		// Returns null because the service is started rather than bound
		return null
	}

	// Releases resources when the service is destroyed
	override fun onDestroy() {
		// Cancels the scheduled fallback refresh
		handler.removeCallbacks(fallbackRefresh)

		// Attempts to unregister the battery receiver
		try {
			unregisterReceiver(batteryChangedReceiver)
		} catch (_: IllegalArgumentException) {
			// The service may have failed before receiver registration completed
		}

		// Completes the base service cleanup
		super.onDestroy()
	}

	// Reads Android's current sticky battery-state intent
	private fun updateFromCurrentBatteryIntent(forceUpdate: Boolean) {
		// Requests the latest sticky battery-state intent without registering a receiver
		val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

		// Processes the battery intent when Android returns one
		if (batteryIntent != null) {
			updateFromBatteryIntent(batteryIntent, forceUpdate)
		}
	}

	// Updates the notification and widgets using the received battery state
	private fun updateFromBatteryIntent(intent: Intent, forceUpdate: Boolean) {
		// Converts the battery level and scale extras into a percentage
		val level = getBatteryLevel(intent)

		// Ignores invalid battery percentages
		if (level !in 0..100) {
			return
		}

		// Reads the battery temperature in tenths of a degree Celsius
		val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)

		// Reads the battery voltage in millivolts
		val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

		// Updates the persistent battery notification
		updateBatteryNotification(level, temperature, voltage)

		// Avoids unnecessary widget updates when only temperature or voltage changed
		if (forceUpdate == false && lastDisplayedBatteryLevel == level) {
			// Records that the service is still active
			BatteryMonitorWatchdog.recordHeartbeat(this)

			// Stops processing this battery update
			return
		}

		// Updates every installed widget with the current battery percentage
		updateAllWidgets(level)

		// Stores the battery percentage displayed in the widgets
		lastDisplayedBatteryLevel = level

		// Records that the service is still active
		BatteryMonitorWatchdog.recordHeartbeat(this)
	}

	// Updates the persistent notification with current battery information
	private fun updateBatteryNotification(level: Int, temperature: Int, voltage: Int) {
		// Creates a formatter that removes unnecessary decimal places
		val decimalFormat = DecimalFormat("0.##")

		// Converts the battery temperature from tenths of a degree Celsius
		val temperatureText = decimalFormat.format(temperature / 10.0)

		// Converts the battery voltage from millivolts to volts
		val voltageText = decimalFormat.format(voltage / 1000.0)

		// Builds the notification content displayed to the user
		val content = "\uD83D\uDD0B$level%    \uD83C\uDF21${temperatureText}°C    ⚡${voltageText}V"

		// Builds the persistent battery-status notification
		val notification = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentTitle(content)
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.setCategory(NotificationCompat.CATEGORY_STATUS)
			.setOngoing(true)
			.setSilent(true)
			.setOnlyAlertOnce(true)
			.setShowWhen(false)
			.build()

		// Publishes or replaces the battery-status notification
		getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
	}

	// Updates every installed widget using the exact broadcast percentage
	private fun updateAllWidgets(batteryLevel: Int) {
		// Gets the AppWidgetManager instance for this service
		val manager = AppWidgetManager.getInstance(this)

		// Identifies the battery widget provider
		val provider = ComponentName(this, BatteryWidgetProvider::class.java)

		// Gets the IDs of all installed battery widget instances
		val appWidgetIds = manager.getAppWidgetIds(provider)

		// Stops monitoring when no widget instances remain
		if (appWidgetIds.isEmpty()) {
			// Cancels watchdog monitoring
			BatteryMonitorWatchdog.cancel(this)

			// Stops the foreground service
			stopSelf()

			// Stops processing the widget update
			return
		}

		// Updates every installed widget with the current battery percentage
		for (id in appWidgetIds) {
			BatteryWidgetProvider.updateAppWidget(this, manager, id, batteryLevel)
		}
	}

	// Converts Android's battery level and scale extras into a percentage
	private fun getBatteryLevel(intent: Intent): Int {
		// Reads the current battery level
		val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

		// Reads the maximum battery scale
		val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

		// Calculates and clamps the percentage when the battery values are valid
		return if (level >= 0 && scale > 0) {
			(level * 100 / scale).coerceIn(0, 100)
		} else {
			// Returns an invalid value when Android does not provide usable values
			-1
		}
	}

	// Creates the notification channel used by the battery-status notification
	private fun createNotificationChannel() {
		// Gets the system notification manager
		val manager = getSystemService(NotificationManager::class.java)

		// Defines the battery-status notification channel
		val channel = NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).apply {
			// Disables notification sounds
			setSound(null, null)

			// Disables notification vibration
			enableVibration(false)

			// Disables notification lights
			enableLights(false)

			// Hides the notification badge
			setShowBadge(false)

			// Makes the notification visible on the lock screen
			lockscreenVisibility = Notification.VISIBILITY_PUBLIC
		}

		// Registers the notification channel with Android
		manager.createNotificationChannel(channel)
	}

	// Enters foreground mode with Android's mandatory notification
	private fun startAsForegroundService() {
		// Builds the initial foreground-service notification
		val notification = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentTitle("98%")
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.setOngoing(true)
			.setSilent(true)
			.setShowWhen(false)
			.build()

		// Supplies the special-use foreground-service type on Android 14 and later
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
		} else {
			// Starts the foreground service on earlier Android versions
			startForeground(NOTIFICATION_ID, notification)
		}
	}

	companion object {

		// Identifies the battery-status notification channel
		private const val CHANNEL_ID = "battery_widget_status"

		// Identifies the battery-status notification
		private const val NOTIFICATION_ID = 98

		// Defines the two-minute fallback refresh interval
		private const val FALLBACK_REFRESH_MILLIS = 2L * 60L * 1000L

		// Starts or refreshes foreground battery monitoring
		fun start(context: Context) {
			// Attempts to start the foreground service
			try {
				ContextCompat.startForegroundService(context, Intent(context, BatteryMonitorService::class.java))
			} catch (_: Exception) {
				// Android can reject some background foreground-service starts
				// Do not crash the launcher or watchdog receiver in that case
			}
		}

		// Stops foreground monitoring after the final widget is removed
		fun stop(context: Context) {
			// Requests that Android stop the battery-monitoring service
			context.stopService(Intent(context, BatteryMonitorService::class.java))
		}
	}
}