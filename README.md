# Binaural Beats / Fale binauralne

Prosta aplikacja Android (Kotlin + Jetpack Compose, Material 3) generująca stereofoniczne tony sinusoidalne z różnicą częstotliwości L/R — klasyczny efekt **binaural beat**.

**applicationId:** `com.miferstlab.binauralbeats`  
**minSdk 26 · targetSdk 35 · Gradle Kotlin DSL**

---

## Funkcje / Features

- Tryby: **Relaks**, **Skupienie**, **Sen**, **Medytacja**, **Niestandardowy**
- Generacja w czasie rzeczywistym przez `AudioTrack` (PCM float / 16-bit)
- Usługa pierwszoplanowa (foreground service) — odtwarzanie przy wyłączonym ekranie + powiadomienie ze **Stop**
- Suwak głośności, play/pause
- Domyślnie **miks z innymi aplikacjami** (Spotify nie jest pauzowane)
- UI po polsku, spokojny ciemny motyw

### Presety częstotliwości

| Tryb        | Beat (Δf) | Nośna (carrier) | Uwagi              |
|-------------|-----------|-----------------|--------------------|
| Relaks      | ~9 Hz     | ~220 Hz         | fale alfa          |
| Skupienie   | ~16 Hz    | ~220 Hz         | fale beta          |
| Sen         | ~3 Hz     | ~180 Hz         | niższa głośność    |
| Medytacja   | ~6 Hz     | ~200 Hz         | fale theta         |
| Niestandardowy | 1–40 Hz | 80–500 Hz     | suwaki użytkownika |

Lewy kanał ≈ carrier − beat/2, prawy ≈ carrier + beat/2.

---

## Spotify / współistnienie z muzyką (ważne)

Aplikacja **nie** przejmuje wyłącznego fokusu mediów (`AUDIOFOCUS_GAIN`), który pauzowałby Spotify.

Zamiast tego:

1. `AudioAttributes`: `USAGE_ASSISTANCE_SONIFICATION` + `CONTENT_TYPE_SONIFICATION` (nie `USAGE_MEDIA` / music).
2. Fokus: `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` — inne odtwarzacze mogą grać dalej (ew. lekko ściszone).
3. W ustawieniach przełącznik **„Miksuj z innymi aplikacjami”** (domyślnie włączony). Wyłączenie przełącza atrybuty na media/music (agresywniejsze wobec innych playerów).

Szczegóły w kodzie: `BinauralAudioEngine`, `BinauralPlaybackService`.

---

## Disclaimer / Zastrzeżenie

**PL:** Ta aplikacja nie jest wyrobem medycznym i nie stanowi porady medycznej. Nie diagnozuje, nie leczy ani nie zapobiega żadnym chorobom. W razie problemów zdrowotnych skonsultuj się z lekarzem.

**EN:** This app is not a medical device and is not medical advice. It does not diagnose, treat, or prevent any disease. Consult a physician for health concerns.

---

## Wymagania / Build

- Android Studio Ladybug+ (lub nowsze z AGP 8.7)
- JDK 17
- Android SDK 35

### Otwórz projekt

1. Sklonuj / rozpakuj repozytorium.
2. Otwórz folder w **Android Studio** (File → Open).
3. Poczekaj na sync Gradle.

### Zbuduj debug APK

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

> Na niektórych środowiskach CI bez SDK build nie przejdzie — lokalnie w Android Studio jest OK.

---

## Privacy / Google Play Console (MVP)

- **Brak zbierania danych** — brak analityki, kont, reklam, lokalizacji, kontaktów.
- Brak backendu / logowania.
- W Play Console Data safety: zaznacz *No data collected* (dla tego MVP).
- Uprawnienia: `FOREGROUND_SERVICE` / `MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK` — wyłącznie do odtwarzania w tle i powiadomienia Stop.

---

## Struktura (skrót)

```
app/src/main/java/com/miferstlab/binauralbeats/
  audio/BinauralAudioEngine.kt   # sine L/R + AudioTrack
  service/BinauralPlaybackService.kt
  viewmodel/BinauralViewModel.kt
  data/BinauralMode.kt
  ui/screens/HomeScreen.kt, SettingsScreen.kt
  MainActivity.kt
```

---

## Licencja

Kod na potrzeby repozytorium `miferst-lab/binaural-beats`. Dostosuj licencję według potrzeb projektu.

---

## Zmiany po review

Krótki opis nienaruszających zakresu poprawek po code review:

1. **AudioTrack start/stop/pause** — synchronizacja `start`/`stop`/`pause`/`resume`; `stop` najpierw pauzuje/flushuje track (odblokowuje `WRITE_BLOCKING`), potem `join`, potem `release`. Ponowne `start` przy aktywnej sesji wywołuje `play()` (naprawa race pause→play).
2. **Audio focus** — `AudioFocusRequest` trzymany w `FocusHolder` i **oddawany** przy stop/destroy; miks ze Spotify nadal przez `TRANSIENT_MAY_DUCK` + sonification usage.
3. **FGS / Android 14+** — `startForeground` natychmiast w `ACTION_START`/`RESUME` z typem `mediaPlayback`; `ServiceCompat.stopForeground`; ciche powiadomienie + stan „Wstrzymano”.
4. **ViewModel binding** — brak `bindService(BIND_AUTO_CREATE)` w `init` (uniknięcie started service bez FGS); bind dopiero po `startForegroundService`; `togglePlayPause` używa pause/resume zamiast zawsze START; ViewModel nie zatrzymuje FGS w `onCleared`.
5. **POST_NOTIFICATIONS** — prośba raz, przy pierwszym play (API 33+), bez spamowania dialogiem przy starcie Activity.
6. **L/R + volume** — czyste helpery `FrequencyMath` (clamp carrier/beat/volume, L/R, Δ); soft ceiling amplitudy (~0.9) przeciw clippingowi.
7. **Konfiguracja** — nawigacja Home/Settings przez `rememberSaveable`.
8. **Testy JVM** — `FrequencyMathTest`, `BinauralModeTest` (`./gradlew test`).
9. **ProGuard** — keep rules dla service/binder/enum/engine (minify nadal wyłączone).

### Znane ograniczenia

- Bez uprawnienia powiadomień (API 33+) FGS może działać, ale użytkownik nie zobaczy akcji Stop w szufladzie.
- Wyłączenie „Miksuj z innymi…” zmienia atrybuty AudioTrack (restart sesji); nie bierze twardego `AUDIOFOCUS_GAIN`.
- Brak osobnego przycisku Stop w UI (jest w powiadomieniu); pause utrzymuje sesję FGS.
