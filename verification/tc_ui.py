import subprocess,xml.etree.ElementTree as ET,re,time,json,urllib.request,pathlib,os
ADB=str(pathlib.Path(os.environ.get('ANDROID_HOME',pathlib.Path.home()/'Library/Android/sdk'))/'platform-tools/adb')
BASE=['-s',os.environ.get('TC_EMULATOR','emulator-5554')]
assert BASE[1].startswith('emulator-'), '실차에서는 검증용 차량 조작을 실행하지 않습니다.'
def adb(*args):return subprocess.check_output([ADB]+BASE+list(args),stderr=subprocess.STDOUT).decode().strip()
def nodes():
 adb('shell','uiautomator','dump','/sdcard/tc-ui.xml')
 return ET.fromstring(adb('shell','cat','/sdcard/tc-ui.xml')).iter('node')
def texts():return [n.get('text') for n in nodes() if n.get('text')]
def tap(label):
 for n in sorted(list(nodes()),key=lambda n: n.get("text")!=label):
  if label in n.get('text',''):
   assert n.get('enabled')=='true',(label,'disabled')
   x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
   adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));return
 raise AssertionError('missing '+label)
def put(path):
 r=urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8088'+path,method='PUT'));return json.load(r)
def screenshot(name):
 p=pathlib.Path(__file__).resolve().parents[1]/'evidence'/name
 p.write_bytes(subprocess.check_output([ADB]+BASE+['exec-out','screencap','-p']))
def items():
 xml=adb('shell','run-as','io.timecapsule.vehicle','--user','10','cat','shared_prefs/capsules.xml')
 return json.loads(ET.fromstring(xml).find("string[@name='items']").text)
