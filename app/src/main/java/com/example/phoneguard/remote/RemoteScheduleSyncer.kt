package com.example.phoneguard.remote

import android.content.Context
import android.util.Log
import com.example.phoneguard.data.ChildSettingsStore
import com.example.phoneguard.data.WeeklySchedule
import com.example.phoneguard.schedule.ScheduleAlarmScheduler

class RemoteScheduleSyncer(context: Context) {
  private val appContext = context.applicationContext
  private val settingsStore = ChildSettingsStore(appContext)
  private val backendClient = ChildBackendClient()
  private val alarmScheduler = ScheduleAlarmScheduler(appContext)

  fun sync(): Boolean {
    val identity = settingsStore.getOrCreatePairingIdentity()
    val deviceSecret = settingsStore.getOrCreateDeviceSecret()

    return when (
      val result =
        backendClient.fetchSchedule(
          deviceId = identity.deviceId,
          deviceSecret = deviceSecret,
        )
    ) {
      is ChildScheduleResult.Success -> {
        val localVersion = settingsStore.remoteScheduleVersion()

        if (result.config.isNullOrBlank() && result.version == 0L) {
          val localSchedule = settingsStore.getWeeklySchedule()

          when (
            val initializeResult =
              backendClient.initializeSchedule(
                deviceId = identity.deviceId,
                deviceSecret = deviceSecret,
                scheduleConfig = localSchedule.encode(),
              )
          ) {
            is ChildScheduleInitializeResult.Success -> {
              val stored =
                settingsStore.saveRemoteWeeklySchedule(
                  schedule = localSchedule,
                  version = initializeResult.version,
                )

              if (!stored) {
                Log.e(TAG, "Failed to persist bootstrapped schedule version")
                false
              } else {
                Log.i(
                  TAG,
                  "Backend schedule initialized from Child: version=" +
                    initializeResult.version,
                )
                true
              }
            }

            is ChildScheduleInitializeResult.Failure -> {
              Log.w(
                TAG,
                "Schedule initialization failed: " + initializeResult.message,
              )
              false
            }
          }
        } else if (result.config.isNullOrBlank()) {
          Log.w(
            TAG,
            "Remote schedule missing for version=" + result.version,
          )
          false
        } else if (result.version <= localVersion) {
          Log.i(
            TAG,
            "Schedule already current: local=" +
              localVersion +
              ", remote=" +
              result.version,
          )
          true
        } else {
          val schedule = WeeklySchedule.decode(result.config)

          if (
            !settingsStore.saveRemoteWeeklySchedule(
              schedule = schedule,
              version = result.version,
            )
          ) {
            Log.e(TAG, "Failed to persist remote schedule")
            false
          } else {
            alarmScheduler.syncCurrentStateAndScheduleNext()
            ChildHeartbeatSender(appContext).send()
            Log.i(TAG, "Remote schedule applied: version=" + result.version)
            true
          }
        }
      }

      is ChildScheduleResult.Failure -> {
        Log.w(TAG, "Schedule sync failed: " + result.message)
        false
      }
    }
  }

  private companion object {
    const val TAG = "PhoneGuardScheduleSync"
  }
}
