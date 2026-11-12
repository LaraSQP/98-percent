package com.sqp.percent98

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

// Restores battery-widget monitoring after events that terminate the app process and its foreground service
// Android removes the foreground service and its dynamically registered ACTION_BATTERY_CHANGED receiver after device reboot or application update
// This receiver checks whether any widget instances still exist, redraws them, and starts BatteryMonitorService again
class BatteryMonitorRestartReceiver : BroadcastReceiver() {

	// Handles device boot completion and application replacement
	// Does nothing when no BatteryWidgetProvider widget instances are installed
	// Prevents an unnecessary foreground service from being started merely because the application is installed
	override fun onReceive(context: Context, intent: Intent) {
		// Restores monitoring after device boot or application replacement
		when (intent.action) {
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_MY_PACKAGE_REPLACED -> restoreMonitoring(context)
		}
	}

	// Redraws all installed widgets and requests restoration of foreground monitoring after a reboot or application update
	private fun restoreMonitoring(context: Context) {
		// Gets the AppWidgetManager instance for the supplied context
		val manager = AppWidgetManager.getInstance(context)

		// Identifies the battery widget provider
		val provider = ComponentName(context, BatteryWidgetProvider::class.java)

		// Gets the IDs of all installed BatteryWidgetProvider widget instances
		val appWidgetIds = manager.getAppWidgetIds(provider)

		// Does not start a foreground service when the user has no widget instances
		if (appWidgetIds.isEmpty()) {
			return
		}

		// The launcher can retain stale RemoteViews after reboot or APK update
		// Renders every installed instance once before restoring live monitoring
		for (id in appWidgetIds) {
			BatteryWidgetProvider.updateAppWidget(context, manager, id)
		}

		// Starts foreground monitoring after the widgets have been redrawn
		BatteryMonitorService.start(context)
	}
}