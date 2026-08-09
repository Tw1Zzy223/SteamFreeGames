import { requireSteamSession, steamApi, unauthorized } from "../_shared";

export async function GET(request: Request) {
  const session = await requireSteamSession(request);
  if (!session) return unauthorized();
  try {
    const list = await steamApi("/ISteamUser/GetFriendList/v1/", { steamid: session.steamId, relationship: "friend" });
    const friends = (((list.friendslist as { friends?: unknown[] } | undefined)?.friends ?? []) as Record<string, unknown>[]);
    const ids = friends.map((friend) => String(friend.steamid ?? "")).filter(Boolean);
    const profiles: Record<string, unknown>[] = [];
    for (let start = 0; start < ids.length; start += 100) {
      const summary = await steamApi("/ISteamUser/GetPlayerSummaries/v2/", { steamids: ids.slice(start, start + 100).join(",") });
      profiles.push(...((((summary.response as { players?: unknown[] } | undefined)?.players ?? []) as Record<string, unknown>[])));
    }
    profiles.sort((a, b) => Number(b.personastate ?? 0) - Number(a.personastate ?? 0));
    return Response.json({ friends: profiles, total: profiles.length, isPrivate: false });
  } catch (error) {
    const message = error instanceof Error ? error.message : "";
    if (message.includes("401")) return Response.json({ friends: [], total: 0, isPrivate: true });
    return Response.json({ error: message || "Steam временно недоступен" }, { status: 502 });
  }
}
