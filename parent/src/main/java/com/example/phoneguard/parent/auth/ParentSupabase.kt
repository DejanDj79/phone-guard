package com.example.phoneguard.parent.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient

object ParentSupabase {
  const val AUTH_SCHEME = "com.example.phoneguard.parent"
  const val AUTH_HOST = "auth-callback"
  const val REDIRECT_URL = "${AUTH_SCHEME}://${AUTH_HOST}"

  // This is a Supabase publishable key. It is intentionally safe to ship
  // in a client application. Never put a service-role/secret key here.
  private const val SUPABASE_URL =
    "https://lpcytegfsslhugeiefdu.supabase.co"
  private const val SUPABASE_PUBLISHABLE_KEY =
    "sb_publishable_GajuPePv3yNwGPNvVx61OA_6GIsGgDU"

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
