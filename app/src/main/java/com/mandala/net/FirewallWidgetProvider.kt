package com.mandala.net

import com.mandala.net.R
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.widget.RemoteViews
import android.widget.Toast

class FirewallWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_FIREWALL = "com.mandala.net.widget.TOGGLE_FIREWALL"
        const val ACTION_LAUNCH_FORCE = "com.mandala.net.widget.LAUNCH_FORCE"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_LAUNCH_FORCE -> {
                val launched = launchPhoneInfoDirectly(context)
                if (!launched) {
                    Toast.makeText(context, "Perangkat Anda tidak mendukung menu rahasia ini.", Toast.LENGTH_LONG).show()
                }
            }
            ACTION_TOGGLE_FIREWALL -> {
                val settingsHelper = SettingsHelper(context)
                val isCurrentlyRunning = settingsHelper.isFirewallEnabled
                
                if (isCurrentlyRunning) {
                    // Stop the service
                    FirewallVpnService.stopService(context)
                    settingsHelper.isFirewallEnabled = false
                } else {
                    // Start - but first check if VPN permission is granted
                    val vpnPrepared = try {
                        VpnService.prepare(context.applicationContext)
                    } catch (e: SecurityException) {
                        // Fallback to needing permission/redirecting to MainActivity to handle it under Activity context safely
                        Intent()
                    }
                    if (vpnPrepared == null) {
                        // Already has permission, we can start it directly
                        FirewallVpnService.startService(context)
                        settingsHelper.isFirewallEnabled = true
                    } else {
                        // Needs permission, open transparent VpnPermissionActivity
                        val permissionIntent = Intent(context, VpnPermissionActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(permissionIntent)
                    }
                }

                // Immediately update all widgets
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, FirewallWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                onUpdate(context, appWidgetManager, allWidgetIds)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val settingsHelper = SettingsHelper(context)
        val isEnabled = settingsHelper.isFirewallEnabled

        val views = RemoteViews(context.packageName, R.layout.widget_firewall)
        
        // Update Firewall status text & color
        views.setTextViewText(R.id.widget_firewall_status, if (isEnabled) "FW: On" else "FW: Off")
        val statusColor = androidx.core.content.ContextCompat.getColor(
            context,
            if (isEnabled) R.color.widget_accent else R.color.widget_text_secondary
        )
        views.setTextColor(R.id.widget_firewall_status, statusColor)

        // Update Firewall icon
        views.setImageViewResource(
            R.id.widget_firewall_icon,
            if (isEnabled) R.drawable.ic_shield_active else R.drawable.ic_shield_inactive
        )

        // Setup PendingIntent for Force LTE
        val forceIntent = Intent(context, FirewallWidgetProvider::class.java).apply {
            action = ACTION_LAUNCH_FORCE
        }
        val forcePendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 100,
            forceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_btn_force, forcePendingIntent)

        // Setup PendingIntent for Speed Test
        val speedIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("tab_to_open", 3) // Speed tab (index 3)
        }
        val speedPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 200,
            speedIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_btn_speed, speedPendingIntent)

        // Setup PendingIntent for Redialer
        val redialIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("tab_to_open", 2) // Call tab (index 2)
            putExtra("open_redialer", true) // Open redialer sub-tab
        }
        val redialPendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId + 300,
            redialIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_btn_redial, redialPendingIntent)

        // Setup PendingIntent for Firewall Toggle
        val toggleIntent = Intent(context, FirewallWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_FIREWALL
        }
        val togglePendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId + 400,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_btn_firewall, togglePendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun launchPhoneInfoDirectly(context: Context): Boolean {
        val radioInfoIntents = listOf(
            Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.android.settings", "com.android.settings.RadioInfo")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.RadioInfo")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Intent.ACTION_VIEW, Uri.parse("android_secret_code://4636")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$RadioInfoActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName("com.android.phone", "com.android.phone.settings.RadioInfo")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.RadioInfoActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )

        for (intent in radioInfoIntents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Silently try next variant
            }
        }

        // Fallback testing settings
        val testingSettingsIntents = listOf(
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$TestingSettingsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            Intent(Intent.ACTION_MAIN).apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$TestingSettingsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        for (intent in testingSettingsIntents) {
            try {
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Silently try next
            }
        }
        return false
    }
}
