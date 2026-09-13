# Changelog

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
