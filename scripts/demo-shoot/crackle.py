"""Crackle detector: counts abrupt sample jumps inside voiced audio (clean TTS scores 0).
Usage: python -I crackle.py file [start end]"""
import sys, subprocess, numpy as np
f = sys.argv[1]
args = ["ffmpeg", "-v", "error"]
if len(sys.argv) > 3: args += ["-ss", sys.argv[2], "-to", sys.argv[3]]
args += ["-i", f, "-vn", "-ac", "1", "-ar", "48000", "-f", "f32le", "-"]
x = np.frombuffer(subprocess.run(args, capture_output=True).stdout, dtype=np.float32)
sr = 48000; hop = sr // 1000
if x.size < hop * 50: print("NOAUDIO"); sys.exit()
n = x.size // hop; x = x[: n * hop]
rms = np.sqrt((x.reshape(n, hop) ** 2).mean(1)) + 1e-9
env = np.convolve(rms, np.ones(30) / 30, "same")
voiced = env > rms.max() * 0.08
d = np.abs(np.diff(x))
mask = np.repeat(voiced, hop)[1:]
idx = np.nonzero((d > 0.25 * np.abs(x).max()) & mask)[0]
print(f"voiced={voiced.sum()/1000:.1f}s jumps={idx.size} peak={20*np.log10(np.abs(x).max()+1e-9):.1f}dB at={[round(i/sr,2) for i in idx[:12]]}")
