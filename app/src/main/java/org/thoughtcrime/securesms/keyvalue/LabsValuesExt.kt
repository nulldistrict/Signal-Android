/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import org.signal.remoteconfig.RemoteConfig
import org.signal.storage.keyvalue.KeyValueStore
import org.signal.storage.keyvalue.LabsValues

// Fork: Signal only offers Labs to internal users and turns every Labs feature on for them by
// default. The fork offers Labs to everyone as an explicit opt-in instead: every feature reads as
// off until the Labs screen has been opened once, and opening it stores an explicit "off" for each
// feature, so only the features the user switches on are enabled. Neither the opt-in nor the Labs
// values are part of backups.
private const val KEY_OPTED_IN = "fork.labs.opted_in"

private val FEATURE_KEYS = listOf(
  LabsValues.INDIVIDUAL_CHAT_PLAINTEXT_EXPORT,
  LabsValues.STORY_ARCHIVE,
  LabsValues.INCOGNITO,
  LabsValues.BETTER_SEARCH,
  LabsValues.STARRED_MESSAGES,
  LabsValues.IMPROVED_MESSAGE_DELETION
)

/** Whether the user opted in to Labs. Used when [SignalStore] creates [LabsValues]. */
fun isLabsOptedIn(store: KeyValueStore): Boolean {
  return store.getBoolean(KEY_OPTED_IN, false)
}

/**
 * Opts in to Labs with every feature off. Must run before the Labs screen reads its state.
 * Internal users keep upstream's defaults.
 */
fun LabsValues.optInWithFeaturesOff() {
  if (RemoteConfig.internalUser || isLabsOptedIn(store)) {
    return
  }

  val writer = store.beginWrite()
  FEATURE_KEYS.filterNot { store.containsKey(it) }.forEach { writer.putBoolean(it, false) }
  writer.putBoolean(KEY_OPTED_IN, true).apply()
}
