# usage: trial.sh name size bitrate fps
. ./h.sh; export MSYS_NO_PATHCONV=1
"$A" shell am force-stop --user 10 $P; "$A" shell am start --user 10 -n $P/app.timecapsule.MainActivity >/dev/null; sleep 6
"$A" logcat -c
"$A" emu screenrecord start --size $2 --bit-rate $3 --fps $4 --time-limit 14 "$WSHOOT/$1.webm" >/dev/null; sleep 1.5
tap 1275 1053; sleep 1.5; tap 1861 765; sleep 9; "$A" emu screenrecord stop >/dev/null; sleep 3
n=$("$A" logcat -d | grep -c "PleosSpeech: speaking")
echo "$1 tts=$n $(python -I crackle.py $1.webm | cut -d' ' -f1-3)"
