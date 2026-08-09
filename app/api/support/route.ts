import { env } from "cloudflare:workers";

type SupportBody = {
  deviceId?: string;
  name?: string;
  contact?: string;
  message?: string;
};

async function prepareTable() {
  await env.DB.prepare(`
    CREATE TABLE IF NOT EXISTS support_messages (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT NOT NULL,
      name TEXT NOT NULL,
      contact TEXT NOT NULL DEFAULT '',
      message TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'new',
      created_at INTEGER NOT NULL
    )
  `).run();
  await env.DB.prepare(
    "CREATE INDEX IF NOT EXISTS idx_support_created_at ON support_messages(created_at DESC)",
  ).run();
}

function clean(value: unknown, max: number) {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

export async function POST(request: Request) {
  await prepareTable();
  const body = (await request.json().catch(() => ({}))) as SupportBody;
  const deviceId = clean(body.deviceId, 80);
  const name = clean(body.name, 80);
  const contact = clean(body.contact, 160);
  const message = clean(body.message, 2000);
  if (!deviceId || !name || message.length < 5) {
    return Response.json({ error: "Заполните имя и сообщение" }, { status: 400 });
  }

  const since = Date.now() - 60 * 60 * 1000;
  const recent = await env.DB.prepare(
    "SELECT COUNT(*) AS count FROM support_messages WHERE device_id = ? AND created_at > ?",
  ).bind(deviceId, since).first<{ count: number }>();
  if ((recent?.count ?? 0) >= 5) {
    return Response.json({ error: "Слишком много сообщений. Попробуйте через час." }, { status: 429 });
  }

  await env.DB.prepare(
    "INSERT INTO support_messages (device_id, name, contact, message, status, created_at) VALUES (?, ?, ?, ?, 'new', ?)",
  ).bind(deviceId, name, contact, message, Date.now()).run();
  return Response.json({ ok: true });
}

export async function GET(request: Request) {
  const adminKey = request.headers.get("x-admin-key") ?? "";
  if (!env.SUPPORT_ADMIN_KEY || adminKey !== env.SUPPORT_ADMIN_KEY) {
    return Response.json({ error: "Доступ запрещён" }, { status: 403 });
  }
  await prepareTable();
  const result = await env.DB.prepare(
    "SELECT id, name, contact, message, status, created_at AS createdAt FROM support_messages ORDER BY created_at DESC LIMIT 100",
  ).all();
  return Response.json({ messages: result.results });
}
