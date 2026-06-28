package com.mandala.net

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class CallScreener : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val settings = SettingsHelper(applicationContext)
        val handle = callDetails.handle
        val number = handle?.schemeSpecificPart ?: ""

        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            true // CallScreeningService on API 24-28 only fires for incoming calls anyway
        }

        if (isIncoming && settings.isCallBlockingEnabled) {
            val shouldBlock = when (settings.blockMode) {
                0 -> {
                    // Block all incoming calls
                    true
                }
                1 -> {
                    // Block unknown/empty numbers
                    number.isBlank() || number.contains("private", ignoreCase = true) || number.contains("unknown", ignoreCase = true)
                }
                else -> false
            }

            if (shouldBlock) {
                // Log the blocked call in preferences for the app dashboard
                settings.addBlockedCall(number)

                // Silently reject the call using official Telecom APIs
                val response = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(false) // Keeps it in system history so user has reference, but rejects it immediately
                        .setSkipNotification(true) // No ringtone or notification banner
                        .build()
                } else {
                    CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .build()
                }

                respondToCall(callDetails, response)
                Log.d("CallScreener", "Silently blocked call from: $number")
                return
            }
        }

        // Allow call to proceed normally
        val response = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        } else {
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .build()
        }
        respondToCall(callDetails, response)
    }
}
