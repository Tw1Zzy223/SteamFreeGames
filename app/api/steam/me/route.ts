import { requireSteamSession, steamApi, unauthorized } from "../_shared";

export async function GET(request: Request) {
  const session = await requireSteamSession(request);
  if (!session) return unauthorized();
  try {
    const data = await steamApi("/ISteamUser/GetPlayerSummaries/v2/", { steamids: session.steamId });
    const players = ((data.response as { players?: unknown[] } | undefined)?.players ?? []) as Record<string, unknown>[];
    return Response.json({ profile: players[0] ?? { steamid: session.steamId } });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Steam временно недоступен" }, { status: 502 });
  }
}

export async function DELETE(request: Request) {
  const authorization = request.headers.get("authorization") ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  const session = await requireSteamSession(request);
  if (!session) return unauthorized();
  const { env } = await import("cloudflare:workers");
  const { tokenHash } = await import("../_shared");
  await env.DB.prepare("DELETE FROM steam_sessions WHERE token_hash = ?").bind(await tokenHash(token)).run();
  return Response.json({ ok: true });
}
