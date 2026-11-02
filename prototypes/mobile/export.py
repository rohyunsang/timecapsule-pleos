from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,math,shutil
O=Path(__file__).parent/'out';E=O/'exports';E.mkdir(exist_ok=True)
M=json.loads((O/'screens.json').read_text());font=ImageFont.truetype('/System/Library/Fonts/AppleSDGothicNeo.ttc',21);small=ImageFont.truetype('/System/Library/Fonts/AppleSDGothicNeo.ttc',16)
def sheet(ss,target,cols=4):
 w,h=300,650;pad=24;head=90;rh=710;rows=math.ceil(len(ss)/cols);im=Image.new('RGB',(cols*(w+pad)+pad,head+rows*rh+pad),'#f5f3f0');d=ImageDraw.Draw(im);d.text((pad,20),'타임캡슐 · 모바일 UI/UX',font=font,fill='#242424');d.text((pad,53),'그 길에 묻어둔 목소리가, 다시 그 길에서 당신을 기다린다.',font=small,fill='#5b5b5b')
 for n,s in enumerate(ss):
  p=O/'.shots'/f'{s["id"]}.png'
  if not p.exists():continue
  img=Image.open(p).convert('RGB');img=img.crop((0,0,375,812));img.thumbnail((w,h));x=pad+(n%cols)*(w+pad);y=head+(n//cols)*rh;im.paste(img,(x,y));d.text((x,y+h+10),s['title'][:22],font=small,fill='#242424')
 im.save(E/target)
ids=['welcome','home','map','review','seal','date-locked','player','family','album','route-detail','transfer','car-nearby']
sheet([next(s for s in M['screens'] if s['id']==id) for id in ids],'preview.png')
for idx,g in enumerate(M['groups']):sheet([s for s in M['screens'] if s['group']==g],f'flow-{idx+1:02d}.png')
# Every screen gets a standalone PNG, including state screens.
for s in M['screens']:
 src=O/'.shots'/f'{s["id"]}.png'
 if src.exists():shutil.copy2(src,E/src.name)
lines=['# 타임캡슐 화면 목록','',f'기본 {sum(not s.get("state_of") for s in M["screens"])}개 · 상태 {sum(bool(s.get("state_of")) for s in M["screens"])}개 · 전체 {len(M["screens"])}개','']
for g in M['groups']:
 lines+=['## '+g,'','|화면|유형|링크|','|---|---|---|']
 for s in M['screens']:
  if s['group']==g:lines.append(f'|{s["title"]}|{s.get("state","기본")}|[{s["id"]}]({s["file"]})|')
 lines.append('')
(O/'screen-list.md').write_text('\n'.join(lines))
print('exported',len(M['screens']),'screens and 9 contact sheets')
