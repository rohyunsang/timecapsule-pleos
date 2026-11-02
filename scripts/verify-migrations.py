#!/usr/bin/env python3
"""Exercise the production migration SQL against both retained Room schema versions."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/app/timecapsule/data/Migrations.kt').read_text()
create = re.search(r'db.execSQL\("""(CREATE TABLE capsule_new.*?)"""\)', source, re.S).group(1)
original = re.search(r'val original = "([^"]+)"', source).group(1)
sharing = re.search(r'val sharing = if \(social\) "([^"]+)" else "([^"]+)"', source)
expected = json.loads((root / 'app/schemas/app.timecapsule.data.AppDatabase/3.json').read_text())['database']
for version in (1, 2):
    schema = json.loads((root / f'app/schemas/app.timecapsule.data.AppDatabase/{version}.json').read_text())['database']
    db = sqlite3.connect(':memory:')
    for entity in schema['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
    capsule = next(e for e in schema['entities'] if e['tableName'] == 'capsule')
    values = {f['columnName']: (0 if f['affinity'] in ('INTEGER', 'REAL') else '') if f['notNull'] else None for f in capsule['fields']}
    values.update(id=42, audioPath='/private/voice.m4a', createdAt=123, lat=37.0, lng=127.0)
    if version == 2:
        values.update(ownerUid='friend', audience='SOME', recipients='recipient', remoteId='remote42')
    db.execute(f"INSERT INTO capsule ({','.join(values)}) VALUES ({','.join('?' for _ in values)})", list(values.values()))
    db.execute('INSERT INTO letter_lock VALUES (42, 999999, NULL)')
    db.execute(create)
    db.execute(f"INSERT INTO capsule_new SELECT {original},{sharing.group(1 if version == 2 else 2)},0 FROM capsule")
    db.execute('DROP TABLE capsule')
    db.execute('ALTER TABLE capsule_new RENAME TO capsule')
    assert db.execute('SELECT id,audioPath,createdAt,draft FROM capsule').fetchone() == (42, '/private/voice.m4a',123,0)
    assert db.execute('SELECT openAfter FROM letter_lock WHERE capsuleId=42').fetchone() == (999999,)
    if version == 2:
        assert db.execute('SELECT ownerUid,audience,recipients,remoteId FROM capsule').fetchone() == ('friend','SOME','recipient','remote42')
    baseline = sqlite3.connect(':memory:')
    for e in expected['entities']:
        baseline.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
        assert db.execute(f"PRAGMA table_info({e['tableName']})").fetchall() == baseline.execute(f"PRAGMA table_info({e['tableName']})").fetchall()
    print(f'Room {version} -> 3: capsule, date lock, sharing fields and schema preserved')
