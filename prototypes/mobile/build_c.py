from ui import *

def switch(title, sub, checked=False):
    return f'<div class="row"><div class="row-main"><p class="row-title">{title}</p><p class="row-sub">{sub}</p></div><button class="switch" role="switch" aria-label="{title}" aria-checked="{str(checked).lower()}"></button></div>'
def check(title, sub, checked=True):
    return f'<div class="row"><span class="row-art"><img src="../assets/fluent/clipboard.svg" alt=""></span><div class="row-main"><p class="row-title">{title}</p><p class="row-sub">{sub}</p></div><button class="check" role="checkbox" aria-label="{title}" aria-checked="{str(checked).lower()}"><span>{icon("check")}</span></button></div>'
def note(t):return f'<p class="block-note">{t}</p>'
def intro(t,sub):return f'<h2 class="h2">{t}</h2><p class="lead">{sub}</p>'
def icon_art(a):return f'<span class="row-art"><img src="../assets/fluent/{a}.svg" alt=""></span>'

write('my',block(row('김지민','우리 가족 · 아빠','account-edit',person='10')+row('연결한 차량','우리 가족 차','devices',art='car')+row('가족 계정 동기화','기기 보관 중 · 동기화 꺼짐','sync',art='phone')+row('공개 범위와 권한','내가 정한 사람에게만','privacy',art='idcard'),anchor=True,list=True)+block(row('가족 아카이브','오래 지키는 방법','archive-plan',art='invite')+row('새 차로 이어가기','차가 바뀌어도 같은 기억','transfer',art='car')+row('가족에게 기록 넘기기','기억의 다음 주인에게','inherit',art='father'),list=True)+block(row('차량 경험 미리보기','녹음 · 접근 안내 · 안전 중단','car-capture',art='car')+row('그날처럼','그날의 공기를 다시 만나는 미래','like-that-day',art='weather'),list=True),hero='오래 지킬 나의 기억',lead='차가 바뀌어도, 우리 이야기는 이어져요.',tab='my')

write('privacy',block(intro('누구와 함께 들을까요?','새 캡슐의 기본 공개 범위예요.')+'<div class="seg" role="radiogroup"><button class="seg-item is-selected" role="radio" aria-checked="true">나만</button><button class="seg-item" role="radio" aria-checked="false">우리 가족</button><button class="seg-item" role="radio" aria-checked="false">서로 팔로우</button></div>')+block(switch('가족 계정 동기화','처음에는 이 기기에만 보관해요.')+switch('위치 사용','캡슐을 묻거나 열 때만 사용해요.',True)+switch('마이크 사용','녹음 시작을 누른 동안만 사용해요.',True),title='필요한 순간에만')+block(note('공개된 캡슐도 그 장소와 열리는 날짜가 맞아야 들을 수 있어요.')+row('내 기록 관리','캡슐 수정·삭제','albums',art='clipboard')),back='my',cta=btn('설정 저장하기','my'))

sync_body=block(intro('차가 바뀌어도<br>기억은 그대로','가족 계정에 안전하게 이어둘 수 있어요.')+row('현재 보관 위치','우리 가족 차 · 기기 보관',art='car')+row('옮길 곳','우리 가족 · 3명',art='invite'))+block(check('가족 계정에 함께 보관','동기화를 켜면 선택한 캡슐을 계정에도 저장해요.',False)+note('나만 공개한 캡슐은 동기화해도 가족에게 공개되지 않아요.'),title='동기화 선택')
write('sync',sync_body,back='my',cta=btn('동기화 시작하기','sync--progress'))
p=O/'screens/sync.html'; text=p.read_text(); text=text.replace('가족 계정에 함께 보관','가족 계정에 보관 중').replace('동기화를 켜면 선택한 캡슐을 계정에도 저장해요.','3개 중 2개를 보관했어요. 연결이 끊기면 이어서 진행해요.').replace('aria-checked="false"','aria-checked="true"').replace('동기화 선택','기억을 옮기고 있어요').replace(btn('동기화 시작하기','sync--progress'),btn('계속 기다리기','sync-done')).replace('</main>','<section class="block"><div class="progress" role="progressbar" aria-label="캡슐 동기화" aria-valuenow="2" aria-valuemax="3"><span class="is-on"></span><span class="is-on"></span><span></span></div><p class="block-note">앱을 닫아도 기기의 원본은 그대로 남아요.</p>'+btn('나중에 이어하기','sync',False)+'</section></main>');(O/'screens/sync--progress.html').write_text(text)
result('sync-done','기억을 함께 보관했어요','캡슐 3개를 가족 계정에 보관했어요. 각 캡슐의 공개 범위와 장소 잠금은 그대로예요.','family','확인하기','invite')

write('devices',block(row('우리 가족 차','Pleos Connect · 연결됨','transfer',art='car')+row('새 차량 연결','차량에 표시된 코드로 연결','connect',art='phone'),anchor=True,list=True)+block(note('계정 보관을 확인한 뒤 이전 차량에서 지울 수 있어요.')+btn('이전 준비하기','transfer',False)),back='my',hero='기억이 이어지는 차',lead='연결한 차량에서만 내 기록에 접근해요.')

transfer_body=block(intro('먼저 보관하고,<br>이전 차에서 지워요','가족 계정에 보관한 기록은 새 차에서 이어집니다.')+row('이전할 기록','우리의 여름 바다 · 캡슐 3개',art='luggage')+field('차량 이름','우리 가족 차','car-name'))+block(check('계정 보관을 확인했어요','캡슐 3개 · 동기화 완료')+check('이 차에서 로그아웃할게요','기기의 음성과 계정 연결을 함께 지워요.'),title='이전 전 확인')
write('transfer',transfer_body+info('계정에 보관하지 않은 음성은 먼저 동기화해 주세요.','phone'),back='devices',cta=btn('차량에서 지우기','transfer--confirm'))
t=(O/'screens/transfer.html').read_text().replace(field('차량 이름','우리 가족 차','car-name'),field('차량 이름','','car-name',error='연결된 차량 이름을 입력해 주세요.')).replace(btn('차량에서 지우기','transfer--confirm'),btn('다시 확인하기','transfer'));(O/'screens/transfer--error.html').write_text(t)
sheet('transfer--confirm','transfer','이 차에서 기억을 지울까요?','가족 계정에 보관한 캡슐 3개는 남아요. 이 차의 음성과 계정 연결은 삭제되며 되돌릴 수 없어요.','지우고 연결 해제하기','transfer-done')
p=O/'screens/transfer--confirm.html';t=p.read_text().replace('>지우고 연결 해제하기</a>', '> <span class="field-msg">지우고 연결 해제하기</span></a>');p.write_text(t)
result('transfer-done','다음 차에서도<br>같은 길에서 만나요','이전 차량의 기록과 연결을 지웠어요. 가족 계정에 보관한 캡슐 3개는 그대로예요.','connect','새 차 연결하기','car')

inherit_body=block(intro('우리의 기록을<br>다음 사람에게','받는 가족이 수락하면 기록 관리 권한을 이어받아요.')+row('넘길 아카이브','우리의 여름 바다 · 캡슐 3개',art='luggage')+field('받는 가족','이서연','recipient')+row('이서연','우리 가족 · 엄마',person='01'))+block(check('관리 권한 이전을 확인했어요','수락 전까지 요청을 취소할 수 있어요.')+note('음성은 해당 장소에서만 열려요. 이 기능은 법적 상속 절차가 아니에요.'))
write('inherit',inherit_body,back='my',cta=btn('이전 요청하기','inherit-done'))
t=(O/'screens/inherit.html').read_text().replace(field('받는 가족','이서연','recipient'),field('받는 가족','','recipient',error='기록을 이어받을 가족을 선택해 주세요.')).replace(btn('이전 요청하기','inherit-done'),btn('가족 다시 선택하기','inherit'));(O/'screens/inherit--error.html').write_text(t)
result('inherit-done','서연님에게 요청했어요','서연님이 수락하면 기록 관리 권한을 이어받아요. 수락 전에는 요청을 취소할 수 있어요.','inherit','요청 확인·취소하기','invite')

write('archive-plan',block(photo('forest','숲길 분위기 예시','is-16x9')+intro('우리 가족의 목소리를<br>오래 보관하는 곳','한 번의 여행이 다음 세대의 추억이 되도록.'),anchor=True)+block(row('넉넉한 계정 보관','기기 밖에서도 안전하게 보관',art='phone')+row('가족과 이어가는 기록','공개 범위는 캡슐마다 직접 선택',art='invite')+row('여행 앨범 내보내기','장소·날짜를 담은 공유 앨범',art='luggage'),list=True)+block(note('가족 아카이브는 준비 중이에요. 가격과 제공 용량이 정해지면 알려드릴게요. 결제는 진행되지 않아요.')+btn('출시 소식 신청하기','plan-done')+row('아카이브 관리','신청·보관 설정','plan-manage')),back='my')
result('plan-done','준비되면 알려드릴게요','가족 아카이브 출시 소식을 신청한 모습이에요. 이 시안에서는 실제 알림이나 결제가 발생하지 않아요.','plan-manage','신청 관리하기','invite')
write('plan-manage',block(intro('가족 아카이브<br>출시를 기다리고 있어요','현재 이용 중인 유료 구독은 없어요.')+row('출시 소식','신청 완료',art='invite')+row('결제 금액','결제 내역 없음',art='clipboard'))+block(switch('출시 알림 받기','가격과 제공 범위가 정해지면 알려드려요.',True)+row('보관 설정','계정 동기화와 공개 범위','sync',art='phone')),back='archive-plan',cta=btn('설정 저장하기','my'))

write('account-edit',block(row('김지민','우리 가족 · 아빠',person='10')+field('표시 이름','김지민','name')+field('가족에게 보이는 이름','아빠','family-name')+note('가족과 친구에게 보이는 이름이에요. 기존 캡슐의 작성자도 함께 바뀌어요.')),back='my',cta=btn('프로필 저장하기','account-done'))
result('account-done','프로필을 저장했어요','김지민 · 우리 가족의 아빠. 가족과 친구에게 이 이름으로 보여요.','my','확인하기','idcard')

concept_note=note('차량 경험 미리보기 · 실제 차량 연동 전 개념 시안')
write('car-capture',block(concept_note+photo('road','동해안 해안도로 분위기 예시')+intro('“타임캡슐,<br>지금 묻어줘.”','오늘의 목소리를 이 길에 남겨요.'))+block(row('최대 30초','시작과 끝에 차 안으로 알림음이 울려요.',art='phone')+note('함께 탄 사람에게 먼저 물어봐 주세요. 주행 중에는 모바일을 조작하지 않아요.'))+block(btn('녹음 흐름 체험하기','recording')+row('도착한 순간','도착 후 한마디 제안','arrival',art='pin')+row('나중에 다시 만날 때','캡슐 접근 안내','car-nearby',art='car')),back='my')
write('car-nearby',block(concept_note+photo('coast','동해안 바다 분위기 예시')+intro('18년 전의 목소리가<br>이 길에 있어요','2045년 8월 15일 · 하윤의 첫 혼자 여행'))+block(row('바다 냄새 나!','2027.08.15 · 18초 · 아빠의 캡슐',person='10')+note('비 오던 밤, 셋이서 이 길에 남겼어요. 같은 장소와 날짜 조건을 확인한 뒤 재생해요.'))+block(btn('재생 장면 체험하기','car-paused')+btn('다음에 듣기','routes',False)),back='car-capture')
write('car-paused',block(concept_note+'<div class="empty"><img src="../assets/fluent/car.svg" alt=""><h2 class="h2">잠시, 길 안내를<br>먼저 들을게요</h2><p class="lead">목소리는 여기서 잠시 기다려요.</p></div>')+block(row('바다 냄새 나!','일시정지 · 남은 목소리 12초',person='10')+note('안내가 끝나고 같은 구간에 있을 때만 다시 재생해요. 구간을 벗어나면 캡슐은 다시 잠겨요.'))+block(btn('다시 잠긴 모습 체험하기','locked',False)+row('차량 경험 처음으로','목소리 남기기','car-capture',art='car')),back='car-nearby')
write('like-that-day',block(photo('sunset','해변 노을 분위기 예시','is-3x4',True)+intro('그날처럼','목소리와 함께, 그날의 공기도 만날 수 있다면.'))+block(row('바람 맞으며','창문을 열고 달리던 기억',art='weather')+row('따뜻한 차 안','그날의 실내 온도와 빛',art='car')+note('차량 제어를 활용한 미래 경험이에요. 지원되는 차량과 안전한 조건이 확인된 뒤 제공할 예정이에요.')+'<button class="btn secondary full" disabled>아직 준비 중이에요</button>'),back='my')
write('arrival',block(concept_note+photo('coast','강릉 바다 분위기 예시')+intro('도착한 순간,<br>한마디 남길까요?','강릉 안목해변'))+block(note('안전하게 주차한 뒤, 지금의 마음을 짧게 남겨요. 대답하지 않으면 조용히 넘어가요.')+btn('목소리 남기기','capture')+btn('지금은 넘어가기','home',False)+switch('도착 제안 받기','처음 온 장소에서 하루 최대 2번',True)),back='car-capture')

ids=[s['id'] for s in M['screens'] if s['group'].startswith(('07','08'))]
assert all((O/'screens'/f'{id}.html').exists() for id in ids)
print('built C',len(ids),'screens')
