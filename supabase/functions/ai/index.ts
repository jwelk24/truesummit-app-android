// Supabase Edge Function: the Gemini proxy.
//
// The API key lives here, never in the shipped app, where it would be
// trivially extractable from the APK. config.toml sets verify_jwt = true for
// this function, so Supabase validates the caller's session before we run —
// an unauthenticated request never reaches this code, and never spends quota.
//
// Route (suffix after the function name):
//   POST /   { model?: string, contents: [{ role, parts: [{ text }] }] }
//        ->  { text: string }
//
// The body mirrors Gemini's own shape so a multi-turn chat can pass its
// history straight through.
//
// Secrets: GEMINI_API_KEY (set via `supabase secrets set`). SUPABASE_URL and
// SUPABASE_SERVICE_ROLE_KEY are injected by the platform.

const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const MODELS = new Set(["gemini-1.5-flash", "gemini-1.5-pro"]);
const DEFAULT_MODEL = "gemini-1.5-flash";

const CORS = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: { ...CORS, "Content-Type": "application/json" },
    });
}

/**
 * Pulls the user id out of the bearer token.
 *
 * Safe to read without verifying: verify_jwt = true means Supabase already
 * checked the signature and rejected anything invalid before invoking us.
 */
function userIdFromAuthHeader(header: string | null): string | null {
    if (!header?.startsWith("Bearer ")) return null;
    const parts = header.slice(7).trim().split(".");
    if (parts.length !== 3) return null;
    try {
        const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
        const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
        // Decode as UTF-8 rather than passing atob's binary string straight to
        // JSON.parse: the payload carries the user's email and name, and a
        // non-ASCII character there would otherwise corrupt the JSON.
        const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
        const payload = JSON.parse(new TextDecoder().decode(bytes));
        return typeof payload?.sub === "string" ? payload.sub : null;
    } catch {
        return null;
    }
}

/** Returns null when allowed, or a message to show the user. */
async function rateLimit(userId: string): Promise<string | null> {
    const r = await fetch(`${SUPABASE_URL}/rest/v1/rpc/check_ai_rate_limit`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            apikey: SERVICE_ROLE_KEY,
            Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
        },
        body: JSON.stringify({ p_user: userId }),
    });
    if (!r.ok) {
        // Fail open on infrastructure trouble rather than blocking a paying
        // user; the global Gemini quota is still a hard backstop.
        console.error("Rate limit check failed:", r.status);
        return null;
    }
    return await r.json();
}

Deno.serve(async (req) => {
    if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
    if (req.method !== "POST") return json({ error: { message: "POST only." } }, 405);

    if (!GEMINI_API_KEY) {
        return json({ error: { message: "AI is not configured on this server." } }, 503);
    }

    const userId = userIdFromAuthHeader(req.headers.get("authorization"));
    if (!userId) {
        return json({ error: { message: "Sign in to use AI features." } }, 401);
    }

    const limitMessage = await rateLimit(userId);
    if (limitMessage) return json({ error: { message: limitMessage } }, 429);

    const body = await req.json().catch(() => ({}));
    const model = MODELS.has(body?.model) ? body.model : DEFAULT_MODEL;
    const contents = body?.contents;
    if (!Array.isArray(contents) || contents.length === 0) {
        return json({ error: { message: "contents must be a non-empty array." } }, 400);
    }

    try {
        const r = await fetch(
            `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "x-goog-api-key": GEMINI_API_KEY,
                },
                body: JSON.stringify({ contents }),
            },
        );

        if (!r.ok) {
            // Status only — prompt bodies carry the user's financial data and
            // must never reach the logs.
            console.error(`Gemini error ${r.status}`);
            return json({ error: { message: "AI request failed.", status: r.status } }, r.status);
        }

        const data = await r.json();
        const text = (data?.candidates?.[0]?.content?.parts ?? [])
            .map((p: { text?: string }) => p.text ?? "")
            .join("");
        return json({ text });
    } catch (e) {
        console.error("Gemini proxy failure:", (e as Error)?.message);
        return json({ error: { message: "Could not reach the AI service." } }, 502);
    }
});
