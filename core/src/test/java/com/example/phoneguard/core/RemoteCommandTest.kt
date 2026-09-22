package com.example.phoneguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteCommandTest {
  @Test
  fun bonusTimeCarriesRequestedDuration() {
    val command = RemoteCommand.bonusTime(30)

    assertEquals(RemoteCommandType.BONUS_TIME, command.type)
    assertEquals(30, command.bonusMinutes)
  }

  @Test
  fun lockDoesNotCarryBonusDuration() {
    val command = RemoteCommand.lock()

    assertEquals(RemoteCommandType.LOCK, command.type)
    assertEquals(null, command.bonusMinutes)
  }

  @Test
  fun invalidBonusDurationIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      RemoteCommand.bonusTime(0)
    }
  }
}
