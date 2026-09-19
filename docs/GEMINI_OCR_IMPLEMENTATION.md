# Gemini OCR Implementation Reference (Archived)

This document preserves the complete design, code, prompts, configurations, and data models of the Google Gemini multimodal OCR integration previously used in the NavGrades OMR Checker project. It provides all technical details required to recreate or reference this cloud OCR pipeline in the future.

---

## 1. Architectural Overview & Scanning Flow

The Gemini OCR pipeline was designed as an online handwriting transcription service augmenting the offline OMR scanner.

### High-Level Flow
```
OMR Camera Image / Bitmap
           ↓
Four-Corner Fiducial Detection & Perspective Rectification (682 x 1024)
           ↓
Crop Extraction (OmrFieldCrops)
  - Boxed Fields: First Name, Last Name, Phone, WhatsApp
  - Freehand Fields: City / Block / Village, School / College
           ↓
Image Preprocessing & Normalization
  - Boxed Fields: Height normalized to 60px (preserving aspect ratio)
  - Freehand Fields: Label-strip removal, ink isolation, threshold check
           ↓
Supabase Edge Function Gateway (`gemini-ocr`)
  - Authenticates caller using Supabase User JWT (Bearer token)
  - Verifies server-side `GEMINI_API_KEY`
  - Bundles all crops into ONE Multimodal Request (`gemini-3.5-flash`)
           ↓
Google Gemini API (`generateContent`)
  - Temperature: 0.0
  - System instructions + crop parts with visual transcription rules
  - Structured JSON response (`responseMimeType: "application/json"`)
           ↓
Edge Function parses & validates response → returns JSON to Android app
           ↓
HybridHandwritingEngine (Android)
  - If Gemini succeeded: Maps parsed fields directly into `HandwritingScanResult`
  - If offline/error: Gracefully falls back to local on-device TFLite + ML Kit
```

---

## 2. Supabase Edge Function Implementation

The Edge Function acted as a secure proxy between the Android application and the Google Generative Language API. It prevented exposure of the Gemini API key on mobile clients and enforced user session authentication.

### Function Code (`supabase/functions/gemini-ocr/index.ts`)

```typescript
// Supabase Edge Function: gemini-ocr
// Serves as a secure backend proxy between the Android app and Google Gemini API.
// Validates caller's Supabase User JWT before processing.
// Holds GEMINI_API_KEY strictly in server-side secrets (Deno.env).

declare const Deno: {
  env: {
    get(key: string): string | undefined;
  };
  serve(handler: (req: Request) => Promise<Response> | Response): void;
};

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

interface OcrRequest {
  cityImageBase64?: string;
  schoolImageBase64?: string;
  firstNameImageBase64?: string;
  lastNameImageBase64?: string;
  phoneImageBase64?: string;
  whatsappImageBase64?: string;
}

interface OcrResponse {
  status: "SUCCESS" | "ERROR";
  provider: string;
  firstName: string;
  lastName: string;
  phone: string;
  whatsapp: string;
  city: string;
  school: string;
  fallbackUsed: boolean;
  reviewRequired: boolean;
  message?: string;
}

Deno.serve(async (req: Request) => {
  // 1. Handle CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    // 2. Validate Supabase User Authentication (JWT)
    const authHeader = req.headers.get("Authorization") || req.headers.get("authorization");
    if (!authHeader || !authHeader.toLowerCase().startsWith("bearer ")) {
      return new Response(
        JSON.stringify({
          status: "ERROR",
          provider: "GEMINI",
          city: "",
          school: "",
          fallbackUsed: false,
          reviewRequired: true,
          message: "Unauthorized: Missing or invalid Authorization Bearer header",
        } as OcrResponse),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const jwt = authHeader.replace(/^[Bb]earer\s+/i, "").trim();
    if (!jwt) {
      return new Response(
        JSON.stringify({
          status: "ERROR",
          provider: "GEMINI",
          city: "",
          school: "",
          fallbackUsed: false,
          reviewRequired: true,
          message: "Unauthorized: Empty JWT token provided",
        } as OcrResponse),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

    const supabase = createClient(supabaseUrl, supabaseAnonKey, {
      global: { headers: { Authorization: authHeader } },
      auth: { persistSession: false },
    });

    const { data, error: authError } = await supabase.auth.getUser(jwt);
    const user = data?.user;

    if (authError || !user) {
      return new Response(
        JSON.stringify({
          status: "ERROR",
          provider: "GEMINI",
          city: "",
          school: "",
          fallbackUsed: false,
          reviewRequired: true,
          message: `Unauthorized: User session invalid - ${authError?.message ?? "unknown"}`,
        } as OcrResponse),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 3. Verify Server-Side Gemini API Key
    const geminiApiKey = Deno.env.get("GEMINI_API_KEY")?.trim();
    if (!geminiApiKey) {
      return new Response(
        JSON.stringify({
          status: "ERROR",
          provider: "GEMINI",
          city: "",
          school: "",
          fallbackUsed: false,
          reviewRequired: true,
          message: "Server configuration error: GEMINI_API_KEY secret not set",
        } as OcrResponse),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const geminiModel = Deno.env.get("GEMINI_MODEL")?.trim() || "gemini-3.5-flash";

    // 4. Parse Request Body
    let body: OcrRequest;
    try {
      body = await req.json();
    } catch (_e) {
      return new Response(
        JSON.stringify({
          status: "ERROR",
          provider: "GEMINI",
          firstName: "",
          lastName: "",
          phone: "",
          whatsapp: "",
          city: "",
          school: "",
          fallbackUsed: false,
          reviewRequired: true,
          message: "Bad Request: Invalid JSON body",
        } as OcrResponse),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const {
      cityImageBase64,
      schoolImageBase64,
      firstNameImageBase64,
      lastNameImageBase64,
      phoneImageBase64,
      whatsappImageBase64,
    } = body;

    const hasAnyImage = cityImageBase64 || schoolImageBase64 || firstNameImageBase64 ||
                        lastNameImageBase64 || phoneImageBase64 || whatsappImageBase64;

    if (!hasAnyImage) {
      return new Response(
        JSON.stringify({
          status: "ERROR",
          provider: "GEMINI",
          firstName: "",
          lastName: "",
          phone: "",
          whatsapp: "",
          city: "",
          school: "",
          fallbackUsed: false,
          reviewRequired: true,
          message: "Bad Request: No handwriting image crops provided",
        } as OcrResponse),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 5. Query Gemini API with ONE multimodal request containing all provided image crops
    const systemPrompt = `You are an expert handwriting OCR transcription engine for student exam sheets.
Transcribe ONLY the handwritten text visible in each provided image crop according to these strict rules:

FIELD RULES:
1. firstName and lastName:
   - Read only what is visually written in the boxes.
   - Output uppercase English letters A-Z where applicable.
   - Do NOT invent or semantically correct names.
   - Do NOT include spaces between letters of a single name. If empty, return "".
2. phone and whatsapp:
   - Read only what is visually written in the boxes.
   - Output digits 0-9 only. Ignore printed box dividing borders or ruling lines.
   - Preserve the exact visually written digits.
   - Do NOT invent missing digits. If empty, return "".
3. city and school:
   - Preserve visually written words, spacing, commas, hyphens, and visible symbols (such as '&').
   - Do NOT semantically "correct" uncertain handwriting. If empty, return "".

OUTPUT FORMAT:
Return ONLY a valid JSON object matching this schema:
{
  "firstName": "string",
  "lastName": "string",
  "phone": "string",
  "whatsapp": "string",
  "city": "string",
  "school": "string"
}`;

    const parts: Array<{ text: string } | { inline_data: { mime_type: string; data: string } }> = [
      { text: systemPrompt },
    ];

    if (firstNameImageBase64 && firstNameImageBase64.trim().length > 0) {
      parts.push({ text: "Crop 1 - Field: 'firstName'" });
      parts.push({ inline_data: { mime_type: "image/png", data: firstNameImageBase64 } });
    }
    if (lastNameImageBase64 && lastNameImageBase64.trim().length > 0) {
      parts.push({ text: "Crop 2 - Field: 'lastName'" });
      parts.push({ inline_data: { mime_type: "image/png", data: lastNameImageBase64 } });
    }
    if (phoneImageBase64 && phoneImageBase64.trim().length > 0) {
      parts.push({ text: "Crop 3 - Field: 'phone'" });
      parts.push({ inline_data: { mime_type: "image/png", data: phoneImageBase64 } });
    }
    if (whatsappImageBase64 && whatsappImageBase64.trim().length > 0) {
      parts.push({ text: "Crop 4 - Field: 'whatsapp'" });
      parts.push({ inline_data: { mime_type: "image/png", data: whatsappImageBase64 } });
    }
    if (cityImageBase64 && cityImageBase64.trim().length > 0) {
      parts.push({ text: "Crop 5 - Field: 'city'" });
      parts.push({ inline_data: { mime_type: "image/png", data: cityImageBase64 } });
    }
    if (schoolImageBase64 && schoolImageBase64.trim().length > 0) {
      parts.push({ text: "Crop 6 - Field: 'school'" });
      parts.push({ inline_data: { mime_type: "image/png", data: schoolImageBase64 } });
    }

    const payload = {
      contents: [{ parts }],
      generationConfig: {
        temperature: 0.0,
        maxOutputTokens: 1024,
        responseMimeType: "application/json",
      },
    };

    const geminiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${geminiModel}:generateContent?key=${geminiApiKey}`;

    const response = await fetch(geminiUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`Gemini API HTTP ${response.status}: ${errText.slice(0, 200)}`);
    }

    const resJson = await response.json();
    let rawText = resJson.candidates?.[0]?.content?.parts?.[0]?.text ?? "{}";
    rawText = rawText.trim().replace(/^```(?:json)?\n?|```$/g, "").trim();

    let parsed: Record<string, any> = {};
    try {
      parsed = JSON.parse(rawText);
    } catch (_pe) {
      console.warn("Failed to parse Gemini JSON response:", rawText);
    }

    const successResponse: OcrResponse = {
      status: "SUCCESS",
      provider: "GEMINI",
      firstName: typeof parsed.firstName === "string" ? parsed.firstName.trim() : "",
      lastName: typeof parsed.lastName === "string" ? parsed.lastName.trim() : "",
      phone: typeof parsed.phone === "string" ? parsed.phone.trim() : "",
      whatsapp: typeof parsed.whatsapp === "string" ? parsed.whatsapp.trim() : "",
      city: typeof parsed.city === "string" ? parsed.city.trim() : "",
      school: typeof parsed.school === "string" ? parsed.school.trim() : "",
      fallbackUsed: false,
      reviewRequired: false,
    };

    return new Response(JSON.stringify(successResponse), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : String(err);
    const errorResponse: OcrResponse = {
      status: "ERROR",
      provider: "GEMINI",
      firstName: "",
      lastName: "",
      phone: "",
      whatsapp: "",
      city: "",
      school: "",
      fallbackUsed: false,
      reviewRequired: true,
      message: errorMsg,
    };
    return new Response(JSON.stringify(errorResponse), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
```

---

## 3. Android Client Implementation

### Android Supabase Client (`SupabaseOcrClient.kt`)

```kotlin
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
        if (firstNameCrop != null) put("firstNameImageBase64", bitmapToBase64Png(firstNameCrop))
        if (lastNameCrop != null) put("lastNameImageBase64", bitmapToBase64Png(lastNameCrop))
        if (phoneCrop != null) put("phoneImageBase64", bitmapToBase64Png(phoneCrop))
        if (whatsappCrop != null) put("whatsappImageBase64", bitmapToBase64Png(whatsappCrop))
        if (cityCrop != null) put("cityImageBase64", bitmapToBase64Png(cityCrop))
        if (schoolCrop != null) put("schoolImageBase64", bitmapToBase64Png(schoolCrop))
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
        GeminiOcrResponse(
          status = json.optString("status", "SUCCESS"),
          provider = "GEMINI",
          firstName = json.optString("firstName", ""),
          lastName = json.optString("lastName", ""),
          phone = json.optString("phone", ""),
          whatsapp = json.optString("whatsapp", ""),
          city = json.optString("city", ""),
          school = json.optString("school", ""),
          fallbackUsed = json.optBoolean("fallbackUsed", false),
          reviewRequired = json.optBoolean("reviewRequired", false)
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

  private fun bitmapToBase64Png(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val bytes = outputStream.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
  }
}
```

### Provider & Crop Preparation (`GeminiFreehandOcrProvider.kt`)

```kotlin
data class GeminiStudentFieldsResult(
  val firstName: RecognitionResult,
  val lastName: RecognitionResult,
  val phone: RecognitionResult,
  val whatsapp: RecognitionResult,
  val city: RecognitionResult,
  val school: RecognitionResult,
  val fallbackUsed: Boolean = false,
  val reviewRequired: Boolean = false,
  val provider: String = "GEMINI"
)

class GeminiFreehandOcrProvider(
  private val ocrClient: SupabaseOcrClient = SupabaseOcrClient(),
  private val fallbackProvider: FreehandOcrProvider = MlKitFreehandOcrProvider()
) : FreehandOcrProvider {

  suspend fun recognizeAllStudentFields(crops: OmrFieldCrops): GeminiStudentFieldsResult? {
    try {
      // 1. Boxed crops scaled to 60px height to enhance character recognition
      val fnCrop = crops.firstNameCrop?.let { prepareBoxedCrop(it) }
      val lnCrop = crops.lastNameCrop?.let { prepareBoxedCrop(it) }
      val phoneCrop = crops.phoneCrop?.let { prepareBoxedCrop(it) }
      val waCrop = crops.whatsappCrop?.let { prepareBoxedCrop(it) }

      // 2. Preprocess unboxed fields with ink-isolation and label stripping
      val cityPreproc = crops.cityCrop.let { HandwritingPreprocessor.preprocessFreeHandwriting(it, FreeFieldType.CITY) }
      val schoolPreproc = crops.schoolCrop.let { HandwritingPreprocessor.preprocessFreeHandwriting(it, FreeFieldType.SCHOOL) }

      val cityHasInk = cityPreproc.hasInk && cityPreproc.inkPixelCount >= 15
      val schoolHasInk = schoolPreproc.hasInk && schoolPreproc.inkPixelCount >= 15

      val targetCityBmp = if (cityHasInk) cityPreproc.cleanedBitmap else null
      val targetSchoolBmp = if (schoolHasInk) schoolPreproc.cleanedBitmap else null

      val response = ocrClient.recognizeAllFields(
        firstNameCrop = fnCrop,
        lastNameCrop = lnCrop,
        phoneCrop = phoneCrop,
        whatsappCrop = waCrop,
        cityCrop = targetCityBmp,
        schoolCrop = targetSchoolBmp
      )

      if (response.status == "SUCCESS" && !response.fallbackUsed) {
        val cleanFn = HandwritingValidation.validateName(response.firstName)
        val cleanLn = HandwritingValidation.validateName(response.lastName)
        val cleanPhone = HandwritingValidation.validatePhone(response.phone)
        val cleanWa = HandwritingValidation.validatePhone(response.whatsapp).ifBlank { cleanPhone }
        val cleanCity = if (!cityHasInk) "" else response.city.trim()
        val cleanSchool = if (!schoolHasInk) "" else response.school.trim()

        return GeminiStudentFieldsResult(
          firstName = RecognitionResult(cleanFn, if (cleanFn.isNotBlank()) 0.95f else 1.0f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI"),
          lastName = RecognitionResult(cleanLn, if (cleanLn.isNotBlank()) 0.95f else 1.0f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI"),
          phone = RecognitionResult(cleanPhone, if (cleanPhone.isNotBlank()) 0.95f else 1.0f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI"),
          whatsapp = RecognitionResult(cleanWa, if (cleanWa.isNotBlank()) 0.95f else 1.0f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI"),
          city = RecognitionResult(cleanCity, if (cleanCity.isNotBlank()) 0.95f else 1.0f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI"),
          school = RecognitionResult(cleanSchool, if (cleanSchool.isNotBlank()) 0.95f else 1.0f, RecognitionStatus.HIGH_CONFIDENCE, provider = "GEMINI"),
          fallbackUsed = false,
          reviewRequired = false,
          provider = "GEMINI"
        )
      }
      return null
    } catch (t: Throwable) {
      return null
    }
  }

  private fun prepareBoxedCrop(crop: Bitmap): Bitmap {
    val h = crop.height
    val w = crop.width
    return if (h < 60) {
      val scale = 60.0f / h.toFloat()
      val targetW = (w * scale).toInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(crop, targetW, 60, true)
    } else {
      crop
    }
  }
}
```

---

## 4. Configuration & Environment Variables

### Edge Function Environment Variables
- `GEMINI_API_KEY`: API key for Google Generative AI (Google AI Studio). Kept strictly in backend secrets (`supabase secrets set`).
- `GEMINI_MODEL`: Model identifier (e.g., `gemini-3.5-flash` or `gemini-3.6-flash`).
- `SUPABASE_URL`: Project Supabase URL.
- `SUPABASE_ANON_KEY`: Supabase public anon key.

### Security Notes
- Android clients never possessed the Gemini API key directly.
- The Edge Function verified user identity by exchanging the client's JWT with `supabase.auth.getUser(jwt)`.
- Non-authenticated callers received HTTP 401 and were directed to the offline fallback recognizer.
