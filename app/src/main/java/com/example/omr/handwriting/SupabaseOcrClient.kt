package com.example.omr.handwriting

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.remote.SupabaseConfig
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Structured response from the Supabase gemini-ocr Edge Function.
 */
data class GeminiOcrResponse(
  val status: String,
  val provider: String = "GEMINI",
  val firstName: String = "",
  val lastName: String = "",
  val phone: String = "",
  val whatsapp: String = "",
  val city: String = "",
  val school: String = "",
  val fallbackUsed: Boolean = false,
  val reviewRequired: Boolean = false,
  val message: String? = null
)

/**
 * Client for communicating with the Supabase Edge Function 'gemini-ocr'.
 * Encapsulates network communication, timeout handling, and JSON serialization.
 *
 * SECURITY:
 * - Does not hold or log any Gemini API key (the key resides solely on the Supabase backend).
 * - Transmits caller's Supabase User JWT for endpoint authorization.
 * - Only sends handwriting crops; never sends full OMR sheet.
 */
open class SupabaseOcrClient(
  private val endpointUrl: String = "${SupabaseConfig.SUPABASE_URL}/functions/v1/gemini-ocr",
  private val anonKey: String = SupabaseConfig.SUPABASE_ANON_KEY,
  private val tokenProvider: () -> String? = { SupabaseConfig.client.auth.currentAccessTokenOrNull() },
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()
) {

  companion object {
    private const val TAG = "SupabaseOcrClient"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }

  /**
   * Recognizes all six student handwriting fields by delegating to the Supabase Edge Function.
   * Sends a single multimodal request with all provided crops.
   * Returns a structured GeminiOcrResponse. Never throws network exceptions to callers.
   */
  open suspend fun recognizeAllFields(
    firstNameCrop: Bitmap? = null,
    lastNameCrop: Bitmap? = null,
    phoneCrop: Bitmap? = null,
    whatsappCrop: Bitmap? = null,
    cityCrop: Bitmap? = null,
    schoolCrop: Bitmap? = null
  ): GeminiOcrResponse = withContext(Dispatchers.IO) {
    if (firstNameCrop == null && lastNameCrop == null && phoneCrop == null &&
        whatsappCrop == null && cityCrop == null && schoolCrop == null) {
      return@withContext GeminiOcrResponse(
        status = "SUCCESS",
        provider = "GEMINI",
        firstName = "",
        lastName = "",
        phone = "",
        whatsapp = "",
        city = "",
        school = "",
        fallbackUsed = false,
        reviewRequired = false
      )
    }

    val token = tokenProvider()
    if (token.isNullOrBlank()) {
      Log.w(TAG, "No Supabase user token available. Routing to fallback.")
      return@withContext GeminiOcrResponse(
        status = "ERROR",
        provider = "GEMINI",
        fallbackUsed = true,
        reviewRequired = true,
        message = "User not authenticated with Supabase"
      )
    }

    try {
      val requestPayload = JSONObject().apply {
        if (firstNameCrop != null) {
          put("firstNameImageBase64", bitmapToBase64Png(firstNameCrop))
        }
        if (lastNameCrop != null) {
          put("lastNameImageBase64", bitmapToBase64Png(lastNameCrop))
        }
        if (phoneCrop != null) {
          put("phoneImageBase64", bitmapToBase64Png(phoneCrop))
        }
        if (whatsappCrop != null) {
          put("whatsappImageBase64", bitmapToBase64Png(whatsappCrop))
        }
        if (cityCrop != null) {
          put("cityImageBase64", bitmapToBase64Png(cityCrop))
        }
        if (schoolCrop != null) {
          put("schoolImageBase64", bitmapToBase64Png(schoolCrop))
        }
      }

      val request = Request.Builder()
        .url(endpointUrl)
        .addHeader("apikey", anonKey)
        .addHeader("Authorization", "Bearer $token")
        .addHeader("Content-Type", "application/json")
        .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
        .build()

      client.newCall(request).execute().use { response ->
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
          Log.w(TAG, "Edge Function returned HTTP ${response.code}: $responseBody")
          return@withContext GeminiOcrResponse(
            status = "ERROR",
            provider = "GEMINI",
            fallbackUsed = true,
            reviewRequired = true,
            message = "HTTP ${response.code}: $responseBody"
          )
        }

        val json = JSONObject(responseBody)
        val status = json.optString("status", "SUCCESS")
        val firstName = json.optString("firstName", "")
        val lastName = json.optString("lastName", "")
        val phone = json.optString("phone", "")
        val whatsapp = json.optString("whatsapp", "")
        val city = json.optString("city", "")
        val school = json.optString("school", "")
        val fallback = json.optBoolean("fallbackUsed", false)
        val reviewReq = json.optBoolean("reviewRequired", false)

        GeminiOcrResponse(
          status = status,
          provider = "GEMINI",
          firstName = firstName,
          lastName = lastName,
          phone = phone,
          whatsapp = whatsapp,
          city = city,
          school = school,
          fallbackUsed = fallback,
          reviewRequired = reviewReq
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "Network or timeout exception calling gemini-ocr Edge Function: ${e.message}")
      GeminiOcrResponse(
        status = "ERROR",
        provider = "GEMINI",
        fallbackUsed = true,
        reviewRequired = true,
        message = e.localizedMessage ?: "Unknown network error"
      )
    }
  }

  /**
   * Backward-compatible delegation for recognizing only City and School handwriting fields.
   */
  open suspend fun recognizeFreehand(
    cityCrop: Bitmap? = null,
    schoolCrop: Bitmap? = null
  ): GeminiOcrResponse = recognizeAllFields(cityCrop = cityCrop, schoolCrop = schoolCrop)

  private fun bitmapToBase64Png(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val bytes = outputStream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }
}
