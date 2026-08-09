import { env } from "cloudflare:workers";
import { prepareSteamTables, randomToken, tokenHash } from "../_shared";

function page(title: string, body: string, link = "") {
  const action = link ? `<a href="${link}">Вернуться в Steam Hunter</a><script>location.href=${JSON.stringify(link)}</script>` : "";
  return new Response(`<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>${title}</title><style>body{background:#07110c;color:#f4faf6;font:16px sans-serif;display:grid;place-items:center;min-height:100vh;margin:0}.card{max-width:520px;padding:30px;background:#10251a;border:1px solid #397451;border-radius:18px}h1,a{color:#c2ff42}a{display:inline-block;margin-top:16px;font-weight:700}</style><div class="card"><h1>${title}</h1><p>${body}</p>${action}</div></html>`, { headers: { "content-type": "text/html; charset=utf-8" } });
}

export async function GET(request: Request) {
  await prepareSteamTables();
  const url = new URL(request.url);
  const deviceId = (url.searchParams.get("device_id") ?? "").slice(0, 100);
  const nonce = url.searchParams.get("nonce") ?? "";
  const state = await env.DB.prepare(
    "SELECT device_id AS deviceId, created_at AS createdAt FROM steam_auth_states WHERE nonce = ?",
  ).bind(nonce).first<{ deviceId: string; createdAt: number }>();
  if (!state || state.deviceId !== deviceId || Date.now() - state.createdAt > 10 * 60 * 1000) {
    return page("Ссылка устарела", "Начните подключение Steam ещё раз в приложении.");
  }

  const verification = new URLSearchParams();
  for (const [key, value] of url.searchParams) {
    if (key.startsWith("openid.")) verification.set(key, value);
  }
  verification.set("openid.mode", "check_authentication");
  const response = await fetch("https://steamcommunity.com/openid/login", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: verification.toString(),
  });
  const result = await response.text();
  const claimed = url.searchParams.get("openid.claimed_id") ?? "";
  const steamId = claimed.match(/^https:\/\/steamcommunity\.com\/openid\/id\/(\d{17})$/)?.[1];
  if (!result.includes("is_valid:true") || !steamId) {
    return page("Вход не подтверждён", "Steam не подтвердил авторизацию.");
  }

  const token = randomToken();
  const hash = await tokenHash(token);
  const now = Date.now();
  await env.DB.batch([
    env.DB.prepare("DELETE FROM steam_auth_states WHERE nonce = ?").bind(nonce),
    env.DB.prepare("DELETE FROM steam_sessions WHERE device_id = ?").bind(deviceId),
    env.DB.prepare("INSERT INTO steam_sessions (token_hash, device_id, steam_id, created_at, last_used_at) VALUES (?, ?, ?, ?, ?)")
      .bind(hash, deviceId, steamId, now, now),
  ]);
  const deepLink = `steamhunter://steam-auth?token=${token}`;
  return page("Steam подключён", "Аккаунт подтверждён официальной страницей Steam.", deepLink);
}
