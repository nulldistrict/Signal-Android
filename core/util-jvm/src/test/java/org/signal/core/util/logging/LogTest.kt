/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.signal.core.util.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LogTest {

  @Before
  fun setUp() {
    Log.initialize(NoopLogger())
  }

  @After
  fun tearDown() {
    Log.initialize(NoopLogger())
  }

  @Test
  fun tag_short_class_name() {
    assertEquals("MyClass", Log.tag(MyClass::class))
  }

  @Test
  fun tag_23_character_class_name() {
    val tag = Log.tag(TwentyThreeCharacters23::class)
    assertEquals("TwentyThreeCharacters23", tag)
    assertEquals(23, tag.length)
  }

  @Test
  fun tag_24_character_class_name() {
    assertEquals(24, TwentyFour24Characters24::class.simpleName!!.length)
    val tag = Log.tag(TwentyFour24Characters24::class)
    assertEquals("TwentyFour24Characters2", tag)
    assertEquals(23, Log.tag(TwentyThreeCharacters23::class).length)
  }

  @Test
  fun setLogging_disabled_drops_messages_and_enabled_resumes() {
    val logger = RecordingLogger()
    Log.initialize({ false }, logger)

    Log.d("tag", "before")
    Log.setLogging(false)
    Log.d("tag", "while-disabled")
    Log.setLogging(true)
    Log.d("tag", "after")

    assertEquals(listOf("before", "after"), logger.messages)
  }

  @Test
  fun wipeLogs_flushes_and_clears_the_configured_loggers() {
    val logger = RecordingLogger()
    Log.initialize({ false }, logger)

    Log.wipeLogs()

    assertEquals(1, logger.flushCount)
    assertEquals(1, logger.clearCount)
  }

  private class RecordingLogger : Log.Logger() {
    val messages = mutableListOf<String>()
    var flushCount = 0
    var clearCount = 0

    override fun v(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = Unit
    override fun d(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) {
      messages += requireNotNull(message)
    }

    override fun i(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = Unit
    override fun w(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = Unit
    override fun e(tag: String, message: String?, t: Throwable?, keepLonger: Boolean) = Unit

    override fun flush() {
      flushCount++
    }

    override fun clear() {
      clearCount++
    }
  }

  private inner class MyClass

  private inner class TwentyThreeCharacters23

  private inner class TwentyFour24Characters24
}
