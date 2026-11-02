from ui import *

# 모든 데이터는 journey.md의 고정 시나리오를 사용한다.
def badge(t): return f'<span class="badge {M["badges"][t]}">{t}</span>'
def progress(n=1): return '<div class="progress" role="progressbar" aria-label="캡슐 만들기" aria-valuenow="'+str(n)+'" aria-valuemax="3">'+''.join('<span class="is-on"></span>' if i<n else '<span></span>' for i in range(3))+'</div>'
def copy_state(base,id,replacements):
 t=(O/'screens'/f'{base}.html').read_text()
 for a,b in replacements:
  assert a in t,(id,a[:60]);t=t.replace(a,b)
 (O/'screens'/f'{id}.html').write_text(t)
def line(title,text):return f'<p class="row-title">{title}</p><p class="row-sub">{text}</p>'
def player_visual(title='00:18',sub='최대 30초'):
 return '<div class="empty"><img src="../assets/fluent/phone.svg" alt="음성 기록"><p class="title">'+title+'</p><p class="lead">'+sub+'</p></div>'
def search():return '<label class="search">'+icon('search')+'<input type="search" placeholder="장소나 캡슐 이름으로 찾기" aria-label="캡슐 검색"></label>'
def select(label,id,options): return '<div class="field"><label class="label" for="'+id+'">'+label+'</label><select class="input" id="'+id+'">'+''.join('<option>'+x+'</option>' for x in options)+'</select></div>'

write('welcome',photo('road','바다와 이어지는 길',ratio='')+block('<p class="eyebrow">그 길에서 다시 만나는 기억</p><h2 class="title">오늘의 목소리를<br>내일의 길에 묻어요</h2><p class="lead">차가 바뀌어도, 시간이 흘러도.<br>같은 길에서 우리 목소리가 기다려요.</p>')+info('기록은 나만 보관하고, 함께할 사람은 직접 골라요.','invite'),back='home',cta=btn('타임캡슐 시작하기','login'))
write('login',block(field('이메일','jimin@example.com','email','email')+field('비밀번호','','password','password')+'<p class="block-note">가족의 계정과 연결해도 비밀번호는 공유하지 않아요.</p>')+block(row('기기에만 먼저 보관할게요','계정 연결과 가족 공유는 나중에 할 수 있어요.','permissions',art='phone')),back='welcome',hero='기억을 이어갈<br>나의 계정',lead='처음이라면 같은 화면에서 계정을 만들 수 있어요.',cta=btn('이메일로 계속하기','permissions'),plain=False)
copy_state('login','login--error',[(field('이메일','jimin@example.com','email','email'),field('이메일','jimin','email','email','이메일 주소에 @와 도메인을 입력해 주세요.')),(btn('이메일로 계속하기','permissions'),btn('입력 수정하기','login'))])
write('permissions',block(row('위치','캡슐을 묻고 같은 장소인지 확인할 때','nearby--error',art='pin')+row('마이크','녹음 버튼을 누른 동안에만','capture',art='phone')+row('알림','길 위의 캡슐과 가족 초대 소식','privacy',art='invite'),list=True)+info('권한은 필요한 순간에 요청해요. 허용하지 않아도 기록 목록은 볼 수 있어요.'),back='login',hero='필요한 순간에만<br>허용해 주세요',cta=btn('확인하고 계속하기','connect'))
connect_form=field('차량에 표시된 연결 코드','482 163','connect-code')
write('connect',block('<div class="empty"><img src="../assets/fluent/car.svg" alt="Pleos Connect 차량"></div>'+connect_form+'<p class="block-note">차량의 타임캡슐 앱에서 ‘휴대폰 연결’을 눌러 주세요.</p>')+block(row('차량 없이 먼저 둘러보기','연결은 내 정보에서 할 수 있어요.','home',art='phone')),back='permissions',hero='우리 차와<br>기억을 연결해요',cta=btn('내 차 연결하기','connect-done'))
copy_state('connect','connect--error',[(connect_form,field('차량에 표시된 연결 코드','482 163','connect-code',error='연결 코드가 만료됐어요. 차량에서 새 코드를 확인해 주세요.')),(btn('내 차 연결하기','connect-done'),btn('새 코드 입력하기','connect'))])
result('connect-done','우리 가족 차와<br>연결했어요','Pleos Connect에서 남긴 캡슐을 이 휴대폰에서 관리할 수 있어요.','home','첫 기억 만나기','car')
home_feature='<a class="feature" href="locked.html" data-anchor>'+photo('coast','해안 풍경 예시',ratio='')+'<div class="feature-text">'+badge('잠김')+'<p class="feature-title">바다 냄새 나!</p><p class="feature-sub">강릉 안목해변 · 2027.08.15<br>2045.08.15부터, 이 장소에서 열려요</p></div></a>'
write('home',home_feature+block('<div class="block-head"><h2 class="h2">오늘 남긴 목소리</h2><a class="block-link" href="album.html">앨범</a></div>'+row('우리의 여름 바다','캡슐 3개 · 2027.08.15','album',art='beach')+row('기기에 보관 중','가족 계정에 안전하게 이어가려면','sync',art='phone'))+block('<h2 class="h2">지금의 목소리도 남겨요</h2><p class="lead">짧은 한마디면 충분해요.</p>'+btn('캡슐 묻기','capture'))+block(row('숲을 지나며','대관령 숲길 · 24초 · 나만','locked',art='pin')+row('노을 앞의 약속','경포해변 · 12초 · 가족','locked',art='beach'),list=True),back='welcome',tab='home')
empty_feature='<div class="feature" data-anchor>'+photo('coast','첫 여행을 기다리는 해안 풍경',ratio='')+'<div class="feature-text"><p class="feature-title">처음 남기는 목소리</p><p class="feature-sub">오늘의 짧은 한마디가<br>언젠가 다시 만날 기억이 돼요.</p></div></div>'
copy_state('home','home--empty',[(home_feature,empty_feature),('<div class="block-head"><h2 class="h2">오늘 남긴 목소리</h2><a class="block-link" href="album.html">앨범</a></div>'+row('우리의 여름 바다','캡슐 3개 · 2027.08.15','album',art='beach')+row('기기에 보관 중','가족 계정에 안전하게 이어가려면','sync',art='phone'),'<h2 class="h2">아직 묻은 캡슐이 없어요</h2><p class="lead">기억하고 싶은 곳에 도착하면 첫 캡슐을 남겨 보세요.</p>'),(row('숲을 지나며','대관령 숲길 · 24초 · 나만','locked',art='pin')+row('노을 앞의 약속','경포해변 · 12초 · 가족','locked',art='beach'),row('함께할 사람 연결하기','가족이나 서로 팔로우한 친구와 나눠요.','together',art='invite'))])

write('capture',block(photo('coast','해안 풍경 예시',ratio='is-16x9')+line('강릉 안목해변','지금 있는 장소에만 묻을 수 있어요.'))+block('<h2 class="h2">함께 탄 분들에게 알려 주세요</h2><p class="lead">녹음해도 괜찮은지 먼저 확인해 주세요. 시작과 끝에는 알림음이 나와요.</p>'+row('안전하게 주차한 상태예요','주행 중에는 휴대폰을 조작하지 마세요.',None,art='car'))+info('녹음은 시작 버튼을 누른 뒤 최대 30초 동안만 진행돼요.','phone'),back='home',hero='지금의 목소리를<br>여기에 남길까요?',cta=btn('동의 확인하고 녹음하기','recording'))
write('recording',block(progress(1)+player_visual('00:18','30초가 되면 녹음이 끝나요.')+'<p class="h2">지금, 듣고 있어요</p><p class="lead">강릉 안목해변</p>')+block(row('주변의 목소리도 담겨요','함께 녹음 중임을 동승자에게 알려 주세요.',None,art='people' if False else 'phone')+btn('녹음 취소하기','capture',False)),back='capture',cta=btn('녹음 마치기','review'))
write('review',block(progress(2)+player_visual('18초','아직 묻지 않은 임시 녹음이에요.')+btn('미리듣기','review',False)+field('캡슐 이름','바다 냄새 나!','capsule-title'))+block('<div class="block-head"><h2 class="h2">그날의 분위기</h2>'+badge('확인 필요')+'</div>'+select('날씨','weather',['비 오던','맑았던','직접 선택 안 함'])+select('시간','time',['밤','낮'])+select('함께한 사람','companions',['우리 가족 · 3명','나 혼자','직접 정하기'])+'<p class="block-note">차량 상태로 추정한 분위기예요. 그날과 다르면 고쳐 주세요.</p>')+block(btn('다시 녹음하기','capture',False)),back='recording',cta=btn('받는 사람 정하기','seal'))
# 미리듣기는 사용자 조작을 드러내는 토글이며 오디오 파일은 연결하지 않는다.
p=O/'screens/review.html';t=p.read_text();t=t.replace(btn('미리듣기','review',False),'<button class="btn secondary full" data-demo-audio aria-pressed="false">'+icon('play')+'임시 녹음 미리듣기</button>');p.write_text(t)
seal_date=field('열리는 날','2045-08-15','open-date','date')
write('seal',block(progress(3)+select('공개 범위','audience',['가족에게','나만','서로 팔로우한 친구'])+select('받는 사람','recipient',['김하윤','우리 가족 모두','이서연'])+seal_date)+block(row('바다 냄새 나!','강릉 안목해변 · 18초',None,art='beach')+'<p class="lead">2045년 8월 15일부터, 이 길에 온 하윤이에게 열려요.</p>')+info('우선 기기에 보관해요. 가족에게 전하려면 계정 동기화를 직접 켜 주세요.','phone'),back='review',hero='누구에게,<br>언제 열어줄까요?',cta=btn('여기에 캡슐 묻기','buried'))
copy_state('seal','seal--error',[(seal_date,field('열리는 날','2027-08-14','open-date','date','오늘보다 이전 날짜예요. 2027년 8월 15일 이후로 골라 주세요.')),(btn('여기에 캡슐 묻기','buried'),btn('날짜 다시 정하기','seal'))])
write('buried',block('<div class="empty"><img src="../assets/fluent/beach.svg" alt="해변에 남긴 캡슐"><h2 class="title">그 길에<br>잘 묻어두었어요</h2><p class="lead">바다 냄새 나! · 18초</p></div>'+row('강릉 안목해변','2027.08.15 20:14',None,art='pin')+row('김하윤에게','2045.08.15부터 · 현장에서만',None,person='07'))+block(row('기기에 저장 완료','차가 바뀌어도 이어가려면 계정에 보관해요.','sync',art='phone',badge='완료')),back='home',cta=btn('캡슐 확인하기','date-locked'))
write('capsule-edit',block(field('캡슐 이름','바다 냄새 나!','title')+select('공개 범위','scope',['가족에게','나만','서로 팔로우한 친구'])+field('열리는 날','2045-08-15','date','date')+select('분위기','scene',['비 오던 밤','맑았던 낮','선택 안 함']))+block(row('장소는 바꿀 수 없어요','강릉 안목해변에 묻은 캡슐이에요.',None,art='pin')+btn('캡슐 삭제하기','capsule-edit--confirm',False)),back='locked',cta=btn('변경 저장하기','date-locked'))
sheet('capsule-edit--confirm','capsule-edit','이 캡슐을 지울까요?','바다 냄새 나!의 음성과 기록을 지워요. 삭제한 캡슐은 다시 복구할 수 없어요.','영구 삭제하기','deleted')
result('deleted','캡슐을 지웠어요','이 캡슐은 가족의 목록에서도 더 이상 열 수 없어요.','map','확인하기','pin')

# 실측 좌표가 아닌 개념 지도를 별도 SVG 에셋으로 만든다.
import re
colors=dict(re.findall(r'(--c-[a-z0-9-]+):\s*(#[0-9a-fA-F]+)',(O/'tokens.css').read_text()))
def col(name):return colors['--c-'+name]
domain=O/'assets/domain';domain.mkdir(exist_ok=True)
svg=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 654 400"><rect width="654" height="400" fill="{col('brand-tint')}"/><path d="M450 0C350 90 530 140 430 230S480 360 400 400H654V0Z" fill="{col('info-tint')}"/><g fill="none" stroke="{col('surface')}" stroke-width="28"><path d="M80 0L140 135 310 210 360 400M0 250L250 260 490 90M185 0L280 100 440 170"/></g><path d="M140 135L310 210 430 230 490 90" fill="none" stroke="{col('brand')}" stroke-width="5" stroke-dasharray="8 8"/><g fill="{col('brand')}" stroke="{col('surface')}" stroke-width="4"><circle cx="140" cy="135" r="23"/><circle cx="430" cy="230" r="23"/><circle cx="490" cy="90" r="23"/></g><g fill="{col('on-brand')}" text-anchor="middle" font-family="sans-serif" font-weight="bold" font-size="20"><text x="140" y="142">1</text><text x="430" y="237">2</text><text x="490" y="97">3</text></g><g fill="{col('text-2')}" font-family="sans-serif" font-size="18"><text x="36" y="102">대관령 숲길</text><text x="400" y="285">안목해변</text><text x="510" y="54">경포해변</text></g><circle cx="300" cy="310" r="22" fill="{col('info-tint')}"/><circle cx="300" cy="310" r="10" fill="{col('info')}" stroke="{col('surface')}" stroke-width="4"/><text x="325" y="317" font-family="sans-serif" font-size="18" fill="{col('text-2')}">현재 위치</text></svg>'''
(domain/'map.svg').write_text(svg)
map_content=block('<div class="photo is-16x9"><img src="../assets/domain/map.svg" alt="현재 위치와 대관령 숲길, 안목해변, 경포해변 캡슐 3개를 표시한 개념 지도"></div><p class="block-note">위치 관계를 보여주는 예시 지도예요.</p>',anchor=True)

map_rows=row('바다 냄새 나!','안목해변 · 18초 · 2045.08.15부터 현장에서','locked',art='pin',badge='잠김')+row('숲을 지나며','대관령 숲길 · 24초 · 나만','locked',art='pin',badge='잠김')+row('노을 앞의 약속','경포해변 · 12초 · 가족','locked',art='pin',badge='잠김')
write('map',map_content+search()+chips(['전체','가족','나만'],targets=['map','family','my'])+block(map_rows,list=True)+block(row('추억 경로 만들기','기억이 있는 장소를 이어가요.','routes',art='car')),back='home',hero='목소리가 기다리는 길',tab='map')
copy_state('map','map--empty',[(map_content,block(photo('road','드라이브 길 풍경 예시',ratio='is-16x9',anchor=True)+'<p class=block-note>새로운 기억을 남길 길이에요.</p>')),(map_rows,'<div class="empty"><img src="../assets/fluent/pin.svg" alt="장소"><h2 class="h2">이 근처에는 아직 없어요</h2><p class="lead">여기에 첫 목소리를 남겨 보세요.</p>'+btn('첫 캡슐 묻기','capture')+'</div>')])
locked_card=photo('coast','해안 풍경 예시',ratio='',anchor=True)
write('locked',locked_card+block(badge('잠김')+'<h2 class="title">바다 냄새 나!</h2><p class="lead">2045년 8월 15일부터,<br>강릉 안목해변에서 열려요.</p>'+row('김지민이 남긴 목소리','2027.08.15 20:14 · 18초',None,person='10'))+block(row('비 오던 밤 · 함께 3명','직접 확인한 그날의 분위기',None,art='weather')+row('캡슐 편집','내가 남긴 캡슐의 공개 범위와 날짜','capsule-edit',art='clipboard'))+info('지금은 날짜와 장소가 모두 잠겨 있어요. 하윤이에게 전하는 편지예요.'),back='map',cta=btn('추억 경로에 담기','route-create'))
write('date-locked',locked_card+block(badge('잠김')+'<h2 class="h2">하윤이에게 남긴 편지</h2><p class="lead">2045.08.15부터 이 장소에서 열려요.</p>'+row('바다 냄새 나!','강릉 안목해변 · 18초',None,person='07'))+block(row('열리는 날 변경','내가 남긴 편지의 날짜를 바꿀 수 있어요.','capsule-edit',art='calendar')),back='home')
future_note='<p class=block-note>2045.08.15 · 23세 하윤의 재방문</p>'
near_body=block(badge('열 수 있어요')+'<h2 class="title">이 길에서<br>목소리를 만나요</h2><p class="lead">바다 냄새 나! · 18초</p>'+row('장소를 확인했어요','강릉 안목해변 · 안전하게 주차한 상태',None,art='pin'))
near_cta=btn('목소리 듣기','player')
write('nearby',future_note+locked_card+near_body+block(btn('다음에 들을게요','map',False))+info('장소를 벗어나거나 위치 확인이 끊기면 다시 잠겨요.'),back='map',cta=near_cta)
copy_state('nearby','nearby--error',[(near_body,block(badge('확인 필요')+'<h2 class="title">위치를 확인하지<br>못했어요</h2><p class="field-msg">'+icon('warning')+'위치 확인 전에는 목소리를 열 수 없어요.</p>'+row('위치 권한을 확인해 주세요','정확한 위치를 허용한 뒤 다시 시도해요.','permissions',art='pin'))),(near_cta,btn('위치 다시 확인하기','nearby'))])
player_body=block(photo('coast','해안 풍경 예시',ratio='is-16x9')+'<h2 class="title">바다 냄새 나!</h2><p class="lead">김지민 · 2027.08.15 20:14</p>'+player_visual('00:08 / 00:18','비 오던 밤 · 함께 3명')+'<button class="btn primary full" data-demo-audio aria-pressed="true">'+icon('pause')+'잠시 멈추기</button>',anchor=True)
write('player',future_note+player_body+block(row('이 장소에서만 재생돼요','강릉 안목해변',None,art='pin')+btn('다 들었어요','opened',False)),back='nearby')
copy_state('player','player--error',[(player_body,block(photo('coast','해안 풍경 예시',ratio='is-16x9')+'<h2 class="title">재생을 멈췄어요</h2><p class="field-msg">'+icon('warning')+'그 장소에서 벗어나 목소리가 다시 잠겼어요.</p>'+player_visual('00:08 / 00:18','바다 냄새 나!')+btn('캡슐 위치 확인하기','locked'),anchor=True)),(btn('다 들었어요','opened',False),'<p class="block-note">다음 방문에 이어 들을 수 있어요.</p>')])
result('opened','같은 길에서<br>기억을 만났어요','바다 냄새 나! · 18초\n오늘의 목소리도 이 자리에 남겨볼까요?','capture','여기에 답장 묻기','beach')
p=O/'screens/opened.html';p.write_text(p.read_text().replace('<main class="page">','<main class="page">'+future_note))

# 담당 화면의 유효 링크와 h1 구조를 점검한다.
from html.parser import HTMLParser
class Check(HTMLParser):
 def __init__(self):super().__init__();self.links=[];self.h1=0
 def handle_starttag(self,t,a):
  d=dict(a)
  if t=='h1':self.h1+=1
  if t=='a':self.links.append(d.get('href',''))
ids=[s['id'] for s in M['screens'] if s['group'].startswith(('01','02','03'))]
for id in ids:
 p=Check();p.feed((O/'screens'/f'{id}.html').read_text());assert p.h1==1,(id,p.h1)
 for href in p.links:
  assert href.endswith('.html') and href[:-5] in S,(id,href)
print('built and checked',len(ids),'screens')
