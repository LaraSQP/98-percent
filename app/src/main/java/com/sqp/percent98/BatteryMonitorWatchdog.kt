package com.sqp.percent98

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.edit

// Periodically verifies that BatteryMonitorService is still active
// This receiver is not a service and does not remain running
// Android wakes it briefly through AlarmManager approximately every twenty minutes
// The receiver checks the last service heartbeat and requests a restart only when the heartbeat is stale
class BatteryMonitorWatchdog : BroadcastReceiver() {

	// Handles the watchdog alarm delivered by Android
	override fun onReceive(context: Context, intent: Intent) {
		// Ignores intents that are not watchdog alarms
		if (intent.action != ACTION_WATCHDOG) {
			return
		}

		// Stops watchdog monitoring when no battery widgets are installed
		if (hasInstalledWidgets(context) == false) {
			// Cancels the watchdog alarm and clears its saved state
			cancel(context)

			// Stops processing the watchdog alarm
			return
		}

		// Gets the current elapsed realtime value
		val now = SystemClock.elapsedRealtime()

		// Gets the shared preferences used to store watchdog state
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

		// Reads the elapsed realtime value of the latest service heartbeat
		val lastHeartbeat = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)

		// A healthy foreground service records a heartbeat after every live battery update
		// The service also records a heartbeat after every two-minute fallback refresh
		if (now - lastHeartbeat >= WATCHDOG_INTERVAL_MILLIS) {
			// Requests a foreground-service restart when the heartbeat is stale
			BatteryMonitorService.start(context)
		}

		// This is a one-shot alarm rather than a repeating alarm
		// Rescheduling explicitly prevents old repeating schedules from accumulating
		// The service heartbeat can move the next check forward
		schedule(context)
	}

	companion object {

		// Identifies the private action delivered to this application's watchdog receiver
		private const val ACTION_WATCHDOG = "com.sqp.percent98.ACTION_WATCHDOG"

		// Identifies the SharedPreferences file used for watchdog state
		private const val PREFS = "battery_monitor_watchdog"

		// Identifies the elapsed-realtime timestamp of the latest service activity
		private const val KEY_LAST_HEARTBEAT = "last_heartbeat"

		// Identifies the single watchdog PendingIntent
		private const val REQUEST_CODE = 98

		// Defines the approximate twenty-minute watchdog interval
		// Android may delay the alarm under Doze or OEM restrictions
		private const val WATCHDOG_INTERVAL_MILLIS = 20L * 60L * 1000L

		// Records successful service activity and moves the next watchdog check twenty minutes into the future
		fun recordHeartbeat(context: Context) {
			// Stores the current elapsed realtime value as the latest heartbeat
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
				// Saves the latest healthy service activity timestamp
				putLong(KEY_LAST_HEARTBEAT, SystemClock.elapsedRealtime())
			}

			// Schedules the next watchdog check
			schedule(context)
		}

		// Schedules one future system-managed watchdog check
		fun schedule(context: Context) {
			// Cancels watchdog monitoring when no battery widgets are installed
			if (hasInstalledWidgets(context) == false) {
				// Cancels the watchdog alarm and clears its saved state
				cancel(context)

				// Stops scheduling the watchdog alarm
				return
			}

			// Gets the system AlarmManager instance
			val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

			// Schedules the next watchdog check while allowing execution during idle mode
			alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MILLIS, pendingIntent(context))
		}

		// Cancels the outstanding watchdog alarm and clears its saved state
		fun cancel(context: Context) {
			// Gets the system AlarmManager instance
			val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

			// Cancels the watchdog alarm identified by the PendingIntent
			alarmManager.cancel(pendingIntent(context))

			// Removes the saved heartbeat timestamp
			context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
				// Deletes the latest heartbeat value
				remove(KEY_LAST_HEARTBEAT)
			}
		}

		// Determines whether at least one battery widget is installed
		private fun hasInstalledWidgets(context: Context): Boolean {
			// Gets the AppWidgetManager instance for the supplied context
			val manager = AppWidgetManager.getInstance(context)

			// Identifies the battery widget provider
			val provider = ComponentName(context, BatteryWidgetProvider::class.java)

			// Returns whether the provider has at least one installed widget instance
			return manager.getAppWidgetIds(provider).isNotEmpty()
		}

		// Returns the one PendingIntent used for scheduling and cancelling the watchdog alarm
		private fun pendingIntent(context: Context): PendingIntent {
			// Creates an intent targeting this watchdog receiver
			val intent = Intent(context, BatteryMonitorWatchdog::class.java).apply {
				// Sets the private watchdog action
				action = ACTION_WATCHDOG
			}

			// Returns the immutable PendingIntent used by the watchdog alarm
			return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		}
	}
}