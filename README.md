# Binaural Waves (binaural-beats)

Android app (Kotlin + Jetpack Compose, Material 3) that generates stereo sine tones with an L/R frequency difference — the classic **binaural beat** — layered with a library of real-world ambient soundscapes.

**applicationId:** `com.miferstlab.binauralbeats`  
**minSdk 26 · targetSdk 35 · Gradle Kotlin DSL**

---

## Features

- Modes: **Relax**, **Reading**, **Focus**, **Energy**, **Sleep**, **Meditation**, **Custom**
- Real-time synthesis via `AudioTrack` (PCM 16-bit)
- Foreground service — keeps playing with the screen off; notification with **Stop**
- Volume slider with −/+ (1%) buttons, play/pause
- **Ambient library**: 55 recordings and soundscapes (~3–5 min each, seamless loops) in 10 groups —
  Nature, Joyful, Calm, Focus, Sleep, Melancholic, Crime & Noir, Fantasy, Sci‑Fi, Dark ambient — with separate volume and ±1% buttons
- **Sound credits** screen (Settings → About)
- **Mix with other apps** on by default (Spotify is not paused)
- 7-day free trial of the full app from install; after that playback is blocked until a Premium subscription (`binaural_premium`, monthly or yearly base plan, each with a 7-day Play free-trial offer). No free tier, no per-track locks
- English UI, calm dark "galactic" theme

### Frequency presets

| Mode       | Beat (Δf) | Carrier   | Notes                |
|------------|-----------|-----------|----------------------|
| Relax      | ~9 Hz     | ~220 Hz   | alpha                |
| Reading    | ~13 Hz    | ~210 Hz   | SMR / low beta       |
| Focus      | ~16 Hz    | ~220 Hz   | beta (also work)     |
| Energy     | ~22 Hz    | ~230 Hz   | high beta / sport    |
| Sleep      | ~3 Hz     | ~180 Hz   | lower default volume |
| Meditation | ~6 Hz     | ~200 Hz   | theta                |
| Custom     | 1–40 Hz   | 80–500 Hz | user sliders         |

Left ≈ carrier − beat/2, right ≈ carrier + beat/2.

---

## Ambient library

- Audio: `app/src/main/assets/ambient/<key>.ogg` — OGG Vorbis q0 (~64 kbps), 44.1 kHz stereo, ~5 min,
  loudness-normalised to −20 LUFS, 4 s equal-power crossfade baked into the loop seam. At runtime (1.5.3+)
  the clip is decoded pass after pass with MediaExtractor/MediaCodec on a worker thread and written to the app's
  own streaming AudioTrack; every wrap is a sample-accurate ~2 s equal-power crossfade (`audio/SeamlessPcmLooper.kt`,
  `audio/MediaCodecPassSource.kt`, `audio/AmbientPlayer.kt`; multi-cycle JVM tests in `SeamlessPcmLooperTest`).
  No media player has to "get past the end" any more, so nothing can stop the bed after one pass.
- Battery usage **Restricted** (Settings → Apps → Binaural Waves → Battery) makes Android stop background playback
  shortly after the screen turns off; the app warns about it when you press Play.
- Sources: Freesound.org, **CC0 1.0 only** — see `app/src/main/assets/ambient/CREDITS.md` / `manifest.json`.
- Spec: `tools/ambient_tracks.json` (source id, excerpt start, category, English name).
- Rebuild audio: `python3 tools/build_ambient_library.py` (needs ffmpeg + numpy).
- Regenerate Kotlin enum + strings: `python3 tools/gen_ambient_kotlin.py`.
- No per-track tiers: every track is available during the trial and with Premium; after the trial, without Premium, nothing plays.

---

## Spotify / coexisting with music

Audio goes to the **media stream** (same volume as Spotify).

- **Mix with other apps** (default on): no exclusive audio focus — Spotify keeps playing under/over the tones.
- Mix off: other players pause.

See `BinauralAudioEngine`, `BinauralPlaybackService`.

---

## Disclaimer

This app is not a medical device and is not medical advice. It does not diagnose, treat, or prevent any disease. Consult a physician for health concerns.

---

## Build

- Android Studio Ladybug+ (AGP 8.7), JDK 17, Android SDK 35

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests
```

---

## Release build & Google Play

```bash
./gradlew bundleRelease   # app/build/outputs/bundle/release/app-release.aab (signed if keystore configured)
```

Signing: create `keystore.properties` in the repo root (git-ignored; never commit it or the keystore):

```
storeFile=/absolute/path/to/upload.jks
storePassword=...
keyAlias=binaural-upload
keyPassword=...
```

or set env vars `BINAURAL_KEYSTORE_FILE`, `BINAURAL_KEYSTORE_PASSWORD`, `BINAURAL_KEY_ALIAS`,
`BINAURAL_KEY_PASSWORD`. Without either, `bundleRelease` produces an unsigned bundle and debug/CI builds are unaffected.

Play Console setup for billing (Monetize → Products → Subscriptions):

1. Create subscription `binaural_premium`.
2. Base plan `monthly` (auto-renewing, billing period 1 month) and base plan `yearly` (1 year); set prices
   (reference: 14.99 PLN/month, 99.99 PLN/year, ~3.99 USD / 24.99 USD) and activate both.
3. On each base plan add an offer with a **Free trial, 7 days**, eligibility "new customer acquisition"; activate.
4. Add license testers (Settings → License testing) and install the app from an Internal testing track
   (billing only works for a Play-installed build signed with the Play-registered key).

The app reads prices from Play at runtime; do not hardcode them.

## Privacy / Play Console

- No data collection — no analytics, accounts, ads, location or contacts. Data safety: *No data collected*.
- Permissions (release manifest): `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK` — background playback and the Stop notification; `INTERNET`, `ACCESS_NETWORK_STATE`, `com.android.vending.BILLING` — added by Google Play Billing for the subscription. Full explanation: `store/PRIVACY.md`.
- Policy text: `store/PRIVACY.md`; listing text: `store/PLAY.md`.

---

## Structure

```
app/src/main/java/com/miferstlab/binauralbeats/
  audio/BinauralAudioEngine.kt, AmbientPlayer.kt
  service/BinauralPlaybackService.kt
  viewmodel/BinauralViewModel.kt
  data/BinauralMode.kt, AmbientSound.kt (generated), Entitlements.kt
  ui/screens/HomeScreen.kt, SettingsScreen.kt, CreditsScreen.kt
  ui/components/AmbientPicker.kt, ui/AmbientText.kt (generated)
  MainActivity.kt
```

---

## Review notes (historical)

1. **AudioTrack start/stop/pause** — synchronised start/stop/pause/resume; stop pauses/flushes first (unblocks `WRITE_BLOCKING`), then join, then release.
2. **Audio focus** — `AudioFocusRequest` kept in `FocusHolder` and abandoned on stop/destroy.
3. **FGS / Android 14+** — `startForeground` immediately with type `mediaPlayback`; silent notification + "Paused" state.
4. **ViewModel binding** — bind only after `startForegroundService`; `togglePlayPause` uses pause/resume.
5. **POST_NOTIFICATIONS** — requested once, on first play (API 33+).
6. **L/R + volume** — pure `FrequencyMath` helpers; soft amplitude ceiling (~0.9).
7. **Navigation** — Home/Settings/Credits via `rememberSaveable`, system Back supported.
8. **JVM tests** — `FrequencyMathTest`, `BinauralModeTest`, `EntitlementsTest`, `AmbientSoundTest`.

### Known limitations

- Without notification permission (API 33+) the FGS still runs, but there is no Stop action in the shade.
- Turning "Mix with other apps" off restarts the AudioTrack session.
- No separate Stop button in the UI (it is in the notification); pause keeps the FGS session.
