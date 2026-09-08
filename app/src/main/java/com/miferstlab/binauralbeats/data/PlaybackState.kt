package com.miferstlab.binauralbeats.data

data class PlaybackState(
    val isPlaying: Boolean = false,
    val mode: BinauralMode = BinauralMode.RELAKS,
    val volume: Float = BinauralMode.RELAKS.defaultVolume,
    val customCarrierHz: Float = 200f,
    val customBeatHz: Float = 10f,
    val mixWithOtherApps: Boolean = true,
    val keepScreenOn: Boolean = false
)
