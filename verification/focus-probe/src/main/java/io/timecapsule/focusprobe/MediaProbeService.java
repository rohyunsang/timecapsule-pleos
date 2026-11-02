package io.timecapsule.focusprobe;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.browse.MediaBrowser;
import android.media.session.*;
import android.os.*;
import android.util.Log;
import java.util.List;

/** Separate test-only APK. Does not launch a screen over navigation. */
public class MediaProbeService extends Service {
 private MediaBrowser browser;
 private AudioFocusRequest focus;
 private final Handler handler=new Handler(Looper.getMainLooper());
 @Override public int onStartCommand(Intent intent,int flags,int id) {
  getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("probe","검증 중",NotificationManager.IMPORTANCE_LOW));
  startForeground(9,new Notification.Builder(this,"probe").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("테스트 전용 · 미디어 제어 검증").build());
  String command=intent.getStringExtra("command");
  if("FOCUS".equals(command)) {
   focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
     .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
     .setOnAudioFocusChangeListener(value->{}).build();
   Log.i("TimeCapsuleProbe","FOCUS result="+getSystemService(AudioManager.class).requestAudioFocus(focus));
   handler.postDelayed(()->stopSelf(id),intent.getIntExtra("duration",6000));
   return START_NOT_STICKY;
  }
  browser=new MediaBrowser(this,new ComponentName("io.timecapsule.vehicle","io.timecapsule.vehicle.PlaybackService"),new MediaBrowser.ConnectionCallback(){
   @Override public void onConnected() {
    Log.i("TimeCapsuleProbe","BROWSER_CONNECTED");
    MediaController controller=new MediaController(MediaProbeService.this,browser.getSessionToken());
    MediaController.TransportControls c=controller.getTransportControls();
    if("CUSTOM".equals(command)) c.sendCustomAction(intent.getStringExtra("action"),null);
    if("PLAY_ID".equals(command)) c.playFromMediaId(intent.getStringExtra("mediaId"),null);
    if("SEARCH".equals(command)) c.playFromSearch(intent.getStringExtra("query"),null);
    if("BROWSE".equals(command)) browser.subscribe(browser.getRoot(),new MediaBrowser.SubscriptionCallback(){
      @Override public void onChildrenLoaded(String parent,List<MediaBrowser.MediaItem> items) {
       StringBuilder ids=new StringBuilder();for(MediaBrowser.MediaItem item:items)ids.append(item.getMediaId()).append(",");
       Log.i("TimeCapsuleProbe","ITEMS="+ids);stopSelf(id);
      }
    });
    handler.postDelayed(()->{Log.i("TimeCapsuleProbe","STATE="+controller.getPlaybackState());Log.i("TimeCapsuleProbe","MEDIA_ID="+controller.getMetadata().getString(MediaMetadata.METADATA_KEY_MEDIA_ID));stopSelf(id);},1500);
   }
   @Override public void onConnectionFailed() {Log.i("TimeCapsuleProbe","BROWSER_REJECTED");stopSelf(id);}
  },null);
  browser.connect();return START_NOT_STICKY;
 }
 @Override public void onDestroy() {
  handler.removeCallbacksAndMessages(null);
  if(browser!=null)browser.disconnect();
  if(focus!=null)getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
  super.onDestroy();
 }
 @Override public IBinder onBind(Intent i){return null;}
}
