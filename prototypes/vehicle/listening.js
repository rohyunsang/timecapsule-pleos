/* Browser implementation. Vehicle/Gleo/FusedLocation adapters remain unconnected. */
(()=>{
 const baseRender=render,baseGo=go;
 const audio=new Audio('audio/demo.wav');audio.preload='metadata';const previewAudio=new Audio('audio/demo.wav');
 let enabled=false;try{enabled=localStorage.getItem('tc-driving-listening')==='true'}catch{}
 const model={enabled,fresh:true,warning:false,status:'idle',reason:'',offered:false,position:0,source:'합성 안내 음성 · 가족 녹음 아님',mediaReady:false};
 let recorder=null,stream=null,chunks=[],recordingBlob=null,recordTimer=null,recordTicker=null,recordStart=0,objectUrl=null,attempt=0,recordDiscard=false;let micRequest=0;
 const input=()=>({...state,...model});
 const permitted=()=>!ListeningPolicy.reason(input());
 const log=[];
 const event=(name)=>{log.push({event:name,time:Date.now(),status:model.status});if(log.length>80)log.shift()};
 const pause=(why,status='paused')=>{attempt++;audio.pause();previewAudio.pause();model.status=status;model.reason=why;event(why)};
 const stop=why=>{pause(why,'stopped');audio.currentTime=0;model.position=0};
 async function start(){
  const reason=ListeningPolicy.reason(input());if(reason){pause(reason,'blocked');render();return false}
  const version=++attempt;
  try{await audio.play();if(version!==attempt||!permitted()){audio.pause();return false}model.status='playing';model.reason='';event('재생 시작');render();return true}
  catch{if(version===attempt){model.status='blocked';model.reason='브라우저에서 재생 시작을 눌러 주세요';render()}return false}
 }
 function drivingView(){
  const playing=model.status==='playing';const blocked=ListeningPolicy.reason(input());
  let title=playing?'이 길에 남겨진 목소리':model.status==='ended'?'다음에 이 길에서 만나요':model.status==='paused'?'잠시 멈췄어요':model.status==='blocked'?'재생을 시작하지 못했어요':!model.enabled?'운전에 집중해 주세요':blocked==='장소 밖'?'이 길의 목소리를 기다리고 있어요':blocked||'이 장소에 캡슐이 있어요';
  let desc=playing?model.source:model.reason||(!model.enabled?'주행 청취는 주차 중 켤 수 있어요.':blocked||'주차 중 선택한 청취 설정으로 들을 수 있어요.');
  const resume=model.enabled&&!blocked&&!playing&&model.status!=='ended';
  return `<div class="drive-player"><div class="eyebrow">주행 중 · ${model.enabled?'청취 켜짐':'청취 꺼짐'}</div><div class="hero-icon">${icon(playing?'play':model.status==='paused'?'pause':'route')}</div><h1>${title}</h1><p>${desc}</p>${playing?'<div class="drive-time" id="drive-time">'+Math.floor(audio.currentTime)+'초</div>':''}<div class="actions">${playing?'<button class="primary" id="listen-pause">'+icon('pause')+'잠시 멈추기</button>':resume?'<button class="primary" id="listen-start">'+icon('play')+'듣기</button>':''}${model.enabled?'<button id="listen-defer">이번에는 듣지 않기</button>':''}</div><small>화면 탐색·편집은 주차 후 이용해 주세요.</small></div>`;
 }
 render=function(){
  baseRender();
  if(state.driving){document.querySelector('.content').innerHTML=drivingView()}
  else if(current==='listening-settings'){
   document.querySelector('.content').innerHTML=`<div class="eyebrow">주차 중 설정</div><h1>이 길을 지날 때 들을까요?</h1><div class="card"><h2>주행 중 캡슐 청취</h2><p>미리 켜두면, 조건이 맞는 장소에 진입할 때<br>캡슐 음성을 재생합니다.</p><button class="${model.enabled?'':'primary'}" id="listen-toggle" aria-pressed="${model.enabled}">${model.enabled?'주행 청취 끄기':'주행 청취 켜기'}</button></div><p>내비·통화·안전 경고가 우선입니다.<br>위치가 불확실하거나 장소를 벗어나면 멈춥니다.</p><div class="actions"><button data-listen-go="settings">설정으로</button><button id="listen-preview">안내 음성 미리 듣기</button></div><small>주행 상태와 위치는 시연 입력입니다. Pleos 승인·연동은 별도입니다.</small>`;
  }
  else if(current==='settings')document.querySelector('.list').insertAdjacentHTML('afterbegin',`<button data-listen-go="listening-settings">주행 중 캡슐 청취 <small>${model.enabled?'켜짐':'꺼짐'} · 주차 중 변경</small></button>`);
  else if(current==='play'){
   const el=document.querySelector('.player-content');if(el){el.querySelector('h1').textContent=model.source.startsWith('직접')?'직접 남긴 목소리':'주행 청취 안내';el.querySelector('p').textContent=model.source;el.querySelector('.memory-context').textContent='오디오 실제 재생 · 위치·날짜는 시연 입력';el.querySelector('.player-actions').innerHTML=`<button data-listen-go="detail">그만 듣기</button><button class="primary playback-main" id="${model.status==='playing'?'listen-pause':'listen-start'}">${icon(model.status==='playing'?'pause':'play')}${model.status==='playing'?'일시 정지':'재생 시작'}</button>`;el.querySelector('.player-note').textContent=model.reason||'이 장소를 벗어나면 재생이 멈춰요.';}
  }
  else if(current==='consent'){
   const a=document.querySelector('.center .actions');a.insertAdjacentHTML('afterbegin','<button class="primary" id="real-record">동의 확인 후 실제 녹음</button>');const mock=a.querySelector('[data-go="record"]');mock.classList.remove('primary');mock.textContent='녹음 화면만 시연';
  }
  else if(current==='record'&&recorder&&recorder.state==='recording'){
   document.querySelector('.recording-layout small').textContent='마이크 녹음 중 · 이 브라우저 메모리에만 보관';document.querySelector('[data-go="review"]').onclick=finishRecording;document.querySelector('[data-go="home"]').onclick=cancelRecording;
  }
  else if(current==='review'&&recordingBlob){document.querySelector('.content').insertAdjacentHTML('beforeend','<div class="actions"><button id="preview-recording">내 녹음 확인</button><button id="delete-recording">녹음 삭제</button></div>');}
  document.querySelectorAll('[data-listen-go]').forEach(b=>b.onclick=()=>go(b.dataset.listenGo));
  const bind=(id,fn)=>{const b=document.getElementById(id);if(b)b.onclick=fn};
  bind('listen-start',start);bind('listen-pause',()=>{pause('사용자가 멈췄어요');render()});
  bind('listen-defer',()=>{stop('이번 이동에서는 듣지 않아요');model.offered=true;render()});
  bind('listen-toggle',()=>{if(state.driving)return;model.enabled=!model.enabled;try{localStorage.setItem('tc-driving-listening',String(model.enabled))}catch{}if(!model.enabled)stop('주행 청취 꺼짐');event('청취 설정 변경');render()});
  bind('listen-preview',async()=>{if(state.driving)return;stop('안내 음성 미리 듣기');const version=++attempt;try{await previewAudio.play();if(version!==attempt||state.driving)previewAudio.pause();else{model.status='preview';render()}}catch{model.reason='오디오를 재생하지 못했어요';render()}});
  bind('real-record',record);bind('preview-recording',async()=>{if(state.driving)return;try{await audio.play();model.status='preview'}catch{} });
  bind('delete-recording',()=>{stop('녹음 삭제');if(objectUrl)URL.revokeObjectURL(objectUrl);objectUrl=null;recordingBlob=null;audio.src='audio/demo.wav';model.source='합성 안내 음성 · 가족 녹음 아님';render()});
  document.getElementById('note').textContent='웹 기능 구현: 실제 녹음·재생 / 시연 입력: 차량·위치·날짜·권한 / Pleos 주행 청취 승인 및 SDK 연동 미완료';
  updateProgress();
 };
 go=function(id){
  micRequest++;
  if(state.driving&&!['play','paused','driving'].includes(id)){render();return}
  if(recorder?.state==='recording'&&id!=='record'){cancelRecording();return}
  if(!['play','paused','driving'].includes(id)&&['playing','preview'].includes(model.status))stop('재생 종료');
  if(id==='paused')pause('사용자가 멈췄어요');
  baseGo(id);
  if(id==='play')start();
 };
 window.go=go;
 async function record(){
  if(state.driving)return;
  const request=++micRequest;const recordButton=document.getElementById('real-record');if(recordButton){recordButton.disabled=true;recordButton.textContent='마이크 연결 중'}
  try{
   stream=await navigator.mediaDevices.getUserMedia({audio:true});
   if(state.driving||request!==micRequest){stream.getTracks().forEach(t=>t.stop());stream=null;return}
   stop('녹음 시작');chunks=[];recordDiscard=false;recorder=new MediaRecorder(stream);recordStart=Date.now();
   recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)};
   recorder.onstop=()=>{stream?.getTracks().forEach(t=>t.stop());stream=null;clearTimeout(recordTimer);clearInterval(recordTicker);if(!recordDiscard&&chunks.length){recordingBlob=new Blob(chunks,{type:recorder.mimeType});if(objectUrl)URL.revokeObjectURL(objectUrl);objectUrl=URL.createObjectURL(recordingBlob);audio.src=objectUrl;model.source='직접 녹음 · 브라우저 메모리에만 보관';if(!state.driving)baseGo('review')}else render();};
   recorder.start();recordTimer=setTimeout(finishRecording,30000);baseGo('record');const tick=()=>{const el=document.querySelector('.recording-layout .timer');if(el)el.innerHTML=fmt(Math.min(30,(Date.now()-recordStart)/1000))+' <span>/ 00:30</span>'};tick();recordTicker=setInterval(tick,250);event('실제 녹음 시작');
  }catch{model.reason='마이크 권한 또는 장치를 확인해 주세요';baseGo('denied')}
 }
 function finishRecording(){if(recorder?.state==='recording')recorder.stop()}
 function cancelRecording(){recordDiscard=true;if(recorder?.state==='recording')recorder.stop();clearTimeout(recordTimer);clearInterval(recordTicker);stream?.getTracks().forEach(t=>t.stop());if(!state.driving)baseGo('home')}
 function updateProgress(){
  const dur=Number.isFinite(audio.duration)?audio.duration:0,pos=audio.currentTime||0;model.position=pos;
  const progress=document.querySelector('.scene-play .progress');if(progress){progress.firstElementChild.style.width=(dur?pos/dur*100:0)+'%';progress.setAttribute('aria-valuenow',Math.floor(pos));progress.setAttribute('aria-valuemax',Math.floor(dur));const row=progress.nextElementSibling;if(row?.classList.contains('row')){row.firstElementChild.textContent=fmt(pos);row.lastElementChild.textContent=fmt(dur)}}
  const timer=document.getElementById('drive-time');if(timer)timer.textContent=`${fmt(pos)} / ${fmt(dur)}`;
 }
 const fmt=n=>`${String(Math.floor(n/60)).padStart(2,'0')}:${String(Math.floor(n%60)).padStart(2,'0')}`;
 audio.ontimeupdate=updateProgress;audio.onloadedmetadata=()=>{model.mediaReady=true;updateProgress()};audio.onended=()=>{model.status='ended';event('재생 완료');render()};audio.onerror=()=>{pause('오디오 파일을 읽지 못했어요','error');render()};
 const toolbar=document.querySelector('.controls');toolbar.insertAdjacentHTML('beforeend','<label><input id="fresh" type="checkbox" checked> 위치 최신·정확</label><label><input id="warning" type="checkbox"> 안전 경고</label><button id="listen-settings-shortcut">주행 청취 설정</button>');
 document.getElementById('listen-settings-shortcut').onclick=()=>go('listening-settings');
 [...ids,'fresh','warning'].forEach(id=>document.getElementById(id).onchange=e=>{
  const before=input();if(id==='fresh'||id==='warning')model[id]=e.target.checked;else state[id]=e.target.checked;
  if((state.driving||state.interrupt||model.warning)&&recorder?.state==='recording')cancelRecording();
  const why=ListeningPolicy.reason(input());
  if(why&&['playing','preview'].includes(model.status))pause(why);
  if(state.driving&&model.status==='preview')stop('미리 듣기 종료');
  if(why&&model.status==='idle')model.reason=why;
  if(before.driving&&!state.driving&&model.status==='playing')current='play';
  if(ListeningPolicy.mayAutoStart(before,input(),model)){model.offered=true;start()}
  render();
 });
 document.addEventListener('visibilitychange',()=>{if(document.hidden&&['playing','preview'].includes(model.status)){pause('다른 화면으로 이동했어요');render()}});
 window.addEventListener('pagehide',()=>{audio.pause();previewAudio.pause();stream?.getTracks().forEach(t=>t.stop())});
 window.canPlay=permitted;window.Listening={model,audio,log,start,pause,stop,input};
 scenes.push(['listening-settings','주행 청취 설정','주차 중 청취 동의',()=> '']);document.getElementById('scene').insertAdjacentHTML('beforeend','<option value="listening-settings">주행 청취 설정</option>');document.getElementById('scene').onchange=e=>go(e.target.value);
 render();
})();
