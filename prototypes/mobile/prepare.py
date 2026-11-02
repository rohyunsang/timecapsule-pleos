import json
from pathlib import Path
O=Path(__file__).parent/'out'
G=['01 처음 만나는 타임캡슐','02 목소리를 묻는 순간','03 같은 길에서 다시 열기','04 추억을 따라가는 여행','05 하루를 담은 앨범','06 가족과 서로의 길','07 오래 지키는 기억','08 차량 경험 · 개념 시안']
rows=[
('welcome','처음 만나는 타임캡슐',0,'form'),('login','계정 시작',0,'form'),('permissions','필요할 때 권한 허용',0,'form'),('connect','내 차 연결',0,'form'),('connect-done','차 연결 완료',0,'result'),('home','오늘의 타임캡슐',0,'home'),
('capture','캡슐 묻기',1,'form'),('recording','목소리 녹음',1,'form'),('review','녹음과 분위기 확인',1,'form'),('seal','받는 사람과 여는 날',1,'form'),('buried','캡슐을 묻었어요',1,'result'),('capsule-edit','캡슐 편집',1,'form'),('deleted','캡슐 삭제 완료',1,'result'),
('map','길 위의 캡슐',2,'list'),('locked','그 길에서 기다리는 목소리',2,'detail'),('date-locked','아직 오지 않은 날짜',2,'detail'),('nearby','다시 만난 장소',2,'detail'),('player','그날의 목소리',2,'detail'),('opened','다시 만난 기억',2,'result'),
('routes','추억 경로',3,'list'),('route-create','다시 갈 장소 고르기',3,'form'),('route-detail','그때 그 길',3,'detail'),('route-sent','차로 경로 전송 완료',3,'result'),
('albums','여행 앨범',4,'list'),('album','우리의 여름 바다',4,'detail'),('album-edit','앨범 정리',4,'form'),('album-share','앨범 초대 링크',4,'form'),
('together','함께 남기는 기억',5,'home'),('family-create','가족 아카이브 만들기',5,'form'),('family','우리 가족',5,'detail'),('family-invite','가족 초대',5,'form'),('invite-accept','가족 초대 수락',5,'form'),('family-edit','가족 관리',5,'form'),('family-done','가족 설정 완료',5,'result'),('following','서로 팔로우',5,'list'),('person','친구 프로필',5,'detail'),('follow-done','팔로우 요청 완료',5,'result'),
('my','내 정보',6,'home'),('privacy','기억의 공개 범위',6,'form'),('sync','가족 계정 동기화',6,'form'),('sync-done','동기화 완료',6,'result'),('devices','연결한 차량',6,'list'),('transfer','새 차로 이어가기',6,'form'),('transfer-done','차량 이전 완료',6,'result'),('inherit','가족에게 기록 넘기기',6,'form'),('inherit-done','이전 요청 완료',6,'result'),('archive-plan','가족 아카이브',6,'detail'),('plan-done','출시 알림 신청 완료',6,'result'),
('car-capture','차량 · 목소리 남기기',7,'form'),('car-nearby','차량 · 캡슐 접근',7,'form'),('car-paused','차량 · 안전 안내 우선',7,'form'),('like-that-day','그날처럼 · 미래 경험',7,'detail')]
ss=[]
for id,title,g,k in rows:
 t={"list_first_use": id in ['home','map','albums','following'],"form":id in ['login','connect','seal','family-create','family-invite','transfer','inherit'],"shares":id in ['album-share','family-invite'],"large_list": k=='list' and id not in ['devices'],"long_task":id=='sync'}
 arche={'home':'목록형','list':'행 목록','detail':'읽기·재생형'}.get(k)
 if id in ['home']: arche='대표 콘텐츠형'
 if id=='map': arche='지도+목록'
 if id in ['locked','date-locked','nearby','album','like-that-day']: arche='대상형'
 if id in ['family','person']: arche='사람형'
 if id=='route-detail': arche='진행 추적형'
 s={'id':id,'file':f'screens/{id}.html','title':title,'group':G[g],'purpose':title,'role':'everyone','kind':k,'traits':t,'policies':['음성은 장소·날짜·공개 대상 조건을 모두 충족해야 재생합니다.','시안 데이터이며 실제 차량 연동·녹음·결제는 실행하지 않습니다.'],'branches':[]}
 if arche:s['archetype']=arche;s['archetype_why']='현장 잠금과 가족의 기억을 우선하는 타임캡슐의 핵심 흐름'
 ss.append(s)
people=[{'name':'어린 자녀를 둔 가족','who':'한국의 부모와 어린 자녀, 여행 중 목소리를 남기는 가족','why':'아이의 지금 목소리를 미래에 전하고 싶다','feels':'애틋함과 기대','wants':[{'q':'오늘 남긴 목소리는 어디에 보관되나','at':['home','buried','sync']}],'win':'녹음을 확인하고 가족에게 안전하게 남긴 순간','screens':['home','capture','family'],'core':True},{'name':'부모와 독립한 자녀','who':'한국의 성인 자녀, 부모와 다른 차를 이용','why':'부모가 남긴 목소리를 같은 길에서 만나고 싶다','feels':'그리움과 호기심','wants':[{'q':'언제 어디에서 이 목소리를 들을 수 있나','at':['locked','date-locked','nearby']}],'win':'장소에서 가족의 캡슐을 여는 순간','screens':['map','locked','player']},{'name':'커플·부부와 혼자 여행하는 운전자','who':'기념일 드라이브 또는 혼자 떠나는 한국 성인','why':'오늘의 마음을 미래에 전하고 싶다','feels':'설렘','wants':[{'q':'누구에게 언제 공개할 수 있나','at':['seal','privacy']}],'win':'받는 사람과 날짜를 정해 캡슐을 묻은 순간','screens':['seal','routes','following']}]
m={'groups':G,'story':{'headline':'그 길에 묻어둔 목소리가,<br><em>다시 그 길에서</em> 당신을 기다린다.','summary':'타임캡슐 · 모바일 전체 UX 시안. 장소와 날짜가 맞아야 열리는 목소리, 차가 바뀌어도 이어지는 가족의 기억. 마지막 행은 차량 경험의 개념 시안입니다.','ideas':[{'title':'그 길에서만','desc':'앨범과 공유 링크도 음성의 장소 잠금을 우회하지 않습니다.'},{'title':'나에서 가족으로','desc':'기기 보관이 기본이며 동기화와 공유는 직접 선택합니다.'},{'title':'기억은 오래, 조작은 짧게','desc':'모바일에서 준비하고 차량에서는 간결하게 만납니다.'}]},'screens':ss,'people':people,'avatar_style':'people','photo_centric':False,'promos':[],'badges':{'잠김':'','열 수 있어요':'is-brand','확인 필요':'is-warning','초대 중':'is-info','완료':'is-success','연결 실패':'is-danger'},'prd_coverage':{}}
(O/'screens.json').write_text(json.dumps(m,ensure_ascii=False,indent=2))
(O/'journey.md').write_text('''# 타임캡슐 모바일 여정과 사실 표

## 사람 카드
- 핵심: 어린 자녀를 둔 가족. 미래로 보내고 싶은 오늘의 목소리가 있으며 보관과 공개 범위를 확인하고 싶다. 홈·녹음·저장 완료에서 답한다.
- 부모와 독립한 자녀: 부모가 남긴 목소리를 언제 어디에서 들을 수 있는지 궁금하다. 장소·날짜 잠금과 도착 화면이 답한다.
- 커플·부부 / 혼자 여행하는 운전자: 미래 수신인과 열리는 날을 정하고 기록을 모으고 싶다. 편지 설정·경로·앨범에서 답한다.

## 여정
|사람|사용 전|사용 중|사용 후|막힘과 해법|
|가족|계정·차 연결|녹음·분위기 확인·공개 선택|앨범·동기화|권한 거부→설정 안내, 오프라인→기기 보관|
|독립한 자녀|초대 수락·잠금 확인|같은 구간 진입·재생|같은 장소에 답장|날짜 미도래→날짜 표시, 이탈→재잠금|
|커플·혼자 여행|편지 수신인·날짜 선택|장소에서 기록|경로 계획·다음 차 이전|팔로우 미성립→요청, 차 이전→백업 확인|

## 시나리오 사실 표
모든 인물·기록은 시안용 가상 데이터이다. 기본 화면의 현재 시점은 2027년 8월 15일이다. 2045년 장면은 차량 미래 시안에만 사용한다.
|항목|고정 값|
|사용자|김지민, 성인 남성, 아빠, people/10.png|
|가족|이서연, 성인 여성, 엄마, people/01.png; 김하윤, 5세 여아, people/07.png|
|친구|박도윤, 성인 남성, people/11.png|
|가족 이름|우리 가족 · 3명|
|현재 여행|우리의 여름 바다 · 2027.08.15 · 캡슐 3개|
|캡슐 A|바다 냄새 나! · 강릉 안목해변 · 2027.08.15 20:14 · 18초 · 김지민 · 가족 공개|
|캡슐 B|숲을 지나며 · 대관령 숲길 · 2027.08.15 15:20 · 24초 · 나만|
|캡슐 C|노을 앞의 약속 · 경포해변 · 2027.08.15 18:40 · 12초 · 이서연 · 가족 공개|
|편지 날짜|2045.08.15부터 · 받는 사람 김하윤|
|대표 미래 장면|2045.08.15 · 23세 김하윤 · 18년 전 캡슐 A를 같은 장소에서 듣기|
|차량|우리 가족 차 · Pleos Connect · 검증 전 데모 연결 상태|
|분위기|와이퍼 작동·전조등 켜짐은 관측 후보. 비 오던 밤·함께 3명은 사용자가 확인한 표현|
|구독|가격·출시일 미정. 시안은 출시 알림만 신청하며 실제 구매하지 않음|
|장소 사진|coast.jpg, road.jpg, forest.jpg, sunset.jpg. 실제 기록 사진이 아닌 장소 예시|

## 범위와 가정 로그
- 이전 길소리의 상호 팔로우·현장 전용 재생과 새 가족 서사를 모두 유지한다.
- 모바일은 관리·준비·동기화, 차량은 순간 기록·현장 재생 중심이다. 모바일 녹음은 안전한 정차 상태에서의 보조 입력 시안이며 위치 인증은 별도 검증 대상이다.
- 원본 녹음을 게시 전 검토하는 것은 허용하지만, 게시된 캡슐·앨범은 위치 잠금을 우회하지 않는다. 공유 앨범의 원격 화면은 제목·장소·날짜만 표시한다.
- 운전 중 모바일 사용을 유도하지 않는다. 차량의 음성 호출·핸들 버튼·주행 중 녹음과 재생은 지원·정책 검증을 전제로 한 개념 화면이다.
- 그날처럼은 비전으로 유지하며 실제 창문·공조 제어 버튼을 활성화하지 않는다.
- 가족 이전은 기록 접근 권한 이전이다. 법적 상속이나 영구 서비스 보장을 뜻하지 않는다. 내보내기는 암호화 보관용으로 제안하며 음성 공개 우회는 제외한다.
- 자동 제안과 여행 종료는 차량 이벤트 검증 후 적용한다. 모바일에는 확인·수정 수단을 제공한다.
- 팔레트: setup_out 자동 선택 에어비앤비(여행·감성·따뜻함), 대안 마이리얼트립·올리브영 시드. 아바타 people은 가족 세대 구분에 적합하다.
- 기본은 실명 신뢰 인증이 아닌 가족 표시명이다. 계정 생성·연결·권한·결제는 디자인 시뮬레이션이다.
''')
(O/'decisions.json').write_text(json.dumps({'core':'가족과 가까운 사람이 남긴 목소리를 같은 길에서 다시 만나고, 차가 바뀌어도 지킨다.','items':[{'id':'D1','name':'모바일과 차량 역할 분리','source':'하네스','verdict':'keep','how':'모바일 전체 흐름과 차량 개념 화면을 모두 포함','why':'요구 기능을 빼지 않으며 주행 중 모바일 조작을 유도하지 않음','ask':False},{'id':'D2','name':'현장 잠금 유지','source':'PRD','verdict':'keep','how':'가족 앨범과 공유 링크에서도 음성은 현장에서만','why':'기존 기획의 핵심 조건','ask':False},{'id':'D3','name':'그날처럼','source':'PRD','verdict':'keep','how':'준비 중인 미래 경험으로 시각화','why':'사용자가 지정한 비전 단계 유지','ask':False},{'id':'D4','name':'광고·가상 할인','source':'하네스','verdict':'cut','why':'추억 보관 경험에 불필요하며 근거 없음','ask':False}]},ensure_ascii=False,indent=2))
