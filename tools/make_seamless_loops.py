#!/usr/bin/env python3
"""Equal-power end→start acrossfade for ambient OGG beds (CC0)."""
from __future__ import annotations
import math, struct, subprocess, tempfile, wave, shutil
from pathlib import Path

CROSS = 2.5
SR = 44100

def run(cmd):
    subprocess.check_call(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def decode(ogg: Path, wav: Path):
    run(["ffmpeg", "-y", "-i", str(ogg), "-acodec", "pcm_s16le", "-ar", str(SR), "-ac", "2", str(wav)])

def encode(wav: Path, ogg: Path):
    run(["ffmpeg", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(ogg)])

def read_pcm(wav: Path):
    with wave.open(str(wav), "rb") as w:
        assert w.getnchannels() == 2 and w.getsampwidth() == 2
        raw = w.readframes(w.getnframes())
        sr = w.getframerate()
    samples = struct.unpack("<" + "h" * (len(raw) // 2), raw)
    L = [samples[i] / 32768.0 for i in range(0, len(samples), 2)]
    R = [samples[i] / 32768.0 for i in range(1, len(samples), 2)]
    return L, R, sr

def write_pcm(wav: Path, L, R, sr: int):
    frames = bytearray()
    for i in range(len(L)):
        for v in (L[i], R[i]):
            frames += struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32767.0))
    with wave.open(str(wav), "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(frames)

def soft_trim(L, R, thresh=0.0005, max_trim_s=0.2):
    max_trim = int(SR * max_trim_s)
    def first_loud(xs):
        for i, v in enumerate(xs):
            if abs(v) >= thresh:
                return min(i, max_trim)
        return 0
    def end_trim(xs):
        for i in range(len(xs) - 1, -1, -1):
            if abs(xs[i]) >= thresh:
                return min(len(xs) - 1 - i, max_trim)
        return 0
    a = min(first_loud(L), first_loud(R), max_trim)
    b = min(end_trim(L), end_trim(R), max_trim)
    return (L[a:len(L) - b], R[a:len(R) - b]) if b else (L[a:], R[a:])

def make_seamless(L, R, cross_s=CROSS):
    n = len(L)
    c = int(cross_s * SR)
    if n <= 2 * c + SR:
        raise SystemExit(f"clip too short ({n/SR:.2f}s) for {cross_s}s crossfade")
    out_L = L[c:n - c][:]
    out_R = R[c:n - c][:]
    for i in range(c):
        t = i / (c - 1) if c > 1 else 1.0
        fo = math.cos(0.5 * math.pi * t)
        fi = math.sin(0.5 * math.pi * t)
        out_L.append(L[n - c + i] * fo + L[i] * fi)
        out_R.append(R[n - c + i] * fo + R[i] * fi)
    return out_L, out_R

def main():
    import sys
    cross = float(sys.argv[1]) if len(sys.argv) > 1 else CROSS
    root = Path(__file__).resolve().parents[1]
    raw = root / "app/src/main/res/raw"
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        for ogg in sorted(raw.glob("ambient_*.ogg")):
            print(f"=== {ogg.name} ===")
            wav_in = td / f"{ogg.stem}_in.wav"
            wav_out = td / f"{ogg.stem}_out.wav"
            ogg_out = td / f"{ogg.stem}_out.ogg"
            decode(ogg, wav_in)
            L, R, sr = read_pcm(wav_in)
            assert sr == SR
            L, R = soft_trim(L, R)
            L2, R2 = make_seamless(L, R, cross)
            write_pcm(wav_out, L2, R2, SR)
            encode(wav_out, ogg_out)
            shutil.copy2(ogg_out, ogg)
            print(f"  {len(L)/SR:.3f}s → {len(L2)/SR:.3f}s  junction={abs(L2[0]-L2[-1]):.6f}")
    print("Done.")

if __name__ == "__main__":
    main()
