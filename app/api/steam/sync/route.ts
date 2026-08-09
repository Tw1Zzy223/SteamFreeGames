import { env } from "cloudflare:workers";
import { prepareSteamTables, requireSteamSession, unauthorized } from "../_shared";

export async function GET(request: Request) {
  const session = await requireSteamSession(request);
  if (!session) return unauthorized();
  await prepareSteamTables();
  const row = await env.DB.prepare("SELECT payload, updated_at AS updatedAt FROM steam_sync WHERE steam_id = ?")
    .bind(session.steamId).first<{ payload: string; updatedAt: number }>();
  if (!row) return Response.json({ data: {}, updatedAt: 0 });
  try { return Response.json({ data: JSON.parse(row.payload), updatedAt: row.updatedAt }); }
  catch { return Response.json({ data: {}, updatedAt: row.updatedAt }); }
}

export async function PUT(request: Request) {
  const session = await requireSteamSession(request);
  if (!session) return unauthorized();
  const body = await request.json().catch(() => null) as { data?: unknown } | null;
  if (!body || !body.data || typeof body.data !== "object" || Array.isArray(body.data)) {
    return Response.json({ error: "Некорректные данные синхронизации" }, { status: 400 });
  }
  const payload = JSON.stringify(body.data);
  if (payload.length > 200_000) return Response.json({ error: "Слишком много данных" }, { status: 413 });
  const now = Date.now();
  await prepareSteamTables();
  await env.DB.prepare(`INSERT INTO steam_sync (steam_id, payload, updated_at) VALUES (?, ?, ?)
    ON CONFLICT(steam_id) DO UPDATE SET payload = excluded.payload, updated_at = excluded.updated_at`)
    .bind(session.steamId, payload, now).run();
  return Response.json({ ok: true, updatedAt: now });
}
