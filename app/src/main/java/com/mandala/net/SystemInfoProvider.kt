package com.mandala.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager

data class SystemInfo(
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val networkOperator: String,
    val simOperator: String,
    val networkType: String,
    val cpuArch: String
)

class SystemInfoProvider(private val context: Context) {

    fun getSystemInfo(): SystemInfo {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} $model"
        }

        val osVersion = Build.VERSION.RELEASE
        val sdkInt = Build.VERSION.SDK_INT
        val securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            "N/A"
        }

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val networkOperator = try {
            telephonyManager?.networkOperatorName?.takeIf { it.isNotEmpty() } ?: "Tidak Diketahui"
        } catch (e: Exception) {
            "Tidak Diketahui"
        }
        val simOperator = try {
            telephonyManager?.simOperatorName?.takeIf { it.isNotEmpty() } ?: "Tidak Diketahui"
        } catch (e: Exception) {
            "Tidak Diketahui"
        }

        val cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch") ?: "Unknown"

        val networkType = try {
            getActiveNetworkType()
        } catch (e: Exception) {
            "Tidak Diketahui"
        }

        return SystemInfo(
            deviceName = deviceName,
            manufacturer = manufacturer,
            model = model,
            osVersion = osVersion,
            sdkInt = sdkInt,
            securityPatch = securityPatch,
            networkOperator = networkOperator,
            simOperator = simOperator,
            networkType = networkType,
            cpuArch = cpuArch
        )
    }

    private fun getActiveNetworkType(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "Disconnect"
            val activeNetwork = connectivityManager.activeNetwork ?: return "Disconnect"
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "Disconnect"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN Aktif"
                else -> "Lainnya"
            }
        } catch (e: Exception) {
            "Tidak Diketahui"
        }
    }
}
