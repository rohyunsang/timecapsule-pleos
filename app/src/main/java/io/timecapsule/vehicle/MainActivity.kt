package io.timecapsule.vehicle

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity:Activity() {
    private var background=Color.rgb(12,23,27)
    private var panel=Color.rgb(23,39,43)
    private var ink=Color.rgb(244,240,226)
    private var muted=Color.rgb(162,186,185)
    private var accent=Color.rgb(132,214,193)
    private var buttonColor=Color.rgb(44,63,66)
    private lateinit var engine:CapsuleEngine
    private lateinit var parkedContent:LinearLayout
    private lateinit var drivingContent:LinearLayout
    private lateinit var status:TextView
    private lateinit var vehicleText:TextView
    private lateinit var list:LinearLayout
    private lateinit var recordButton:Button
    private lateinit var dateButton:Button
    private lateinit var listenButton:Button
    private lateinit var captureButton:Button
    private lateinit var transportButton:Button
    private lateinit var tripText:TextView
    private lateinit var preparation:LinearLayout
    private lateinit var recordingPanel:LinearLayout
    private var dateDelay=0L
    private var section="prepare"
    private var signature=""
    private val dialogs=mutableListOf<Dialog>()
    override fun onCreate(saved:Bundle?) {
        super.onCreate(saved)
        section=saved?.getString("section") ?: "prepare"; dateDelay=saved?.getLong("dateDelay") ?: 0L
        engine=CapsuleEngine.get(this)
        if(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK!=Configuration.UI_MODE_NIGHT_YES) {
            background=Color.rgb(244,247,243); panel=Color.WHITE; ink=Color.rgb(17,43,44); muted=Color.rgb(73,98,96); accent=Color.rgb(0,95,79); buttonColor=Color.rgb(220,232,226)
        }
        buildUi(); engine.connect()
    }
    override fun onSaveInstanceState(out:Bundle) { out.putString("section",section); out.putLong("dateDelay",dateDelay); super.onSaveInstanceState(out) }
    override fun onStart() { super.onStart(); engine.setVisible(true); engine.observer={render()}; render() }
    override fun onStop() {
        engine.observer=null
        engine.setVisible(false); dialogs.forEach {it.dismiss()}; dialogs.clear(); super.onStop()
    }
    private fun scale()=(resources.configuration.screenWidthDp/1100f).coerceIn(1f,1.65f)
    private fun dp(v:Int)=(v*resources.displayMetrics.density*scale()).toInt()
    private fun text(s:String,size:Float=18f,color:Int=ink)=TextView(this).apply {text=s;textSize=size*scale();setTextColor(color);setPadding(0,dp(6),0,dp(6))}
    private fun column()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
    private fun box(color:Int)=GradientDrawable().apply {setColor(color);cornerRadius=dp(20).toFloat()}
    private fun button(s:String,primary:Boolean=false,action:()->Unit)=Button(this).apply {
        text=s;textSize=18f*scale();isAllCaps=false;setTextColor(if(primary)Color.WHITE else ink)
        background=box(if(primary) Color.rgb(0,105,85) else buttonColor);minHeight=dp(64);setPadding(dp(16),dp(8),dp(16),dp(8))
        layoutParams=LinearLayout.LayoutParams(-1,dp(64)).apply {topMargin=dp(12)};setOnClickListener {action()}
    }
    private fun card()=column().apply {background=box(panel);setPadding(dp(24),dp(18),dp(24),dp(18))}
    private fun buildUi() {
        window.statusBarColor=background;window.navigationBarColor=background
        val root=column().apply {setPadding(dp(28),dp(16),dp(28),dp(16));setBackgroundColor(this@MainActivity.background)};setContentView(root)
        val header=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        header.addView(text("◉  타임캡슐",27f).apply {typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f))
        vehicleText=text("차량 상태 확인 중",16f,muted);header.addView(vehicleText);root.addView(header)
        parkedContent=column()
        val tabs=LinearLayout(this)
        listOf("prepare" to "출발 준비","record" to "목소리 남기기","library" to "보관함").forEach { (key,label)->
            tabs.addView(button(label) {if(engine.parked()&&!engine.recording) {section=key;render()}},LinearLayout.LayoutParams(0,dp(58),1f).apply {rightMargin=dp(12);topMargin=dp(12)})
        }
        parkedContent.addView(tabs)
        val scroll=ScrollView(this).apply {isFillViewport=true}
        val body=column().apply {setPadding(0,dp(18),0,0)}
        preparation=card()
        preparation.addView(text("내비는 그대로,\n목소리는 이 길에서.",32f).apply {typeface=Typeface.DEFAULT_BOLD})
        preparation.addView(text("출발 전에 준비하면 내비 화면에서도 기다려요.",18f,muted))
        tripText=text("도착 알림 뒤 차량 미디어 재생 버튼으로 들으세요.",19f,accent);preparation.addView(tripText)
        listenButton=button("이번 주행 듣기 준비",true) {prepareListening()};preparation.addView(listenButton)
        captureButton=button("이번 주행 기록 준비") {prepareRecording()};preparation.addView(captureButton)
        transportButton=button("이어 듣기") {if(engine.isPlaying)engine.pause() else engine.resume()};preparation.addView(transportButton)
        preparation.addView(button("내비 화면으로 이동") {openNavigation()})
        preparation.addView(text("위치에서 한 번 열린 캡슐은 지나가도 마저 들을 수 있어요.\n내비 안내 때 멈추며, 자동으로 다시 틀지 않아요.",16f,muted))
        body.addView(preparation)
        recordingPanel=card()
        recordingPanel.addView(text("지나가는 지금을 남겨요.",30f).apply {typeface=Typeface.DEFAULT_BOLD})
        recordingPanel.addView(text("최대 15초 · 녹음 시작 위치에 자동 저장",17f,muted))
        dateButton=button("열리는 날 · 지금부터") {
            if(engine.parked()&&!engine.recording) showDialog(AlertDialog.Builder(this).setTitle("언제 열까요?")
                .setSingleChoiceItems(arrayOf("지금부터","1년 뒤부터"),if(dateDelay==0L)0 else 1) {d,i->dateDelay=if(i==0)0 else 365L*24*60*60*1000;d.dismiss();render()}.setNegativeButton("취소",null).create())
        };recordingPanel.addView(dateButton)
        recordButton=button("녹음 시작",true) {recordTapped()};recordingPanel.addView(recordButton)
        recordingPanel.addView(text("내비 화면에서도 기록 명령으로 남길 수 있어요.\n내비 안내나 방향지시등으로 중단되면 임시보관해요.",16f,muted))
        body.addView(recordingPanel)
        list=column();body.addView(list);scroll.addView(body)
        parkedContent.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(parkedContent,LinearLayout.LayoutParams(-1,0,1f))
        drivingContent=column().apply {gravity=Gravity.CENTER;addView(text("내비 화면을 유지하세요.",30f));addView(text("내비를 유지하며 음성 기록과 청취를 제어해요.",20f,muted));visibility=View.GONE}
        root.addView(drivingContent,LinearLayout.LayoutParams(-1,0,1f))
        status=text("",16f,muted);root.addView(status)
    }
    private fun prepareListening() {
        if(!engine.parked()||engine.recording) return
        if(engine.armed) {engine.silence();return}
        if(!ensureLocation()) return
        showDialog(AlertDialog.Builder(this).setTitle("이번 주행에서 어떻게 들을까요?")
            .setMessage("기존 음악 대신 타임캡슐을 청취 소스로 준비해요. 내비 화면은 유지됩니다. 위치를 사용하며, 2시간 또는 15분 주차 후 종료해요.")
            .setPositiveButton("알림 후 버튼으로 듣기") {_,_->engine.setArmed(true,false)}
            .setNeutralButton("도착하면 자동 듣기") {_,_->engine.setArmed(true,true)}
            .setNegativeButton("취소",null).create())
    }
    private fun prepareRecording() {
        if(!engine.parked()) return
        if(engine.captureReady) {engine.endCapture();return}
        if(!ensureLocation()) return
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10);return}
        showDialog(AlertDialog.Builder(this).setTitle("이번 주행의 기록을 준비할까요?")
            .setMessage("함께 탄 분들의 녹음 동의를 확인하세요. 대기 중에는 녹음하지 않으며, 별도의 기록 명령이 올 때만 15초 녹음해요. 시작한 위치에 자동 저장하고 내비 안내 때는 임시보관해요. 2시간 또는 15분 주차 후 준비를 종료합니다.\n\nGleo 음성 호출과 전용 차량 버튼 연결은 제조사 검증이 필요합니다. 현재 APK는 기록 명령 수신과 백그라운드 녹음을 제공합니다.")
            .setPositiveButton("동의 확인 · 기록 준비") {_,_->engine.prepareCapture(dateDelay)}.setNegativeButton("취소",null).create())
    }
    private fun openNavigation() {
        if(!engine.parked()) return
        val maps=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
        val intent=if(maps.resolveActivity(packageManager)!=null) maps else packageManager.getLaunchIntentForPackage("ai.umos.maps.android.navigation.app")
        try { if(intent!=null) startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) else Toast.makeText(this,"차량의 내비 버튼으로 이동해 주세요.",Toast.LENGTH_LONG).show() }
        catch(_:Exception) {Toast.makeText(this,"차량의 내비 버튼으로 이동해 주세요.",Toast.LENGTH_LONG).show()}
    }
    private fun recordTapped() {
        if(!engine.parked()) return
        if(engine.recording) {engine.finishRecording(true);return}
        if(!ensureLocation()) return
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10);return}
        showDialog(AlertDialog.Builder(this).setTitle("함께 탄 분들의 동의를 확인하세요")
            .setMessage("알림음과 함께 최대 15초 녹음하고 시작한 위치에 자동 저장해요. 내비 화면으로 옮기거나 출발해도 녹음이 이어져요.")
            .setPositiveButton("동의 확인 · 녹음") {_,_->if(engine.prepareCapture(dateDelay)) engine.main.postDelayed({engine.recordCommand()},300)}.setNegativeButton("취소",null).create())
    }
    private fun ensureLocation():Boolean {
        if(engine.hasLocationPermission()) return true
        val p=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,CapsuleEngine.PLEOS_LOCATION_PERMISSION)
        if(Build.VERSION.SDK_INT>=33)p.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(p.toTypedArray(),11);return false
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results)
        Toast.makeText(this,if(results.isNotEmpty()&&results.all {it==PackageManager.PERMISSION_GRANTED})"허용했어요. 원하는 기능을 다시 눌러 주세요." else "필요한 권한을 허용해야 사용할 수 있어요.",Toast.LENGTH_LONG).show()
    }
    private fun showDialog(d:Dialog) {dialogs.add(d);d.show()}
    private fun render() {
        val parked=engine.parked()
        parkedContent.visibility=if(parked)View.VISIBLE else View.GONE;drivingContent.visibility=if(parked)View.GONE else View.VISIBLE
        if(!parked) {dialogs.forEach {it.dismiss()};dialogs.clear()}
        vehicleText.text=if(parked)"P · 주차 중" else if(engine.ux.known&&engine.gearFresh())"주행 중 · 화면 조작 제한" else "차량 상태 확인 중"
        preparation.visibility=if(section=="prepare")View.VISIBLE else View.GONE
        recordingPanel.visibility=if(section=="record")View.VISIBLE else View.GONE
        list.visibility=if(section=="library")View.VISIBLE else View.GONE
        recordButton.text=if(engine.recording)"녹음 저장 · ${engine.recordSeconds} / 15초" else "녹음 시작"
        recordButton.isEnabled=parked&&(engine.locationFresh()||!engine.hasLocationPermission()||engine.recording)
        dateButton.text=if(dateDelay==0L)"열리는 날 · 지금부터" else "열리는 날 · 1년 뒤부터"
        dateButton.isEnabled=!engine.recording
        listenButton.text=if(engine.armed)"이번 청취 종료" else "이번 주행 듣기 준비"
        captureButton.text=if(engine.captureReady)"이번 주행 기록 종료" else "이번 주행 기록 준비"
        listenButton.isEnabled=!engine.recording
        transportButton.visibility=if(engine.selectedId!=null)View.VISIBLE else View.GONE
        transportButton.text=if(engine.isPlaying)"잠시 멈춤" else "이어 듣기"
        tripText.text=when {engine.isPlaying->"내비 안내 때 잠시 멈춰요.";engine.selectedId!=null->engine.pauseReason;engine.armed->if(engine.session.automatic)"도착하면 자동 듣기 · 켜짐" else "도착 알림 후 버튼으로 듣기 · 켜짐";else->"도착 알림 뒤 차량 미디어 재생 버튼으로 들으세요."}
        status.text=engine.message
        val next=engine.capsules.joinToString {"${it.id}:${it.draft}:${engine.canOpen(it)}"}+engine.selectedId+engine.isPlaying+parked
        if(next!=signature) {signature=next;renderList()}
    }
    private fun renderList() {
        list.removeAllViews()
        list.addView(text("보관함",27f).apply {typeface=Typeface.DEFAULT_BOLD})
        if(engine.capsules.isEmpty()) list.addView(text("아직 묻어둔 목소리가 없어요.\n출발 전에 ‘주행 기록 준비’를 켜 주세요.",20f,muted))
        val format=SimpleDateFormat("yyyy.MM.dd HH:mm",Locale.KOREA)
        engine.capsules.forEach {c->
            val card=card()
            card.addView(text("${format.format(Date(c.created))} · ${c.duration}초",21f))
            val label=when {c.draft->"임시보관 · 녹음한 장소에 저장할 수 있어요";c.id==engine.selectedId->"이번 방문에서 열렸어요";System.currentTimeMillis()<c.opens->"${format.format(Date(c.opens))} 이후 공개";engine.canOpen(c)->"저장 위치 주변 · 열 수 있어요";else->"저장한 장소에 다시 가면 열려요"}
            card.addView(text(label,17f,accent))
            if(c.draft) card.addView(button("캡슐로 저장",true) {engine.saveDraft(c)})
            else card.addView(button(if(c.id==engine.playingId)"잠시 멈춤" else if(c.id==engine.selectedId)"이어 듣기" else "듣기",true) {
                if(c.id==engine.playingId)engine.pause() else engine.play(c)
            }.apply {isEnabled=engine.parked()&&(engine.canOpen(c)||c.id==engine.selectedId);alpha=if(isEnabled)1f else 0.4f})
            card.addView(button("삭제") {if(engine.parked())showDialog(AlertDialog.Builder(this).setTitle("목소리를 삭제할까요?").setMessage("음성과 장소 정보를 지웁니다. 되돌릴 수 없어요.").setPositiveButton("삭제") {_,_->engine.delete(c)}.setNegativeButton("취소",null).create())})
            list.addView(card,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(14)})
        }
    }
}
