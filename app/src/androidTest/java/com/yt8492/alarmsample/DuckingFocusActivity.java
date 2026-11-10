package com.yt8492.alarmsample;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.widget.TextView;

// テストAPK自身の別UIDで動かす。独立プロセスではKotlinの依存に頼らない。
public final class DuckingFocusActivity extends Activity {
  public static final String STATE = "com.yt8492.alarmsample.test.FOCUS_STATE";
  public static final String RELEASE = "com.yt8492.alarmsample.test.RELEASE_FOCUS";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final BroadcastReceiver release = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) { finish(); }
  };
  private AudioManager audio;
  private AudioFocusRequest request;
  private String target;

  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    target = getIntent().getStringExtra("target_package");
    TextView text = new TextView(this);
    text.setText("音声フォーカスの実機テスト");
    setContentView(text);
    if (Build.VERSION.SDK_INT >= 33) {
      registerReceiver(release, new IntentFilter(RELEASE), Context.RECEIVER_EXPORTED);
    } else {
      registerLegacyReceiver();
    }
    handler.postDelayed(this::finish, 10000);
  }

  // API 32以下だけで呼ぶ。テストAPKの独立プロセスではAndroidXに依存しない。
  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  private void registerLegacyReceiver() {
    registerReceiver(release, new IntentFilter(RELEASE));
  }

  @Override public void onResume() {
    super.onResume();
    if (request != null) return;
    audio = getSystemService(AudioManager.class);
    request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      .setAudioAttributes(new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
      .setOnAudioFocusChangeListener(change -> {}, handler).build();
    int result = audio.requestAudioFocus(request);
    sendBroadcast(new Intent(STATE).setPackage(target)
      .putExtra("state", "requested").putExtra("result", result)
      .putExtra("uid", Process.myUid()));
  }

  @Override public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (request != null) audio.abandonAudioFocusRequest(request);
    unregisterReceiver(release);
    sendBroadcast(new Intent(STATE).setPackage(target).putExtra("state", "released"));
    super.onDestroy();
  }
}
