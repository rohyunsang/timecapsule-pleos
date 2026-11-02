/* Pure policy: platform authorization is an adapter concern; browser inputs are simulations. */
(function(root){
 const reason=s=>!s.access?'권한 없음':!s.date?'개봉일 이전':!s.fresh?'위치 확인 필요':!s.near?'장소 밖':s.interrupt?'내비·통화 우선':s.warning?'안전 경고':s.driving&&!s.enabled?'주행 청취 꺼짐':null;
 const mayAutoStart=(before,after,session)=>!before.near&&after.near&&after.driving&&after.enabled&&!session.offered&&!reason(after);
 root.ListeningPolicy={reason,mayAutoStart};
 if(typeof module!=='undefined')module.exports=root.ListeningPolicy;
})(typeof window!=='undefined'?window:globalThis);
