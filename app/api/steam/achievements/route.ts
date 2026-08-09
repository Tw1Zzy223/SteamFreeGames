import { requireSteamSession, steamApi, unauthorized } from "../_shared";

export async function GET(request: Request) {
  const session = await requireSteamSession(request);
  if (!session) return unauthorized();
  const appId = new URL(request.url).searchParams.get("app_id") ?? "";
  if (!/^\d+$/.test(appId)) return Response.json({ error: "Некорректный AppID" }, { status: 400 });
  try {
    const data = await steamApi("/ISteamUserStats/GetPlayerAchievements/v1/", {
      steamid: session.steamId,
      appid: appId,
      l: "russian",
    });
    const stats = (data.playerstats ?? {}) as { success?: boolean; gameName?: string; achievements?: Record<string, unknown>[] };
    const achievements = stats.achievements ?? [];
    return Response.json({ gameName: stats.gameName ?? "", achievements, unlocked: achievements.filter((item) => Number(item.achieved ?? 0) === 1).length, total: achievements.length });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Достижения недоступны";
    if (message.includes("400") || message.includes("403")) return Response.json({ gameName: "", achievements: [], unlocked: 0, total: 0, isPrivate: true });
    return Response.json({ error: message }, { status: 502 });
  }
}
