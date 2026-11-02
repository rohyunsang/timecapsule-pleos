package io.timecapsule.vehicle

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.location.LocationManager
import android.os.*
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import org.json.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CapsuleEngine private constructor(val context: Context) {
    companion object {
        const val PLEOS_LOCATION_PERMISSION="pleos.car.permission.FUSED_LOCATION"
        @android.annotation.SuppressLint("StaticFieldLeak")
        @Volatile private var instance: CapsuleEngine? = null
        fun get(c: Context): CapsuleEngine = instance ?: synchronized(this) {
            instance ?: CapsuleEngine(c.applicationContext).also { instance = it }
        }
    }
    data class Capsule(val id: String, val created: Long, val opens: Long, val lat: Double, val lon: Double,
                       val duration: Int, val filename: String, val draft: Boolean=false)
    val main = Handler(Looper.getMainLooper())
    private val playbackIntent=Intent(context,PlaybackService::class.java)
    private val prefs = context.getSharedPreferences("capsules", Context.MODE_PRIVATE)
    val capsules = mutableListOf<Capsule>()
    val session=ListeningSession()
    val ux=DrivingRestrictions(context,main) { enforce(); changed() }
    var observer: (() -> Unit)? = null
    var foreground = false; private set
    var gear: VehicleGear? = null; private set
    var gearAt = 0L; private set
    var signal: TurnSignalValue? = null; private set
    var signalAt = 0L; private set
    var location: LocationData? = null; private set
    var locationAt = 0L; private set
    var vehicleStatus = "차량 연결 중"; private set
    var locationStatus = "현재 위치 확인 중"; private set
    var message = "출발 전에 듣기를 준비하세요. 내비 화면에서도 함께합니다."; private set
    val armed get()=session.active
    var captureReady = false; private set
    private var captureStarted=0L
    private var captureParkedSince=0L
    private var captureOpenDelay=0L
    val active get()=armed || captureReady || recording
    val recordingLimit get()=15
    var recording = false; private set
    var recordSeconds = 0; private set
    var isPlaying=false; private set
    val selectedId get()=session.currentId
    val playingId get()=if(isPlaying) selectedId else null
    var pauseReason=""; private set
    private var initialized = false
    private var subscribed = false
    private var locationSubscribed = false
    private var vehicle: Vehicle? = null
    private var fused: FusedLocation? = null
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null
    private var recordingLocation: LocationData? = null
    private var recordStart = 0L
    private var releaseAt = 0L
    private var sessionWallStart=0L
    private var parkedSince=0L
    private var lastPositionSample=0L
    private var insideCandidate: String?=null
    private var insideSamples=0
    private val audio = context.getSystemService(AudioManager::class.java)
    private var focus: AudioFocusRequest? = null
    private var focusBlocked=false
    private var lastPoll = 0L
    private fun elapsed()=SystemClock.elapsedRealtime()
    private val gearListener = object: GearSelectionListener {
        override fun onSelected(value: VehicleGear?) { main.post { acceptGear(value) } }
        override fun onFailed(exception: Exception) { main.post { gear = null; vehicleStatus = "차량 상태 확인 중"; enforce(); changed() } }
    }
    private val signalListener = object: TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) { main.post { signal = value; signalAt = elapsed(); enforce(); changed() } }
        override fun onFailed(exception: Exception) { main.post { signal = null; enforce(); changed() } }
    }
    private val locationListener = object: FusedLocationListener {
        override fun onChanged(value: LocationData?) { main.post {
            location = value; locationAt = elapsed()
            locationStatus = if (value == null) "현재 위치 확인 중" else "현재 위치 확인됨"
            enforce(); observeArrival(); changed()
        } }
        override fun onError(exception: Exception) { main.post { location=null; locationStatus="위치 연결 대기"; enforce(); changed() } }
    }
    init {
        try {
            val array = JSONArray(prefs.getString("items", "[]"))
            for (i in 0 until array.length()) {
                val o=array.getJSONObject(i); val file=o.getString("file")
                if(!file.contains('/') && File(context.filesDir,file).isFile)
                    capsules.add(Capsule(o.getString("id"),o.getLong("created"),o.getLong("opens"),o.getDouble("lat"),o.getDouble("lon"),o.getInt("duration"),file,o.optBoolean("draft")))
            }
        } catch (_: Exception) { message="저장 목록을 읽지 못했어요. 앱을 다시 실행해 주세요." }
    }
    fun connect() {
        if(initialized) return
        initialized=true; ux.connect()
        try { vehicle=Vehicle(context).also { it.initialize() } } catch(_:Exception) { vehicleStatus="차량 서비스 연결 대기" }
        try { val candidate=FusedLocation(context); if(candidate.isSupported()) { candidate.initialize(); fused=candidate } else locationStatus="위치 서비스를 지원하지 않는 차량이에요" } catch(_:Exception) { locationStatus="위치 서비스 연결 대기" }
        main.post(tick)
    }
    fun setVisible(visible:Boolean) {
        foreground=visible
        if(visible) connect() else if(!active) disconnect()
    }
    private fun disconnect() {
        if(!initialized) return
        initialized=false; main.removeCallbacks(tick)
        try { if(subscribed) { vehicle?.getGear()?.unregisterGearSelection(gearListener); vehicle?.getTurnSignal()?.unregisterTurnSignalState(signalListener) }; vehicle?.release() } catch(_:Exception){}
        try { if(locationSubscribed) fused?.unregisterFusedLocationCallback(locationListener); fused?.release() } catch(_:Exception){}
        ux.disconnect(); subscribed=false; locationSubscribed=false; vehicle=null; fused=null; gear=null; location=null; signal=null
    }
    private val tick=object:Runnable {
        override fun run() {
            val now=elapsed()
            if(now-lastPoll>2000) { lastPoll=now; poll() }
            if(recording) { recordSeconds=((now-recordStart)/1000).toInt(); if(recordSeconds>=recordingLimit) finishRecording(true) }
            if(captureReady) {
                if(parked()) { if(captureParkedSince==0L) captureParkedSince=now } else captureParkedSince=0
                if(now-captureStarted>=ListeningSession.SESSION_MS || (captureParkedSince>0 && now-captureParkedSince>=ListeningSession.PARK_IDLE_MS)) {
                    endCapture(); message="이번 주행 기록 준비를 마쳤어요."
                }
            }
            if(armed) {
                if(gear==VehicleGear.GEAR_PARK && gearFresh()) { if(parkedSince==0L) parkedSince=now } else parkedSince=0
                if(session.expired(now) || (parkedSince>0 && now-parkedSince>=ListeningSession.PARK_IDLE_MS)) {
                    silence(); message="이번 청취를 마쳤어요. 다음 출발 전에 다시 켜 주세요."
                }
                selectedId?.let { if(!session.validGrant(it,now)) skip("개봉 시간이 지나 보관했어요. 다음 방문에 다시 만나요.") }
            }
            enforce(); changed()
            if(initialized) main.postDelayed(this,500)
        }
    }
    private fun poll() {
        try {
            vehicle?.getGear()?.getCurrentGearSelectionState({ v-> main.post { acceptGear(v) } }, { e-> gearListener.onFailed(e) })
            vehicle?.getTurnSignal()?.getCurrentTurnSignalState({ v-> signalListener.onTurnSignalStateUpdated(v) }, { e-> signalListener.onFailed(e) })
            if(!subscribed && vehicle!=null) {
                vehicle!!.getGear().registerGearSelection(gearListener); vehicle!!.getTurnSignal().registerTurnSignalState(signalListener); subscribed=true
            }
        } catch(_:Exception) { gear=null; vehicleStatus="차량 연결 대기" }
        if(!hasLocationPermission()) { location=null; locationStatus="정확한 위치 권한이 필요해요"; return }
        try {
            fused?.getLastKnownLocation(locationListener)
            if(!locationSubscribed && fused!=null) { fused!!.registerFusedLocationCallback(locationListener); locationSubscribed=true }
        } catch(_:Exception) { location=null; locationStatus="위치 연결 대기" }
    }
    private fun acceptGear(value:VehicleGear?) {
        if(gear!=value) Log.i("TimeCapsule","Gear received: $value")
        gear=value; gearAt=elapsed(); vehicleStatus="차량 연결됨"; enforce(); changed()
    }
    fun gearFresh()=CapsulePolicy.fresh(gearAt,elapsed(),5_000)
    fun parked()=gear==VehicleGear.GEAR_PARK && gearFresh() && ux.known && !ux.restricted
    fun locationFresh():Boolean {
        if(!hasLocationPermission() || !context.getSystemService(LocationManager::class.java).isLocationEnabled) return false
        val loc=location ?: return false
        return CapsulePolicy.coordinate(loc.latitude,loc.longitude) && CapsulePolicy.fresh(locationAt,elapsed()) && CapsulePolicy.fresh(loc.timestampMillis,System.currentTimeMillis())
    }
    fun distance(c:Capsule)=location?.let { CapsulePolicy.distance(it.latitude,it.longitude,c.lat,c.lon) } ?: Double.POSITIVE_INFINITY
    fun canOpen(c:Capsule)=!c.draft && CapsulePolicy.canOpen(distance(c),locationFresh(),c.opens,System.currentTimeMillis())
    fun playbackStateKnown()=ux.known && gearFresh() && (gear==VehicleGear.GEAR_PARK || gear==VehicleGear.GEAR_DRIVE) && signal==TurnSignalValue.OFF && CapsulePolicy.fresh(signalAt,elapsed(),5_000)
    fun hasLocationPermission()=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(PLEOS_LOCATION_PERMISSION)==PackageManager.PERMISSION_GRANTED
    fun canRecord()=CapsulePolicy.canRecord(captureReady && PlaybackService.microphoneReady(),playbackStateKnown(),locationFresh(),context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
    fun prepareCapture(openDelay:Long=0L):Boolean {
        if(!foreground || !parked() || !locationFresh() || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) return false
        captureReady=true; captureStarted=elapsed(); captureParkedSince=elapsed(); captureOpenDelay=openDelay.coerceAtLeast(0)
        try { context.startForegroundService(playbackIntent) } catch(_:Exception) { captureReady=false; return false }
        message="주행 기록을 준비했어요. 명시적인 기록 명령이 올 때만 15초 녹음해요."
        changed(); return true
    }
    fun endCapture() {
        if(recording) finishRecording(true,true)
        captureReady=false; changed(); if(!foreground && !active) disconnect()
    }
    fun recordCommand():Boolean=beginRecording(System.currentTimeMillis()+captureOpenDelay)

    fun current()=capsules.firstOrNull { it.id==selectedId && !it.draft }
    fun positionMs():Long=try { player?.currentPosition?.toLong() ?: 0L } catch(_:Exception) { 0L }
    fun title(c:Capsule)=SimpleDateFormat("yyyy년 M월 d일의 목소리",Locale.KOREA).format(Date(c.created))
    fun mediaItems()=capsules.filter { !it.draft && ((it.id==selectedId && session.validGrant(it.id,elapsed())) || canOpen(it)) }
    fun setArmed(value:Boolean,automatic:Boolean=false) {
        if(!parked() || recording) return
        if(!value) { silence(); return }
        if(!hasLocationPermission() || !locationFresh()) { message="위치 확인을 마친 뒤 듣기를 준비해 주세요."; changed(); return }
        releasePlayer(); session.start(elapsed(),automatic); sessionWallStart=System.currentTimeMillis(); parkedSince=elapsed(); lastPositionSample=0; insideCandidate=null; insideSamples=0
        capsules.filter { !it.draft }.forEach { session.observe(it.id,distance(it),canOpen(it),elapsed()) }
        try { context.startForegroundService(playbackIntent) } catch(_:Exception) { session.end(); message="청취를 시작하지 못했어요. 주차 상태에서 다시 시도해 주세요."; changed(); return }
        message=if(automatic)"내비를 보셔도 돼요. 다음 장소에 도착하면 한 번 재생해요." else "내비를 보셔도 돼요. 도착 알림 뒤 미디어 재생 버튼으로 들으세요."
        changed()
    }
    private fun observeArrival() {
        if(!armed || recording || !locationFresh() || !PlaybackService.isRunning()) return
        val stamp=location?.timestampMillis ?: return
        if(stamp==lastPositionSample) return
        lastPositionSample=stamp
        val now=elapsed()
        var candidate:Capsule?=null
        for(c in capsules.filter { !it.draft && it.created<sessionWallStart }) {
            if(session.observe(c.id,distance(c),canOpen(c),now) && candidate==null) candidate=c
        }
        if(candidate!=null) { insideCandidate=candidate.id; insideSamples=1; return }
        val c=capsules.firstOrNull { it.id==insideCandidate } ?: return
        if(!canOpen(c) || selectedId!=null) { insideCandidate=null; insideSamples=0; return }
        insideSamples++
        if(insideSamples<2) return
        insideCandidate=null; session.grant(c.id,now); pauseReason="도착한 캡슐 · 재생 버튼으로 듣기"
        message="이곳에 남겨둔 목소리가 있어요. 준비되면 미디어 재생 버튼을 누르세요."
        if(session.automatic && playbackStateKnown()) resume() else arrivalCue()
        changed()
    }
    private fun arrivalCue() {
        if(!playbackStateKnown() || !locationFresh() || !requestFocus()) return
        cue()
        main.postDelayed({ if(!isPlaying && !recording && !focusBlocked) abandonFocus() },350)
    }
    fun beginRecording(openAt:Long):Boolean {
        if(recording) return true // Duplicate commands never restart or extend a recording.
        if(!canRecord() || focusBlocked) { message="기록 준비와 차량 상태를 확인해 주세요."; changed(); return false }
        if(isPlaying) pause("기록 중 · 재생 버튼으로 이어 듣기")
        if(!requestFocus()) { message="다른 안내가 끝난 뒤 다시 기록해 주세요."; changed(); return false }
        val f=File(context.filesDir,"capsule-${System.currentTimeMillis()}.m4a")
        recordingFile=f; recordingLocation=location; releaseAt=openAt
        try {
            recorder=MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100); setAudioEncodingBitRate(96000); setOutputFile(f.absolutePath); prepare(); cue(); start()
            }
            recordStart=elapsed(); recordSeconds=0; recording=true; message="15초 녹음 중이에요. 시작한 위치에 자동으로 남겨요."
            Log.i("TimeCapsule","RECORD_STARTED gear=$gear background=${!foreground}")
        } catch(_:Exception) { recorder?.release(); recorder=null; f.delete(); recordingFile=null; recordingLocation=null; abandonFocus(); message="마이크를 사용할 수 없어요." }
        changed(); return recording
    }
    fun finishRecording(save:Boolean,interrupted:Boolean=false,keepFocus:Boolean=false) {
        if(!recording) return
        recording=false
        var valid=save && elapsed()-recordStart>=1000
        try { recorder?.stop() } catch(_:Exception) { valid=false }
        recorder?.release(); recorder=null
        if(!keepFocus) { cue(); abandonFocus() }
        val f=recordingFile; val loc=recordingLocation
        if(valid && f!=null && loc!=null && f.length()>0) {
            val now=System.currentTimeMillis(); val draft=interrupted
            capsules.add(0,Capsule(now.toString(),now,releaseAt,loc.latitude,loc.longitude,((elapsed()-recordStart)/1000).toInt().coerceAtMost(recordingLimit),f.name,draft))
            persist(); Log.i("TimeCapsule","RECORD_SAVED id=$now draft=$draft"); message=if(draft)"녹음을 임시보관했어요. 다음 주차 때 저장해 주세요." else "이곳에 목소리를 묻었어요. 다음 방문을 기다려요."
        } else { f?.delete(); message="녹음을 저장하지 않았어요." }
        recordingFile=null; recordingLocation=null; changed()
    }
    fun saveDraft(c:Capsule) {
        if(!parked() || !c.draft) return
        val index=capsules.indexOf(c); if(index<0) return
        capsules[index]=c.copy(draft=false); persist(); message="녹음한 장소에 캡슐을 저장했어요."; changed()
    }
    fun play(c:Capsule,automatic:Boolean=false) {
        if(c.draft || recording) return
        if(c.id==selectedId && session.validGrant(c.id,elapsed())) { resume(); return }
        if(!canOpen(c) || !playbackStateKnown() || (!parked() && !armed)) return
        if(!armed) setArmed(true,false)
        if(!armed) return
        releasePlayer(); session.grant(c.id,elapsed()); resume()
    }
    fun mediaPlay(id:String?=null,query:String?=null) {
        if(recording) return
        if(id!=null) { capsules.firstOrNull { it.id==id }?.let { play(it) }; return }
        if(query!=null) {
            if(query.isBlank()) { resume(); return }
            mediaItems().firstOrNull { title(it).contains(query) }?.let { play(it) }; return
        }
        resume()
    }
    fun resume() {
        val c=current() ?: return
        if(!session.validGrant(c.id,elapsed()) || !hasLocationPermission() || !locationFresh() || !playbackStateKnown() || focusBlocked || recording) {
            message="다른 안내와 차량 상태를 확인한 뒤 이어 들을 수 있어요."; changed(); return
        }
        if(isPlaying) return
        if(!requestFocus()) { pauseReason="다른 안내 중 · 잠시 보류"; message="다른 안내가 끝나면 재생 버튼으로 이어 들으세요."; changed(); return }
        try {
            if(player==null) {
                player=MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(File(context.filesDir,c.filename).absolutePath); prepare()
                    setOnCompletionListener { releasePlayer(); session.finish(elapsed()); message="목소리를 모두 들었어요. 다음 장소를 기다려요."; changed() }
                    setOnErrorListener { _,_,_-> skip("음성을 재생하지 못했어요."); true }
                }
            }
            isPlaying=true; pauseReason=""; changed(); player!!.start()
            message="그날의 목소리를 듣고 있어요. 내비 안내가 나오면 잠시 멈춰요."
            Log.i("TimeCapsule","PLAY id=${c.id} position=${positionMs()}")
        } catch(_:Exception) { skip("재생을 시작하지 못했어요.") }
        changed()
    }
    fun pause(reason:String="잠시 멈춤 · 재생 버튼으로 이어 듣기",keepFocus:Boolean=false) {
        if(selectedId==null) { silence(); return }
        try { if(isPlaying) player?.pause() } catch(_:Exception){}
        isPlaying=false; pauseReason=reason; message=reason
        if(!keepFocus && !focusBlocked) abandonFocus()
        Log.i("TimeCapsule","PAUSE position=${positionMs()} reason=$reason")
        changed()
    }
    fun skip(reason:String="이번 목소리는 건너뛰었어요. 다음 장소를 기다려요.") { releasePlayer(); session.finish(elapsed()); message=reason; changed() }
    fun stopPlayback()=pause()
    fun silence() {
        if(recording) finishRecording(true,true)
        captureReady=false; releasePlayer(); session.end(); insideCandidate=null; insideSamples=0; message="이번 청취를 종료했어요."
        changed(); if(!foreground) disconnect()
    }
    private fun releasePlayer() { player?.release(); player=null; isPlaying=false; pauseReason=""; abandonFocus() }
    private fun requestFocus():Boolean {
        if(focusBlocked) return false
        if(focus!=null) return true
        val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(true).setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener({ change ->
                when(change) {
                    AudioManager.AUDIOFOCUS_GAIN -> { focusBlocked=false; changed() }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        focusBlocked=true
                        if(recording) finishRecording(true,true,true)
                        if(selectedId!=null) pause("다른 안내 중 · 안내 후 재생 버튼으로 이어 듣기",true)
                        else { message="안내가 시작되어 녹음을 임시보관했어요."; changed() }
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> { silence(); message="다른 오디오로 전환되어 청취를 종료했어요."; changed() }
                }
            },main).build()
        focus=request
        val granted=audio.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if(!granted) { focus=null; audio.abandonAudioFocusRequest(request) }
        return granted
    }
    private fun abandonFocus() { val old=focus; focus=null; focusBlocked=false; old?.let { audio.abandonAudioFocusRequest(it) } }
    private fun enforce() {
        if(recording && !canRecord()) finishRecording(true,true)
        if(captureReady && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) endCapture()
        if(active && !hasLocationPermission()) { silence(); message="위치 권한이 해제되어 청취를 종료했어요."; return }
        if(isPlaying && (!playbackStateKnown() || !locationFresh())) pause("운전 상황 확인 중 · 준비되면 재생 버튼으로 이어 듣기")
    }
    fun delete(c:Capsule) {
        if(!parked()) return
        if(c.id==selectedId) skip()
        File(context.filesDir,c.filename).delete(); capsules.remove(c); persist(); message="목소리와 장소 정보를 삭제했어요."; changed()
    }
    private fun persist() {
        val array=JSONArray()
        capsules.forEach { c-> array.put(JSONObject().put("id",c.id).put("created",c.created).put("opens",c.opens).put("lat",c.lat).put("lon",c.lon).put("duration",c.duration).put("file",c.filename).put("draft",c.draft)) }
        prefs.edit().putString("items",array.toString()).apply()
    }
    private fun cue() { try { val t=ToneGenerator(AudioManager.STREAM_MUSIC,45); t.startTone(ToneGenerator.TONE_PROP_BEEP,150); main.postDelayed({t.release()},200) } catch(_:Exception){} }
    private fun changed() { observer?.invoke(); PlaybackService.refresh() }
}
