const assert=require('node:assert/strict');const p=require('./listening-policy.js');
const valid={near:true,date:true,access:true,fresh:true,interrupt:false,warning:false,driving:true,enabled:true};
let n=0;function check(name,fn){fn();console.log('PASS '+name);n++}
check('주행 청취 사전 동의 시 허용',()=>assert.equal(p.reason(valid),null));
for(const [key,value,label] of [['enabled',false,'주행 청취 꺼짐'],['near',false,'장소 밖'],['date',false,'개봉일 이전'],['access',false,'권한 없음'],['fresh',false,'위치 확인 필요'],['interrupt',true,'내비·통화 우선'],['warning',true,'안전 경고']])check(label,()=>assert.equal(p.reason({...valid,[key]:value}),label));
check('장소 진입 때만 자동 재생',()=>assert.equal(p.mayAutoStart({...valid,near:false},valid,{offered:false}),true));
check('같은 장소 체류 시 중복 재생 금지',()=>assert.equal(p.mayAutoStart(valid,valid,{offered:false}),false));
check('이번 이동에 미룬 캡슐 재제안 금지',()=>assert.equal(p.mayAutoStart({...valid,near:false},valid,{offered:true}),false));
check('주차 청취는 주행 동의와 분리',()=>assert.equal(p.reason({...valid,driving:false,enabled:false}),null));
console.log(`${n} policy assertions passed`);
