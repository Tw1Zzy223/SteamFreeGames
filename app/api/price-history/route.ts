import { env } from "cloudflare:workers";
import { prepareSteamTables } from "../steam/_shared";

const REGIONS = new Set(["us", "de", "ru", "kz"]);

export async function GET(request: Request) {
  const url = new URL(request.url);
  const appId = url.searchParams.get("app_id") ?? "";
  const region = (url.searchParams.get("region") ?? "us").toLowerCase();
  if (!/^\d+$/.test(appId) || !REGIONS.has(region)) return Response.json({ error: "Некорректные параметры" }, { status: 400 });
  await prepareSteamTables();
  const latest = await env.DB.prepare("SELECT captured_at AS capturedAt FROM price_history WHERE app_id = ? AND region = ? ORDER BY captured_at DESC LIMIT 1")
    .bind(appId, region).first<{ capturedAt: number }>();
  if (!latest || Date.now() - latest.capturedAt > 60 * 60 * 1000) {
    try {
      const response = await fetch(`https://store.steampowered.com/api/appdetails?appids=${appId}&cc=${region}&l=russian`);
      const root = await response.json() as Record<string, { success?: boolean; data?: { is_free?: boolean; price_overview?: { currency?: string; initial?: number; final?: number; discount_percent?: number } } }>;
      const data = root[appId]?.data;
      const price = data?.price_overview;
      const nowHour = Math.floor(Date.now() / 3_600_000) * 3_600_000;
      if (data?.is_free || price) {
        await env.DB.prepare(`INSERT OR IGNORE INTO price_history
          (app_id, region, currency, initial_cents, final_cents, discount_percent, captured_at) VALUES (?, ?, ?, ?, ?, ?, ?)`)
          .bind(appId, region, price?.currency ?? "FREE", price?.initial ?? 0, price?.final ?? 0, price?.discount_percent ?? (data?.is_free ? 100 : 0), nowHour).run();
      }
    } catch { /* Старые точки всё равно можно показать. */ }
  }
  const result = await env.DB.prepare(`SELECT currency, initial_cents AS initialCents, final_cents AS finalCents,
    discount_percent AS discountPercent, captured_at AS capturedAt FROM price_history
    WHERE app_id = ? AND region = ? ORDER BY captured_at DESC LIMIT 180`).bind(appId, region).all();
  return Response.json({ appId, region, points: result.results.reverse() });
}
