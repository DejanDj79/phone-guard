package com.example.phoneguard.parent.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient

object ParentSupabase {
  const val AUTH_SCHEME = "com.example.phoneguard.parent"
  const val AUTH_HOST = "auth-callback"
  const val REDIRECT_URL = "${AUTH_SCHEME}://${AUTH_HOST}"

  // These are public client configuration values. Never put a
  // service-role/secret key in the Android application.
  const val SUPABASE_URL =
    "https://lpcytegfsslhugeiefdu.supabase.co"
  const val SUPABASE_PUBLISHABLE_KEY =
    "sb_publishable_GajuPePv3yNwGPNvVx61OA_6GIsGgDU"

  fun functionUrl(functionName: String): String =
    "$SUPABASE_URL/functions/v1/$functionName"

  val client: SupabaseClient =
    createSupabaseClient(
      supabaseUrl = SUPABASE_URL,
      supabaseKey = SUPABASE_PUBLISHABLE_KEY,
    ) {
      install(Auth) {
        flowType = FlowType.PKCE
        scheme = AUTH_SCHEME
        host = AUTH_HOST
      }
    }
}
