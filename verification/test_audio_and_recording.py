import sys,time
from tc_ui import *
put('/vehicle/gear?gear=Park');put('/vehicle/turn-signal?mode=Off')
time.sleep(2)
tap('▶  듣기')
adb('shell','am','start','--user','10','-n','io.timecapsule.focusprobe/.ProbeActivity')
time.sleep(1)
s=adb('shell','dumpsys','activity','services','io.timecapsule.vehicle');assert 'isForeground=true' not in s,s
print('AUDIO_FOCUS_STOP_PASS',flush=True)
time.sleep(3)
adb('shell','am','start','--user','10','-n','io.timecapsule.vehicle/.MainActivity')
assert not any('■  멈추기' in x for x in texts())
screenshot('07-audio-focus-stopped.png');print('NO_AUTO_RESUME_PASS',flush=True)
before=len(items());tap('목소리 남기기');tap('녹음 시작');time.sleep(1);put('/vehicle/gear?gear=Drive');time.sleep(1)
assert len(items())==before;assert not any('녹음 저장' in x for x in texts());print('DRIVE_CANCELS_RECORDING_PASS',flush=True)
put('/vehicle/gear?gear=Park');time.sleep(1)
tap('열리는 날');tap('1년 뒤부터');tap('목소리 남기기');tap('녹음 시작')
print('30_SECOND_RECORDING_STARTED',flush=True)
time.sleep(31)
values=items();assert len(values)==before+1,values
assert values[0]['duration']>=30 and values[0]['duration']<=31,values[0]
assert values[0]['opens']>values[0]['created']+360*24*3600*1000
ui=list(nodes());matches=[n for n in ui if '▶  듣기' in n.get('text','')]
assert matches and matches[0].get('enabled')=='false'
screenshot('08-future-date-locked.png');print('30_SECOND_LIMIT_AND_FUTURE_LOCK_PASS',flush=True)
# Delete only the capsule that this test just created.
tap('삭제');tap('삭제');time.sleep(.3)
assert len(items())==before
print('DELETE_PASS',flush=True)
