/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.location

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class SignalPlaceTest {

  private val google = "https://maps.google.com/maps?q=41.0082%2C28.9784"
  private val osm = "https://www.openstreetmap.org/?mlat=41.0082&mlon=28.9784#map=16/41.0082/28.9784"
  private val apple = "https://maps.apple.com/?ll=41.0082,28.9784&q=41.0082,28.9784"

  @Test
  fun `google is the default and matches upstream's link`() {
    assertThat(SignalPlace.getMapLinks("google", 41.0082, 28.9784)).isEqualTo(google)
    assertThat(SignalPlace.getMapLinks("unknown", 41.0082, 28.9784)).isEqualTo(google)
  }

  @Test
  fun `openstreetmap link`() {
    assertThat(SignalPlace.getMapLinks("osm", 41.0082, 28.9784)).isEqualTo(osm)
  }

  @Test
  fun `apple maps link`() {
    assertThat(SignalPlace.getMapLinks("apple", 41.0082, 28.9784)).isEqualTo(apple)
  }

  @Test
  fun `all puts each link on its own line`() {
    assertThat(SignalPlace.getMapLinks("all", 41.0082, 28.9784)).isEqualTo("$google\n$osm\n$apple")
  }
}
