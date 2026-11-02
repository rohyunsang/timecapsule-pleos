from pathlib import Path
import json,html
O=Path(__file__).parent/'out'
M=json.loads((O/'screens.json').read_text())
S={s['id']:s for s in M['screens']}
def esc(x):return html.escape(str(x),quote=True)
def icon(name='next'):
 paths={'back':'M15 5l-7 7 7 7','next':'M9 5l7 7-7 7','plus':'M12 5v14M5 12h14','home':'M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10','map':'M3 5l6-2 6 2 6-2v16l-6 2-6-2-6 2zM9 3v16M15 5v16','album':'M4 4h16v16H4zM4 16l5-5 4 4 3-3 4 4','people':'M8 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M1 21v-2a7 7 0 0 1 14 0v2M17 5a3 3 0 0 1 0 6M18 15a5 5 0 0 1 5 5','user':'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M4 21v-2a8 8 0 0 1 16 0v2','mic':'M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0zM5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8','play':'M8 4l12 8-12 8z','pause':'M8 5v14M16 5v14','lock':'M6 10h12v11H6zM8 10V6a4 4 0 0 1 8 0v4','check':'M4 12l5 5L20 6','search':'M16 16l5 5M10 17a7 7 0 1 0 0-14 7 7 0 0 0 0 14','warning':'M12 3l10 18H2zM12 9v5M12 17v1','close':'M6 6l12 12M18 6L6 18','car':'M3 11l2-7h14l2 7v8H3zM3 11h18M6 15h2M16 15h2'}
 return f'<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="{paths.get(name,paths["next"])}"/></svg>'
def btn(label,to,primary=True,small=False):return f'<a class="btn {"primary" if primary else "secondary"}{" small" if small else " full"}" href="{to}.html">{label}</a>'
def row(title,sub='',to=None,art=None,person=None,badge=None):
 left=f'<span class="row-art"><img src="../assets/fluent/{art}.svg" alt=""></span>' if art else ''
 if person:left=f'<span class="avatar"><img src="../assets/avatars/people/{person}.png" alt=""></span>'
 b=f'<span class="badge {M["badges"][badge]}">{badge}</span>' if badge else ''
 tag='a' if to else 'div';href=f' href="{to}.html"' if to else ''
 return f'<{tag} class="row"{href}>{left}<div class="row-main">{b}<p class="row-title">{title}</p><p class="row-sub">{sub}</p></div>{"<span class=row-trail>"+icon()+"</span>" if to else ""}</{tag}>'
def block(content,title=None,anchor=False,list=False):return f'<section class="block{" is-list" if list else ""}" {"data-anchor" if anchor else ""}>{"<h2 class=h2>"+title+"</h2>" if title else ""}{content}</section>'
def photo(slot='coast',alt='강릉 해안 풍경',ratio='is-16x9',anchor=False):return f'<div class="photo {ratio}" {"data-anchor" if anchor else ""}><img src="../assets/photos/{slot}.jpg" alt="{alt}"></div>'
def info(text,art='pin'):return f'<p class="info"><img src="../assets/fluent/{art}.svg" alt=""><span>{text}</span></p>'
def field(label,value='',id='field',type='text',error=None):return f'<div class="field{" is-error" if error else ""}"><label class="label" for="{id}">{label}</label><input class="input" id="{id}" type="{type}" value="{esc(value)}">'+(f'<p class="field-msg">{icon("warning")}{error}</p>' if error else '')+'</div>'
def chips(labels,selected=0,targets=None):return '<div class="chips">'+''.join(f'<a class="chip{" is-selected" if i==selected else ""}" href="{(targets or ["map"]*len(labels))[i]}.html">{t}</a>' for i,t in enumerate(labels))+'</div>'
def tabbar(active):
 return '<nav class="tabbar" aria-label="주요 메뉴">'+''.join(f'<a class="tab" href="{id}.html" {"aria-current=page" if active==id else ""}>{icon(ic)}{name}</a>' for id,name,ic in [('home','홈','home'),('map','지도','map'),('albums','앨범','album'),('together','함께','people'),('my','내 정보','user')])+'</nav>'
def write(id,content,back='home',hero=None,lead=None,tab=None,cta=None,plain=False,extra=''):
 s=S[id]; hashero=bool(hero)
 header=(f'<header class="appbar"><a class="appbar-btn" href="{back}.html" aria-label="뒤로">{icon("back")}</a><{"p" if hashero else "h1"} class="appbar-title">{s["title"]}</{"p" if hashero else "h1"}><a class="appbar-btn" href="privacy.html" aria-label="공개 범위">{icon("lock")}</a></header>')
 if hero:header+=f'<header class="hero"><p class="eyebrow">타임캡슐</p><h1 class="title">{hero}</h1>{"<p class=lead>"+lead+"</p>" if lead else ""}</header>'
 cl=('plain ' if plain else '')+('has-tab ' if tab else '')+('has-cta' if cta else '')
 body=header+'<main class="page">'+content+'</main>'+(f'<div class="cta">{cta}</div>' if cta else '')+(tabbar(tab) if tab else '')+extra
 text=f'<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{s["title"]} · 타임캡슐</title><link id="tokens" rel="stylesheet" href="../tokens.css"><link rel="stylesheet" href="../components.css"><script src="../palette.js"></script><script src="../prototype.js" defer></script></head><body class="{cl}">{body}</body></html>'
 (O/'screens'/f'{id}.html').write_text(text)
def result(id,title,sub,to,label='계속하기',art='invite'):
 write(id,block(f'<div class="empty"><img src="../assets/fluent/{art}.svg" alt=""><h2 class="h2">{title}</h2><p class="lead">{sub}</p></div>')+info('타임캡슐의 음성은 그 장소에서만 열려요.'),cta=btn(label,to),plain=False)
def sheet(id,base,title,body,action,to):
 p=O/'screens'/f'{base}.html';t=p.read_text();t=t.replace('</body>',f'<div class="dim"></div><section class="sheet" role="dialog" aria-modal="true" aria-label="{title}"><span class="sheet-grip"></span><div class="sheet-head"><p class="h2">{title}</p></div><p class="lead">{body}</p>{btn(action,to,False)}{btn("취소하기",base,False)}</section></body>');(O/'screens'/f'{id}.html').write_text(t)
