/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.remoteconfig.RemoteConfig
import org.thoughtcrime.securesms.testutil.SignalStoreRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LabsValuesExtTest {

  @get:Rule
  val signalStore = SignalStoreRule()

  @Before
  fun setUp() {
    // Behave like a release build: not an internal user.
    RemoteConfig.internalUserDisabled = true
  }

  @After
  fun tearDown() {
    RemoteConfig.internalUserDisabled = false
  }

  @Test
  fun `features are off for external users before opting in`() {
    assertAllFeatures(enabled = false)
  }

  @Test
  fun `opting in keeps every feature off`() {
    SignalStore.labs.optInWithFeaturesOff()

    assertAllFeatures(enabled = false)
  }

  @Test
  fun `after opting in a feature can be switched on on its own`() {
    SignalStore.labs.optInWithFeaturesOff()

    SignalStore.labs.starredMessages = true

    assertThat(SignalStore.labs.starredMessages).isTrue()
    assertThat(SignalStore.labs.incognito).isFalse()
    assertThat(SignalStore.labs.individualChatPlaintextExport).isFalse()
  }

  @Test
  fun `opting in again does not reset features the user switched on`() {
    SignalStore.labs.optInWithFeaturesOff()
    SignalStore.labs.betterSearch = true

    SignalStore.labs.optInWithFeaturesOff()

    assertThat(SignalStore.labs.betterSearch).isTrue()
  }

  @Test
  fun `a feature switched on before opting in stays off until opting in`() {
    SignalStore.labs.storyArchive = true

    assertThat(SignalStore.labs.storyArchive).isFalse()

    SignalStore.labs.optInWithFeaturesOff()

    assertThat(SignalStore.labs.storyArchive).isTrue()
  }

  private fun assertAllFeatures(enabled: Boolean) {
    val values = listOf(
      SignalStore.labs.individualChatPlaintextExport,
      SignalStore.labs.storyArchive,
      SignalStore.labs.incognito,
      SignalStore.labs.betterSearch,
      SignalStore.labs.starredMessages,
      SignalStore.labs.improvedMessageDeletion
    )

    values.forEach { if (enabled) assertThat(it).isTrue() else assertThat(it).isFalse() }
  }
}
