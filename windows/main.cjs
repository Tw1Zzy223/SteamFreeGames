const { app, BrowserWindow, ipcMain, shell, Notification, clipboard, Tray, Menu, globalShortcut } = require("electron");
const { autoUpdater } = require("electron-updater");
const path = require("node:path");
const fs = require("node:fs");
const crypto = require("node:crypto");

const VERSION = "0.4.0";
const SERVER = "https://steam-hunter-games.pagrishaevich.chatgpt.site";
const STEAM_SEARCH = "https://store.steampowered.com/search/results/";
let mainWindow;
let pendingSteamToken = null;
let tray = null;
let isQuitting = false;

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) app.quit();

function dataFile() {
  return path.join(app.getPath("userData"), "steam-hunter.json");
}

function defaults() {
  return {
    deviceId: crypto.randomUUID(),
    favorites: [],
    viewed: [],
    searchHistory: [],
    compare: [],
    steamToken: "",
    seenFree: [],
    settings: { theme: "dark", notifications: true, minDiscount: 10, tray: true, autoStart: false, globalHotkeys: true },
  };
}

function readStore() {
  try {
    const saved = JSON.parse(fs.readFileSync(dataFile(), "utf8"));
    return { ...defaults(), ...saved, settings: { ...defaults().settings, ...(saved.settings || {}) } };
  } catch {
    return defaults();
  }
}

function writeStore(next) {
  fs.mkdirSync(path.dirname(dataFile()), { recursive: true });
  fs.writeFileSync(dataFile(), JSON.stringify(next, null, 2), "utf8");
  return next;
}

function updateStore(patch) {
  return writeStore({ ...readStore(), ...patch });
}

function decodeHtml(value = "") {
  return value
    .replace(/<[^>]+>/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#039;|&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&nbsp;/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function match(value, expression) {
  return value.match(expression)?.[1] || "";
}

async function jsonFetch(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      Accept: "application/json",
      "Accept-Language": "ru-RU,ru;q=0.9",
      "User-Agent": `SteamHunter-Windows/${VERSION}`,
      ...(options.headers || {}),
    },
  });
  const text = await response.text();
  let body;
  try { body = JSON.parse(text); } catch { body = { error: text || `Ошибка сервера ${response.status}` }; }
  if (!response.ok) throw new Error(body.error || `Ошибка сервера ${response.status}`);
  return body;
}

async function searchSteam({ mode = "discounts", query = "", start = 0, sort = "Reviews_DESC", tag = "", minimumDiscount = 0 }) {
  const url = new URL(STEAM_SEARCH);
  url.searchParams.set("start", String(start));
  url.searchParams.set("count", "50");
  url.searchParams.set("dynamic_data", "");
  url.searchParams.set("category1", "998");
  url.searchParams.set("infinite", "1");
  url.searchParams.set("cc", "us");
  url.searchParams.set("l", "russian");
  url.searchParams.set("sort_by", sort);
  if (mode === "free" || mode === "weekends") {
    url.searchParams.set("specials", "1");
    url.searchParams.set("maxprice", "free");
  } else if (mode === "discounts" || mode === "best") {
    url.searchParams.set("specials", "1");
  }
  if (query.trim()) url.searchParams.set("term", query.trim());
  if (tag) url.searchParams.set("tags", tag);

  const payload = await jsonFetch(url.toString());
  const rows = String(payload.results_html || "").match(/<a\s+href=[\s\S]*?<\/a>/g) || [];
  const games = [];
  for (const row of rows) {
    const appId = match(row, /data-ds-appid="(\d+)"/);
    const title = decodeHtml(match(row, /<span class="title">([\s\S]*?)<\/span>/));
    const image = match(row, /class="search_capsule"><img src="([^"]+)"/).replace(/&amp;/g, "&");
    const discount = Number(match(row, /data-discount="(\d+)"/) || 0);
    const price = decodeHtml(match(row, /<div class="discount_final_price">([\s\S]*?)<\/div>/)) || "Цена не указана";
    const rowText = decodeHtml(row);
    const freeWeekend = /free weekend|play for free|играть бесплатно|бесплатн(?:ые|ые\s+выходные)|попробовать бесплатно/i.test(rowText);
    if (!appId || !title || !image) continue;
    if (mode === "free" && discount !== 100) continue;
    if (mode === "weekends" && !freeWeekend) continue;
    if (["discounts", "best"].includes(mode) && discount < Number(minimumDiscount || 0)) continue;
    games.push({ appId, title, image, discount, price, freeWeekend, steamUrl: `https://store.steampowered.com/app/${appId}` });
  }
  return { games, total: Number(payload.total_count || 0), start, nextStart: start + 50 };
}

function regionalPrice(data) {
  if (!data) return "Недоступно";
  if (data.is_free) return "Бесплатно";
  const price = data.price_overview;
  if (!price) return "Нет цены";
  return price.discount_percent > 0
    ? `${price.final_formatted} (−${price.discount_percent}%)`
    : price.final_formatted;
}

async function gameDetails(appId) {
  const regions = [["us", "США"], ["de", "Германия"], ["ru", "Россия"], ["kz", "Казахстан"]];
  let mainData = null;
  const prices = {};
  for (const [code, name] of regions) {
    const root = await jsonFetch(`https://store.steampowered.com/api/appdetails?appids=${appId}&cc=${code}&l=russian`);
    const entry = root[appId];
    const data = entry?.success ? entry.data : null;
    if (!mainData && data) mainData = data;
    prices[name] = regionalPrice(data);
  }
  if (!mainData) throw new Error("Steam не вернул подробности игры");
  const reviewRoot = await jsonFetch(`https://store.steampowered.com/appreviews/${appId}?json=1&language=all&purchase_type=all&num_per_page=3`);
  const summary = reviewRoot.query_summary || {};
  const total = Number(summary.total_reviews || 0);
  const positive = Number(summary.total_positive || 0);
  return {
    appId,
    title: mainData.name,
    description: decodeHtml(mainData.short_description || "Описание пока недоступно"),
    image: mainData.header_image || "",
    genres: (mainData.genres || []).map((item) => item.description),
    prices,
    reviews: {
      percent: total ? Math.round((positive * 100) / total) : 0,
      total,
      samples: (reviewRoot.reviews || []).slice(0, 3).map((item) => ({ positive: item.voted_up, text: String(item.review || "").replace(/\s+/g, " ").slice(0, 300) })),
    },
    requirements: {
      minimum: decodeHtml(mainData.pc_requirements?.minimum || "Не указаны"),
      recommended: decodeHtml(mainData.pc_requirements?.recommended || "Не указаны"),
    },
  };
}

function safeExternal(url) {
  if (!/^(https?:\/\/|steam:\/\/)/i.test(url)) throw new Error("Недопустимая ссылка");
  return shell.openExternal(url);
}

function consumeProtocol(url) {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== "steamhunter:" || parsed.hostname !== "steam-auth") return;
    const token = parsed.searchParams.get("token");
    if (!token || !/^[a-f0-9]{64}$/i.test(token)) return;
    updateStore({ steamToken: token });
    pendingSteamToken = token;
    mainWindow?.webContents.send("steam-authenticated", token);
    mainWindow?.show();
    mainWindow?.focus();
  } catch { /* Ссылки других программ игнорируем. */ }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1040,
    minHeight: 680,
    show: false,
    frame: false,
    backgroundColor: "#07100c",
    icon: path.join(__dirname, "icon.png"),
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  mainWindow.loadFile(path.join(__dirname, "index.html"));
  mainWindow.once("ready-to-show", () => mainWindow.show());
  mainWindow.on("close", (event) => {
    if (!isQuitting && readStore().settings.tray) {
      event.preventDefault();
      mainWindow.hide();
      if (Notification.isSupported()) new Notification({ title: "Steam Hunter", body: "Приложение продолжает проверять скидки в системном трее." }).show();
    }
  });
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    safeExternal(url);
    return { action: "deny" };
  });
  mainWindow.webContents.on("will-navigate", (event, url) => {
    if (url !== mainWindow.webContents.getURL()) {
      event.preventDefault();
      safeExternal(url);
    }
  });
  if (pendingSteamToken) mainWindow.webContents.once("did-finish-load", () => mainWindow.webContents.send("steam-authenticated", pendingSteamToken));
}

function createTray() {
  tray = new Tray(path.join(__dirname, "icon.png"));
  tray.setToolTip("Steam Hunter — скидки Steam");
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: "Открыть Steam Hunter", click: () => { mainWindow.show(); mainWindow.focus(); } },
    { label: "Обновить предложения", click: () => mainWindow.webContents.send("shortcut-refresh") },
    { type: "separator" },
    { label: "Выйти", click: () => { isQuitting = true; app.quit(); } },
  ]));
  tray.on("double-click", () => { mainWindow.show(); mainWindow.focus(); });
}

function configureShortcuts() {
  globalShortcut.unregisterAll();
  if (!readStore().settings.globalHotkeys) return;
  globalShortcut.register("CommandOrControl+Shift+F", () => { mainWindow.show(); mainWindow.focus(); mainWindow.webContents.send("shortcut-search"); });
  globalShortcut.register("CommandOrControl+Shift+R", () => { mainWindow.show(); mainWindow.webContents.send("shortcut-refresh"); });
}

function configureUpdater() {
  if (!app.isPackaged) return;
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.on("checking-for-update", () => mainWindow?.webContents.send("update-status", { state: "checking" }));
  autoUpdater.on("update-available", (info) => mainWindow?.webContents.send("update-status", { state: "available", version: info.version }));
  autoUpdater.on("download-progress", (progress) => mainWindow?.webContents.send("update-status", { state: "downloading", percent: Math.round(progress.percent) }));
  autoUpdater.on("update-downloaded", (info) => mainWindow?.webContents.send("update-status", { state: "downloaded", version: info.version }));
  autoUpdater.on("update-not-available", () => mainWindow?.webContents.send("update-status", { state: "current" }));
  autoUpdater.on("error", (error) => mainWindow?.webContents.send("update-status", { state: "error", message: error.message }));
  setTimeout(() => autoUpdater.checkForUpdates().catch(() => {}), 8000);
}

function notifyNewFreeGames() {
  const store = readStore();
  if (!store.settings.notifications) return;
  searchSteam({ mode: "free" }).then(({ games }) => {
    const known = new Set(store.seenFree || []);
    const fresh = games.filter((game) => !known.has(game.appId));
    if (store.seenFree?.length && fresh.length && Notification.isSupported()) {
      new Notification({ title: "Steam Hunter", body: `${fresh[0].title} можно забрать бесплатно!`, icon: path.join(__dirname, "icon.png") }).show();
    }
    updateStore({ seenFree: games.map((game) => game.appId) });
  }).catch(() => {});
}

app.on("second-instance", (_event, argv) => {
  const protocol = argv.find((value) => value.startsWith("steamhunter://"));
  if (protocol) consumeProtocol(protocol);
  if (mainWindow) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
  }
});
app.on("open-url", (event, url) => { event.preventDefault(); consumeProtocol(url); });

app.whenReady().then(() => {
  app.setAppUserModelId("com.steamhunter.desktop");
  app.setAsDefaultProtocolClient("steamhunter");
  createWindow();
  createTray();
  app.setLoginItemSettings({ openAtLogin: Boolean(readStore().settings.autoStart), openAsHidden: true });
  configureShortcuts();
  configureUpdater();
  const protocol = process.argv.find((value) => value.startsWith("steamhunter://"));
  if (protocol) consumeProtocol(protocol);
  notifyNewFreeGames();
  setInterval(notifyNewFreeGames, 60 * 60 * 1000).unref();
});

app.on("window-all-closed", () => app.quit());
app.on("will-quit", () => globalShortcut.unregisterAll());

ipcMain.handle("window-action", (_event, action) => {
  if (action === "minimize") mainWindow.minimize();
  if (action === "maximize") mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize();
  if (action === "close") mainWindow.close();
});
ipcMain.handle("catalog", (_event, options) => searchSteam(options || {}));
ipcMain.handle("details", (_event, appId) => gameDetails(String(appId)));
ipcMain.handle("external", (_event, url) => safeExternal(String(url)));
ipcMain.handle("copy", (_event, value) => { clipboard.writeText(String(value)); return true; });
ipcMain.handle("open-steam", (_event, appId) => safeExternal(`steam://store/${String(appId).replace(/\D/g, "")}`));
ipcMain.handle("store-read", () => readStore());
ipcMain.handle("store-write", (_event, patch) => updateStore(patch || {}));
ipcMain.handle("steam-login", async () => {
  const store = readStore();
  await safeExternal(`${SERVER}/api/steam/login?device_id=${encodeURIComponent(store.deviceId)}`);
  return true;
});
ipcMain.handle("steam-me", async () => {
  const token = readStore().steamToken;
  if (!token) return null;
  return jsonFetch(`${SERVER}/api/steam/me`, { headers: { Authorization: `Bearer ${token}` } });
});
ipcMain.handle("steam-friends", async () => {
  const token = readStore().steamToken;
  if (!token) return { friends: [], isPrivate: false };
  return jsonFetch(`${SERVER}/api/steam/friends`, { headers: { Authorization: `Bearer ${token}` } });
});
ipcMain.handle("steam-library", async () => {
  const token = readStore().steamToken;
  if (!token) return { games: [], total: 0 };
  return jsonFetch(`${SERVER}/api/steam/library`, { headers: { Authorization: `Bearer ${token}` } });
});
ipcMain.handle("steam-achievements", async (_event, appId) => {
  const token = readStore().steamToken;
  if (!token) return { achievements: [], unlocked: 0, total: 0 };
  return jsonFetch(`${SERVER}/api/steam/achievements?app_id=${encodeURIComponent(String(appId))}`, { headers: { Authorization: `Bearer ${token}` } });
});
ipcMain.handle("steam-news", (_event, appId) => jsonFetch(`${SERVER}/api/steam/news?app_id=${encodeURIComponent(String(appId))}`));
ipcMain.handle("price-history", (_event, appId, region = "us") => jsonFetch(`${SERVER}/api/price-history?app_id=${encodeURIComponent(String(appId))}&region=${encodeURIComponent(String(region))}`));
ipcMain.handle("sync-pull", async () => {
  const token = readStore().steamToken;
  if (!token) return { data: {}, updatedAt: 0 };
  return jsonFetch(`${SERVER}/api/steam/sync`, { headers: { Authorization: `Bearer ${token}` } });
});
ipcMain.handle("sync-push", async (_event, data) => {
  const token = readStore().steamToken;
  if (!token) throw new Error("Сначала подключите Steam");
  return jsonFetch(`${SERVER}/api/steam/sync`, { method: "PUT", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ data }) });
});
ipcMain.handle("steam-logout", async () => {
  const token = readStore().steamToken;
  if (token) await jsonFetch(`${SERVER}/api/steam/me`, { method: "DELETE", headers: { Authorization: `Bearer ${token}` } }).catch(() => {});
  updateStore({ steamToken: "" });
  return true;
});
ipcMain.handle("support-send", (_event, payload) => jsonFetch(`${SERVER}/api/support`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ ...payload, deviceId: readStore().deviceId }),
}));
ipcMain.handle("support-messages", (_event, adminKey) => jsonFetch(`${SERVER}/api/support`, { headers: { "X-Admin-Key": String(adminKey || "") } }));
ipcMain.handle("desktop-settings", (_event, settings) => {
  const next = updateStore({ settings: { ...readStore().settings, ...(settings || {}) } });
  app.setLoginItemSettings({ openAtLogin: Boolean(next.settings.autoStart), openAsHidden: true });
  configureShortcuts();
  return next;
});
ipcMain.handle("check-update", async () => {
  if (app.isPackaged) {
    const result = await autoUpdater.checkForUpdates();
    return { current: VERSION, latest: result?.updateInfo?.version ?? VERSION, automatic: true };
  }
  const release = await jsonFetch("https://api.github.com/repos/Tw1Zzy223/SteamFreeGames/releases/latest");
  return { current: VERSION, latest: String(release.tag_name || "").replace(/^v/, ""), url: release.html_url, automatic: false };
});
ipcMain.handle("install-update", () => { if (app.isPackaged) autoUpdater.quitAndInstall(false, true); return true; });
