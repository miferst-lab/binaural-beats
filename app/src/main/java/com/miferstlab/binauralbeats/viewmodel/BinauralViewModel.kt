package com.miferstlab.binauralbeats.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import com.miferstlab.binauralbeats.data.AppearanceMode
import com.miferstlab.binauralbeats.data.BinauralMode
import com.miferstlab.binauralbeats.data.FrequencyMath
import com.miferstlab.binauralbeats.data.PlaybackState
import com.miferstlab.binauralbeats.service.BinauralPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BinauralViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        PlaybackState(
            appearanceMode = AppearanceMode.fromPrefs(
                prefs.getString(KEY_APPEARANCE_MODE, AppearanceMode.NIGHT.prefsValue)
            )
        )
    )
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var service: BinauralPlaybackService? = null
    private var bound = false

    /** True after START until STOP — covers paused sessions so volume updates still reach the FGS. */
    private var sessionActive = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? BinauralPlaybackService.LocalBinder ?: return
            service = local.getService()
            bound = true
            val playing = service?.isPlaying() == true
            sessionActive = playing || sessionActive
            _state.update { it.copy(isPlaying = playing) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    fun selectMode(mode: BinauralMode) {
        _state.update {
            it.copy(
                mode = mode,
                volume = if (it.mode != mode) mode.defaultVolume else it.volume
            )
        }
        if (_state.value.isPlaying) {
            pushUpdateToService()
        }
    }

    fun setVolume(volume: Float) {
        _state.update { it.copy(volume = FrequencyMath.clampVolume(volume)) }
        if (sessionActive) {
            pushUpdateToService()
        }
    }

    fun setCustomCarrier(hz: Float) {
        _state.update { it.copy(customCarrierHz = FrequencyMath.clampCarrier(hz)) }
        if (_state.value.isPlaying && _state.value.mode == BinauralMode.NIESTANDARDOWY) {
            pushUpdateToService()
        }
    }

    fun setCustomBeat(hz: Float) {
        _state.update { it.copy(customBeatHz = FrequencyMath.clampBeat(hz)) }
        if (_state.value.isPlaying && _state.value.mode == BinauralMode.NIESTANDARDOWY) {
            pushUpdateToService()
        }
    }

    fun setMixWithOtherApps(enabled: Boolean) {
        _state.update { it.copy(mixWithOtherApps = enabled) }
        // Apply on next play/resume; if already playing, restart with new attrs.
        if (sessionActive) {
            play()
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _state.update { it.copy(keepScreenOn = enabled) }
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        prefs.edit().putString(KEY_APPEARANCE_MODE, mode.prefsValue).apply()
        _state.update { it.copy(appearanceMode = mode) }
    }

    fun play() {
        val ctx = getApplication<Application>()
        val s = _state.value
        val intent = BinauralPlaybackService.startIntent(
            context = ctx,
            mode = s.mode,
            volume = s.volume,
            carrier = s.customCarrierHz,
            beat = s.customBeatHz,
            mixWithOtherApps = s.mixWithOtherApps
        )
        // Always use startForegroundService for START (Android 8+ / 14 FGS contract).
        ctx.startForegroundService(intent)
        ensureBound()
        sessionActive = true
        _state.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        if (!sessionActive) return
        val ctx = getApplication<Application>()
        // Service is already an FGS from play(); startService is safe for pause/update.
        ctx.startService(
            Intent(ctx, BinauralPlaybackService::class.java).apply {
                action = BinauralPlaybackService.ACTION_PAUSE
            }
        )
        _state.update { it.copy(isPlaying = false) }
    }

    fun resume() {
        if (!sessionActive) {
            play()
            return
        }
        val ctx = getApplication<Application>()
        ctx.startForegroundService(
            Intent(ctx, BinauralPlaybackService::class.java).apply {
                action = BinauralPlaybackService.ACTION_RESUME
            }
        )
        _state.update { it.copy(isPlaying = true) }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else if (sessionActive) resume() else play()
    }

    fun stop() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, BinauralPlaybackService::class.java).apply {
                action = BinauralPlaybackService.ACTION_STOP
            }
        )
        sessionActive = false
        _state.update { it.copy(isPlaying = false) }
    }

    private fun ensureBound() {
        if (bound) return
        val ctx = getApplication<Application>()
        // Bind without creating a non-foreground started service on init.
        runCatching {
            ctx.bindService(
                Intent(ctx, BinauralPlaybackService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    private fun pushUpdateToService() {
        if (!sessionActive) return
        val ctx = getApplication<Application>()
        val s = _state.value
        ctx.startService(
            Intent(ctx, BinauralPlaybackService::class.java).apply {
                action = BinauralPlaybackService.ACTION_UPDATE
                putExtra(BinauralPlaybackService.EXTRA_MODE, s.mode.name)
                putExtra(BinauralPlaybackService.EXTRA_VOLUME, s.volume)
                putExtra(BinauralPlaybackService.EXTRA_CARRIER, s.customCarrierHz)
                putExtra(BinauralPlaybackService.EXTRA_BEAT, s.customBeatHz)
            }
        )
    }

    override fun onCleared() {
        if (bound) {
            runCatching { getApplication<Application>().unbindService(connection) }
            bound = false
        }
        // Do not stop playback here — FGS should outlive the Activity/ViewModel.
        super.onCleared()
    }

    companion object {
        private const val PREFS_NAME = "binaural"
        private const val KEY_APPEARANCE_MODE = "appearance_mode"
    }
}
