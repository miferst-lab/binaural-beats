# Binaural Beats — keep rules for when minify is enabled later.

# Foreground service + binder entry points (reflection / manifest)
-keep class com.miferstlab.binauralbeats.service.BinauralPlaybackService { *; }
-keep class com.miferstlab.binauralbeats.service.BinauralPlaybackService$LocalBinder { *; }

# Enum used across intents / extras
-keepclassmembers enum com.miferstlab.binauralbeats.data.BinauralMode { *; }

# Audio engine called from service thread
-keep class com.miferstlab.binauralbeats.audio.BinauralAudioEngine { *; }
-keep class com.miferstlab.binauralbeats.audio.BinauralAudioEngine$FocusHolder { *; }

# Play Billing (consumer rules ship with the library; keep our wrapper classes readable in crash traces)
-keep class com.miferstlab.binauralbeats.billing.** { *; }
-keep class com.android.billingclient.** { *; }
-dontwarn com.google.errorprone.annotations.**

# Audio decoding via MediaCodec/MediaExtractor is framework API; keep service/ambient classes used from native/threads
-keep class com.miferstlab.binauralbeats.audio.** { *; }
-keep class com.miferstlab.binauralbeats.service.** { *; }
