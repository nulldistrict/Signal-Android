/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation

import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobs.MultiDeviceViewedUpdateJob
import org.thoughtcrime.securesms.jobs.SendViewedReceiptJob

/**
 * Fork: view-once media kept as a normal message (the global or per-contact "keep view-once media" setting) is marked
 * in its message extras. The sender never learns that it was kept, and gets a viewed receipt only when the user marks
 * it as viewed by hand, the same receipt Signal sends when a view-once message is opened.
 */
object KeptViewOnce {

  private val TAG = Log.tag(KeptViewOnce::class)

  /** Whether [record] is kept view-once media that has not been marked as viewed yet. */
  @JvmStatic
  fun canMarkViewed(record: MessageRecord): Boolean {
    return !record.isOutgoing && record.messageExtras?.keptViewOnce == true && !record.isViewed
  }

  /** Marks the message viewed and sends the sender (and linked devices) the viewed receipt. */
  fun markViewed(messageId: Long) {
    SignalExecutors.BOUNDED.execute {
      val info = SignalDatabase.messages.setIncomingMessageViewed(messageId)
      if (info == null) {
        Log.w(TAG, "Message was already viewed or is not an incoming message.")
        return@execute
      }

      AppDependencies.jobManager.add(
        SendViewedReceiptJob(info.threadId, info.syncMessageId.recipientId, info.syncMessageId.timetamp, info.messageId)
      )
      MultiDeviceViewedUpdateJob.enqueue(listOf(info.syncMessageId))
    }
  }
}
