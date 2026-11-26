/*
 * Signal EK fork: Material You (wallpaper-based) colors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.google.android.material.color.DynamicColors

/**
 * Fork: the user's "Material You colors" choice. Kept in the app's plain default shared preferences (it is not
 * sensitive) so that it can be read before the encrypted store is unlocked, e.g. on the passphrase screen.
 *
 * Compose screens pick it up in [SignalTheme]; View-based screens get it through the app's DynamicTheme, which
 * applies Material's dynamic color overlay to the activity theme.
 */
object ForkDynamicColors {

  private const val KEY = "pref_fork_dynamic_colors"

  /** Dynamic colors need Android 12. */
  @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
  @JvmStatic
  val isAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  /** Whether dynamic colors are both chosen and available on this device. */
  @JvmStatic
  fun isEnabled(context: Context): Boolean {
    return isAvailable && prefs(context).getBoolean(KEY, false)
  }

  @JvmStatic
  fun setEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY, enabled).apply()
  }

  /**
   * Applies Material's dynamic color overlay to [activity]'s theme when enabled. Call it right after the activity's
   * theme is set and before any view is inflated. Returns whether it was applied.
   */
  @JvmStatic
  fun applyTo(activity: Activity): Boolean {
    if (!isEnabled(activity)) {
      return false
    }
    DynamicColors.applyToActivityIfAvailable(activity)
    return true
  }

  // Same file as PreferenceManager.getDefaultSharedPreferences(), without depending on androidx.preference.
  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
}
