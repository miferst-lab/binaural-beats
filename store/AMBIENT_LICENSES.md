# Ambient sound licenses

The ambient library (v1.5.0+) lives in `app/src/main/assets/ambient/`.
Full per-track credits (source URL, author, license) are in:

- `app/src/main/assets/ambient/CREDITS.md` — human-readable
- `app/src/main/assets/ambient/manifest.json` — machine-readable
- In the app: **Settings → About → Sound credits**

All tracks are **Freesound.org recordings released under CC0 1.0** (public-domain dedication):
commercial use in a paid app is allowed, no attribution required (we credit anyway).
No NC/ND, Epidemic Sound or unclear-license material is included.

The previous BigSoundBank (CC0) loops used up to v1.4.0 were removed.

Regenerate: `python3 tools/build_ambient_library.py` then `python3 tools/gen_ambient_kotlin.py`
(spec: `tools/ambient_tracks.json`).
