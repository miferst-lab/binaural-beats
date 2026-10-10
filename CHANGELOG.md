# Changelog

## 1.6.1

- **Light theme redesign for readability (WCAG AA).** The light palette reused the night neons: cyan
  `#3DE0E8` on white was 1.6:1, teal `#4ECDC4` 1.9:1, amber 1.8:1, sky 1.9:1, ember 2.3:1, violet 3.3:1;
  the outline was 1.7:1 and the slider "inactive" tracks nearly invisible. Light mode now has its own darkened
  accents (`Accents.kt`, via `LocalAccents`), solid white cards with visible borders, a stronger muted-text
  colour, 3:1+ outlines/switch/slider tracks, white-on-teal play button, light dialogs and explicit
  surfaceContainer colours. Night colours are unchanged.
- Status/navigation bar icons now follow the app theme (not only the device theme); the window background no
  longer flashes dark in light mode.
- New `ThemeContrastTest` computes WCAG ratios for the key text/background/control pairs of both themes.
- **English default resources, Polish in `values-pl`.** `values-pl` previously held only the billing strings;
  it now translates every string (modes, settings, notification, paywall, trial/battery dialogs, credits,
  all 44 ambient names and 9 categories). `tools/gen_ambient_kotlin.py` generates both ambient blocks
  (Polish names in `tools/ambient_pl.json`). Fixed Polish grammar in the billing-unavailable message.
- App language follows the device language (English fallback).

## 1.6.0

- **Real Google Play Billing (billing library 8.3.0), as a subscription.** Subscription id `binaural_premium`
  with base plans `monthly` (P1M) and `yearly` (P1Y), each with a 7-day free-trial offer. Prices are never
  hardcoded: the paywall shows the localized price strings from Play `ProductDetails` at runtime; if Play or
  the product is unavailable (offline, not created yet) a clear PL/EN message is shown and the trial is untouched.
- `BillingManager` (testable via `BillingGateway` interface, unit-tested with a fake): connect with
  exponential-backoff retry, query SUBS details, `launchBillingFlow` with the chosen plan's offer token,
  acknowledge, restore/re-check on app start and every resume (`queryPurchasesAsync(SUBS)`). Premium only
  while Play reports an active subscription (expiry, refund → locked again in release builds; offline keeps
  last known state). PENDING purchases never unlock.
- **Trial interaction:** the in-app 7 days from install stay for users who never subscribed. The Play free-trial
  offer is only used once the in-app trial ended; subscribing during the in-app trial gets the plain base plan,
  so nobody receives 14 free days by accident.
- Debug premium switch and "Remove Premium (debug)" are gated by `BuildConfig.DEBUG`; release builds clear
  any leftover debug flag and never honor it.
- Release signing via git-ignored `keystore.properties` or env vars; R8 minify + resource shrinking enabled
  for release (keep rules for billing, audio, service).
- Version 1.6.0 (versionCode 12)

## 1.5.3

- **Fix: ambient beds still stopped after the first pass on the owner's phone with 1.5.2.** The phone ran 1.5.0
  (single ExoPlayer, `REPEAT_MODE_ONE`) before 1.5.2, never 1.5.1, so the 1.5.2 diagnosis (1.5.1's
  `REPEAT_MODE_OFF`) did not apply; both builds depended on ExoPlayer wrapping / a main-thread handover timer.
- Ambient playback rewritten without ExoPlayer: MediaExtractor + MediaCodec decode each pass (fresh decoder
  per pass) into the app's own streaming AudioTrack on a worker thread; `SeamlessPcmLooper` crossfades the last
  2 s of every pass into the first 2 s of the next (equal power, sample-accurate). Decoder failures reopen the
  clip with the same crossfade, a dead AudioTrack is rebuilt, partial wake lock while playing. Media3 dependency removed.
- Binaural tone: a failed `AudioTrack.write` (e.g. dead track after an output change) no longer ends the
  generator thread silently; the track is rebuilt and generation continues.
- Battery usage "Restricted" detection: Play shows a one-time dialog with a shortcut to App info, and the
  service logs a warning (Android demotes the playback service at screen-off for restricted apps).
- Tests: `SeamlessPcmLooperTest` (12–50 passes, exact crossfade samples, decoder death mid-pass, failed reopens
  with back-off, empty reads, clip shorter than the crossfade) replaces `AmbientLoopControllerTest`.
- Version 1.5.3 (versionCode 11)

## 1.5.2

- **Fix: ambient beds stopped after one pass (~5 min).** In 1.5.1 both crossfade players ran with
  `REPEAT_MODE_OFF`, so looping depended entirely on the app-side handover timer; whenever that handover
  did not run in time (main-thread Handler stalled, screen off without a player wake lock, player error on the
  incoming player) the active clip reached its end and the bed went silent.
- Loop logic moved into a pure, unit-tested `AmbientLoopController` (fade progress from elapsed real time,
  not tick counting); both ExoPlayers now use `REPEAT_MODE_ONE` as a fallback loop and `WAKE_MODE_LOCAL`;
  a failed player is re-prepared and the other one takes over (fade aborted if the incoming player fails).
- New headless tests simulate 12 loop cycles, stalled/missing ticks, player failures, pause mid-fade.
- Version 1.5.2 (versionCode 10)

## 1.5.1

- **Ambient loop seams**: dual ExoPlayer equal-power crossfade (~2 s) at loop boundaries replaces
  single-player `REPEAT_MODE_ONE`, which left an audible OGG/Vorbis decoder gap every ~5 minutes
  despite the baked 4 s end→start crossfade in assets. Backup position poll + `STATE_ENDED` safety net.
- Version 1.5.1 (versionCode 9)


## 1.5.0

- **New ambient library**: 44 real field recordings from Freesound (CC0 1.0 only), ~5 min each,
  loudness-normalised to −20 LUFS, 4 s equal-power crossfade at the loop seam, OGG Vorbis q0 in `assets/ambient/`
- 9 groups: Nature, Joyful, Calm, Focus, Sleep, Melancholic, Crime & Noir, Fantasy, Sci‑Fi;
  ambient picker now shows category chips + tracks of the selected category (volume slider and ±1% kept)
- Business model: 7-day free trial of the full app (all 44 tracks, all modes), then playback is blocked until Premium
  (`binaural_premium_unlock`). **No free tier and no per-track free/premium split** — the `free` flag was removed from
  `tools/ambient_tracks.json`, the `premium` field from `manifest.json`, the Tier column from `CREDITS.md`,
  `AmbientSound.isPremium` / `freeEntries` / `premiumEntries`, and the lock icons + "Premium ambient" upsell dialog from the picker
- Trial gate is also enforced in the playback service (checked every minute while playing), so a session started
  before the trial ends cannot keep playing after it once the Activity/ViewModel is gone
- Store listing (`store/PLAY.md`), README and Settings copy describe the 7-day trial → Premium model
- Old ambient selections (≤1.4.0) migrate to the closest new track
- **Sound credits** screen (Settings → About → Sound credits); `CREDITS.md` + `manifest.json` next to the audio
- **App switched to English**: all UI strings, mode names, trial/paywall texts, notifications, store listing,
  privacy policy, README (Polish `values-pl` removed); mode enum constants renamed to English
- Version shown in About now comes from the package; system Back navigates Settings/Credits
- Tooling: `tools/build_ambient_library.py`, `tools/gen_ambient_kotlin.py`, spec `tools/ambient_tracks.json`
  (replaces `tools/make_seamless_loops.py`); old BigSoundBank `res/raw` loops removed

## 1.4.0

- Seamless ambient loops (v2): equal-power end→start acrossfade (~2.5s) re-encoded into every OGG bed + gapless ExoPlayer `REPEAT_MODE_ONE` (replaces dual MediaPlayer crossfade that still clicked on thunder/cave)
- Freemium: **7-day trial from first install** replaces the 30-minute per-session listen clock; after expiry playback is blocked until Premium (`binaural_premium_unlock`)
- Trial clock rollback clamp (`trial_furthest_now_ms`); debug Premium unlock kept
- Tooling: `tools/make_seamless_loops.py` for regenerating looped beds

## 1.3.1

- Truly seamless ambient loops: dual MediaPlayer equal-power crossfade ~1.8s (was ~280ms), seek-complete before overlap, completion safety net
- Re-encoded all ambient OGG beds with silence trim + end→start acrossfade (helps rain/thunder wrap)
- Dedicated premium assets: Cave drip (BigSoundBank Cave #1), Soft thunder (rain/thunder EQ), Mountain wind
- Cave (jaskinia) no longer reuses stream — was effectively wrong/broken for that bed

## 1.3.0

- Seamless ambient loops via dual MediaPlayer crossfade (fixes silence gap on OGG wrap)
- Freemium: free tier hard 30-minute listen clock per session; Premium unlimited
- Premium ambient placeholders (Mountain wind, Cave drip, Soft thunder) with lock UI
- Debug premium unlock in Settings; Play Billing product id `binaural_premium_unlock` (scaffolding)
- AmbientSound without Android R (unit-test friendly)


## 1.2.0

- Ambient background loops (6 CC0 beds + Off) with separate volume
- − / + buttons next to binaural and ambient volume sliders (1% steps)
- Persist ambient selection and ambient volume


## 1.1.2 — Czytanie i Energia

- Nowe presety: **Czytanie** (~13 Hz SMR), **Energia** (~22 Hz, sport)
- **Skupienie** obejmuje też pracę (osobny tryb byłby tym samym pasmem beta)
- Praca nie dostała osobnego chipa — to ten sam zakres co Skupienie


## 1.1.1 — ikona i Play

- Nowa ikona (neonowa nieskończoność cyan/fiolet)
- Grafiki pod Google Play w `store/`
- Szkic listingu i polityki prywatności


## 1.1.0 — galactic night

- Night-first cosmic UI: void backgrounds, nebula accents (cyan / violet / magenta), soft starlight text
- `GalaxyBackdrop` behind Home + Settings; animated `BrainWaveVisual` while playing
- Appearance setting: **Nocny** (default) | **System** | **Jasny**, persisted in SharedPreferences (`binaural` / `appearance_mode`)
- Glass-ish cards, glowing play FAB, polished chips — audio engine unchanged

## 1.0.1 — cisza na telefonie

- Audio idzie na `USAGE_MEDIA` (głośność multimediów), nie na sonification/powiadomienia
- PCM 16-bit jako domyślny (float bywa niemy na OEM)
- Przy miksie z innymi apkami: brak audio focus (Spotify nie pauzuje)
- Fallback sample rate 44.1/48 kHz + logi `BinauralAudio`

## 1.0.0 — po code review

### Naprawione
- Race AudioTrack pause→play (brak `play()` przy ponownym starcie aktywnej sesji)
- Race stop vs `WRITE_BLOCKING` (flush/stop przed join/release)
- Brak `abandonAudioFocus` / utrata referencji `AudioFocusRequest`
- Ryzyko started service bez `startForeground` przez `bindService` w `init` ViewModelu
- Aktualizacja powiadomienia przy pauzie; poprawniejszy kontrakt FGS (Android 14+)
- Clamp L/R i walidacja custom carrier/beat w czystych helperach
- Soft volume scaling (headroom) przeciw clippingowi
- Permission `POST_NOTIFICATIONS` proszone przy play, nie przy każdym cold start

### Dodane
- JVM unit testy częstotliwości (`FrequencyMath`, `BinauralMode`)
- ProGuard keep rules
- Lekki polish UI (hierarchia, feedback trybu, głośność, tip słuchawek, ustawienia)

### Znane ograniczenia
- Patrz README → „Zmiany po review”
