# Binaural Beats — keep rules for when minify is enabled later.

# Foreground service + binder entry points (reflection / manifest)
-keep class com.miferstlab.binauralbeats.service.BinauralPlaybackService { *; }
-keep class com.miferstlab.binauralbeats.service.BinauralPlaybackService$LocalBinder { *; }

# Enum used across intents / extras
-keepclassmembers enum com.miferstlab.binauralbeats.data.BinauralMode { *; }

# Audio engine called from service thread
-keep class com.miferstlab.binauralbeats.audio.BinauralAudioEngine { *; }
-keep class com.miferstlab.binauralbeats.audio.BinauralAudioEngine$FocusHolder { *; }
