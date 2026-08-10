import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import { Configuration, PlaidApi, PlaidEnvironments, Products, CountryCode } from 'plaid';

const {
    PLAID_CLIENT_ID,
    PLAID_SECRET,
    PLAID_ENV = 'sandbox',
    PLAID_PRODUCTS = 'transactions',
    PLAID_COUNTRY_CODES = 'US',
    PLAID_LANGUAGE = 'en',
    PLAID_REDIRECT_URI,
    GEMINI_API_KEY,
    SUPABASE_URL,
    SUPABASE_ANON_KEY,
    PORT = 8080,
} = process.env;

if (!PLAID_CLIENT_ID || !PLAID_SECRET) {
    console.error('Missing PLAID_CLIENT_ID or PLAID_SECRET. Copy .env.example to .env and fill them in.');
    process.exit(1);
}

// The AI routes are optional: the rest of the backend still serves Plaid if
// Gemini is not configured, it just answers 503 on /api/ai/*.
const aiEnabled = Boolean(GEMINI_API_KEY && SUPABASE_URL && SUPABASE_ANON_KEY);
if (!aiEnabled) {
    console.warn('AI proxy disabled: set GEMINI_API_KEY, SUPABASE_URL and SUPABASE_ANON_KEY to enable /api/ai/generate.');
}

const plaid = new PlaidApi(new Configuration({
    basePath: PlaidEnvironments[PLAID_ENV],
    baseOptions: {
        headers: {
            'PLAID-CLIENT-ID': PLAID_CLIENT_ID,
            'PLAID-SECRET': PLAID_SECRET,
        },
    },
}));

const products = PLAID_PRODUCTS.split(',').map(s => s.trim()).filter(Boolean).map(p => Products[
    Object.keys(Products).find(k => Products[k] === p) ?? p
] ?? p);

const countryCodes = PLAID_COUNTRY_CODES.split(',').map(s => s.trim()).filter(Boolean).map(c => CountryCode[
    Object.keys(CountryCode).find(k => CountryCode[k] === c) ?? c
] ?? c);

const app = express();
app.use(cors());
app.use(express.json());

app.get('/api/health', (_req, res) => {
    res.json({ ok: true, env: PLAID_ENV });
});

// 1. Create a Hosted Link token. App opens the returned hosted_link_url in a
// WKWebView and watches for redirect to PLAID_REDIRECT_URI?public_token=...
app.post('/api/link/token/create', async (req, res) => {
    try {
        const clientUserId = req.body?.clientUserId ?? 'truesummit-local-user';
        const response = await plaid.linkTokenCreate({
            user: { client_user_id: clientUserId },
            client_name: 'TrueSummit',
            products,
            country_codes: countryCodes,
            language: PLAID_LANGUAGE,
            redirect_uri: PLAID_REDIRECT_URI,
            hosted_link: {},
        });
        res.json({
            linkToken: response.data.link_token,
            hostedLinkUrl: response.data.hosted_link_url,
            expiration: response.data.expiration,
            redirectUri: PLAID_REDIRECT_URI,
        });
    } catch (e) {
        sendPlaidError(res, e);
    }
});

// 2. Exchange the public_token (returned by Hosted Link redirect) for an
// access_token. The app stores access_token in Keychain.
app.post('/api/item/public_token/exchange', async (req, res) => {
    try {
        const { publicToken } = req.body ?? {};
        if (!publicToken) return res.status(400).json({ error: 'publicToken required' });
        const response = await plaid.itemPublicTokenExchange({ public_token: publicToken });
        res.json({
            accessToken: response.data.access_token,
            itemId: response.data.item_id,
        });
    } catch (e) {
        sendPlaidError(res, e);
    }
});

// 3. List accounts for an item. Access token comes from the X-Plaid-Access-Token header.
app.get('/api/accounts', async (req, res) => {
    try {
        const accessToken = req.get('x-plaid-access-token');
        if (!accessToken) return res.status(401).json({ error: 'X-Plaid-Access-Token header required' });
        const response = await plaid.accountsGet({ access_token: accessToken });
        res.json({
            item: response.data.item,
            accounts: response.data.accounts,
        });
    } catch (e) {
        sendPlaidError(res, e);
    }
});

// 4. Sync transactions. Client passes its last cursor (or omits it on first
// sync) and gets back added / modified / removed plus the new cursor to store.
app.post('/api/transactions/sync', async (req, res) => {
    try {
        const accessToken = req.get('x-plaid-access-token');
        if (!accessToken) return res.status(401).json({ error: 'X-Plaid-Access-Token header required' });

        let cursor = req.body?.cursor || undefined;
        const added = [];
        const modified = [];
        const removed = [];
        let hasMore = true;

        while (hasMore) {
            const response = await plaid.transactionsSync({
                access_token: accessToken,
                cursor,
                count: 500,
            });
            added.push(...response.data.added);
            modified.push(...response.data.modified);
            removed.push(...response.data.removed);
            hasMore = response.data.has_more;
            cursor = response.data.next_cursor;
        }

        res.json({ added, modified, removed, nextCursor: cursor });
    } catch (e) {
        sendPlaidError(res, e);
    }
});

// 5. Pull holdings (positions) for any investment / retirement accounts on
// this item. Returns Plaid's `holdings` and `securities` arrays.
app.get('/api/investments/holdings', async (req, res) => {
    try {
        const accessToken = req.get('x-plaid-access-token');
        if (!accessToken) return res.status(401).json({ error: 'X-Plaid-Access-Token header required' });
        const response = await plaid.investmentsHoldingsGet({ access_token: accessToken });
        res.json({
            accounts: response.data.accounts,
            holdings: response.data.holdings,
            securities: response.data.securities,
        });
    } catch (e) {
        sendPlaidError(res, e);
    }
});

// 6. Pull investment transactions (buys, sells, dividends, fees, etc.) over a
// rolling window. Client passes `startDate` (defaults to 2 years ago) and
// `endDate` (defaults to today).
app.post('/api/investments/transactions', async (req, res) => {
    try {
        const accessToken = req.get('x-plaid-access-token');
        if (!accessToken) return res.status(401).json({ error: 'X-Plaid-Access-Token header required' });
        const today = new Date();
        const twoYearsAgo = new Date(today.getFullYear() - 2, today.getMonth(), today.getDate());
        const startDate = req.body?.startDate || twoYearsAgo.toISOString().slice(0, 10);
        const endDate = req.body?.endDate || today.toISOString().slice(0, 10);

        const investmentTransactions = [];
        const securitiesById = new Map();
        let offset = 0;
        const count = 500;
        let total = Infinity;

        while (offset < total) {
            const response = await plaid.investmentsTransactionsGet({
                access_token: accessToken,
                start_date: startDate,
                end_date: endDate,
                options: { count, offset },
            });
            investmentTransactions.push(...response.data.investment_transactions);
            for (const security of response.data.securities) {
                securitiesById.set(security.security_id, security);
            }
            total = response.data.total_investment_transactions;
            offset += response.data.investment_transactions.length;
            if (response.data.investment_transactions.length === 0) break;
        }

        res.json({
            investmentTransactions,
            securities: Array.from(securitiesById.values()),
            startDate,
            endDate,
        });
    } catch (e) {
        sendPlaidError(res, e);
    }
});

// 7. Pull liabilities (credit cards, student loans, mortgages) for this item.
app.get('/api/liabilities', async (req, res) => {
    try {
        const accessToken = req.get('x-plaid-access-token');
        if (!accessToken) return res.status(401).json({ error: 'X-Plaid-Access-Token header required' });
        const response = await plaid.liabilitiesGet({ access_token: accessToken });
        res.json({
            accounts: response.data.accounts,
            liabilities: response.data.liabilities,
        });
    } catch (e) {
        sendPlaidError(res, e);
    }
});

// Sandbox-only convenience: fire a webhook to advance the sandbox item so
// transactions show up sooner. Useful while iterating in the simulator.
app.post('/api/sandbox/fire-webhook', async (req, res) => {
    try {
        const accessToken = req.get('x-plaid-access-token');
        if (!accessToken) return res.status(401).json({ error: 'X-Plaid-Access-Token header required' });
        const webhookCode = req.body?.webhookCode ?? 'SYNC_UPDATES_AVAILABLE';
        const response = await plaid.sandboxItemFireWebhook({
            access_token: accessToken,
            webhook_code: webhookCode,
        });
        res.json(response.data);
    } catch (e) {
        sendPlaidError(res, e);
    }
});

// ── Gemini proxy ────────────────────────────────────────────────────────────
//
// The API key lives here, never in the shipped app, where it would be
// trivially extractable from the APK. Requests must carry a Supabase access
// token so the key cannot be spent by anyone who finds the URL.

const AI_MODELS = new Set(['gemini-1.5-flash', 'gemini-1.5-pro']);
const AI_DEFAULT_MODEL = 'gemini-1.5-flash';

// Free tier allows 15 req/min and 1500 req/day across the whole project, so
// cap per user well under that and keep a global daily ceiling as a backstop.
const AI_USER_WINDOW_MS = 5 * 60 * 1000;
const AI_USER_MAX_IN_WINDOW = 20;
const AI_GLOBAL_DAILY_MAX = 1200;

const aiUserHits = new Map(); // userId -> number[] (timestamps)
let aiGlobalDay = null;
let aiGlobalCount = 0;

function aiRateLimit(userId) {
    const now = Date.now();

    const today = new Date().toISOString().slice(0, 10);
    if (aiGlobalDay !== today) {
        aiGlobalDay = today;
        aiGlobalCount = 0;
    }
    if (aiGlobalCount >= AI_GLOBAL_DAILY_MAX) {
        return { ok: false, reason: 'Daily AI limit reached. Try again tomorrow.' };
    }

    const hits = (aiUserHits.get(userId) ?? []).filter(t => now - t < AI_USER_WINDOW_MS);
    if (hits.length >= AI_USER_MAX_IN_WINDOW) {
        return { ok: false, reason: 'Too many AI requests. Try again in a few minutes.' };
    }
    hits.push(now);
    aiUserHits.set(userId, hits);
    aiGlobalCount++;
    return { ok: true };
}

// Opportunistic cleanup so the map does not grow without bound.
setInterval(() => {
    const cutoff = Date.now() - AI_USER_WINDOW_MS;
    for (const [userId, hits] of aiUserHits) {
        const live = hits.filter(t => t > cutoff);
        if (live.length) aiUserHits.set(userId, live);
        else aiUserHits.delete(userId);
    }
}, AI_USER_WINDOW_MS).unref();

/** Resolves the Supabase user for a bearer token, or null if unauthenticated. */
async function resolveUser(req) {
    const header = req.get('authorization') ?? '';
    if (!header.startsWith('Bearer ')) return null;
    const token = header.slice(7).trim();
    if (!token) return null;
    try {
        const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
            headers: { Authorization: `Bearer ${token}`, apikey: SUPABASE_ANON_KEY },
        });
        if (!r.ok) return null;
        const user = await r.json();
        return user?.id ? user : null;
    } catch (e) {
        console.error('Supabase token check failed:', e?.message ?? e);
        return null;
    }
}

// Generates content from Gemini. Body mirrors the Gemini REST shape so a
// multi-turn chat can pass its history straight through:
//   { model?: string, contents: [{ role, parts: [{ text }] }] }
// Prompt bodies are never logged - they carry the user's financial data.
app.post('/api/ai/generate', async (req, res) => {
    if (!aiEnabled) {
        return res.status(503).json({ error: { message: 'AI is not configured on this server.' } });
    }

    const user = await resolveUser(req);
    if (!user) {
        return res.status(401).json({ error: { message: 'Sign in to use AI features.' } });
    }

    const limit = aiRateLimit(user.id);
    if (!limit.ok) {
        return res.status(429).json({ error: { message: limit.reason } });
    }

    const model = AI_MODELS.has(req.body?.model) ? req.body.model : AI_DEFAULT_MODEL;
    const contents = req.body?.contents;
    if (!Array.isArray(contents) || contents.length === 0) {
        return res.status(400).json({ error: { message: 'contents must be a non-empty array.' } });
    }

    try {
        const r = await fetch(
            `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'x-goog-api-key': GEMINI_API_KEY,
                },
                body: JSON.stringify({ contents }),
            }
        );

        if (!r.ok) {
            const detail = await r.text();
            console.error(`Gemini error ${r.status}`); // status only, no prompt
            return res.status(r.status).json({
                error: { message: 'AI request failed.', status: r.status, detail: detail.slice(0, 500) },
            });
        }

        const data = await r.json();
        const text = data?.candidates?.[0]?.content?.parts
            ?.map(p => p.text ?? '')
            .join('') ?? '';
        res.json({ text });
    } catch (e) {
        console.error('Gemini proxy failure:', e?.message ?? e);
        res.status(502).json({ error: { message: 'Could not reach the AI service.' } });
    }
});

function sendPlaidError(res, e) {
    const data = e?.response?.data;
    const status = e?.response?.status ?? 500;
    if (data) {
        console.error('Plaid error:', data);
        res.status(status).json({ error: data });
    } else {
        console.error(e);
        res.status(500).json({ error: { message: e?.message ?? 'unknown error' } });
    }
}

app.listen(PORT, () => {
    console.log(`TrueSummit backend listening on http://localhost:${PORT} (Plaid ${PLAID_ENV})`);
});
