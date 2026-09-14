package com.example.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

import com.example.BuildConfig

object SupabaseConfig {
  val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
    .trim()
    .replace(Regex("/rest/v1/?$"), "")
    .trimEnd('/')

  val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY.trim()
  const val IMAGES_BUCKET = "navgrade-images"

  fun isConfigured(url: String = SUPABASE_URL, key: String = SUPABASE_ANON_KEY): Boolean {
    return url.isNotBlank() &&
      !url.contains("your-project") &&
      !url.contains("placeholder") &&
      key.isNotBlank() &&
      !key.contains("your-anon-public-key") &&
      !key.contains("placeholder")
  }

  val client: SupabaseClient by lazy {
    try {
      createSupabaseClient(
        supabaseUrl = if (isConfigured()) SUPABASE_URL else "https://placeholder.supabase.co",
        supabaseKey = if (isConfigured()) SUPABASE_ANON_KEY else "placeholder-key"
      ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
      }
    } catch (e: Throwable) {
      Log.e("SupabaseConfig", "Error initializing Supabase client: ${e.message}", e)
      createSupabaseClient(
        supabaseUrl = "https://placeholder.supabase.co",
        supabaseKey = "placeholder-key"
      ) {}
    }
  }
}
