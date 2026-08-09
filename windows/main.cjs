const { app, BrowserWindow, ipcMain, shell, Notification, clipboard } = require("electron");
const path = require("node:path");
const fs = require("node:fs");
const crypto = require("node:crypto");

const VERSION = "0.1.0";
const SERVER = "https://steam-hunter-games.pagrishaevich.chatgpt.site";
const STEAM_SEARCH = "https://store.steampowered.com/search/results/";
let mainWindow;

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
    seenFree: [],
    settings: { theme: "dark", notifications: true, minDiscount: 10 },
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
  if (mode === "free") {
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
    if (!appId || !title || !image) continue;
    if (mode === "free" && discount !== 100) continue;
    if (["discounts", "best"].includes(mode) && discount < Number(minimumDiscount || 0)) continue;
    games.push({ appId, title, image, discount, price, steamUrl: `https://store.steampowered.com/app/${appId}` });
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

app.on("second-instance", () => {
  if (mainWindow) {
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.show();
    mainWindow.focus();
  }
});

app.whenReady().then(() => {
  app.setAppUserModelId("com.steamhunter.desktop");
  createWindow();
  notifyNewFreeGames();
  setInterval(notifyNewFreeGames, 60 * 60 * 1000).unref();
});

app.on("window-all-closed", () => app.quit());

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
ipcMain.handle("support-send", (_event, payload) => jsonFetch(`${SERVER}/api/support`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ ...payload, deviceId: readStore().deviceId }),
}));
ipcMain.handle("support-messages", (_event, adminKey) => jsonFetch(`${SERVER}/api/support`, { headers: { "X-Admin-Key": String(adminKey || "") } }));
ipcMain.handle("check-update", async () => {
  const release = await jsonFetch("https://api.github.com/repos/Tw1Zzy223/SteamFreeGames/releases/latest");
  return { current: VERSION, latest: String(release.tag_name || "").replace(/^v/, ""), url: release.html_url };
});
