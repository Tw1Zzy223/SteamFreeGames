import { requireSteamSession, steamApi, unauthorized } from "../_shared";

export async function GET(request: Request) {
  const session = await requireSteamSession(request);
  if (!session) return unauthorized();
  try {
    const data = await steamApi("/IPlayerService/GetOwnedGames/v1/", {
      steamid: session.steamId,
      include_appinfo: "true",
      include_played_free_games: "true",
      format: "json",
    });
    const response = (data.response ?? {}) as { game_count?: number; games?: Record<string, unknown>[] };
    const games = (response.games ?? []).sort((a, b) => Number(b.playtime_forever ?? 0) - Number(a.playtime_forever ?? 0));
    return Response.json({ games, total: response.game_count ?? games.length, isPrivate: false });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Steam временно недоступен";
    if (message.includes("401") || message.includes("403")) return Response.json({ games: [], total: 0, isPrivate: true });
    return Response.json({ error: message }, { status: 502 });
  }
}
