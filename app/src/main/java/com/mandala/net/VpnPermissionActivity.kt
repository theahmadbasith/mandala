package com.mandala.net

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast

class VpnPermissionActivity : Activity() {

    private val REQUEST_VPN = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            try {
                startActivityForResult(vpnIntent, REQUEST_VPN)
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka persetujuan VPN.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            // Already authorized
            startVpnService()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                startVpnService()
            } else {
                Toast.makeText(this, "Izin VPN ditolak.", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    private fun startVpnService() {
        val settingsHelper = SettingsHelper(this)
        FirewallVpnService.startService(this)
        settingsHelper.isFirewallEnabled = true
        
        // Immediately force update all widgets
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val thisWidget = ComponentName(this, FirewallWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        
        val updateIntent = Intent(this, FirewallWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
        }
        sendBroadcast(updateIntent)
    }
}
