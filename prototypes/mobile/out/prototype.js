/* 정적 UX 시안의 로컬 상호작용. 네트워크 요청·녹음·실제 차량 제어 없음. */
document.addEventListener('DOMContentLoaded',()=>{
 document.querySelectorAll('button[role="switch"],button[role="checkbox"]').forEach(el=>el.addEventListener('click',()=>el.setAttribute('aria-checked',el.getAttribute('aria-checked')!=='true')));
 document.querySelectorAll('input[type="search"]').forEach(el=>el.addEventListener('input',()=>{
  const q=el.value.trim().toLocaleLowerCase();
  document.querySelectorAll('main .row').forEach(row=>{row.hidden=!!q&&!row.textContent.toLocaleLowerCase().includes(q);});
 }));
 document.querySelectorAll('button[role="radio"]').forEach(el=>el.addEventListener('click',()=>{const parent=el.closest('[role="radiogroup"]');if(!parent)return;parent.querySelectorAll('[role="radio"]').forEach(x=>{x.setAttribute('aria-checked',String(x===el));x.classList.toggle('is-selected',x===el);});}));
});
document.addEventListener('DOMContentLoaded',()=>{
 document.querySelectorAll('[data-demo-audio]').forEach(el=>el.addEventListener('click',()=>{
  const on=el.getAttribute('aria-pressed')!=='true';el.setAttribute('aria-pressed',String(on));
  el.textContent=on?'시안 재생 중 · 멈추기':'시안 재생하기';
  let note=document.getElementById('audio-demo-note');if(!note){note=document.createElement('p');note.id='audio-demo-note';note.className='block-note';note.setAttribute('role','status');el.after(note);}note.textContent='화면 동작을 보여주는 시안으로, 실제 음성은 재생되지 않아요.';
 }));
});
