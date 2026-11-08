/*
 * Ported from Molly IM (https://github.com/mollyim/mollyim-android).
 * Licensed under the GNU General Public License v3.0 or later.
 */
package org.thoughtcrime.securesms.service;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.signal.core.util.ForegroundServiceUtil;
import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.ServiceUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.util.WakeLockUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.MIN_PRIORITY;

/**
 * Best-effort wipe of free process memory when the app locks, followed by process death so live secrets do not
 * survive. Ported from Molly's secure RAM wiper.
 */
public class WipeMemoryService extends IntentService {

  private static final String TAG = Log.tag(WipeMemoryService.class);

  private static final float LOW_MEMORY_THRESHOLD_ADJ = 2.00f;

  private static final long WAKE_LOCK_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

  private static final long START_TIMEOUT = TimeUnit.SECONDS.toMillis(5);

  private static boolean nativeLoaded;

  static {
    try {
      System.loadLibrary("molly-wipe");
      nativeLoaded = true;
    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, "Native memory wipe library is unavailable", e);
    }
  }

  private static native long allocPages(int count);
  private static native void freePages(long p);
  private static native void wipePage(long p, int index);
  private static native int  getPageSize();

  private static boolean restart;

  private final ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();

  private NotificationManagerCompat  notificationManager;
  private NotificationCompat.Builder notification;

  private ActivityManager activityManager;

  private volatile boolean lowMemory;
  private boolean canPostNotifications;

  private PowerManager.WakeLock busyWakeLock;

  private long lastProgressNotification;

  /**
   * Starts the wiper. When {@code restartApp} is set the process is killed after the wipe is scheduled, matching
   * Molly's lock behavior: the app comes back cold and requires the user to unlock it.
   */
  public static void run(@NonNull Context context, boolean restartApp) {
    Context appContext = context.getApplicationContext();

    SignalExecutors.BOUNDED.execute(() -> {
      restart = restartApp;

      try {
        ForegroundServiceUtil.startWhenCapableOrThrow(appContext, new Intent(appContext, WipeMemoryService.class), START_TIMEOUT);
      } catch (Exception e) {
        Log.w(TAG, "Unable to start WipeMemoryService", e);
        if (restartApp) {
          killProcess();
        }
      }
    });
  }

  public WipeMemoryService() {
    super("WipeMemoryService");
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    if (restart) {
      stopService(new Intent(this, KeyCachingService.class));
      killProcess();
    }
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  protected void onHandleIntent(@Nullable Intent intent) {
    long bytes = doWipe();
    Log.i(TAG, "Free memory wiped: " + bytes + " bytes");
  }

  private long doWipe() {
    if (!nativeLoaded) {
      return 0;
    }

    int pageSize = getPageSize();
    int chunkPageLimit = 4096 * 1024 / pageSize;

    int maxChunks = (int) (getTotalMemory() / pageSize / chunkPageLimit);
    List<Long> chunks = new ArrayList<>(maxChunks);

    long overwritten = 0;

    int savePriority = Thread.currentThread().getPriority();

    canPostNotifications = notificationManager.areNotificationsEnabled();

    try {
      Thread.currentThread().setPriority(MIN_PRIORITY);

      long memFree = getFreeMemory();

      long maxProgress = memFree;
      long progress = 0;

      while (memFree >= pageSize) {
        if (lowMemory) {
          return overwritten;
        }

        int pageCount = Math.min(chunkPageLimit, (int) (memFree / pageSize));
        long ptr = allocPages(pageCount);

        // If returned a NULL pointer, the memory is exhausted.
        if (ptr == 0) {
          return overwritten;
        }

        chunks.add(ptr);

        for (int i = 0; (i < pageCount); i++) {
          wipePage(ptr, i);
          overwritten += pageSize;
          if (lowMemory) {
            return overwritten;
          }
        }

        memFree = getFreeMemory();

        if (memFree < maxProgress && canPostNotifications) {
          progress = Math.max(progress, maxProgress - memFree);
          showProgress(maxProgress, progress);
        }
      }
    } catch (Error ignored) {
      // Defensively catch all runtime errors including OOM
    } finally {
      for (Long ptr : chunks) {
        freePages(ptr);
      }

      Thread.currentThread().setPriority(savePriority);
    }

    return overwritten;
  }

  @Override
  public void onCreate() {
    Log.i(TAG, "onCreate()");
    super.onCreate();
    showForegroundNotification();
    busyWakeLock = WakeLockUtil.acquire(this, PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TIMEOUT, TAG);
    notificationManager = NotificationManagerCompat.from(this);
    activityManager = ServiceUtil.getActivityManager(this);
  }

  @Override
  public void onDestroy() {
    Log.i(TAG, "onDestroy()");
    super.onDestroy();
    hideForegroundNotification();
    WakeLockUtil.release(busyWakeLock, TAG);
  }

  @Override
  public void onTrimMemory(int level) {
    if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
      notifyLowMemory();
    }
    Log.i(TAG, "onTrimMemory(" + level + ")");
    super.onTrimMemory(level);
  }

  @Override
  public void onLowMemory() {
    notifyLowMemory();
    Log.i(TAG, "onLowMemory()");
    super.onLowMemory();
  }

  private void notifyLowMemory() {
    lowMemory = true;
  }

  private long getFreeMemory() {
    activityManager.getMemoryInfo(memoryInfo);
    long freeMem = (long) (memoryInfo.availMem - (memoryInfo.threshold * LOW_MEMORY_THRESHOLD_ADJ));
    return (freeMem > 0) ? freeMem : 0;
  }

  private long getTotalMemory() {
    activityManager.getMemoryInfo(memoryInfo);
    return memoryInfo.totalMem;
  }

  private void showForegroundNotification() {
    Intent intent = new Intent(this, MainActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntentFlags.immutable());
    notification = new NotificationCompat.Builder(this, NotificationChannels.getInstance().LOCKED_STATUS)
        .setOngoing(true)
        .setContentTitle(getString(R.string.WipeMemoryService_secure_wipe_in_progress))
        .setContentText(getString(R.string.WipeMemoryService_clearing_unencrypted_data_from_ram))
        .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.WipeMemoryService_clearing_unencrypted_data_from_ram)))
        .setSmallIcon(R.drawable.ic_notification_unlocked)
        .setColor(ContextCompat.getColor(this, org.signal.core.ui.R.color.signal_light_colorSecondary))
        .setContentIntent(pendingIntent)
        .setShowWhen(false)
        .setProgress(0, 0, true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS);
    startForeground(NotificationIds.MEMORY_WIPE, notification.build());
  }

  private void hideForegroundNotification() {
    notificationManager.cancel(NotificationIds.MEMORY_WIPE);
  }

  private void showProgress(long totalBytes, long progressBytes) {
    long now = System.currentTimeMillis();
    if (now - lastProgressNotification < 250L) {
      // Updating too frequently
      return;
    }
    lastProgressNotification = now;
    notification.setProgress((int) (totalBytes / 1024), (int) (progressBytes / 1024), false);
    try {
      notificationManager.notify(NotificationIds.MEMORY_WIPE, notification.build());
    } catch (SecurityException ignored) {
      canPostNotifications = false;
    }
  }

  private static void killProcess() {
    System.exit(0);
  }
}
