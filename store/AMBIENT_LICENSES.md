# Ambient sound licenses

The ambient library (v1.5.0+) lives in `app/src/main/assets/ambient/`.
Full per-track credits (source URL, author, license) are in:

- `app/src/main/assets/ambient/CREDITS.md` — human-readable
- `app/src/main/assets/ambient/manifest.json` — machine-readable
- In the app: **Settings → About → Sound credits**

## Audit (v1.6.0, 10 October 2026)

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

Regenerate: `python3 tools/build_ambient_library.py` then `python3 tools/gen_ambient_kotlin.py`
(spec: `tools/ambient_tracks.json`).
