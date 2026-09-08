package com.miferstlab.binauralbeats.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.miferstlab.binauralbeats.MainActivity
import com.miferstlab.binauralbeats.R
import com.miferstlab.binauralbeats.audio.BinauralAudioEngine
import com.miferstlab.binauralbeats.data.BinauralMode

/**
 * Foreground service so binaural tones continue with the screen off.
 * Notification includes a Stop action.
 *
 * Android 14+: uses [ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK] and
 * calls [ServiceCompat.startForeground] before any long-running work.
 */
class BinauralPlaybackService : Service() {

    private val binder = LocalBinder()
    private val engine = BinauralAudioEngine()
    private val focusHolder = BinauralAudioEngine.FocusHolder()
    private var currentMode: BinauralMode = BinauralMode.RELAKS
    private var mixWithOtherApps: Boolean = true
    private var foregroundStarted = false

    inner class LocalBinder : Binder() {
        fun getService(): BinauralPlaybackService = this@BinauralPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: BinauralMode.RELAKS.name
                currentMode = runCatching { BinauralMode.valueOf(modeName) }
                    .getOrDefault(BinauralMode.RELAKS)
                val volume = intent.getFloatExtra(EXTRA_VOLUME, currentMode.defaultVolume)
                val carrier = intent.getFloatExtra(EXTRA_CARRIER, currentMode.carrierHz)
                val beat = intent.getFloatExtra(EXTRA_BEAT, currentMode.beatHz)
                val newMix = intent.getBooleanExtra(EXTRA_MIX, true)

                // Promote to FGS immediately (Android 8+/14 timeout).
                startAsForeground()

                engine.setFrequencies(
                    currentMode.leftHz(carrier, beat),
                    currentMode.rightHz(carrier, beat)
                )
                engine.setVolume(volume)

                // If mix mode changed while a session exists, rebuild AudioTrack attributes.
                if (engine.isSessionActive() && newMix != mixWithOtherApps) {
                    engine.stop()
                }
                mixWithOtherApps = newMix

                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (mixWithOtherApps) {
                    BinauralAudioEngine.requestMixableFocus(am, focusHolder)
                } else {
                    BinauralAudioEngine.abandonMixableFocus(am, focusHolder)
                }

                if (engine.isSessionActive()) {
                    engine.resume()
                } else {
                    engine.start(mixWithOtherApps)
                }
                updateNotification()
            }
            ACTION_PAUSE -> {
                engine.pause()
                updateNotification(paused = true)
            }
            ACTION_RESUME -> {
                startAsForeground()
                engine.resume()
                updateNotification(paused = false)
            }
            ACTION_UPDATE -> {
                val volume = intent.getFloatExtra(EXTRA_VOLUME, 0.35f)
                val carrier = intent.getFloatExtra(EXTRA_CARRIER, currentMode.carrierHz)
                val beat = intent.getFloatExtra(EXTRA_BEAT, currentMode.beatHz)
                val modeName = intent.getStringExtra(EXTRA_MODE)
                if (modeName != null) {
                    currentMode = runCatching { BinauralMode.valueOf(modeName) }
                        .getOrDefault(currentMode)
                }
                engine.setFrequencies(
                    currentMode.leftHz(carrier, beat),
                    currentMode.rightHz(carrier, beat)
                )
                engine.setVolume(volume)
                if (foregroundStarted) {
                    updateNotification()
                }
            }
            else -> {
                // Unknown / null action: if somehow started, still satisfy FGS contract.
                if (!foregroundStarted) {
                    startAsForeground()
                }
            }
        }
        return START_STICKY
    }

    fun isPlaying(): Boolean = engine.isRunning()

    fun stopPlayback() {
        engine.stop()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        BinauralAudioEngine.abandonMixableFocus(am, focusHolder)
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification = buildNotification(paused = !engine.isRunning() && foregroundStarted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateNotification(paused: Boolean = false) {
        if (!foregroundStarted) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(paused))
    }

    private fun buildNotification(paused: Boolean = false): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BinauralPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val modeLabel = modeDisplayName(currentMode)
        val text = if (paused) {
            getString(R.string.notification_paused, modeLabel)
        } else {
            getString(R.string.notification_playing, modeLabel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_notification, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun modeDisplayName(mode: BinauralMode): String {
        val resId = when (mode) {
            BinauralMode.RELAKS -> R.string.mode_relax
            BinauralMode.SKUPIENIE -> R.string.mode_focus
            BinauralMode.SEN -> R.string.mode_sleep
            BinauralMode.MEDYTACJA -> R.string.mode_meditation
            BinauralMode.NIESTANDARDOWY -> R.string.mode_custom
        }
        return getString(resId)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "binaural_playback"
        const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.miferstlab.binauralbeats.START"
        const val ACTION_STOP = "com.miferstlab.binauralbeats.STOP"
        const val ACTION_PAUSE = "com.miferstlab.binauralbeats.PAUSE"
        const val ACTION_RESUME = "com.miferstlab.binauralbeats.RESUME"
        const val ACTION_UPDATE = "com.miferstlab.binauralbeats.UPDATE"

        const val EXTRA_MODE = "mode"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_CARRIER = "carrier"
        const val EXTRA_BEAT = "beat"
        const val EXTRA_MIX = "mix"

        fun startIntent(
            context: Context,
            mode: BinauralMode,
            volume: Float,
            carrier: Float,
            beat: Float,
            mixWithOtherApps: Boolean
        ): Intent = Intent(context, BinauralPlaybackService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_MODE, mode.name)
            putExtra(EXTRA_VOLUME, volume)
            putExtra(EXTRA_CARRIER, carrier)
            putExtra(EXTRA_BEAT, beat)
            putExtra(EXTRA_MIX, mixWithOtherApps)
        }
    }
}
