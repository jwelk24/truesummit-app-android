# AI Edge Function

Gemini proxy. Holds the API key so the app never does — anything compiled into
the APK can be recovered by unzipping it, subscription check or not.

Companion to the `plaid` function (which lives in the iOS repo and is shared by
both apps — same Supabase project, same deployment).

## Deploy

```bash
# 1. Install the CLI (once)
brew install supabase/tap/supabase

# 2. Auth + link to the project
supabase login
supabase link --project-ref eebpmgilbguussctttgl

# 3. Apply the rate-limit table
supabase db push

# 4. Set the key. Use a NEW one — the previous key was compiled into
#    already-built APKs, so treat it as burned.
supabase secrets set GEMINI_API_KEY=<new key from aistudio.google.com>

# 5. Deploy (verify_jwt = true comes from ../../config.toml)
supabase functions deploy ai
```

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected by the platform;
don't set them yourself.

## Verify

Unauthenticated calls must be rejected by the platform, before the function
runs and before any quota is spent:

```bash
curl -i -X POST https://eebpmgilbguussctttgl.supabase.co/functions/v1/ai \
  -H 'Content-Type: application/json' \
  -d '{"contents":[{"role":"user","parts":[{"text":"hi"}]}]}'
# -> 401
```

With a real session token it should return `{"text":"..."}`. The app sends the
logged-in user's token automatically.

## Rate limits

Enforced in Postgres via `check_ai_rate_limit`, not in memory: Edge Function
isolates are ephemeral, so an in-process counter resets constantly and enforces
nothing. Currently 20 requests per user per 5 minutes, and 1200/day across all
users — under Gemini's free-tier ceiling of 1500/day.

If the rate-limit check itself fails, the function **allows** the request
rather than blocking a paying user; Gemini's own quota is the hard backstop.

## Notes

- Prompt bodies are never logged. They carry the user's transactions and
  balances.
- Model is restricted to an allowlist so a crafted request can't select a more
  expensive one.
- Proxying means prompts now transit Supabase rather than going straight from
  device to Google. Same data leaving the device either way, but one more
  party — worth reflecting in the privacy policy and the Play Data Safety form.
