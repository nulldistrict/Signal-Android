/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.signal.core.ui.compose.SignalIcons;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.eventbus.EventBus;
import org.signal.core.util.logging.Log;
import org.signal.devicetransfer.TransferStatus;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.DummyActivity;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.InvalidPassphraseException;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.migrations.ApplicationMigrations;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.signal.core.util.AppForegroundObserver;
import org.signal.core.util.ServiceUtil;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Small service that stays running to keep a key cached in memory.
 *
 * @author Moxie Marlinspike
 */

public class KeyCachingService extends Service {

  private static final String TAG = Log.tag(KeyCachingService.class);

  public  static final String KEY_PERMISSION           = BuildConfig.APPLICATION_ID + ".ACCESS_SECRETS";
  public  static final String NEW_KEY_EVENT            = BuildConfig.APPLICATION_ID + ".service.action.NEW_KEY_EVENT";
  public  static final String CLEAR_KEY_EVENT          = BuildConfig.APPLICATION_ID + ".service.action.CLEAR_KEY_EVENT";
  public  static final String LOCK_TOGGLED_EVENT       = BuildConfig.APPLICATION_ID + ".service.action.LOCK_ENABLED_EVENT";
  private static final String PASSPHRASE_EXPIRED_EVENT = BuildConfig.APPLICATION_ID + ".service.action.PASSPHRASE_EXPIRED_EVENT";
  private static final String SCREEN_LOCK_EVENT        = BuildConfig.APPLICATION_ID + ".service.action.SCREEN_LOCK_EVENT";
  public  static final String CLEAR_KEY_ACTION         = BuildConfig.APPLICATION_ID + ".service.action.CLEAR_KEY";
  public  static final String DISABLE_ACTION           = BuildConfig.APPLICATION_ID + ".service.action.DISABLE";

  private final IBinder binder  = new KeySetBinder();

  private static MasterSecret masterSecret;

  /**
   * Fork: two-tier lock. While only the screen lock is engaged, the UI is locked ({@link #isLocked} is true and
   * notifications hide their contents) but the database stays open and messages keep arriving. The master secret
   * is held here so that the screen lock (biometric or device credential) can restore it. A full lock (manual
   * lock or passphrase timeout) clears it, wipes memory and requires the passphrase again.
   */
  private static MasterSecret screenLockedSecret;

  public KeyCachingService() {}

  /** Fork: true while only the screen lock is engaged and the database is still open. */
  public static synchronized boolean isScreenLockedOnly() {
    return screenLockedSecret != null;
  }

  /** Fork: the master secret held during a screen lock, to be restored after biometric or device credential unlock. */
  public static synchronized @Nullable MasterSecret getScreenLockedSecret() {
    return screenLockedSecret;
  }

  public static synchronized boolean isLocked(Context context) {
    if (!ApplicationContext.getInstance(context).isAppInitialized()) {
      Log.d(TAG, "Locked! The database has not been unlocked yet.");
      return true;
    }

    boolean locked = masterSecret == null && (!SignalStore.settings().getPassphraseDisabled() || SignalStore.settings().getScreenLockEnabled());

    if (locked) {
      Log.d(TAG, "Locked! PasswordDisabled: " + SignalStore.settings().getPassphraseDisabled() + ", ScreenLock: " + SignalStore.settings().getScreenLockEnabled());
    }

    return locked;
  }

  public static synchronized @Nullable MasterSecret getMasterSecret(Context context) {
    if (!ApplicationContext.getInstance(context).isAppInitialized()) {
      return null;
    }

    if (masterSecret == null && (SignalStore.settings().getPassphraseDisabled() && !SignalStore.settings().getScreenLockEnabled())) {
      try {
        return MasterSecretUtil.getMasterSecret(context, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
      } catch (InvalidPassphraseException e) {
        Log.w(TAG, e);
      }
    }

    return masterSecret;
  }

  public static void onAppForegrounded(@NonNull Context context) {
    if (!ApplicationContext.getInstance(context).isAppInitialized()) {
      return;
    }

    if (SignalStore.settings().getScreenLockEnabled() || !SignalStore.settings().getPassphraseDisabled()) {
      ServiceUtil.getAlarmManager(context).cancel(buildExpirationPendingIntent(context, PASSPHRASE_EXPIRED_EVENT));
      ServiceUtil.getAlarmManager(context).cancel(buildExpirationPendingIntent(context, SCREEN_LOCK_EVENT));
    }
  }

  public static void onAppBackgrounded(@NonNull Context context) {
    if (!ApplicationContext.getInstance(context).isAppInitialized()) {
      return;
    }

    startTimeoutIfAppropriate(context);
  }

  @SuppressLint("StaticFieldLeak")
  public void setMasterSecret(final MasterSecret masterSecret) {
    synchronized (KeyCachingService.class) {
      KeyCachingService.masterSecret       = masterSecret;
      KeyCachingService.screenLockedSecret = null;

      foregroundService();
      broadcastNewSecret();
      startTimeoutIfAppropriate(this);

      SignalExecutors.BOUNDED.execute(() -> {
        if (!ApplicationMigrations.isUpdate(KeyCachingService.this)) {
          AppDependencies.getMessageNotifier().updateNotification(KeyCachingService.this);
        }
      });
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;
    Log.d(TAG, "onStartCommand, " + intent.getAction());

    if (intent.getAction() != null) {
      switch (intent.getAction()) {
        case CLEAR_KEY_ACTION:         handleClearKey(); scheduleSecureWipe(); break;
        case PASSPHRASE_EXPIRED_EVENT: handleClearKey(); scheduleSecureWipe(); break;
        case SCREEN_LOCK_EVENT:        handleScreenLock();      break;
        case DISABLE_ACTION:           handleDisableService();  break;
        case LOCK_TOGGLED_EVENT:       handleLockToggled();     break;
      }
    }

    return START_NOT_STICKY;
  }

  @Override
  public void onCreate() {
    Log.i(TAG, "onCreate()");
    super.onCreate();

    if (!ApplicationContext.getInstance(this).isAppInitialized()) {
      Log.i(TAG, "Database is not initialized yet; not caching a master secret.");
      return;
    }

    if (SignalStore.settings().getPassphraseDisabled() && !SignalStore.settings().getScreenLockEnabled()) {
      try {
        MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
        setMasterSecret(masterSecret);
      } catch (InvalidPassphraseException e) {
        Log.w(TAG, e);
      }
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.w(TAG, "KCS Is Being Destroyed!");
    handleClearKey();
  }

  /**
   * Workaround for Android bug:
   * https://code.google.com/p/android/issues/detail?id=53313
   */
  @Override
  public void onTaskRemoved(Intent rootIntent) {
    Intent intent = new Intent(this, DummyActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleClearKey() {
    Log.i(TAG, "handleClearKey()");
    synchronized (KeyCachingService.class) {
      KeyCachingService.masterSecret       = null;
      KeyCachingService.screenLockedSecret = null;
    }
    stopForeground(true);

    Intent intent = new Intent(CLEAR_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);

    SignalExecutors.BOUNDED.execute(() -> {
      AppDependencies.getMessageNotifier().updateNotification(KeyCachingService.this);
    });
  }

  /**
   * Fork: locks the UI only. The database stays open, the service stays in the foreground and no memory wipe runs,
   * so messages and (content-free) notifications keep arriving until the user unlocks with the screen lock.
   */
  private void handleScreenLock() {
    synchronized (KeyCachingService.class) {
      if (KeyCachingService.masterSecret == null || !SignalStore.settings().getScreenLockEnabled()) {
        return;
      }

      Log.i(TAG, "handleScreenLock()");
      KeyCachingService.screenLockedSecret = KeyCachingService.masterSecret;
      KeyCachingService.masterSecret       = null;
    }

    Intent intent = new Intent(CLEAR_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);

    SignalExecutors.BOUNDED.execute(() -> {
      AppDependencies.getMessageNotifier().updateNotification(KeyCachingService.this);
    });
  }

  /**
   * Molly: once the key has been cleared, wipe process memory and restart the process after a short grace period.
   * The delay lets activities receive the clear-key broadcast and route to the unlock screen first.
   */
  private void scheduleSecureWipe() {
    if ((ApplicationMigrations.isUpdate(this) && ApplicationMigrations.isUiBlockingMigrationRunning()) ||
        EventBus.getDefault().getStickyEvent(TransferStatus.class) != null)
    {
      Log.w(TAG, "Skipping secure wipe during app migration or device transfer.");
      return;
    }

    Context appContext = getApplicationContext();

    ThreadUtil.runOnMainDelayed(() -> WipeMemoryService.run(appContext, true), TimeUnit.SECONDS.toMillis(1));
  }

  private void handleLockToggled() {
    stopForeground(true);

    try {
      MasterSecret masterSecret = MasterSecretUtil.getMasterSecret(this, MasterSecretUtil.UNENCRYPTED_PASSPHRASE);
      setMasterSecret(masterSecret);
    } catch (InvalidPassphraseException e) {
      Log.w(TAG, e);
    }
  }

  private void handleDisableService() {
    if (SignalStore.settings().getPassphraseDisabled() &&
        !SignalStore.settings().getScreenLockEnabled())
    {
      stopForeground(true);
    }
  }

  /**
   * Fork: two-tier lock. The screen lock timeout only locks the UI ({@link #SCREEN_LOCK_EVENT}); the passphrase
   * timeout fully locks the app ({@link #PASSPHRASE_EXPIRED_EVENT}). Both run independently while the app is in the
   * background, and the passphrase timeout keeps running while the screen is locked.
   */
  private static void startTimeoutIfAppropriate(@NonNull Context context) {
    boolean appVisible       = AppForegroundObserver.isForegrounded();
    boolean screenLocked     = isScreenLockedOnly();
    boolean secretSet        = KeyCachingService.masterSecret != null || screenLocked;

    boolean timeoutEnabled   = SignalStore.settings().getPassphraseTimeoutEnabled();
    boolean passLockActive   = timeoutEnabled && !SignalStore.settings().getPassphraseDisabled();

    boolean screenLockActive = SignalStore.settings().getScreenLockEnabled() && !screenLocked;

    if (appVisible || !secretSet) {
      return;
    }

    if (screenLockActive) {
      scheduleExpiration(context, SCREEN_LOCK_EVENT, TimeUnit.SECONDS.toMillis(SignalStore.settings().getScreenLockTimeout()));
    }

    if (passLockActive) {
      scheduleExpiration(context, PASSPHRASE_EXPIRED_EVENT, TimeUnit.MINUTES.toMillis(SignalStore.settings().getPassphraseTimeout()));
    }
  }

  private static void scheduleExpiration(@NonNull Context context, @NonNull String action, long timeoutMillis) {
    if (timeoutMillis <= 0) {
      Log.i(TAG, "Starting immediate lock: " + action);
      Intent intent = new Intent(action, null, context, KeyCachingService.class);
      context.startService(intent);
    } else {
      Log.i(TAG, "Starting timeout for " + action + ": " + timeoutMillis);

      AlarmManager  alarmManager     = ServiceUtil.getAlarmManager(context);
      PendingIntent expirationIntent = buildExpirationPendingIntent(context, action);

      alarmManager.cancel(expirationIntent);
      alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + timeoutMillis, expirationIntent);
    }
  }

  private void foregroundService() {
    if (SignalStore.settings().getPassphraseDisabled() && !SignalStore.settings().getScreenLockEnabled()) {
      stopForeground(true);
      return;
    }

    Log.i(TAG, "foregrounding KCS");
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NotificationChannels.getInstance().LOCKED_STATUS);

    builder.setContentTitle(getString(R.string.KeyCachingService_passphrase_cached));
    builder.setContentText(getString(R.string.KeyCachingService_signal_passphrase_cached));
    builder.setSmallIcon(R.drawable.ic_notification_unlocked);
    builder.setColor(ContextCompat.getColor(this, org.signal.core.ui.R.color.signal_light_colorSecondary));
    builder.setWhen(0);
    builder.setPriority(Notification.PRIORITY_MIN);
    builder.setOngoing(true);

    builder.addAction(org.signal.core.ui.R.drawable.symbol_lock_24, getString(R.string.KeyCachingService_lock), buildLockIntent());
    builder.setContentIntent(buildLaunchIntent());

    stopForeground(true);

    try {
      startForeground(NotificationIds.KEY_CACHING_SERVICE, builder.build());
    } catch (Exception e) {
      if (Build.VERSION.SDK_INT >= 31 && e instanceof ForegroundServiceStartNotAllowedException) {
        Log.w(TAG, "Not allowed to start foreground service.", e);
      } else {
        throw e;
      }
    }
  }

  private void broadcastNewSecret() {
    Log.i(TAG, "Broadcasting new secret...");

    Intent intent = new Intent(NEW_KEY_EVENT);
    intent.setPackage(getApplicationContext().getPackageName());

    sendBroadcast(intent, KEY_PERMISSION);
  }

  private PendingIntent buildLockIntent() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(PASSPHRASE_EXPIRED_EVENT);
    return PendingIntent.getService(getApplicationContext(), 0, intent, getPendingIntentFlags());
  }

  private PendingIntent buildLaunchIntent() {
    // TODO [greyson] Navigation
    return PendingIntent.getActivity(getApplicationContext(), 0, MainActivity.clearTop(this), getPendingIntentFlags());
  }

  private static PendingIntent buildExpirationPendingIntent(@NonNull Context context, @NonNull String action) {
    Intent expirationIntent = new Intent(action, null, context, KeyCachingService.class);
    return PendingIntent.getService(context, 0, expirationIntent, getPendingIntentFlags());
  }

  private static int getPendingIntentFlags() {
    return PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
  }

  @Override
  public IBinder onBind(Intent arg0) {
    return binder;
  }

  public class KeySetBinder extends Binder {
    public KeyCachingService getService() {
      return KeyCachingService.this;
    }
  }
}
