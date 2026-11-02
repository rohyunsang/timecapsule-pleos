from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from lxml import etree as E
import hashlib,json
base=Path(__file__).parent
source=base/'official-form.docx';dest=base/'타임캡슐_공모전_접수양식.docx'
ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
content=json.loads((base/'proposal-content.json').read_text())
with ZipFile(source) as z:
 inventory={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist()}
 root=E.fromstring(z.read('word/document.xml'));tables=root.findall('.//w:tbl',ns)
 contract=f'''# Official template contract\nReference: {source.resolve()}\nSHA256: {hashlib.sha256(source.read_bytes()).hexdigest()}\nRendered source: qa-template-fonts, 6 pages with bundled LibreOffice and Noto CJK fallback. The retained source itself spills one application-table row onto page 2; preserve source geometry and page furniture.\nOne section: 11906 x 16838 twips; margins top935 bottom907 left1020 right1020 (source XML authoritative).\nEditable slots: word/document.xml body tables 1..8, each single-cell plain-text sdt w:text. Preserve sdt wrappers, ids, tags, styles, run properties, borders, spacing, row rules. Replace only the w:t value. Preserve table0 participant data and table9 consent/signature unchanged.\nApp-name placeholders in three body paragraphs become 타임캡슐. Other participant, signature, consent, date fields remain empty.\nPreserve every ZIP part except word/document.xml and docProps/core.xml byte-for-byte. Clear the original template author metadata for anonymous review, without adding an author. Content growth may increase pages as explicitly allowed in the form. Default typography and headings remain template-controlled. Paragraph boundaries are represented by two spaces in existing plain-text nodes without rebuilding controls; the companion Markdown retains full paragraphs. Remove placeholder yellow highlight only inside editable slots. Move participant-table heading to a fresh page to keep rows together; this is a pagination repair, not a new visual design. No secondary design system.\nRendering font fallback: source requests Korean fonts absent in headless renderer. Task-local Noto Sans CJK KR is provided using FONTCONFIG_FILE, without changing source fonts.\nSource inspected on all 6 pages. No charts, new images, custom page furniture, or invented participant data added.\n'''
 (base/'artifact.md').write_text(contract)
 (base/'template-package-inventory.json').write_text(json.dumps(inventory,indent=2))
 for i in range(1,9):
  ts=tables[i].findall('.//w:sdtContent//w:t',ns)
  assert len(ts)==1,(i,len(ts))
  ts[0].text=content[str(i)].replace('\n', '  ') or ' '
  for h in tables[i].findall('.//w:highlight',ns): h.getparent().remove(h)
 for p in root.find('w:body',ns).findall('w:p',ns):
  text=''.join(p.itertext())
  if text.startswith('앱명 / 프로젝트명:'):
   ts=p.findall('.//w:t',ns)
   filled=False
   for t in ts:
    if '_' in (t.text or ''):
     t.text=t.text.replace('_','')+('타임캡슐' if not filled else '')
     filled=True
 # Keep the participant table with its heading instead of orphaning its last row.
 body=root.find('w:body',ns)
 for par in body.findall('w:p',ns):
  if ''.join(par.itertext()).startswith('B. 참가 구성원'):
   props=par.find('w:pPr',ns)
   if props is None: props=E.SubElement(par,'{'+ns['w']+'}pPr')
   E.SubElement(props,'{'+ns['w']+'}pageBreakBefore')
 data=E.tostring(root,xml_declaration=True,encoding='UTF-8',standalone=True)
 core=E.fromstring(z.read('docProps/core.xml'))
 for c in core:
  if c.tag.split('}')[-1] in ['creator','lastModifiedBy','description']:c.text=''
 corebytes=E.tostring(core,xml_declaration=True,encoding='UTF-8',standalone=True)
 with ZipFile(dest,'w',ZIP_DEFLATED) as out:
  for item in z.infolist():out.writestr(item,data if item.filename=='word/document.xml' else corebytes if item.filename=='docProps/core.xml' else z.read(item.filename))
with ZipFile(source) as a,ZipFile(dest) as b:
 assert a.namelist()==b.namelist()
 changed=[n for n in a.namelist() if a.read(n)!=b.read(n)]
 assert set(changed)=={'word/document.xml','docProps/core.xml'},changed
 aroot=E.fromstring(a.read('word/document.xml'))
 for idx in [0,9]:assert E.tostring(aroot.findall('.//w:tbl',ns)[idx])==E.tostring(root.findall('.//w:tbl',ns)[idx])
print(dest, 'changed parts:',changed)
headers=['앱 한 줄 소개','기획 배경','앱 주요 기능 및 차량 특화 요소','주요 사용 시나리오','개발 계획','기대효과 및 발전 가능성','참고자료','숏폼 링크']
(base/'타임캡슐_기획서.md').write_text('# 타임캡슐 공모전 기획서\n\n작성일 2026년 11월 2일\n\n'+'\n\n'.join('## '+headers[i-1]+'\n\n'+content[str(i)] for i in range(1,9)))
