package com.miferstlab.binauralbeats.viewmodel

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miferstlab.binauralbeats.billing.BillingManager
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.AppearanceMode
import com.miferstlab.binauralbeats.data.BinauralMode
import com.miferstlab.binauralbeats.data.Entitlements
import com.miferstlab.binauralbeats.data.FrequencyMath
import com.miferstlab.binauralbeats.data.PlaybackState
import com.miferstlab.binauralbeats.service.BinauralPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BinauralViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val trialStartMs: Long = ensureTrialStart()
    private var furthestNowMs: Long = prefs.getLong(KEY_FURTHEST_NOW, 0L)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var service: BinauralPlaybackService? = null
    private var bound = false

    /** True after START until STOP — covers paused sessions so volume updates still reach the FGS. */
    private var sessionActive = false

    private var trialTickerJob: Job? = null

    private val billing = BillingManager(
        context = application,
        onPremiumUnlocked = { setPremium(true) },
        allowDebugUnlock = { prefs.getBoolean(KEY_DEBUG_UNLOCK, false) }
    )

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

    init {
        refreshTrialState()
        startTrialTicker()
    }

    private fun initialState(): PlaybackState {
        val now = effectiveNow()
        val premium = prefs.getBoolean(KEY_PREMIUM, false)
        val trialActive = !premium && Entitlements.isTrialActive(trialStartMs, now)
        return PlaybackState(
            appearanceMode = AppearanceMode.fromPrefs(
                prefs.getString(KEY_APPEARANCE_MODE, AppearanceMode.NIGHT.prefsValue)
            ),
            ambient = AmbientSound.fromPrefs(prefs.getString(KEY_AMBIENT, AmbientSound.OFF.prefsValue)),
            ambientVolume = FrequencyMath.clampVolume(
                prefs.getFloat(KEY_AMBIENT_VOLUME, 0.35f)
            ),
            isPremium = premium,
            isTrialActive = trialActive,
            trialDaysRemaining = if (premium) 0 else Entitlements.trialRemainingDays(trialStartMs, now)
        )
    }

    /** Persist first-install / trial start once; never move backwards. */
    private fun ensureTrialStart(): Long {
        val existing = prefs.getLong(KEY_TRIAL_START, 0L)
        if (existing > 0L) return existing
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_TRIAL_START, now)
            .putLong(KEY_FURTHEST_NOW, now)
            .apply()
        return now
    }

    /**
     * Wall-clock with rollback clamp: [furthestNowMs] only advances.
     * Clock going backwards does not extend remaining trial time.
     */
    private fun effectiveNow(): Long {
        val wall = System.currentTimeMillis()
        val effective = maxOf(wall, furthestNowMs)
        if (effective > furthestNowMs) {
            furthestNowMs = effective
            prefs.edit().putLong(KEY_FURTHEST_NOW, furthestNowMs).apply()
        }
        return effective
    }

    private fun refreshTrialState() {
        val now = effectiveNow()
        val premium = _state.value.isPremium || prefs.getBoolean(KEY_PREMIUM, false)
        val trialActive = !premium && Entitlements.isTrialActive(trialStartMs, now)
        val days = if (premium) 0 else Entitlements.trialRemainingDays(trialStartMs, now)
        _state.update {
            it.copy(
                isPremium = premium,
                isTrialActive = trialActive,
                trialDaysRemaining = days
            )
        }
        // If trial just expired while playing, stop and show paywall.
        if (!premium && !trialActive && _state.value.isPlaying) {
            stop()
            _state.update { it.copy(showTrialExpiredDialog = true) }
        }
    }

    private fun startTrialTicker() {
        if (trialTickerJob?.isActive == true) return
        trialTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(TRIAL_TICK_MS)
                if (_state.value.isPremium) continue
                refreshTrialState()
            }
        }
    }

    private fun canPlay(): Boolean {
        refreshTrialState()
        val s = _state.value
        return s.isPremium || s.isTrialActive
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

    /** Nudge main binaural volume by [delta] (e.g. ±0.01). */
    fun nudgeVolume(delta: Float) {
        setVolume(_state.value.volume + delta)
    }

    fun setAmbient(ambient: AmbientSound) {
        if (ambient.isPremium && !_state.value.hasFullAccess) {
            _state.update { it.copy(showPremiumUpsellDialog = true) }
            return
        }
        prefs.edit().putString(KEY_AMBIENT, ambient.prefsValue).apply()
        _state.update { it.copy(ambient = ambient) }
        if (sessionActive) {
            pushUpdateToService()
        }
    }

    fun setAmbientVolume(volume: Float) {
        val clamped = FrequencyMath.clampVolume(volume)
        prefs.edit().putFloat(KEY_AMBIENT_VOLUME, clamped).apply()
        _state.update { it.copy(ambientVolume = clamped) }
        if (sessionActive) {
            pushUpdateToService()
        }
    }

    /** Nudge ambient volume by [delta] (e.g. ±0.01). */
    fun nudgeAmbientVolume(delta: Float) {
        setAmbientVolume(_state.value.ambientVolume + delta)
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

    fun setPremium(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM, enabled).apply()
        _state.update {
            it.copy(
                isPremium = enabled,
                isTrialActive = if (enabled) false else Entitlements.isTrialActive(trialStartMs, effectiveNow()),
                trialDaysRemaining = if (enabled) 0 else Entitlements.trialRemainingDays(trialStartMs, effectiveNow()),
                showTrialExpiredDialog = false,
                showPremiumUpsellDialog = false
            )
        }
    }

    /** Settings debug toggle — allows purchase button / BillingManager debug path. */
    fun setDebugUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_UNLOCK, enabled).apply()
    }

    fun isDebugUnlockEnabled(): Boolean = prefs.getBoolean(KEY_DEBUG_UNLOCK, false)

    fun purchasePremium(activity: Activity?) {
        billing.purchasePremium(activity)
    }

    fun dismissTrialExpiredDialog() {
        _state.update { it.copy(showTrialExpiredDialog = false) }
    }

    /** Back-compat name used by MainActivity / HomeScreen. */
    fun dismissFreeLimitDialog() = dismissTrialExpiredDialog()

    fun dismissPremiumUpsellDialog() {
        _state.update { it.copy(showPremiumUpsellDialog = false) }
    }

    fun play() {
        if (!canPlay()) {
            _state.update { it.copy(showTrialExpiredDialog = true, isPlaying = false) }
            return
        }
        // Downgrade locked ambient if access lapsed.
        if (_state.value.ambient.isPremium && !_state.value.hasFullAccess) {
            prefs.edit().putString(KEY_AMBIENT, AmbientSound.OFF.prefsValue).apply()
            _state.update { it.copy(ambient = AmbientSound.OFF) }
        }
        val ctx = getApplication<Application>()
        val s = _state.value
        val intent = BinauralPlaybackService.startIntent(
            context = ctx,
            mode = s.mode,
            volume = s.volume,
            carrier = s.customCarrierHz,
            beat = s.customBeatHz,
            mixWithOtherApps = s.mixWithOtherApps,
            ambient = s.ambient,
            ambientVolume = s.ambientVolume
        )
        ctx.startForegroundService(intent)
        ensureBound()
        sessionActive = true
        _state.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        if (!sessionActive) return
        val ctx = getApplication<Application>()
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
        if (!canPlay()) {
            _state.update { it.copy(showTrialExpiredDialog = true, isPlaying = false) }
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
                putExtra(BinauralPlaybackService.EXTRA_AMBIENT, s.ambient.prefsValue)
                putExtra(BinauralPlaybackService.EXTRA_AMBIENT_VOLUME, s.ambientVolume)
            }
        )
    }

    override fun onCleared() {
        trialTickerJob?.cancel()
        if (bound) {
            runCatching { getApplication<Application>().unbindService(connection) }
            bound = false
        }
        super.onCleared()
    }

    companion object {
        private const val PREFS_NAME = "binaural"
        private const val KEY_APPEARANCE_MODE = "appearance_mode"
        private const val KEY_AMBIENT = "ambient"
        private const val KEY_AMBIENT_VOLUME = "ambient_volume"
        private const val KEY_PREMIUM = "is_premium"
        private const val KEY_DEBUG_UNLOCK = "debug_premium_unlock"
        private const val KEY_TRIAL_START = "trial_start_ms"
        private const val KEY_FURTHEST_NOW = "trial_furthest_now_ms"
        private const val TRIAL_TICK_MS = 60_000L
    }
}
