import json
from pathlib import Path
O=Path(__file__).parent/'out'; m=json.loads((O/'screens.json').read_text()); inv=json.loads((O/'spec_inventory.json').read_text())
extras=[('album-created','오늘의 앨범 완성',4,'result'),('album-saved','앨범 수정 완료',4,'result'),('route-edit','추억 경로 편집',3,'form'),('plan-manage','아카이브 구독 관리',6,'form'),('account-edit','내 프로필 수정',6,'form'),('account-done','프로필 수정 완료',6,'result'),('arrival','도착한 순간',7,'form')]
for id,title,g,k in extras:m['screens'].append({'id':id,'file':f'screens/{id}.html','title':title,'group':m['groups'][g],'purpose':title,'role':'everyone','kind':k,'traits':{},'policies':['실제 연동이 아닌 UX 시안입니다.'],'branches':[]})
mp={'P1':['capture','car-capture'],'P2':['recording'],'P3':['buried'],'P4':['arrival'],'P5':['route-detail','car-nearby'],'P6':['nearby','player'],'P7':['car-paused'],'P8':['seal'],'P9':['route-create','route-detail','route-sent'],'P10':['album-created'],'P11':['album-share'],'P12':['transfer','transfer-done']}
rp={'R1':'recording','R2':'nearby','R3':'date-locked','R4':'car-capture','R5':'car-paused','R6':'car-paused','R7':'arrival','R8':'capture','R9':'sync','R10':'transfer','R11':'locked','R12':'album'}
for it in inv['items']:
 if it['id'] in mp: it['covered_by']=mp[it['id']]
 else:it['policy']=[rp[it['id']]];it['covered_by']=[rp[it['id']]]
# Additional rule states are same-element errors / irreversible confirmation.
inv['items'] += [{'id':'RX1','kind':'rule','name':'지우면 음성을 다시 복구할 수 없어요','quote':'캡슐은 언제든 삭제할 수 있고','target':'capsule-edit','state':'confirm','covered_by':['capsule-edit--confirm']},{'id':'RX2','kind':'rule','name':'차량에서 지우기 전 계정 보관을 확인하세요','quote':'차를 팔 때는 기기에서 모두 지우고 계정으로만 옮깁니다.','target':'transfer','state':'confirm','covered_by':['transfer--confirm']},{'id':'RX3','kind':'rule','name':'위치를 확인하지 못해 재생할 수 없어요','quote':'핵심 데이터','target':'nearby','state':'error','covered_by':['nearby--error']},{'id':'RX4','kind':'rule','name':'그 장소에서 벗어나 재생을 멈췄어요','quote':'그 길에 묻어둔 목소리가','target':'player','state':'error','covered_by':['player--error']}]
objmap=[['capture','locked','capsule-edit','buried'],['seal','date-locked','capsule-edit','buried'],['route-create','route-detail','route-edit','route-sent'],['album-created','album','album-edit','album-saved'],['sync','family','privacy','sync-done'],['transfer','devices','transfer','transfer-done'],['archive-plan','archive-plan','plan-manage','plan-done']]
for o,ids in zip(inv['objects'],objmap):
 for k,v in zip(['create','view','edit','after'],ids):o[k]=[v]
inv['mode']='spec' # Expanded composite mobile UX scope.
(O/'screens.json').write_text(json.dumps(m,ensure_ascii=False,indent=2));(O/'spec_inventory.json').write_text(json.dumps(inv,ensure_ascii=False,indent=2))
d=json.loads((O/'decisions.json').read_text());c=json.loads((O/'conventions.json').read_text())
for x in c['follow']+c['consider']:d['items'].append({'id':x['id'],'name':x['what'],'source':'시장','verdict':'keep','how':'날짜별 기록·초대·권한 선택 구조 반영','why':'가족의 기록을 안전하게 관리하는 핵심 목적에 맞음','ask':False})
(O/'decisions.json').write_text(json.dumps(d,ensure_ascii=False,indent=2))
