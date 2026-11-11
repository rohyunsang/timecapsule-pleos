A="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
P=app.timecapsule.proto
SHOOT="$(pwd)"
WSHOOT="$(pwd -W 2>/dev/null || pwd)"
tap() { "$A" shell input tap "$1" "$2"; }
shot() { "$A" exec-out screencap -p > "$SHOOT/$1.png"; }
vc() { curl -s -m 3 -o /dev/null -w "%{http_code} " -X PUT "http://127.0.0.1:8088$1"; }
rec() { "$A" emu screenrecord start --size 2560x1440 --bit-rate 24000000 --fps 30 --time-limit "${2:-60}" "$WSHOOT/$1.webm"; }
stoprec() { "$A" emu screenrecord stop; }
