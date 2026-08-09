export async function GET(request: Request) {
  const url = new URL(request.url);
  const appId = url.searchParams.get("app_id") ?? "";
  if (!/^\d+$/.test(appId)) return Response.json({ error: "Некорректный AppID" }, { status: 400 });
  try {
    const steam = new URL("https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/");
    steam.searchParams.set("appid", appId);
    steam.searchParams.set("count", "10");
    steam.searchParams.set("maxlength", "600");
    steam.searchParams.set("format", "json");
    const response = await fetch(steam, { headers: { "Accept-Language": "ru-RU,ru;q=0.9" } });
    if (!response.ok) throw new Error(`Steam ответил кодом ${response.status}`);
    const data = await response.json() as { appnews?: { newsitems?: unknown[] } };
    return Response.json({ news: data.appnews?.newsitems ?? [] });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Новости недоступны" }, { status: 502 });
  }
}
