package com.example.phoneguard.parent.data

import com.example.phoneguard.parent.auth.ParentSupabase
import io.github.jan.supabase.auth.auth
import java.net.HttpURLConnection

internal fun HttpURLConnection.applyParentAuthHeaders() {
  setRequestProperty(
    "apikey",
    ParentSupabase.SUPABASE_PUBLISHABLE_KEY,
  )

  ParentSupabase.client.auth
    .currentSessionOrNull()
    ?.accessToken
    ?.takeIf { it.isNotBlank() }
    ?.let { accessToken ->
      setRequestProperty(
        "Authorization",
        "Bearer $accessToken",
      )
    }
}
