package com.example.data.remote

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseConfig {
  // TODO: Replace with your actual Supabase project URL and anon public key from https://supabase.com/dashboard/project/_/settings/api
  const val SUPABASE_URL = "https://your-project.supabase.co"
  const val SUPABASE_ANON_KEY = "your-anon-public-key"
  const val IMAGES_BUCKET = "navgrade-images"

  fun isConfigured(): Boolean {
    return SUPABASE_URL.isNotBlank() &&
      !SUPABASE_URL.contains("your-project") &&
      SUPABASE_ANON_KEY.isNotBlank() &&
      !SUPABASE_ANON_KEY.contains("your-anon-public-key")
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
