package com.mandala.net

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

class CallStateMonitor(
    private val context: Context,
    private val onCallStateChanged: (Int) -> Unit
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null

    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallback()
        } else {
            registerPhoneStateListener()
        }
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            unregisterTelephonyCallback()
        } else {
            unregisterPhoneStateListener()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallback() {
        val executor = context.mainExecutor
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                onCallStateChanged.invoke(state)
            }
        }
        telephonyCallback = callback
        try {
            telephonyManager.registerTelephonyCallback(executor, callback)
        } catch (e: SecurityException) {
            // Read phone state permission required
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterTelephonyCallback() {
        telephonyCallback?.let {
            telephonyManager.unregisterTelephonyCallback(it)
        }
        telephonyCallback = null
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        val listener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                onCallStateChanged.invoke(state)
            }
        }
        phoneStateListener = listener
        try {
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            // Read phone state permission required
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneStateListener() {
        phoneStateListener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneStateListener = null
    }
}
