"""AirSky Browser の起動音(app/src/main/assets/boot.ogg)を作る。

静かで明るい、短いチャイム: 柔らかいベルの 2 音(A5 → E6)と、その下でふくらむ A メジャーの淡い和音。
軽い左右のディレイと残響で、最後は自然に消える(約 2.2 秒)。

    python tools/gen_boot_sound.py      # ffmpeg (libvorbis) が必要
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 48000
LENGTH = 2.3


def bell(freq, start, dur, gain):
    """サイン波に少しだけ倍音を足した、角の取れたベルの音。"""
    t = np.arange(int(dur * SR)) / SR
    attack = np.minimum(1.0, t / 0.012)
    decay = np.exp(-t * 3.2)
    tone = (np.sin(2 * np.pi * freq * t)
            + 0.22 * np.sin(2 * np.pi * freq * 2.0 * t) * np.exp(-t * 6)
            + 0.06 * np.sin(2 * np.pi * freq * 3.01 * t) * np.exp(-t * 9))
    out = np.zeros(int(LENGTH * SR))
    s = int(start * SR)
    seg = tone * attack * decay * gain
    out[s:s + len(seg)] += seg[:len(out) - s]
    return out


def pad(freqs, start, dur, gain):
    """ゆっくりふくらんで消える、淡い和音。"""
    t = np.arange(int(dur * SR)) / SR
    env = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 2
    tone = sum(np.sin(2 * np.pi * f * t + i) for i, f in enumerate(freqs)) / len(freqs)
    out = np.zeros(int(LENGTH * SR))
    s = int(start * SR)
    seg = tone * env * gain
    out[s:s + len(seg)] += seg[:len(out) - s]
    return out


def delay(x, seconds, feedback, mix):
    d = int(seconds * SR)
    y = x.copy()
    for k in range(1, 6):
        shifted = np.zeros_like(x)
        shifted[d * k:] = x[:len(x) - d * k] * (feedback ** k)
        y += shifted * mix
    return y


def main():
    dry = (bell(880.0, 0.00, 2.0, 0.55)        # A5
           + bell(1318.5, 0.13, 2.0, 0.42)     # E6
           + bell(1760.0, 0.26, 1.6, 0.12)     # A6(ほんの少し)
           + pad([440.0, 554.4, 659.3], 0.05, 1.9, 0.10))
    left = delay(dry, 0.19, 0.45, 0.30)
    right = delay(dry, 0.27, 0.42, 0.30)
    stereo = np.stack([left, right], axis=1)
    # 最後の 0.4 秒でなめらかに 0 へ
    fade = np.ones(len(stereo))
    n = int(0.4 * SR)
    fade[-n:] = np.linspace(1, 0, n) ** 2
    stereo *= fade[:, None]
    # 目立ちすぎないよう、ピークを -12 dBFS にそろえる
    stereo *= (10 ** (-12 / 20)) / np.max(np.abs(stereo))
    pcm = (stereo * 32767).astype("<i2")

    out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "boot.ogg")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "boot.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(2)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
                        "-c:a", "libvorbis", "-q:a", "5", os.path.abspath(out)], check=True)
    print("wrote", os.path.abspath(out))


if __name__ == "__main__":
    sys.exit(main())
