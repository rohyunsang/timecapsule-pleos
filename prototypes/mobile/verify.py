from pathlib import Path
from html.parser import HTMLParser
import json
O=Path(__file__).parent/'out'
class P(HTMLParser):
 def __init__(self):super().__init__();self.links=[];self.assets=[];self.h1=0;self.empty=[]
 def handle_starttag(self,t,a):
  d=dict(a)
  if t=='h1':self.h1+=1
  if t=='a':self.links.append(d.get('href',''))
  if t in ['img','script']:self.assets.append(d.get('src',''))
errs=[];links=0;files=list((O/'screens').glob('*.html'))
for f in files:
 p=P();p.feed(f.read_text())
 if p.h1!=1:errs.append(f'{f.name}: h1 {p.h1}')
 for x in p.links:
  links+=1
  if not x or x=='#':errs.append(f'{f.name}: dead anchor {x}')
  elif not x.startswith(('http','mailto','tel','#')) and not (f.parent/x.split('?')[0].split('#')[0]).exists():errs.append(f'{f.name}: missing link {x}')
 for x in p.assets:
  if x and not x.startswith(('http','data:')) and not (f.parent/x).exists():errs.append(f'{f.name}: missing asset {x}')
r={'screens':len(files),'links':links,'errors':errs};(O/'link-check.json').write_text(json.dumps(r,ensure_ascii=False,indent=2));print(json.dumps(r,ensure_ascii=False,indent=2))
