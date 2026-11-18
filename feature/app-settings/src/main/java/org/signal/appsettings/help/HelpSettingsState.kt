/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.help

/**
 * [HelpSettingsScreen] state.
 *
 * This is logged, so be sure `toString()` contains nothing sensitive.
 */
data class HelpSettingsState(
  /** The installed app version, shown on the version row and copied when it is long-pressed. */
  val versionName: String = "",
  /** Fork: whether debug logging is currently enabled (Molly's optional debug logging). */
  val logEnabled: Boolean = true,
  /** Fork: whether the "Contact us" row is shown. The fork has no support team to email, so it hides the row. */
  val showContactUs: Boolean = true,
  /** Fork: the currently shown dialog, if any. */
  val dialog: Dialog = Dialog.None,
  /** The snackbar to show, or [Snackbar.None]. */
  val snackbar: Snackbar = Snackbar.None
) {

  /** Fork: dialogs for the debug logging toggle. */
  sealed interface Dialog {
    data object None : Dialog

    /** Shown when the user turns logging off, because doing so also deletes existing logs. */
    data object ConfirmDisableLogging : Dialog
  }

  /** Whichever snackbar the screen is showing, if any. */
  sealed interface Snackbar {
    data object None : Snackbar

    /** The version was copied to the clipboard. */
    data object VersionCopied : Snackbar
  }
}
