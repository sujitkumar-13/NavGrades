// Supabase Edge Function: gemini-ocr
// Serves as a secure backend proxy between the NavGrades Android app and Google Gemini API.
// Validates caller's Supabase User JWT before processing.
// Holds GEMINI_API_KEY strictly in server-side secrets (Deno.env).

// Ambient declarations for TypeScript IDE / editor language servers
declare const Deno: {
  env: {
    get(key: string): string | undefined;
  };
  serve(handler: (req: Request) => Promise<Response> | Response): void;
};

// @ts-ignore: URL import supported in Deno / Supabase Edge Runtime
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

    // Model name configured in backend environment only (validated gemini-3.5-flash)
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
