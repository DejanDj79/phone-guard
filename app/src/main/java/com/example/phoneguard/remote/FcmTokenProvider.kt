package com.example.phoneguard.remote

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object FcmTokenProvider {
  suspend fun currentToken(): String? =
    suspendCoroutine { continuation ->
      FirebaseMessaging.getInstance().token
        .addOnCompleteListener { task ->
          continuation.resume(
            if (task.isSuccessful) task.result else null,
          )
        }
    }
}
