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
}

interface OcrResponse {
  status: "SUCCESS" | "ERROR";
  provider: string;
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

    // Model name configured in backend environment only
    const geminiModel = Deno.env.get("GEMINI_MODEL")?.trim() || "gemini-3.6-flash";

    // 4. Parse Request Body (Only City and School crops)
    let body: OcrRequest;
    try {
      body = await req.json();
    } catch (_e) {
      return new Response(
        JSON.stringify({
          status: "ERROR",
          provider: "GEMINI",
          city: "",
          school: "",
          fallbackUsed: false,
          reviewRequired: true,
          message: "Bad Request: Invalid JSON body",
        } as OcrResponse),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const { cityImageBase64, schoolImageBase64 } = body;

    if (!cityImageBase64 && !schoolImageBase64) {
      return new Response(
        JSON.stringify({
          status: "ERROR",
          provider: "GEMINI",
          city: "",
          school: "",
          fallbackUsed: false,
          reviewRequired: true,
          message: "Bad Request: Neither cityImageBase64 nor schoolImageBase64 provided",
        } as OcrResponse),
        { status: 400, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    // 5. Query Gemini API for each crop
    const transcribeCrop = async (imageBase64?: string): Promise<string> => {
      if (!imageBase64 || imageBase64.trim().length === 0) return "";

      const prompt = "Transcribe ONLY the handwritten text in this image. Return the text exactly as visually read. Preserve visible punctuation/symbols. Output ONLY the raw transcribed text without explanations.";

      const payload = {
        contents: [
          {
            parts: [
              { text: prompt },
              {
                inline_data: {
                  mime_type: "image/png",
                  data: imageBase64,
                },
              },
            ],
          },
        ],
        generationConfig: {
          temperature: 0.0,
          maxOutputTokens: 1024,
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
      let rawText = resJson.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
      // Clean markdown wrappers if any, preserve exact visual characters and symbols
      rawText = rawText.trim().replace(/^```[a-zA-Z]*\n?|```$/g, "").trim().replace(/\n+/g, " ");
      return rawText;
    };

    const [cityText, schoolText] = await Promise.all([
      transcribeCrop(cityImageBase64),
      transcribeCrop(schoolImageBase64),
    ]);

    const successResponse: OcrResponse = {
      status: "SUCCESS",
      provider: "GEMINI",
      city: cityText,
      school: schoolText,
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
