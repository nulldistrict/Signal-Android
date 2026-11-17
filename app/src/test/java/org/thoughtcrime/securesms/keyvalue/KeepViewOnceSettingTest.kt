/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.keyvalue

import android.app.Application
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.models.recipients.RecipientId
import org.thoughtcrime.securesms.testutil.SignalStoreRule

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class KeepViewOnceSettingTest {

  @get:Rule
  val signalStore = SignalStoreRule()

  private val alice = RecipientId.from(1)
  private val bob = RecipientId.from(12)

  @Test
  fun `nothing is kept by default`() {
    assertThat(SignalStore.misc.shouldKeepViewOnceFrom(alice)).isFalse()
  }

  @Test
  fun `the per-contact setting only applies to that contact`() {
    SignalStore.misc.setKeepViewOnceFrom(alice, true)

    assertThat(SignalStore.misc.shouldKeepViewOnceFrom(alice)).isTrue()
    assertThat(SignalStore.misc.shouldKeepViewOnceFrom(bob)).isFalse()
  }

  @Test
  fun `ids that share digits are not confused`() {
    SignalStore.misc.setKeepViewOnceFrom(bob, true)

    assertThat(SignalStore.misc.isKeepViewOnceFrom(alice)).isFalse()
    assertThat(SignalStore.misc.isKeepViewOnceFrom(bob)).isTrue()
  }

  @Test
  fun `turning the per-contact setting off removes it`() {
    SignalStore.misc.setKeepViewOnceFrom(alice, true)
    SignalStore.misc.setKeepViewOnceFrom(bob, true)
    SignalStore.misc.setKeepViewOnceFrom(alice, false)

    assertThat(SignalStore.misc.isKeepViewOnceFrom(alice)).isFalse()
    assertThat(SignalStore.misc.isKeepViewOnceFrom(bob)).isTrue()
  }

  @Test
  fun `the global setting keeps media from everyone`() {
    SignalStore.misc.keepViewOnceMessages = true

    assertThat(SignalStore.misc.shouldKeepViewOnceFrom(alice)).isTrue()
    assertThat(SignalStore.misc.isKeepViewOnceFrom(alice)).isFalse()
  }
}
