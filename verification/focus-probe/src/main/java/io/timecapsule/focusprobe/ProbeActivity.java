package io.timecapsule.focusprobe;
import android.app.Activity;
import android.os.*;
import android.media.*;
import android.widget.TextView;
public class ProbeActivity extends Activity {
 private AudioFocusRequest request;
 private AudioManager manager;
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  TextView label=new TextView(this);label.setText("테스트 전용 · 내비 안내의 오디오 포커스 요청");label.setTextSize(28);setContentView(label);
  manager=getSystemService(AudioManager.class);
  request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
    .setOnAudioFocusChangeListener(change -> {}).build();
  new Handler(Looper.getMainLooper()).postDelayed(() -> {
   int result=manager.requestAudioFocus(request);
   android.util.Log.i("TimeCapsuleFocusProbe","navigation focus result="+result);
  },300);
  new Handler(Looper.getMainLooper()).postDelayed(this::finish,3000);
 }
 @Override public void onDestroy() { manager.abandonAudioFocusRequest(request);super.onDestroy(); }
}
