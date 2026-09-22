package com.example.phoneguard.parent.data

import com.example.phoneguard.core.ChildDevice
import com.example.phoneguard.core.DeviceAccessState
import com.example.phoneguard.core.PairingRequest
import com.example.phoneguard.core.PairingResult

interface PairingGateway {
  fun pair(request: PairingRequest): PairingResult
}

class MockPairingGateway : PairingGateway {
  override fun pair(request: PairingRequest): PairingResult =
    PairingResult.Success(
      device =
        ChildDevice(
          deviceId = "paired-" + request.pairingCode.lowercase(),
          displayName = "Redmi Note 10",
          state = DeviceAccessState.ALLOWED,
        ),
    )
}
