import sys,time,json
from tc_ui import *
adb('shell','am','force-stop','--user','10','io.timecapsule.vehicle')
put('/vehicle/gear?gear=Park');put('/vehicle/turn-signal?mode=Off')
adb('emu','geo','fix','128.9496','37.7800')
adb('shell','am','start','--user','10','-n','io.timecapsule.vehicle/.MainActivity');time.sleep(3)
values=texts();assert any('잠겨 있어요' in v for v in values),values
screenshot('05-location-locked.png');print('LOCATION_LOCK_PASS',flush=True)
tap('이 길에서 자동 듣기');tap('이 주행에서 켜기');time.sleep(1)
s=adb('shell','dumpsys','activity','services','io.timecapsule.vehicle');assert 'isForeground=true' in s and 'types=00000008' in s,s
print('WAITING_LOCATION_FGS_PASS',flush=True)
adb('shell','input','keyevent','KEYCODE_HOME');put('/vehicle/gear?gear=Drive');time.sleep(1)
adb('emu','geo','fix','128.9496','37.7714');time.sleep(2)
s=adb('shell','dumpsys','activity','services','io.timecapsule.vehicle');assert 'isForeground=true' in s and 'types=0000000A' in s,s
m=adb('shell','dumpsys','media_session');assert 'TimeCapsule' in m and 'state=PLAYING(3)' in m,m[-4000:]
print('BACKGROUND_ARRIVAL_PLAY_PASS',flush=True)
put('/vehicle/turn-signal?mode=BlinkLeft');time.sleep(1)
s=adb('shell','dumpsys','activity','services','io.timecapsule.vehicle');assert 'isForeground=true' not in s,s
print('TURN_SIGNAL_STOP_PASS',flush=True)
adb('shell','am','start','--user','10','-n','io.timecapsule.vehicle/.MainActivity');time.sleep(1);screenshot('06-driving-locked.png')
values=texts();assert not any('목소리 남기기' in v for v in values),values
print('DRIVING_UI_LOCK_PASS',flush=True)
put('/vehicle/turn-signal?mode=Off');put('/vehicle/gear?gear=Park')
