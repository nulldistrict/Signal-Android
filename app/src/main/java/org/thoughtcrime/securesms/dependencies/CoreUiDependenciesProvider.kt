/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.dependencies

import android.os.Build
import org.signal.core.ui.CoreUiDependencies
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.service.KeyCachingService

object CoreUiDependenciesProvider : CoreUiDependencies.Provider {
  override fun providePackageId(): String {
    return BuildConfig.APPLICATION_ID
  }

  override fun provideIsIncognitoKeyboardEnabled(): Boolean {
    return SignalStore.settings.isIncognitoKeyboardEnabled
  }

  override fun provideIsScreenSecurityEnabled(): Boolean {
    // Molly: while the app is locked, or while the screen lock protects content on older platforms, always block
    // screenshots regardless of the separate screen-security setting.
    if (KeyCachingService.isLocked(AppDependencies.application) || (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && SignalStore.settings.screenLockEnabled)) {
      return true
    }

    return SignalStore.settings.isScreenSecurityEnabled
  }
}
