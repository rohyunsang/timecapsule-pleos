"""Emulator-only integration. Seeds synthetic 30-second silent AAC into a debuggable build.
Never use on a physical car. Back up original preferences and clean only ux-v02 fixtures.
Requires the official Virtual Console open at localhost:8088 and signed probe APK installed.
"""
from tc_ui import *
import shutil, shlex, sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'evidence'/('v0.3.0' if '--reuse-fixtures' in sys.argv else 'v0.2.0');EVIDENCE.mkdir(exist_ok=True)
P='io.timecapsule.vehicle'
def shot(name):(EVIDENCE/name).write_bytes(subprocess.check_output([ADB]+BASE+['exec-out','screencap','-p']))
def data(path,content):
 subprocess.run([ADB]+BASE+['shell','run-as',P,'--user','10','sh','-c',shlex.quote(f'cat > {path}')],input=content,check=True)
def state():
 s=adb('shell','dumpsys','media_session')
 blocks=s.split('package=io.timecapsule.vehicle')
 assert len(blocks)>1,s[-3000:]
 m=re.search(r'state=PlaybackState \{state=(\w+)\(\d+\), position=(\d+)',blocks[1])
 if not m:m=re.search(r'state=(\w+)\(\d+\), position=(\d+)',blocks[1])
 assert m,blocks[1][:3000]
 return m.group(1),int(m.group(2))
def nav_visible():
 s=adb('shell','dumpsys','activity','activities')
 assert re.search(r'topResumedActivity=.*ai\.umos\.maps\.android\.navigation\.app',s),'navigation lost foreground'
def probe(command,**extras):
 args=['shell','am','start-foreground-service','--user','10','-n','io.timecapsule.focusprobe/.MediaProbeService','--es','command',command]
 for k,v in extras.items():args+=['--es',k,str(v)]
 return adb(*args)
def key(name):adb('shell','input','keyevent','KEYCODE_MEDIA_'+name.upper())
def park():
 put('/vehicle/controls?speed_kph=0&accelerator_pct=0&brake_pct=0&steering_deg=0')
 put('/vehicle/gear?gear=Park');put('/vehicle/turn-signal?mode=Off');time.sleep(1)
def near():adb('emu','geo','fix','128.9496','37.7714')
def far():adb('emu','geo','fix','128.9496','37.7800')
def show_app():
 adb('shell','am','start','--user','10','-n',P+'/.MainActivity');time.sleep(1);adb('shell','input','tap','1000','150')
def check(label):print(label,flush=True)

# Preserve any existing data. Fail rather than overwrite a prior unfinished test backup.
adb('shell','am','force-stop','--user','10',P);park();far()
if '--reuse-fixtures' not in sys.argv:
 backup=pathlib.Path('/tmp/timecapsule-v02-original-prefs.xml')
 original=adb('shell','run-as',P,'--user','10','cat','shared_prefs/capsules.xml')
 root=ET.fromstring(original);existing=json.loads(root.find("string[@name='items']").text)
 existing=[x for x in existing if not x['id'].startswith('ux-v02')]
 if not backup.exists():backup.write_text(original)
 audio=pathlib.Path('/tmp/timecapsule-v02-fixture.m4a')
 subprocess.run(['ffmpeg','-v','error','-y','-f','lavfi','-i','anullsrc=r=44100:cl=mono','-t','30','-c:a','aac',str(audio)],check=True)
 adb('shell','run-as',P,'--user','10','mkdir','-p','files')
 now=int(adb('shell','date','+%s'))*1000
 fixtures=[]
 for name,opens in [('near',0),('future',now+86400000)]:
  filename=f'ux-v02-{name}.m4a';data('files/'+filename,audio.read_bytes())
  fixtures.append(dict(id=f'ux-v02-{name}',created=now-10000,opens=opens,lat=37.7714,lon=128.9496,duration=30,file=filename,draft=False))
 root.find("string[@name='items']").text=json.dumps(existing+fixtures)
 data('shared_prefs/capsules.xml',ET.tostring(root))
show_app();time.sleep(2)
assert 'P · 주차 중' in texts(),texts()
shot('01-departure.png')
tap('이번 주행 듣기 준비');tap('알림 후 버튼으로 듣기');time.sleep(1)
tap('내비 화면으로 이동');time.sleep(2);nav_visible();check('NAVIGATION_OPENED_FROM_PREPARATION_PASS')
put('/vehicle/gear?gear=Drive');put('/vehicle/controls?speed_kph=30&accelerator_pct=0&brake_pct=0&steering_deg=0')
near();time.sleep(3)
assert state()[0]=='PAUSED',state();nav_visible();check('ARRIVAL_READY_WITHOUT_AUTOPLAY_PASS')
probe('BROWSE');time.sleep(2)
logs=adb('logcat','-d','-s','TimeCapsuleProbe:I')
available=re.findall(r'ITEMS=(.*)',logs)[-1].split(',')
assert 'ux-v02-near' in available and 'ux-v02-future' not in available,logs[-1500:]
check('MEDIA_BROWSER_EXCLUDES_FUTURE_CAPSULE_PASS')
key('play');time.sleep(1)
assert state()[0]=='PLAYING',state();nav_visible();shot('02-navigation-playing.png');check('SYSTEM_MEDIA_PLAY_WITH_NAVIGATION_FOREGROUND_PASS')
time.sleep(2);probe('FOCUS');time.sleep(1)
assert state()[0]=='PAUSED',state();paused=state()[1]
nav_visible();shot('03-navigation-paused.png');check('NAVIGATION_FOCUS_PAUSES_WITH_POSITION_PASS')
key('play');time.sleep(.4);assert state()[0]=='PAUSED',state();check('RESUME_DURING_GUIDANCE_BLOCKED_PASS')
time.sleep(5);assert state()[0]=='PAUSED',state();assert state()[1]==paused,state();check('NO_AUTO_RESUME_AFTER_GUIDANCE_PASS')
key('play');time.sleep(1);assert state()[0]=='PLAYING' and state()[1]>=paused,state();nav_visible();check('SYSTEM_MEDIA_RESUMES_AT_POSITION_PASS')
far();time.sleep(2);assert state()[0]=='PLAYING',state();check('LEAVING_LOCATION_KEEPS_OPENED_CAPSULE_PASS')
put('/vehicle/turn-signal?mode=BlinkLeft');time.sleep(1);assert state()[0]=='PAUSED',state();check('TURN_SIGNAL_PAUSES_SESSION_NOT_END_PASS')
put('/vehicle/turn-signal?mode=Off');time.sleep(1);assert state()[0]=='PAUSED',state()
key('play');time.sleep(1);assert state()[0]=='PLAYING',state();check('USER_RESUME_AFTER_TURN_PASS')
probe('PLAY_ID',mediaId='ux-v02-future');time.sleep(2)
logs=adb('logcat','-d','-s','TimeCapsuleProbe:I')
current_id=re.findall(r'MEDIA_ID=(.*)',logs.split('BROWSER_CONNECTED')[-1])[-1]
assert current_id in available and current_id!='ux-v02-future',logs[-1500:]
check('FUTURE_MEDIA_ID_CANNOT_BYPASS_LOCK_PASS')
key('next');time.sleep(1);assert state()[0]=='STOPPED',state();nav_visible();check('SKIP_KEEPS_WAITING_WITHOUT_CHAIN_PLAY_PASS')
key('stop');time.sleep(1)
s=adb('shell','dumpsys','activity','services',P);assert 'isForeground=true' not in s,s
check('STOP_ENDS_FOREGROUND_LOCATION_SESSION_PASS')
park();near();show_app();time.sleep(2)
tap('이번 주행 듣기 준비');tap('도착하면 자동 듣기');time.sleep(2)
assert state()[0]=='STOPPED',state();check('ENABLING_AT_SAME_PLACE_DOES_NOT_AUTOPLAY_PASS')
far();time.sleep(2);tap('내비 화면으로 이동');time.sleep(1);put('/vehicle/gear?gear=Drive');near();time.sleep(3)
assert state()[0]=='PLAYING',state();nav_visible();check('EXPLICIT_AUTO_MODE_REVISIT_PASS')
key('stop');park();show_app();check('NAVIGATION_UX_SUITE_PASS')
