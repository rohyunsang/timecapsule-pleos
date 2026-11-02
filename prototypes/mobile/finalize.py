from pathlib import Path
import json,re
from ui import *
for id,title,slot,loc,sec,owner,avatar,scope,time in [('capsule-forest','숲을 지나며','forest','대관령 숲길',24,'김지민','10','나만','15:20'),('capsule-sunset','노을 앞의 약속','sunset','경포해변',12,'이서연','01','우리 가족','18:40')]:
 s={'id':id,'file':f'screens/{id}.html','title':title,'group':M['groups'][2],'purpose':'캡슐별 장소와 공개 조건 확인','role':'everyone','kind':'detail','archetype':'대상형','archetype_why':'개별 장소와 목소리를 중심으로 결정','traits':{},'policies':['사진은 장소 분위기 예시입니다.','장소를 벗어나면 음성은 잠겨 있습니다.'],'branches':[]}
 S[id]=s
 if not any(x['id']==id for x in M['screens']):M['screens'].append(s)
 write(id,photo(slot,'장소 분위기 예시',ratio='',anchor=True)+block('<span class="badge">잠김</span><h2 class="title">'+title+'</h2><p class="lead">'+loc+'에서 다시 만나요.</p>'+row(owner+'의 목소리',f'2027.08.15 {time} · {sec}초',person=avatar))+block(row('공개 범위',scope,art='idcard')+row('열리는 조건','이 장소를 방문하면 열려요.',art='pin')),back='map',cta=btn('추억 경로에 담기','route-create'))
(O/'screens.json').write_text(json.dumps(M,ensure_ascii=False,indent=2))
for p in (O/'screens').glob('*.html'):
 t=p.read_text()
 def link(m):
  x=m.group(0)
  if 'href="locked.html"' in x:
   if '숲을 지나며' in x:x=x.replace('href="locked.html"','href="capsule-forest.html"')
   elif '노을 앞의 약속' in x:x=x.replace('href="locked.html"','href="capsule-sunset.html"')
  return x
 t=re.sub(r'<a\b[^>]*>.*?</a>',link,t,flags=re.S)
 if p.name=='transfer--confirm.html':t=t.replace('class="btn secondary full" href="transfer-done.html"','class="btn full" href="transfer-done.html"')
 p.write_text(t)
# Branches are real alternate outcomes, not sequential arrows.
b={'connect':[('connect--error','연결할 수 없을 때')],'seal':[('seal--error','날짜가 올바르지 않을 때')],'nearby':[('nearby--error','위치 확인 실패')],'player':[('player--error','구간을 벗어났을 때')],'capsule-edit':[('capsule-edit--confirm','삭제를 선택하면')],'family-invite':[('family-invite--error','받는 주소를 확인해야 할 때')],'transfer':[('transfer--error','계정 보관 전'),('transfer--confirm','차량에서 지우기')]}
for s in M['screens']:
 if s['id'] in b:s['branches']=[{'to':to,'label':label} for to,label in b[s['id']]]
(O/'screens.json').write_text(json.dumps(M,ensure_ascii=False,indent=2))
