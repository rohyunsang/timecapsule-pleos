"""Emulator-only, debuggable APK. Uses a signed controller to test real custom commands.
Does not test Gleo speech recognition or physical steering-wheel input.
"""
from tc_ui import *
P='io.timecapsule.vehicle'
E=pathlib.Path(__file__).resolve().parents[1]/'evidence/v0.3.0';E.mkdir(exist_ok=True)
def park():
 put('/vehicle/controls?speed_kph=0&accelerator_pct=0&brake_pct=0&steering_deg=0');put('/vehicle/gear?gear=Park');put('/vehicle/turn-signal?mode=Off');time.sleep(1)
def show():
 adb('shell','am','start','--user','10','-n',P+'/.MainActivity');time.sleep(1);adb('shell','input','tap','1000','150');time.sleep(1)
def probe(cmd,action=None):
 a=['shell','am','start-foreground-service','--user','10','-n','io.timecapsule.focusprobe/.MediaProbeService','--es','command',cmd]
 if action:a+=['--es','action',action+'_CAPSULE_RECORDING']
 adb(*a)
def nav():
 assert re.search(r'topResumedActivity=.*ai\.umos\.maps\.android\.navigation\.app',adb('shell','dumpsys','activity','activities')),'Navigation lost foreground'
def logs():return adb('logcat','-d','-s','TimeCapsule:I')
def check(s):print(s+'_PASS',flush=True)
def shot(s):(E/s).write_bytes(subprocess.check_output([ADB]+BASE+['exec-out','screencap','-p']))
adb('shell','am','force-stop','--user','10',P);park();adb('emu','geo','fix','128.9496','37.7714');show();time.sleep(2)
original=items();originalIds={x['id'] for x in original}
(E/'original-test-ids.json').write_text(json.dumps(list(originalIds)))
adb('logcat','-c');probe('CUSTOM','START');time.sleep(2)
assert 'RECORD_STARTED' not in logs();check('UNPREPARED_RECORDING_REJECTED')
tap('이번 주행 기록 준비');tap('동의 확인 · 기록 준비');time.sleep(2)
s=adb('shell','dumpsys','activity','services',P)
assert 'isForeground=true' in s,s
assert 'RECORD_STARTED' not in logs();check('PREPARATION_DOES_NOT_CAPTURE_AUDIO')
shot('01-recording-preparation.png')
tap('내비 화면으로 이동');time.sleep(1)
put('/vehicle/gear?gear=Drive');put('/vehicle/controls?speed_kph=30&accelerator_pct=0&brake_pct=0&steering_deg=0');time.sleep(1)
probe('CUSTOM','START');time.sleep(2);nav()
assert 'RECORD_STARTED gear=GEAR_DRIVE background=true' in logs(),logs()[-2500:]
check('DRIVE_BACKGROUND_RECORDING_WITH_NAVIGATION')
shot('02-navigation-recording.png')
# A repeated start must not extend the original timer.
probe('CUSTOM','START');time.sleep(2)
assert logs().count('RECORD_STARTED')==1;check('DUPLICATE_COMMAND_DOES_NOT_RESTART_RECORDING')
adb('emu','geo','fix','128.9496','37.7800');time.sleep(12);nav()
new=[x for x in items() if x['id'] not in originalIds]
assert len(new)==1 and not new[0]['draft'],new
assert new[0]['duration']==15 and abs(new[0]['lat']-37.7714)<.00001,new
check('FIFTEEN_SECOND_AUTOSAVE_AT_START_LOCATION')
# Focus interruption must preserve a draft, and never auto-record after focus returns.
probe('CUSTOM','START');time.sleep(2);probe('FOCUS');time.sleep(1);nav()
new=[x for x in items() if x['id'] not in originalIds]
assert len(new)==2 and new[0]['draft'],new
check('NAVIGATION_FOCUS_PRESERVES_RECORDING_DRAFT')
time.sleep(6);assert logs().count('RECORD_STARTED')==2;check('NO_AUTOMATIC_RECORDING_AFTER_GUIDANCE')
probe('CUSTOM','START');time.sleep(2);probe('CUSTOM','CANCEL');time.sleep(2)
assert len(items())==len(original)+2;check('EXPLICIT_CANCEL_DISCARDS_CURRENT_RECORDING')
# Actual record-specific key; normal play must never trigger recording.
adb('shell','input','keyevent','KEYCODE_MEDIA_PLAY');time.sleep(1)
assert logs().count('RECORD_STARTED')==3;check('PLAY_KEY_NEVER_RECORDS')
adb('shell','input','keyevent','KEYCODE_MEDIA_RECORD');time.sleep(2)
assert logs().count('RECORD_STARTED')==4,logs()[-2500:];check('DEDICATED_RECORD_KEY_STARTS_WITHOUT_UI')
put('/vehicle/turn-signal?mode=BlinkLeft');time.sleep(1)
new=[x for x in items() if x['id'] not in originalIds];assert len(new)==3 and new[0]['draft'];check('TURN_SIGNAL_PRESERVES_DRAFT')
put('/vehicle/turn-signal?mode=Off');time.sleep(1)
adb('shell','input','keyevent','KEYCODE_MEDIA_STOP');time.sleep(1)
assert 'isForeground=true' not in adb('shell','dumpsys','activity','services',P);check('STOP_RELEASES_MICROPHONE_SESSION')
# Only delete records made by this test, through the application's parked UI.
park();show();tap('보관함');time.sleep(1)
for _ in range(3):tap('삭제');tap('삭제');time.sleep(.5)
assert {x['id'] for x in items()}==originalIds;check('TEST_RECORDINGS_CLEANED')
show();tap('출발 준비');shot('03-final-preparation.png')
(E/'driving-recording-log.txt').write_text(logs())
check('DRIVING_RECORDING_SUITE')
