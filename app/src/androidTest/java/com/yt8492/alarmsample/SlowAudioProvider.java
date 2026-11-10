package com.yt8492.alarmsample;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// テストAPKの独立プロセスでも動くように、Kotlinランタイムへ依存させない。
public class SlowAudioProvider extends ContentProvider {
  private volatile CountDownLatch gate = new CountDownLatch(1);
  private volatile boolean opened;

  @Override public boolean onCreate() { return true; }
  @Override public String getType(Uri uri) { return "audio/wav"; }

  @Override public Cursor query(Uri uri, String[] projection, String selection,
      String[] selectionArgs, String sortOrder) {
    MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME });
    cursor.addRow(new Object[] { "rotation-test.wav" });
    return cursor;
  }

  @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    final ParcelFileDescriptor[] pipe;
    try { pipe = ParcelFileDescriptor.createPipe(); }
    catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    gate = new CountDownLatch(1);
    CountDownLatch currentGate = gate;
    opened = true;
    new Thread(() -> {
      try (ParcelFileDescriptor.AutoCloseOutputStream output =
          new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
        if (!currentGate.await(15, TimeUnit.SECONDS)) return;
        try (InputStream input = getContext().getAssets().open("rotation-test.wav")) {
          byte[] buffer = new byte[8192];
          int count;
          while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
      } catch (Exception ignored) { }
    }, "SlowAudioProvider").start();
    return pipe[0];
  }

  @Override public Bundle call(String method, String arg, Bundle extras) {
    if ("release".equals(method)) gate.countDown();
    Bundle result = new Bundle();
    result.putBoolean("opened", opened);
    return result;
  }

  @Override public Uri insert(Uri uri, ContentValues values) { return null; }
  @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
  @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
