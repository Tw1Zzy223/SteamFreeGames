import assert from "node:assert/strict";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);
  return worker.fetch(
    new Request("http://localhost/", { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("показывает готовую главную страницу Steam Hunter", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);
  const html = await response.text();
  assert.match(html, /Steam Hunter/);
  assert.match(html, /Можно забрать прямо сейчас/);
  assert.match(html, /Включить уведомления/);
  assert.doesNotMatch(html, /Подключить Steam|Steam-аккаунт|Войти через Steam/);
});

test("не публикует маршруты подключения Steam", async () => {
  const login = await render();
  const html = await login.text();
  assert.doesNotMatch(html, /\/api\/steam\/(login|callback|me|friends|library|achievements|sync)/);
});
