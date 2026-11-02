import asyncio,json
from pathlib import Path
from playwright.async_api import async_playwright
from PIL import Image,ImageOps,ImageDraw
ROOT=Path(__file__).parent
URL='http://127.0.0.1:8791/road-voice-planning/timecapsule-vehicle/'
async def main():
 async with async_playwright() as p:
  browser=await p.chromium.launch()
  page=await browser.new_page(viewport={'width':1280,'height':720},device_scale_factor=1)
  errors=[]
  page.on('pageerror',lambda e:errors.append(str(e)))
  await page.goto(URL+'?capture=1')
  scenes=await page.evaluate('scenes.map(s=>s.slice(0,3))')
  overflow=[]
  for width in [1280,600]:
   await page.set_viewport_size({'width':width,'height':720})
   for s in scenes:
    await page.evaluate("([id])=>{state.near=true;state.date=true;state.access=true;state.driving=false;state.interrupt=false;go(id)}",s)
    bad=await page.evaluate("document.querySelector('#app').scrollWidth>document.querySelector('#app').clientWidth+1")
    if bad:overflow.append([width,s[0]])
    if width==1280: await page.screenshot(path=str(ROOT/'exports'/f'{s[0]}.png'))
   await page.evaluate("go('home')")
   await page.screenshot(path=str(ROOT/'exports'/f'home-{width}.png'))
  # Guard checks for every unlock condition and interruption.
  checks=[]
  for key,label in [('near','장소를 벗어났어요'),('date','2045년 8월 15일부터'),('access','권한이 필요해요')]:
   await page.evaluate("k=>{state.near=true;state.date=true;state.access=true;state[k]=false;go('play')}",key)
   checks.append([key,label in await page.locator('#app').inner_text()])
  await page.evaluate("state.near=true;state.date=true;state.access=true;state.interrupt=true;go('play')")
  checks.append(['audio-focus','안내가 끝나면' in await page.locator('#app').inner_text()])
  await page.evaluate("state.driving=true;go('settings')")
  checks.append(['driving',await page.locator('.drive-player').count()==1 and await page.locator('.rail').count()==0])
  await page.evaluate("state.driving=false;state.interrupt=false;go('record')")
  await page.get_by_role('button',name='녹음 마치기').click()
  checks.append(['record-review',await page.evaluate("current==='review'")])
  await page.get_by_role('button',name='개봉 조건 정하기').click()
  await page.get_by_role('button',name='캡슐 묻기').click()
  checks.append(['save-flow',await page.evaluate("current==='saved'")])
  await page.set_viewport_size({'width':1280,'height':720})
  await page.evaluate("document.body.classList.add('day');go('home')")
  await page.screenshot(path=str(ROOT/'exports/home-day.png'))
  await page.goto(URL)
  await page.set_viewport_size({'width':1440,'height':1000})
  await page.get_by_role('button',name='분할 창',exact=True).click()
  await page.screenshot(path=str(ROOT/'exports/split-view.png'))
  gallery='<!doctype html><html lang="ko"><meta charset="utf-8"><title>타임캡슐 차량 화면 모음</title><link rel="stylesheet" href="style.css"><main class="gallery"><small>PLEOS CONNECT · VEHICLE APP DESIGN</small><h1>그 길에서 다시 만나는 목소리</h1><p>차량 앱 '+str(len(scenes))+'개 화면 · 전체/분할 창 · 주간/야간</p><p>화면을 누르면 조작 가능한 시안이 열립니다. 재생 시연은 하단에서 장소·날짜·권한 조건을 켜 주세요.</p><a class="action" href="index.html">프로토타입 열기</a><div class="gallery-grid">'
  gallery+=''.join(f'<a href="index.html#{s[0]}"><h2>{i+1:02} · {s[1]}</h2><img loading="lazy" src="exports/{s[0]}.png" alt="{s[2]}"><p>{s[2]}</p></a>' for i,s in enumerate(scenes))+'</div></main></html>'
  (ROOT/'gallery.html').write_text(gallery)
  report={'screens':len(scenes),'viewports':[1280,600],'horizontal_overflow':overflow,'runtime_errors':errors,'interaction_checks':checks}
  (ROOT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
  print(json.dumps(report,ensure_ascii=False))
  await browser.close()
 # Montage
 chosen=['home','record','conditions','detail','arrival','play','interrupted','driving','album','family','settings','vision']
 out=Image.new('RGB',(1920,1660),'#080e10');draw=ImageDraw.Draw(out)
 for i,name in enumerate(chosen):
  shot=Image.open(ROOT/'exports'/f'{name}.png').convert('RGB').resize((620,349))
  x=20+(i%3)*640;y=24+(i//3)*408
  out.paste(shot,(x,y));draw.text((x,y+360),f'{i+1:02} / {name}',fill='#f1be80')
 out.save(ROOT/'exports/overview.png')
asyncio.run(main())
