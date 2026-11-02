from tc_ui import *
ROOT=pathlib.Path(__file__).resolve().parents[1]
P='io.timecapsule.vehicle'
def key(k):adb('shell','input','keyevent','KEYCODE_MEDIA_'+k)
def park():
 put('/vehicle/controls?speed_kph=0&accelerator_pct=0&brake_pct=0&steering_deg=0');put('/vehicle/gear?gear=Park');put('/vehicle/turn-signal?mode=Off');time.sleep(1)
def app():adb('shell','am','start','--user','10','-n',P+'/.MainActivity');time.sleep(1);adb('shell','input','tap','1000','150')
def check(s):print(s,flush=True)
key('STOP');park();app();time.sleep(1)
original_ids={x['id'] for x in items()}
tap('목소리 남기기');tap('열리는 날');tap('1년 뒤부터');assert any('열리는 날 · 1년 뒤부터'==x for x in texts()),texts();check('SELECTED_DATE_VISIBLE_PASS')
tap('녹음 시작');tap('동의 확인 · 녹음');time.sleep(3)
put('/vehicle/gear?gear=Drive');time.sleep(1)
new=[x for x in items() if x['id'] not in original_ids];assert len(new)==1 and new[0]['draft'],new
assert new[0]['duration']>=2,new
check('DRIVE_TRANSITION_SAVES_RECORDING_AS_DRAFT_PASS')
adb('shell','am','force-stop','--user','10',P);park();app();time.sleep(1);tap('보관함')
assert any('임시보관' in x for x in texts()),texts()
(ROOT/'evidence/v0.2.0/04-recording-draft.png').write_bytes(subprocess.check_output([ADB]+BASE+['exec-out','screencap','-p']))
check('DRAFT_SURVIVES_PROCESS_RESTART_PASS')
tap('캡슐로 저장');time.sleep(.5);assert not [x for x in items() if x['id']==new[0]['id']][0]['draft'];check('PARKED_DRAFT_COMMIT_PASS')
assert any('이후 공개' in x for x in texts()),texts()
tap('삭제');tap('삭제');time.sleep(.5);assert new[0]['id'] not in {x['id'] for x in items()};check('DELETE_TEST_DRAFT_PASS')
tap('목소리 남기기');tap('열리는 날');tap('지금부터');tap('녹음 시작');tap('동의 확인 · 녹음');time.sleep(31)
new=[x for x in items() if x['id'] not in original_ids];assert len(new)==1 and new[0]['duration']==30 and not new[0]['draft'],new
check('THIRTY_SECOND_RECORDING_SAVE_PASS')
tap('보관함');tap('삭제');tap('삭제');time.sleep(.5);assert new[0]['id'] not in {x['id'] for x in items()}
check('RECORDING_SUITE_PASS')
