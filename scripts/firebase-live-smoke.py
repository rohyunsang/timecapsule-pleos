#!/usr/bin/env python3
"""Disposable real-server smoke checks. No emulator, admin token, or device proof.
Run only against the explicitly approved test project with --project.
Reads the public app API key from ignored local.properties; never prints credentials.
"""
import argparse, base64, json, pathlib, secrets, urllib.request, urllib.error, uuid
parser = argparse.ArgumentParser()
parser.add_argument('--project', required=True)
args = parser.parse_args()
config = dict(line.split('=', 1) for line in pathlib.Path('local.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
assert config['firebase.projectId'] == args.project, 'Project must match local.properties'
key = config['firebase.apiKey']
root = f'projects/{args.project}/databases/(default)/documents'
base = 'https://firestore.googleapis.com/v1/' + root
users, documents, checks = [], [], []

def request(method, url, body=None, token=None):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())

def auth(action, body):
    status, data = request('POST', 'https://identitytoolkit.googleapis.com/v1/accounts:' + action + '?key=' + key, body)
    assert status == 200, (action, status, data.get('error', {}).get('message'))
    return data

def value(v):
    if v is None: return {'nullValue': None}
    if isinstance(v, bool): return {'booleanValue': v}
    if isinstance(v, int): return {'integerValue': str(v)}
    if isinstance(v, float): return {'doubleValue': v}
    if isinstance(v, bytes): return {'bytesValue': base64.b64encode(v).decode()}
    if isinstance(v, list): return {'arrayValue': {'values': [value(x) for x in v]}}
    return {'stringValue': v}

def write(user, path, data, timestamps=(), mask=None, expected=200):
    w = {'update': {'name': root + '/' + path, 'fields': {k: value(v) for k,v in data.items()}}}
    if timestamps: w['updateTransforms'] = [{'fieldPath': k, 'setToServerValue': 'REQUEST_TIME'} for k in timestamps]
    if mask is not None: w['updateMask'] = {'fieldPaths': mask}
    status, result = request('POST', base + ':commit', {'writes':[w]}, user['idToken'])
    assert status == expected, ('write', path.split('/')[0], status, result.get('error', {}).get('status'))
    if status == 200 and not any(p == path for _,p in documents): documents.append((user, path))
    return result

def read(user, path, expected=200):
    status, result = request('GET', base + '/' + path, token=user['idToken'] if user else None)
    assert status == expected, ('read', path.split('/')[0], status, result.get('error', {}).get('status'))
    return result

def passed(label): checks.append(label); print('PASS:', label, flush=True)

try:
    for _ in range(3): users.append(auth('signUp', {'returnSecureToken': True}))
    a,b,c = users
    for i,u in enumerate(users): write(u, 'users/'+u['localId'], {'name': 'TC disposable smoke '+str(i)})
    passed('anonymous authentication and own profiles')
    email = 'tc-smoke-' + uuid.uuid4().hex + '@example.com'
    password = secrets.token_urlsafe(24)
    linked = auth('signUp', {'idToken':a['idToken'], 'email':email, 'password':password, 'returnSecureToken':True})
    a.update(linked)
    second = auth('signInWithPassword', {'email':email,'password':password,'returnSecureToken':True})
    assert second['localId'] == a['localId']
    passed('email linking and second-session same account')
    prefix = a['localId'] + '_'
    capsule = prefix + '1000000000001'
    data = {'ownerUid':a['localId'],'ownerName':'smoke','createdAt':1000000000001,'lat':37.5,'lng':127.0,'radiusM':300,'placeName':'TC disposable smoke','durationSec':3,'tags':[], 'rawVehicleState':'', 'openAfter':None,'audience':'SOME','recipients':[b['localId']],'visibleTo':[b['localId']],'sourceRemoteId':None,'originalOwnerUid':None,'originalOwnerName':None,'replyToId':None,'replyMode':None}
    write(a,'capsules/'+capsule,data)
    write(a,'audio/'+capsule,{'ownerUid':a['localId'],'data':b'smoke fixture, not playable audio'})
    read(b,'audio/'+capsule); read(c,'audio/'+capsule,403);read(None,'audio/'+capsule,403)
    passed('shared audio allowed; third account and signed-out denied')
    receipt = 'users/'+b['localId']+'/collections/'+capsule
    write(b,receipt,{'device':'mobile'},['collectedAt']); read(a,receipt,403)
    write(b,receipt,{'device':'mobile'},['collectedAt'],expected=403)
    passed('collection receipt isolated and immutable')
    memory = 'users/'+b['localId']+'/memories/'+capsule
    write(b,memory,{'title':'smoke','note':'private','favorite':True,'visitLater':False},['updatedAt'])
    read(a,memory,403)
    photo = 'users/'+b['localId']+'/photos/'+capsule
    write(b,photo,{'image':b'fixture'},['updatedAt']);read(a,photo,403)
    passed('private memories and photos isolated')
    album = 'memoryAlbums/smoke_'+uuid.uuid4().hex
    write(b,album,{'title':'smoke','capsuleIds':[capsule],'ownerUid':b['localId'],'members':[b['localId'],a['localId']]},['updatedAt'])
    read(a,album);read(c,album,403)
    write(b,album,{'members':[b['localId']]},['updatedAt'],['members']);read(a,album,403)
    passed('album member access and revocation')
    destination = 'users/'+b['localId']+'/destinations/smoke_'+uuid.uuid4().hex
    write(b,destination,{'capsuleId':capsule,'title':'smoke','lat':37.5,'lng':127.0,'status':'pending'},['createdAt'])
    write(b,destination,{'status':'accepted'},['handledAt'],['status'])
    write(b,destination,{'status':'pending'},[],['status'],403)
    read(a,destination,403)
    passed('destination accepted once; reset and other-account read denied')
    for n,mode in enumerate(['LOCATION','AFTER_THREE_DAYS']):
        reply = b['localId']+'_'+str(1000000000002+n)
        rd = dict(data,ownerUid=b['localId'],replyToId=capsule,replyMode=mode,recipients=[a['localId']],visibleTo=[a['localId']])
        write(b,'capsules/'+reply,rd,['replySentAt'])
        write(b,'audio/'+reply,{'ownerUid':b['localId'],'data':b'reply fixture'})
        read(a,'audio/'+reply,200 if mode=='LOCATION' else 403)
        write(b,'capsules/'+reply,{'replyMode':'AFTER_THREE_DAYS' if mode == 'LOCATION' else 'LOCATION'},[],['replyMode'],403)
        write(b,'audio/'+reply,{'ownerUid':b['localId'],'data':b'replacement'},expected=403)
    passed('location reply; timed reply locked before 72h; reply immutable')
    write(a,'capsules/'+capsule,{'visibleTo':[]},[],['visibleTo'])
    read(b,'audio/'+capsule,403)
    passed('source sharing revocation denies subsequent audio read')
finally:
    failures = 0
    for u,p in reversed(documents):
        status,_ = request('DELETE',base+'/'+p,token=u['idToken'])
        if status not in (200,404): failures += 1
    for u in users:
        status,_ = request('POST','https://identitytoolkit.googleapis.com/v1/accounts:delete?key='+key,{'idToken':u['idToken']})
        if status != 200: failures += 1
    print('Cleanup failures:', failures, flush=True)
    assert failures == 0, 'Disposable fixtures need cleanup'
print('Completed checks:', len(checks))
