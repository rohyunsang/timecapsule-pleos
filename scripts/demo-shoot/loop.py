"""Record the PC's speaker output (WASAPI loopback). Usage: python -I loop.py out.wav seconds"""
import sys, wave, numpy as np, soundcard as sc
out, secs = sys.argv[1], float(sys.argv[2])
spk = sc.default_speaker()
mic = sc.get_microphone(id=str(spk.name), include_loopback=True)
sr = 48000
with mic.recorder(samplerate=sr, channels=2, blocksize=1024) as r:
    print("REC", spk.name, flush=True)
    data = r.record(numframes=int(sr * secs))
pcm = (np.clip(data, -1, 1) * 32767).astype("<i2")
with wave.open(out, "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes())
print("DONE", out, flush=True)
