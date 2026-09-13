package com.miferstlab.binauralbeats.data

/**
 * Preset binaural modes.
 *
 * Beat frequency = |fLeft - fRight|.
 * Carrier ≈ average of the two ear tones.
 *
 * Presets (documented also in README):
 * - Relaks:     ~9 Hz beat,  carrier ~220 Hz  (alpha)
 * - Skupienie:  ~16 Hz beat, carrier ~220 Hz  (beta) — also covers desk work
 * - Czytanie:   ~13 Hz beat, carrier ~210 Hz  (SMR / low beta)
 * - Energia:    ~22 Hz beat, carrier ~230 Hz  (high beta) — sport / alertness
 * - Sen:        ~3 Hz beat,  carrier ~180 Hz  (delta), lower default volume
 * - Medytacja:  ~6 Hz beat,  carrier ~200 Hz  (theta)
 * - Niestandardowy: user-defined carrier + beat
 */
enum class BinauralMode(
    val displayNameRes: String,
    val descriptionRes: String,
    val carrierHz: Float,
    val beatHz: Float,
    val defaultVolume: Float
) {
    RELAKS(
        displayNameRes = "mode_relax",
        descriptionRes = "mode_relax_desc",
        carrierHz = 220f,
        beatHz = 9f,
        defaultVolume = 0.35f
    ),
    SKUPIENIE(
        displayNameRes = "mode_focus",
        descriptionRes = "mode_focus_desc",
        carrierHz = 220f,
        beatHz = 16f,
        defaultVolume = 0.35f
    ),
    CZYTANIE(
        displayNameRes = "mode_reading",
        descriptionRes = "mode_reading_desc",
        carrierHz = 210f,
        beatHz = 13f,
        defaultVolume = 0.32f
    ),
    ENERGIA(
        displayNameRes = "mode_energy",
        descriptionRes = "mode_energy_desc",
        carrierHz = 230f,
        beatHz = 22f,
        defaultVolume = 0.38f
    ),
    SEN(
        displayNameRes = "mode_sleep",
        descriptionRes = "mode_sleep_desc",
        carrierHz = 180f,
        beatHz = 3f,
        defaultVolume = 0.22f
    ),
    MEDYTACJA(
        displayNameRes = "mode_meditation",
        descriptionRes = "mode_meditation_desc",
        carrierHz = 200f,
        beatHz = 6f,
        defaultVolume = 0.30f
    ),
    NIESTANDARDOWY(
        displayNameRes = "mode_custom",
        descriptionRes = "mode_custom_desc",
        carrierHz = 200f,
        beatHz = 10f,
        defaultVolume = 0.30f
    );

    /**
     * Resolves carrier/beat for this mode, then returns left/right ear frequencies.
     * For presets, [customCarrier]/[customBeat] are ignored.
     */
    fun leftHz(customCarrier: Float = carrierHz, customBeat: Float = beatHz): Float {
        val (c, b) = resolvedCarrierBeat(customCarrier, customBeat)
        return FrequencyMath.leftHz(c, b)
    }

    fun rightHz(customCarrier: Float = carrierHz, customBeat: Float = beatHz): Float {
        val (c, b) = resolvedCarrierBeat(customCarrier, customBeat)
        return FrequencyMath.rightHz(c, b)
    }

    fun resolvedCarrierBeat(
        customCarrier: Float = carrierHz,
        customBeat: Float = beatHz
    ): Pair<Float, Float> {
        return if (this == NIESTANDARDOWY) {
            FrequencyMath.clampCarrier(customCarrier) to FrequencyMath.clampBeat(customBeat)
        } else {
            carrierHz to beatHz
        }
    }

    companion object {
        const val CUSTOM_CARRIER_MIN = FrequencyMath.CARRIER_MIN
        const val CUSTOM_CARRIER_MAX = FrequencyMath.CARRIER_MAX
        const val CUSTOM_BEAT_MIN = FrequencyMath.BEAT_MIN
        const val CUSTOM_BEAT_MAX = FrequencyMath.BEAT_MAX
    }
}

/**
 * Pure frequency helpers — safe for JVM unit tests (no Android deps).
 *
 * L = carrier − beat/2, R = carrier + beat/2, so |R − L| = beat.
 */
object FrequencyMath {
    const val CARRIER_MIN = 80f
    const val CARRIER_MAX = 500f
    const val BEAT_MIN = 1f
    const val BEAT_MAX = 40f
    const val EAR_FREQ_MIN = 20f
    const val EAR_FREQ_MAX = 20_000f

    fun clampCarrier(hz: Float): Float = hz.coerceIn(CARRIER_MIN, CARRIER_MAX)

    fun clampBeat(hz: Float): Float = hz.coerceIn(BEAT_MIN, BEAT_MAX)

    fun clampVolume(volume: Float): Float = volume.coerceIn(0f, 1f)

    fun leftHz(carrierHz: Float, beatHz: Float): Float =
        (carrierHz - beatHz / 2f).coerceIn(EAR_FREQ_MIN, EAR_FREQ_MAX)

    fun rightHz(carrierHz: Float, beatHz: Float): Float =
        (carrierHz + beatHz / 2f).coerceIn(EAR_FREQ_MIN, EAR_FREQ_MAX)

    fun beatFromEars(leftHz: Float, rightHz: Float): Float =
        kotlin.math.abs(rightHz - leftHz)
}
