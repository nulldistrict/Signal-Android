package org.thoughtcrime.securesms.service;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.signal.core.util.JavaTimeExtensionsKt;
import org.thoughtcrime.securesms.jobs.LocalBackupJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class LocalBackupListener extends PersistentAlarmManagerListener {

  private static final int BACKUP_JITTER_WINDOW_SECONDS = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10));

  @Override
  protected boolean shouldScheduleExact() {
    return true;
  }

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return SignalStore.settings().getLocalBackupNextTime();
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (SignalStore.settings().isBackupEnabled()) {
      LocalBackupJob.enqueue(false);
    }

    if (SignalStore.backup().getNewLocalBackupsEnabled()) {
      LocalBackupJob.enqueueArchive(SignalStore.settings().isBackupEnabled());
    }

    return setNextBackupTimeToIntervalFromNow(context);
  }

  public static void schedule(Context context) {
    if (SignalStore.settings().isBackupEnabled() || SignalStore.backup().getNewLocalBackupsEnabled()) {
      new LocalBackupListener().onReceive(context, getScheduleIntent());
    }
  }

  public static long setNextBackupTimeToIntervalFromNow(@NonNull Context context) {
    LocalDateTime now    = LocalDateTime.now();
    int           hour   = SignalStore.settings().getBackupHour();
    int           minute = SignalStore.settings().getBackupMinute();
    int           days   = SignalStore.settings().getBackupIntervalDays();
    LocalDateTime next   = getNextBackupTimeFromNowWithJitter(now, hour, minute, days, BACKUP_JITTER_WINDOW_SECONDS, new Random());

    long nextTime = JavaTimeExtensionsKt.toMillis(next);

    SignalStore.settings().setLocalBackupNextTime(nextTime);

    return nextTime;
  }

  /**
   * Computes the next backup time. The backup runs at the configured time of day, repeated every
   * [intervalDays] days, with a small random jitter so that backups from different devices do not
   * all run at exactly the same moment.
   */
  @VisibleForTesting
  public static LocalDateTime getNextBackupTimeFromNowWithJitter(
      @NonNull LocalDateTime now,
      int hour,
      int minute,
      int intervalDays,
      int maxJitterSeconds,
      @NonNull Random randomSource
  ) {
    LocalDateTime next                    = now.withHour(hour).withMinute(minute).withSecond(0);
    LocalDateTime endOfJitterWindowForNow = now.plusSeconds(maxJitterSeconds / 2);

    while (!endOfJitterWindowForNow.isBefore(next)) {
      next = next.plusDays(1);
    }

    next = next.plusDays(Math.max(0, intervalDays - 1));

    int jitter = randomSource.nextInt(maxJitterSeconds) - maxJitterSeconds / 2;

    return next.plusSeconds(jitter);
  }
}
