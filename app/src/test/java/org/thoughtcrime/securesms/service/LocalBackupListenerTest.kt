/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.service

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.testutil.MockRandom
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class LocalBackupListenerTest {

  private val jitterWindowSeconds = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10))
  private val noJitter = MockRandom(listOf(jitterWindowSeconds / 2))

  @Test
  fun `daily interval schedules the next occurrence today`() {
    val now = LocalDateTime.of(2025, 6, 27, 1, 0)

    val next = LocalBackupListener.getNextBackupTimeFromNowWithJitter(now, 2, 0, 1, jitterWindowSeconds, noJitter)

    assertEquals(LocalDateTime.of(2025, 6, 27, 2, 0), next)
  }

  @Test
  fun `weekly interval schedules six days after the next daily occurrence`() {
    val now = LocalDateTime.of(2025, 6, 27, 1, 0)

    val next = LocalBackupListener.getNextBackupTimeFromNowWithJitter(now, 2, 0, 7, jitterWindowSeconds, noJitter)

    assertEquals(LocalDateTime.of(2025, 7, 3, 2, 0), next)
  }

  @Test
  fun `weekly interval after todays backup time schedules a full week out`() {
    val now = LocalDateTime.of(2025, 6, 27, 3, 0)

    val next = LocalBackupListener.getNextBackupTimeFromNowWithJitter(now, 2, 0, 7, jitterWindowSeconds, noJitter)

    assertEquals(LocalDateTime.of(2025, 7, 4, 2, 0), next)
  }

  @Test
  fun `jitter is applied to the selected interval`() {
    val random = MockRandom(listOf(0))
    val now = LocalDateTime.of(2025, 6, 27, 1, 0)

    val next = LocalBackupListener.getNextBackupTimeFromNowWithJitter(now, 2, 0, 7, jitterWindowSeconds, random)

    assertEquals(LocalDateTime.of(2025, 7, 3, 1, 55), next)
  }
}
