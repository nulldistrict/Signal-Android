/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.privacy

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.appsettings.privacy.PrivacySettingsAction
import org.signal.appsettings.privacy.PrivacySettingsEvent
import org.signal.appsettings.privacy.PrivacySettingsScreen
import org.signal.appsettings.routes.AppSettingsNavigationEvent
import org.signal.core.ui.compose.CollectActions
import org.thoughtcrime.securesms.PassphraseChangeActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.signal.appsettings.R as AppSettingsR

/**
 * Hosts [PrivacySettingsScreen] and carries out its [PrivacySettingsAction]s. The view model navigates by sending
 * events to [parentEventEmitter], so this works from whichever host owns the app settings back stack.
 */
@Composable
fun PrivacySettingsEntry(
  parentEventEmitter: (AppSettingsNavigationEvent) -> Unit
) {
  val viewModel: PrivacySettingsViewModel = viewModel {
    PrivacySettingsViewModel(parentEventEmitter = parentEventEmitter)
  }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val activity = LocalActivity.current

  val incognitoLearnMore = stringResource(AppSettingsR.string.preferences__incognito_keyboard_learn_more)
  CollectActions(viewModel.actions) { action ->
    when (action) {
      PrivacySettingsAction.LaunchChangePassphrase -> context.startActivity(Intent(context, PassphraseChangeActivity::class.java))
      PrivacySettingsAction.OpenIncognitoKeyboardLearnMore -> CommunicationActions.openBrowserLink(context, incognitoLearnMore)
      is PrivacySettingsAction.ApplyScreenSecurity -> applyScreenSecurity(activity, action.enabled)
    }
  }

  PrivacySettingsScreen(
    state = state,
    onEvent = viewModel::onEvent
  )
}

private fun applyScreenSecurity(activity: Activity?, enabled: Boolean) {
  val window = activity?.window ?: return

  if (enabled) {
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  } else {
    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
  }
}
