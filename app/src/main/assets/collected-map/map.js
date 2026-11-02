'use strict';
const map = L.map('map').setView([36.3,127.8],6);
const layer = L.layerGroup().addTo(map);
const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom:19, attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>',
  updateWhenIdle:true, keepBuffer:1
}).addTo(map);
tiles.on('tileerror', () => { const s=document.getElementById('status');s.textContent='지도를 불러오지 못했어요. 아래 목록에서 캡슐을 선택할 수 있어요';s.style.display='block'; });
let fitted = false;
window.renderCapsules = function(items,selected) {
  layer.clearLayers();
  const points=[];
  const groups=new Map();
  items.forEach(c=> {const key=c.lat+','+c.lng; if(!groups.has(key))groups.set(key,[]);groups.get(key).push(c);});
  groups.forEach(group=>{
    const c=group.find(c=>c.id===selected)||group[0];
    const circle=L.circleMarker([c.lat,c.lng],{radius:20,color:'#fff',weight:3,fillColor:group.some(c=>c.id===selected)?'#1464d2':'#242424',fillOpacity:1}).addTo(layer);
    circle.bindTooltip(String(group.length>1?group.length:'▶'),{permanent:true,direction:'center',className:'capsule-label'});
    circle.on('click',()=>{ location.href='https://timecapsule.local/select?id='+encodeURIComponent(c.id); });
    points.push([c.lat,c.lng]);
  });
  if(!fitted && points.length){map.fitBounds(points,{padding:[40,40],maxZoom:14});fitted=true;}
};
window.focusCapsule = function(lat,lng){map.setView([lat,lng],Math.max(12,map.getZoom()));};
