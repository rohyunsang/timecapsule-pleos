import json,importlib.util
from pathlib import Path
from playwright.sync_api import sync_playwright
O=Path(__file__).parent/'out';R=[]
sp=importlib.util.spec_from_file_location('audit','/Users/developer/.codex/plugins/cache/k-style-ui-ux/k-style-ui-ux/0.18.1/core/scripts/audit.py');a=importlib.util.module_from_spec(sp);sp.loader.exec_module(a)
with sync_playwright() as p:
 b=p.chromium.launch();page=b.new_page(viewport={'width':375,'height':812})
 page.goto('http://127.0.0.1:8790/screens/map.html');page.wait_for_function('document.fonts.status === "loaded"');r=page.evaluate(a.MEASURE_JS)
 R.append({'check':'최종 지도 가로 넘침 없음','pass':r['scrollWidth']==375});R.append({'check':'최종 지도 렌더 측정','archetype':r.get('anchor'), 'data':{k:v for k,v in r.items() if 'anchor' in k.lower()}})
 page.screenshot(path=str(O/'.shots/map.png'))
 page.get_by_role('link',name='숲을 지나며').click();R.append({'check':'숲 캡슐 개별 상세','pass':'capsule-forest.html' in page.url and page.get_by_role('heading',name='숲을 지나며').count()>0})
 page.goto('http://127.0.0.1:8790/screens/review.html');bt=page.locator('[data-demo-audio]');bt.click();R.append({'check':'미리듣기 시안 버튼 상태 변경','pass':bt.get_attribute('aria-pressed')=='true' and page.locator('#audio-demo-note').is_visible()})
 page.goto('http://127.0.0.1:8790/screens/map.html');page.locator('input[type=search]').fill('숲');R.append({'check':'캡슐 이름 검색','pass':page.get_by_role('link',name='숲을 지나며').is_visible() and not page.get_by_role('link',name='바다 냄새 나!').is_visible()})
 for id in ['locked','date-locked','player--error']:
  page.goto('http://127.0.0.1:8790/screens/'+id+'.html');R.append({'check':id+' 재생 토글 없음','pass':page.locator('[data-demo-audio]').count()==0})
 b.close()
(O/'interaction-check.json').write_text(json.dumps(R,ensure_ascii=False,indent=2));print(json.dumps(R,ensure_ascii=False,indent=2))
