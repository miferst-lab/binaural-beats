# Ambient sound licenses

The ambient library (v1.5.0+) lives in `app/src/main/assets/ambient/`.
Full per-track credits (source URL, author, license) are in:

- `app/src/main/assets/ambient/CREDITS.md` — human-readable
- `app/src/main/assets/ambient/manifest.json` — machine-readable
- In the app: **Settings → About → Sound credits**

## Audit (v1.6.0, 10 October 2026) — superseded by 55 files in v1.7.0, see the Dark ambient section below

The release AAB contains exactly 44 ambient `.ogg` files (plus `CREDITS.md` and `manifest.json`); no other
audio files exist in `res/raw`, `assets` or the AAB (the only `res/raw` entries are Play Billing metadata).
Every one of the 44 files has an entry in `manifest.json` (44 unique Freesound IDs, no file without an entry,
no entry without a file), matching `tools/ambient_tracks.json`, `AmbientSound.kt` and `CREDITS.md`.

| Source | Tracks | License |
|---|---|---|
| Freesound.org (individual authors) | 44 | CC0 1.0 Universal (public-domain dedication) |
| Sonniss GameAudioGDC | 0 | — not used (no mention in code, assets, tools or git history) |
| Pixabay | 0 | — not used (no mention in code, assets, tools or git history) |
| BigSoundBank | 0 | CC0; the 9 short loops used up to v1.4.0 were removed in v1.5.0 |

Freesound CC0 1.0: commercial use and redistribution inside a paid app are allowed, no attribution required
(we credit every author anyway in Settings → About → Sound credits). No NC/ND, Epidemic Sound or
unclear-license material is included. The license recorded for each file is the one in the repo's own
`manifest.json`; it was originally taken from the Freesound page of each sound (spot check of sound 592482
shows the CC0 deed link). Processing (trim, loudness normalisation, loop crossfade, OGG encoding) is allowed
under CC0.

### If Sonniss or Pixabay files are added later
- **Sonniss GameAudioGDC** (https://sonniss.com/gameaudiogdc/): royalty free, commercial use allowed, no
  attribution required, unlimited projects, editing allowed; the files may be sold as part of a finished app
  but not redistributed as standalone files or in sound libraries; AI/ML training is prohibited.
- **Pixabay Content License** (https://pixabay.com/service/license-summary/): free use, no attribution
  required, modification allowed; not allowed to sell or distribute the content on a standalone basis
  (unmodified), or to use it in a misleading/illegal way or as part of a trademark. Only the full license
  text is legally binding.
Neither is bundled today, so the credits need no extra attribution lines.

## Dark ambient category (v1.7.0, 10 October 2026)

11 tracks added (library now 55 files, all Freesound CC0 1.0; Sonniss and Pixabay still not used).
Each Freesound page was fetched on 2026-10-10 and shows the CC0 1.0 deed link
(https://creativecommons.org/publicdomain/zero/1.0/); the Freesound search was filtered to
`license: Creative Commons 0`. CC0 allows commercial use, modification and redistribution inside a paid app
without attribution (we credit every author anyway). Added audio: 15.6 MB.
Several tracks are sound-design / synthesised drones rather than field recordings; the in-app text says
"recordings and soundscapes".

| Track | Freesound ID / page | Author | License | Loop length | OGG size | Checked |
|---|---|---|---|---|---|---|
| Tectonic cathedral (`dark_tectonic_cathedral`) — “Tectonic Cathedral Sacred Drone by Mantice” | https://freesound.org/people/bassimat/sounds/854871/ | bassimat | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 296 s | 0.58 MB | 2026-10-10 |
| Ice cathedral (`dark_ice_cathedral`) — “Ice Cathedral Sacred Drone by Mantice” | https://freesound.org/people/bassimat/sounds/856220/ | bassimat | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 296 s | 1.90 MB | 2026-10-10 |
| Primordial forest (`dark_primordial_forest`) — “Primordial Forest Sacred Drone by Mantice” | https://freesound.org/people/bassimat/sounds/854868/ | bassimat | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 296 s | 1.27 MB | 2026-10-10 |
| Howling wind (`dark_howling_wind`) — “Low Wind Howling Indoor” | https://freesound.org/people/Kinoton/sounds/815100/ | Kinoton | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 195 s | 1.40 MB | 2026-10-10 |
| Windswept ridge (`dark_windswept_ridge`) — “Dark Ambient sound windy” | https://freesound.org/people/pgameart/sounds/558147/ | pgameart | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 210 s | 1.54 MB | 2026-10-10 |
| Resonant cave (`dark_resonant_cave`) — “Resonant Cave Experimental Drone by Mantice” | https://freesound.org/people/bassimat/sounds/856219/ | bassimat | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 296 s | 1.76 MB | 2026-10-10 |
| Dark dungeon (`dark_dungeon`) — “Dark Dungeon Ambience” | https://freesound.org/people/Kinoton/sounds/516566/ | Kinoton | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 176 s | 1.19 MB | 2026-10-10 |
| Abandoned warehouse (`dark_abandoned_warehouse`) — “abandoned warehouse” | https://freesound.org/people/Kostrava/sounds/240895/ | Kostrava | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 206 s | 1.54 MB | 2026-10-10 |
| Deep cosmic drone (`dark_cosmic_drone`) — “Deep Cosmic Drone” | https://freesound.org/people/kkenny101/sounds/855487/ | kkenny101 | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 300 s | 0.99 MB | 2026-10-10 |
| Void monolith (`dark_void_monolith`) — “Void Monolith Subharmonic Drone by Mantice” | https://freesound.org/people/bassimat/sounds/854874/ | bassimat | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 296 s | 1.14 MB | 2026-10-10 |
| Deep sea rumble (`dark_deep_sea`) — “Deep Ocean Rumble – Dark Sea Ambience” | https://freesound.org/people/bassimat/sounds/867644/ | bassimat | CC0 1.0 — https://creativecommons.org/publicdomain/zero/1.0/ | 296 s | 2.26 MB | 2026-10-10 |

Regenerate: `python3 tools/build_ambient_library.py` then `python3 tools/gen_ambient_kotlin.py`
(spec: `tools/ambient_tracks.json`).
