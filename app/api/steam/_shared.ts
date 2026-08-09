import { env } from "cloudflare:workers";

export type SteamSession = { steamId: string; deviceId: string };

export async function prepareSteamTables() {
  await env.DB.batch([
    env.DB.prepare(`CREATE TABLE IF NOT EXISTS steam_auth_states (
      nonce TEXT PRIMARY KEY,
      device_id TEXT NOT NULL,
      created_at INTEGER NOT NULL
    )`),
    env.DB.prepare(`CREATE TABLE IF NOT EXISTS steam_sessions (
      token_hash TEXT PRIMARY KEY,
      device_id TEXT NOT NULL,
      steam_id TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      last_used_at INTEGER NOT NULL
    )`),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS idx_steam_auth_created ON steam_auth_states(created_at)"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS idx_steam_sessions_device ON steam_sessions(device_id)"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS idx_steam_sessions_steam ON steam_sessions(steam_id)"),
  ]);
}

export function randomToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export async function tokenHash(token: string) {
  const data = new TextEncoder().encode(token);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

export async function requireSteamSession(request: Request): Promise<SteamSession | null> {
  const authorization = request.headers.get("authorization") ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  if (!/^[a-f0-9]{64}$/.test(token)) return null;
  await prepareSteamTables();
  const hash = await tokenHash(token);
  const session = await env.DB.prepare(
    "SELECT steam_id AS steamId, device_id AS deviceId FROM steam_sessions WHERE token_hash = ?",
  ).bind(hash).first<SteamSession>();
  if (session) {
    await env.DB.prepare("UPDATE steam_sessions SET last_used_at = ? WHERE token_hash = ?")
      .bind(Date.now(), hash).run();
  }
  return session;
}

export async function steamApi(path: string, parameters: Record<string, string>) {
  if (!env.STEAM_WEB_API_KEY) throw new Error("Steam API не настроен");
  const url = new URL(`https://api.steampowered.com${path}`);
  for (const [key, value] of Object.entries(parameters)) url.searchParams.set(key, value);
  const response = await fetch(url, {
    headers: { "x-webapi-key": env.STEAM_WEB_API_KEY, "Accept-Language": "ru-RU,ru;q=0.9" },
  });
  if (!response.ok) throw new Error(`Steam API ответил кодом ${response.status}`);
  return response.json() as Promise<Record<string, unknown>>;
}

export function unauthorized() {
  return Response.json({ error: "Сначала войдите через Steam" }, { status: 401 });
}
