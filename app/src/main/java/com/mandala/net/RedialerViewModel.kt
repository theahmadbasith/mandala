package com.mandala.net

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "redialer_settings")

enum class RedialerState {
    IDLE,
    WAITING_TO_REDIAL,
    CALLING,
    CALL_SUCCESS,
    STOPPED,
    PAUSED
}

class RedialerViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.dataStore

    companion object {
        val DELAY_KEY = intPreferencesKey("delay_seconds")
        val RING_DURATION_KEY = intPreferencesKey("ring_duration_seconds")
        val MAX_RETRIES_KEY = intPreferencesKey("max_retries")
    }

    private val _delaySeconds = MutableStateFlow(5)
    val delaySeconds = _delaySeconds.asStateFlow()

    private val _ringDurationSeconds = MutableStateFlow(15)
    val ringDurationSeconds = _ringDurationSeconds.asStateFlow()

    private val _maxRetries = MutableStateFlow(100)
    val maxRetries = _maxRetries.asStateFlow()

    private val _targetNumber = MutableStateFlow("")
    val targetNumber = _targetNumber.asStateFlow()

    private val _currentState = MutableStateFlow(RedialerState.IDLE)
    val currentState = _currentState.asStateFlow()

    private val _callCount = MutableStateFlow(0)
    val callCount = _callCount.asStateFlow()

    private val _countdown = MutableStateFlow(0)
    val countdown = _countdown.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _delaySeconds.value = prefs[DELAY_KEY] ?: 5
            _ringDurationSeconds.value = prefs[RING_DURATION_KEY] ?: 15
            _maxRetries.value = prefs[MAX_RETRIES_KEY] ?: 100
        }

        // Listen to active foreground RedialerService state
        viewModelScope.launch {
            com.mandala.net.service.RedialerService.serviceState.collect { serviceState ->
                _currentState.value = serviceState.state
                _callCount.value = serviceState.callCount
                _countdown.value = serviceState.countdown
                if (serviceState.targetNumber.isNotEmpty()) {
                    _targetNumber.value = serviceState.targetNumber
                }
            }
        }
    }

    fun setDelay(seconds: Int) {
        _delaySeconds.value = seconds
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[DELAY_KEY] = seconds }
        }
    }

    fun setRingDuration(seconds: Int) {
        _ringDurationSeconds.value = seconds
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[RING_DURATION_KEY] = seconds }
        }
    }

    fun setMaxRetries(retries: Int) {
        _maxRetries.value = retries
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[MAX_RETRIES_KEY] = retries }
        }
    }

    fun setTargetNumber(number: String) {
        _targetNumber.value = number
    }

    fun startRedialing() {
        if (_targetNumber.value.isBlank()) return
        
        val context = getApplication<Application>()
        val intent = Intent(context, com.mandala.net.service.RedialerService::class.java).apply {
            action = "ACTION_START_REDIAL"
            putExtra("EXTRA_TARGET_NUMBER", _targetNumber.value)
            putExtra("EXTRA_DELAY_SECONDS", _delaySeconds.value)
            putExtra("EXTRA_RING_DURATION", _ringDurationSeconds.value)
            putExtra("EXTRA_MAX_RETRIES", _maxRetries.value)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                context.startService(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun stopRedialing() {
        val context = getApplication<Application>()
        val intent = Intent(context, com.mandala.net.service.RedialerService::class.java).apply {
            action = "ACTION_STOP_REDIAL"
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun resetState() {
        _currentState.value = RedialerState.IDLE
        _callCount.value = 0
        _countdown.value = 0
    }
}
