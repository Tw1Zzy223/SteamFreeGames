type SteamDeal = {
  appId: string;
  title: string;
  image: string;
  steamUrl: string;
};

const STEAM_SEARCH =
  "https://store.steampowered.com/search/results/?query&start=0&count=50&dynamic_data=&sort_by=_ASC&specials=1&maxprice=free&category1=998&supportedlang=russian&infinite=1";

function decodeHtml(value: string) {
  return value
    .replaceAll("&amp;", "&")
    .replaceAll("&quot;", '"')
    .replaceAll("&#039;", "'")
    .replaceAll("&lt;", "<")
    .replaceAll("&gt;", ">");
}

function parseDeals(html: string): SteamDeal[] {
  const rows = html.match(/<a\s+href=[\s\S]*?<\/a>/g) ?? [];

  return rows.flatMap((row) => {
    const appId = row.match(/data-ds-appid="(\d+)"/)?.[1];
    const title = row.match(/<span class="title">([\s\S]*?)<\/span>/)?.[1];
    const image = row.match(/class="search_capsule"><img src="([^"]+)"/)?.[1];
    const discount = row.match(/data-discount="(\d+)"/)?.[1];
    const finalPrice = row.match(/data-price-final="(\d+)"/)?.[1];

    if (!appId || !title || !image || discount !== "100" || finalPrice !== "0") {
      return [];
    }

    return [{
      appId,
      title: decodeHtml(title.trim()),
      image: decodeHtml(image),
      steamUrl: `https://store.steampowered.com/app/${appId}/`,
    }];
  });
}

export async function GET(_request: Request) {
  try {
    const response = await fetch(STEAM_SEARCH, {
      headers: {
        "Accept-Language": "ru-RU,ru;q=0.9",
      },
    });

    if (!response.ok) throw new Error(`Steam responded with ${response.status}`);
    const payload = (await response.json()) as { results_html?: string };
    const deals = parseDeals(payload.results_html ?? "");

    return Response.json(
      { deals, updatedAt: new Date().toISOString(), source: "steam" },
      { headers: { "Cache-Control": "public, max-age=3600, s-maxage=3600" } },
    );
  } catch (error) {
    console.error("Не удалось обновить предложения Steam", error);
    return Response.json(
      {
        deals: [],
        updatedAt: new Date().toISOString(),
        source: "steam",
        error: "Steam временно не отвечает",
      },
      { status: 502 },
    );
  }
}
