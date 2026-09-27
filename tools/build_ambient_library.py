#!/usr/bin/env python3
"""
Build the ambient library shipped in app/src/main/assets/ambient/.

Pipeline per track (spec: tools/ambient_tracks.json, all Freesound CC0 1.0):
  1. fetch a LOOP+XFADE second segment of the Freesound HQ preview (ffmpeg HTTP seek)
     — originals need a logged-in Freesound account; drop originals into
     .ambient_work/originals/<fs>.<ext> to use them instead (higher quality).
  2. loudness-normalise to TARGET_LUFS (ffmpeg loudnorm, two-pass, linear)
  3. seamless loop: equal-power end→start crossfade of XFADE seconds
  4. encode OGG Vorbis (quality VORBIS_Q, 44.1 kHz stereo)
Writes assets/ambient/<key>.ogg, assets/ambient/manifest.json, assets/ambient/CREDITS.md.

Requires: ffmpeg (libvorbis), python3 + numpy.
Usage: python3 tools/build_ambient_library.py [--only key1,key2] [--jobs 6]
"""
from __future__ import annotations
import argparse, json, re, subprocess, sys, concurrent.futures as cf
from pathlib import Path
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "tools/ambient_tracks.json"
OUT = ROOT / "app/src/main/assets/ambient"
WORK = ROOT / ".ambient_work"
SR = 44100
LOOP_S = 300.0      # target loop length (5 min)
XFADE_S = 4.0       # seam crossfade
TARGET_LUFS = -20.0
TRUE_PEAK = -2.0
VORBIS_Q = "0"      # ~64 kbps nominal stereo; ambient beds sound fine here

CAT_TITLES = {
    "nature": "Nature", "joyful": "Joyful & Uplifting", "calm": "Calm & Relax",
    "focus": "Focus", "sleep": "Sleep", "melancholic": "Melancholic",
    "noir": "Crime & Noir", "fantasy": "Fantasy", "scifi": "Sci-Fi",
}

def sh(cmd, **kw):
    return subprocess.run(cmd, check=True, capture_output=True, **kw)

def fetch(t) -> Path:
    seg = WORK / "seg" / f"{t['key']}.wav"
    if seg.exists():
        return seg
    seg.parent.mkdir(parents=True, exist_ok=True)
    orig = next((WORK / "originals").glob(f"{t['fs']}.*"), None) if (WORK / "originals").exists() else None
    src = str(orig) if orig else t["preview"]
    need = LOOP_S + XFADE_S
    tmp = seg.with_suffix(".tmp.wav")
    sh(["ffmpeg", "-y", "-v", "error", "-ss", str(t.get("start", 0)), "-t", f"{need:.2f}",
        "-i", src, "-ac", "2", "-ar", str(SR), "-c:a", "pcm_f32le", str(tmp)])
    tmp.rename(seg)
    return seg

def loudnorm(inp: Path, out: Path):
    r = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(inp), "-af",
                        f"loudnorm=I={TARGET_LUFS}:TP={TRUE_PEAK}:LRA=11:print_format=json",
                        "-f", "null", "-"], capture_output=True, text=True)
    m = json.loads(re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", r.stderr, re.S).group(0))
    af = (f"loudnorm=I={TARGET_LUFS}:TP={TRUE_PEAK}:LRA=11:linear=true:"
          f"measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}:"
          f"measured_thresh={m['input_thresh']}:offset={m['target_offset']}")
    sh(["ffmpeg", "-y", "-v", "error", "-i", str(inp), "-af", af, "-ar", str(SR),
        "-c:a", "pcm_f32le", str(out)])
    return float(m["input_i"])

def measure_lufs(p: Path) -> float:
    r = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(p), "-af", "loudnorm=print_format=json",
                        "-f", "null", "-"], capture_output=True, text=True)
    return float(json.loads(re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", r.stderr, re.S).group(0))["input_i"])

def read_f32(p: Path) -> np.ndarray:
    raw = sh(["ffmpeg", "-v", "error", "-i", str(p), "-f", "f32le", "-ac", "2", "-ar", str(SR), "-"]).stdout
    return np.frombuffer(raw, dtype="<f4").reshape(-1, 2).astype(np.float64)

def seamless(x: np.ndarray) -> np.ndarray:
    """out = x[c:n-c] ++ (tail*cos + head*sin). Wrap point is continuous."""
    n, c = len(x), int(XFADE_S * SR)
    t = np.linspace(0.0, 1.0, c)[:, None]
    fade = x[n - c:] * np.cos(0.5 * np.pi * t) + x[:c] * np.sin(0.5 * np.pi * t)
    return np.concatenate([x[c:n - c], fade])

def build(t) -> dict:
    seg = fetch(t)
    norm = WORK / "norm" / f"{t['key']}.wav"
    norm.parent.mkdir(parents=True, exist_ok=True)
    src_lufs = loudnorm(seg, norm)
    got = measure_lufs(norm)
    if got < TARGET_LUFS - 1.0:
        # loudnorm(linear) could not reach the target without exceeding true peak:
        # add the missing gain behind a latency-compensated limiter (before the seam is built).
        boosted = norm.with_suffix(".boost.wav")
        sh(["ffmpeg", "-y", "-v", "error", "-i", str(norm), "-af",
            f"volume={TARGET_LUFS - got:.2f}dB,alimiter=limit=0.79:level=false:latency=true",
            "-c:a", "pcm_f32le", str(boosted)])
        boosted.replace(norm)
    y = seamless(read_f32(norm))
    peak = float(np.max(np.abs(y)))
    if peak > 0.89:  # keep headroom after crossfade summing
        y *= 0.89 / peak
    OUT.mkdir(parents=True, exist_ok=True)
    ogg = OUT / f"{t['key']}.ogg"
    p = subprocess.Popen(["ffmpeg", "-y", "-v", "error", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                          "-c:a", "libvorbis", "-q:a", VORBIS_Q, "-metadata", f"title={t['name']}",
                          "-metadata", f"artist={t['author']} (Freesound, CC0)", str(ogg)], stdin=subprocess.PIPE)
    p.communicate(y.astype("<f4").tobytes())
    assert p.returncode == 0, t["key"]
    out_lufs = measure_lufs(ogg)
    return dict(key=t["key"], seconds=round(len(y) / SR, 1), bytes=ogg.stat().st_size,
                src_lufs=src_lufs, out_lufs=float(out_lufs))

def write_manifest(spec, stats):
    tracks = []
    for t in spec["tracks"]:
        s = stats.get(t["key"], {})
        tracks.append(dict(key=t["key"], category=t["cat"], name=t["name"], file=f"ambient/{t['key']}.ogg",
                           premium=not t["free"], durationSec=s.get("seconds"), bytes=s.get("bytes"),
                           source="Freesound", sourceId=t["fs"], sourceTitle=t["source_title"],
                           author=t["author"], license=t["license"], url=t["url"]))
    (OUT / "manifest.json").write_text(json.dumps(dict(version=1, loudnessLufs=TARGET_LUFS,
        categories=[dict(key=c, title=CAT_TITLES[c]) for c in spec["categories"]], tracks=tracks),
        indent=1, ensure_ascii=False) + "\n")
    md = ["# Ambient sound credits", "",
          "All ambient loops are real field recordings from [Freesound.org](https://freesound.org), released by their",
          "authors under **Creative Commons 0 (CC0 1.0, public-domain dedication)** — free for commercial use,",
          "no attribution required. We credit every author anyway.", "",
          "Processing: excerpt trimmed to ~5 min, loudness-normalised to -20 LUFS, equal-power 4 s crossfade at the",
          "loop seam, encoded as OGG Vorbis. Built by `tools/build_ambient_library.py` from `tools/ambient_tracks.json`.", ""]
    for c in spec["categories"]:
        md += [f"## {CAT_TITLES[c]}", "", "| Track | Tier | Original title | Author | License | Source |",
               "|---|---|---|---|---|---|"]
        for t in spec["tracks"]:
            if t["cat"] == c:
                md.append(f"| {t['name']} | {'Free' if t['free'] else 'Premium'} | {t['source_title'].replace('|','/')} "
                          f"| {t['author']} | CC0 1.0 | {t['url']} |")
        md.append("")
    md += ["CC0 deed: https://creativecommons.org/publicdomain/zero/1.0/", ""]
    (OUT / "CREDITS.md").write_text("\n".join(md))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only"); ap.add_argument("--jobs", type=int, default=6)
    ap.add_argument("--manifest-only", action="store_true")
    a = ap.parse_args()
    spec = json.loads(SPEC.read_text())
    todo = [t for t in spec["tracks"] if not a.only or t["key"] in a.only.split(",")]
    stats_file = WORK / "stats.json"
    stats = json.loads(stats_file.read_text()) if stats_file.exists() else {}
    if not a.manifest_only:
        with cf.ThreadPoolExecutor(a.jobs) as ex:
            futs = {ex.submit(build, t): t for t in todo}
            for f in cf.as_completed(futs):
                t = futs[f]
                try:
                    s = f.result(); stats[t["key"]] = s
                    print(f"{s['key']:24} {s['seconds']:6.1f}s {s['bytes']/1e6:5.2f} MB  src {s['src_lufs']:6.1f} → {s['out_lufs']:6.1f} LUFS", flush=True)
                except Exception as e:
                    print(f"FAILED {t['key']}: {e}", file=sys.stderr, flush=True)
        WORK.mkdir(exist_ok=True); stats_file.write_text(json.dumps(stats, indent=1))
    write_manifest(spec, stats)
    total = sum((OUT / f"{t['key']}.ogg").stat().st_size for t in spec["tracks"] if (OUT / f"{t['key']}.ogg").exists())
    print(f"total {total/1e6:.1f} MB for {len(spec['tracks'])} tracks")

if __name__ == "__main__":
    main()
