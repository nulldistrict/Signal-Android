package org.thoughtcrime.securesms.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.jobs.RestoreNotificationsJob

/** The system drops our posted notifications on app update and reboot, and nothing else puts them back. */
class RestoreNotificationsReceiver : BroadcastReceiver() {

  companion object {
    private val TAG = Log.tag(RestoreNotificationsReceiver::class)
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (ApplicationContext.isDatabaseLocked(context)) {
      // The job queue lives in the locked database. Notifications are refreshed when the passphrase unlocks it.
      Log.i(TAG, "Database is locked by a passphrase; not restoring notifications after ${intent.action}")
      return
    }

    Log.i(TAG, "Enqueuing notification restore after ${intent.action}")
    RestoreNotificationsJob.enqueue()
  }
}
