package org.thoughtcrime.securesms.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.MessageFetchJob;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (ApplicationContext.isDatabaseLocked(context)) {
      // Messages are fetched after the passphrase unlocks the database.
      return;
    }

    AppDependencies.getJobManager().add(new MessageFetchJob());
  }
}
