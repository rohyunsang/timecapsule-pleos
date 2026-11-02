package io.timecapsule.vehicle

import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.browse.MediaBrowser
import android.media.session.*
import android.os.*
import android.service.media.MediaBrowserService

/** Browsing never starts listening. A parked-screen action explicitly starts a trip. */
class PlaybackService: MediaBrowserService() {
    companion object {
        private var running:PlaybackService?=null
        fun isRunning()=running!=null
        fun microphoneReady()=running?.microphoneStarted==true
        fun refresh():Boolean { val s=running ?: return false; s.update(); return true }
    }
    private lateinit var media:MediaSession
    private var lastSignature=""
    private var foregroundStarted=false
    private var microphoneStarted=false
    override fun onCreate() {
        super.onCreate(); running=this
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("capsule_playback","캡슐 듣기",NotificationManager.IMPORTANCE_LOW))
        media=MediaSession(this,"TimeCapsule").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object:MediaSession.Callback() {
                private fun engine()=CapsuleEngine.get(this@PlaybackService)
                override fun onPlay() { engine().mediaPlay() }
                override fun onCustomAction(action:String,extras:Bundle?) {
                    val caller=media.currentControllerInfo
                    if(!trustedController(caller.packageName,caller.uid)) return
                    when(action) {
                        "START_CAPSULE_RECORDING"->engine().recordCommand()
                        "SAVE_CAPSULE_RECORDING"->engine().finishRecording(true)
                        "CANCEL_CAPSULE_RECORDING"->engine().finishRecording(false)
                    }
                }
                override fun onMediaButtonEvent(intent:Intent):Boolean {
                    @Suppress("DEPRECATION")
                    val key=intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if(key?.keyCode==android.view.KeyEvent.KEYCODE_MEDIA_RECORD) {
                        if(key.action==android.view.KeyEvent.ACTION_DOWN && key.repeatCount==0) engine().recordCommand()
                        return true
                    }
                    return super.onMediaButtonEvent(intent)
                }
                override fun onPlayFromMediaId(mediaId:String?,extras:Bundle?) { if(mediaId!=null) engine().mediaPlay(id=mediaId) }
                override fun onPlayFromSearch(query:String?,extras:Bundle?) { engine().mediaPlay(query=query ?: "") }
                override fun onPause() { engine().pause() }
                override fun onStop() { engine().silence() }
                override fun onSkipToNext() { if(engine().recording)engine().finishRecording(true,true) else engine().skip() }
            })
        }
        sessionToken=media.sessionToken
    }
    override fun onGetRoot(clientPackageName:String,clientUid:Int,rootHints:Bundle?):BrowserRoot? {
        val trusted=trustedController(clientPackageName,clientUid)
        android.util.Log.i("TimeCapsule","Media browser trusted=$trusted uid=$clientUid")
        return if(trusted) BrowserRoot("available",Bundle().apply { putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED",true) }) else null
    }
    private fun trustedController(clientPackageName:String,clientUid:Int):Boolean {
        val pm=packageManager
        return clientUid==applicationInfo.uid || pm.checkSignatures(applicationInfo.uid,clientUid)==PackageManager.SIGNATURE_MATCH ||
            checkPermission("android.permission.MEDIA_CONTENT_CONTROL",-1,clientUid)==PackageManager.PERMISSION_GRANTED ||
            try { pm.getApplicationInfo(clientPackageName,0).flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0 } catch(_:Exception) { false }
    }
    override fun onLoadChildren(parentId:String,result:Result<MutableList<MediaBrowser.MediaItem>>) {
        val engine=CapsuleEngine.get(this)
        val children=if(parentId=="available") engine.mediaItems().map { c->
            MediaBrowser.MediaItem(MediaDescription.Builder().setMediaId(c.id).setTitle(engine.title(c))
                .setSubtitle(if(c.id==engine.selectedId)"이 방문에서 열린 목소리" else "현재 장소에서 열 수 있어요").build(),MediaBrowser.MediaItem.FLAG_PLAYABLE)
        }.toMutableList() else mutableListOf()
        result.sendResult(children)
    }
    private fun update() {
        val engine=CapsuleEngine.get(this)
        val current=engine.current()
        val signature="${engine.captureReady}:${engine.recording}:${engine.armed}:${engine.isPlaying}:${engine.selectedId}:${engine.pauseReason}:${engine.message}:${engine.mediaItems().map { it.id }}"
        if(lastSignature==signature) return
        lastSignature=signature
        val state=when { engine.isPlaying->PlaybackState.STATE_PLAYING; current!=null->PlaybackState.STATE_PAUSED; else->PlaybackState.STATE_STOPPED }
        var actions=if(engine.active) PlaybackState.ACTION_STOP else 0L
        if(current!=null) actions=actions or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            (if(engine.isPlaying) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY)
        if(engine.armed) actions=actions or PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH
        val playback=PlaybackState.Builder().setActions(actions).setState(state,engine.positionMs(),if(engine.isPlaying)1f else 0f,SystemClock.elapsedRealtime())
        if(engine.captureReady) {
            if(engine.recording) {
                playback.addCustomAction("SAVE_CAPSULE_RECORDING","여기에 저장",android.R.drawable.ic_menu_save)
                playback.addCustomAction("CANCEL_CAPSULE_RECORDING","녹음 취소",android.R.drawable.ic_delete)
            } else playback.addCustomAction("START_CAPSULE_RECORDING","지금 남기기",android.R.drawable.ic_btn_speak_now)
        }
        media.setPlaybackState(playback.build())
        media.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,current?.let { engine.title(it) } ?: "다음 장소의 목소리를 기다려요")
            .putString(MediaMetadata.METADATA_KEY_ARTIST,if(engine.isPlaying)"타임캡슐" else engine.pauseReason.ifBlank { "내비 화면을 유지하세요" })
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,current?.id ?: "")
            .putLong(MediaMetadata.METADATA_KEY_DURATION,(current?.duration ?: 0)*1000L).build())
        media.isActive=engine.active
        notifyChildrenChanged("available")
        if(!engine.active) {
            if(foregroundStarted) stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted=false; microphoneStarted=false; stopSelf(); return
        }
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        fun action(name:String)=PendingIntent.getBroadcast(this,name.hashCode(),Intent(this,PlaybackActionReceiver::class.java).setAction(name),PendingIntent.FLAG_IMMUTABLE)
        val recordingText=if(engine.recording) "15초 녹음 중 · 시작한 위치에 자동 저장" else if(engine.captureReady) "주행 기록 준비됨 · 대기 중 녹음하지 않음" else "타임캡슐 · 장소를 기다려요"
        val n=Notification.Builder(this,"capsule_playback").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(if(engine.captureReady)recordingText else current?.let { engine.title(it) } ?: recordingText)
            .setContentText(if(engine.isPlaying)"내비 안내가 나오면 잠시 멈춰요" else engine.pauseReason.ifBlank { "위치 사용 중 · 내비 화면을 유지하세요" })
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
        if(current!=null) {
            n.addAction(Notification.Action.Builder(if(engine.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,if(engine.isPlaying)"잠시 멈춤" else "이어 듣기",action(if(engine.isPlaying)"PAUSE" else "PLAY")).build())
            n.addAction(Notification.Action.Builder(android.R.drawable.ic_media_next,"건너뛰기",action("SKIP")).build())
        }
        n.addAction(Notification.Action.Builder(android.R.drawable.ic_delete,"이번 청취 종료",action("STOP")).build())
        n.setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(*if(current!=null) intArrayOf(0,1,2) else intArrayOf(0)))
        try {
            var types=ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if(engine.isPlaying) types=types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if(engine.captureReady) types=types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if(Build.VERSION.SDK_INT>=29) startForeground(7,n.build(),types) else startForeground(7,n.build())
            foregroundStarted=true; microphoneStarted=engine.captureReady
        } catch(_:Exception) { running=null; engine.silence(); stopSelf() }
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { lastSignature=""; update(); return START_NOT_STICKY }
    override fun onTaskRemoved(rootIntent:Intent?) { CapsuleEngine.get(this).silence(); stopSelf(); super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        running=null; microphoneStarted=false; media.release()
        val engine=CapsuleEngine.get(this)
        if(engine.active) engine.silence()
        super.onDestroy()
    }
}

/** Only immutable, app-created notification PendingIntents reach this receiver. */
class PlaybackActionReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val e=CapsuleEngine.get(context)
        when(intent.action) { "PLAY"->e.resume(); "PAUSE"->e.pause(); "SKIP"->e.skip(); "STOP"->e.silence() }
    }
}
