"""Release-APK smoke test; no run-as or data injection. Emulator only."""
from tc_ui import *
P='io.timecapsule.vehicle'
def park():
 put('/vehicle/controls?speed_kph=0&accelerator_pct=0&brake_pct=0&steering_deg=0');put('/vehicle/gear?gear=Park');put('/vehicle/turn-signal?mode=Off');time.sleep(1)
def show():
 adb('shell','am','start','--user','10','-n',P+'/.MainActivity');time.sleep(1);adb('shell','input','tap','1000','150');time.sleep(1)
def probe(cmd,action=None):
 a=['shell','am','start-foreground-service','--user','10','-n','io.timecapsule.focusprobe/.MediaProbeService','--es','command',cmd]
 if action:a+=['--es','action',action+'_CAPSULE_RECORDING']
 adb(*a)
def check(s):print(s+'_PASS',flush=True)
adb('shell','am','force-stop','--user','10',P);park();adb('emu','geo','fix','128.9496','37.7714');show();time.sleep(2)
tap('이번 주행 기록 준비');tap('동의 확인 · 기록 준비');time.sleep(1);tap('내비 화면으로 이동');time.sleep(1)
put('/vehicle/gear?gear=Drive');put('/vehicle/controls?speed_kph=30&accelerator_pct=0&brake_pct=0&steering_deg=0');time.sleep(1)
adb('logcat','-c');probe('FOCUS');time.sleep(1);probe('CUSTOM','START');time.sleep(1)
logs=adb('logcat','-d','-s','TimeCapsule:I')
assert 'RECORD_STARTED' not in logs,logs;check('RECORD_START_DURING_EXISTING_GUIDANCE_BLOCKED')
time.sleep(6);probe('CUSTOM','START');time.sleep(2)
logs=adb('logcat','-d','-s','TimeCapsule:I');assert 'RECORD_STARTED gear=GEAR_DRIVE background=true' in logs,logs
check('RELEASE_BACKGROUND_DRIVE_RECORDING')
time.sleep(15)
logs=adb('logcat','-d','-s','TimeCapsule:I');m=re.search(r'RECORD_SAVED id=(\d+) draft=false',logs);assert m,logs
assert re.search(r'topResumedActivity=.*ai\.umos\.maps\.android\.navigation\.app',adb('shell','dumpsys','activity','activities'))
check('RELEASE_AUTOSAVE_WITH_NAVIGATION_FOREGROUND')
adb('shell','input','keyevent','KEYCODE_MEDIA_STOP');park();show();tap('보관함');time.sleep(1)
assert any('15초' in x for x in texts());check('RELEASE_SAVED_CAPSULE_VISIBLE')
tap('삭제');tap('삭제');time.sleep(1);check('RELEASE_TEST_RECORDING_DELETED')
show();tap('출발 준비');check('RELEASE_RECORDING_SUITE')
