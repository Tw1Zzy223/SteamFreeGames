import { env } from "cloudflare:workers";
import { prepareSteamTables, randomToken } from "../_shared";

export async function GET(request: Request) {
  await prepareSteamTables();
  const requestUrl = new URL(request.url);
  const deviceId = (requestUrl.searchParams.get("device_id") ?? "").trim().slice(0, 100);
  if (!deviceId) return Response.json({ error: "Не указано устройство" }, { status: 400 });

  const nonce = randomToken();
  const now = Date.now();
  await env.DB.batch([
    env.DB.prepare("DELETE FROM steam_auth_states WHERE created_at < ?").bind(now - 10 * 60 * 1000),
    env.DB.prepare("INSERT INTO steam_auth_states (nonce, device_id, created_at) VALUES (?, ?, ?)")
      .bind(nonce, deviceId, now),
  ]);

  const origin = requestUrl.origin;
  const returnTo = `${origin}/api/steam/callback?device_id=${encodeURIComponent(deviceId)}&nonce=${nonce}`;
  const steam = new URL("https://steamcommunity.com/openid/login");
  steam.searchParams.set("openid.ns", "http://specs.openid.net/auth/2.0");
  steam.searchParams.set("openid.mode", "checkid_setup");
  steam.searchParams.set("openid.return_to", returnTo);
  steam.searchParams.set("openid.realm", origin);
  steam.searchParams.set("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select");
  steam.searchParams.set("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");
  return Response.redirect(steam.toString(), 302);
}
