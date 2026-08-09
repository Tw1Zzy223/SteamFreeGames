const api = window.steamHunter;
const content = document.querySelector("#content");
const modalLayer = document.querySelector("#modal-layer");
const modal = document.querySelector("#modal");

const viewInfo = {
  free: ["БЕСПЛАТНО", "Можно забрать бесплатно", "Только временные скидки 100%, без обычных Free-to-Play."],
  discounts: ["СКИДКИ", "Все игры со скидкой", "Ищите игру, выбирайте размер скидки и открывайте её прямо в Steam."],
  all: ["КАТАЛОГ", "Все игры Steam", "Полный каталог подгружается страницами, чтобы программа оставалась быстрой."],
  weekends: ["БЕСПЛАТНЫЙ ДОСТУП", "Бесплатные выходные", "Играть можно временно: такие игры не остаются в библиотеке навсегда."],
  best: ["ЛУЧШЕЕ", "Лучшие предложения", "Самые заметные скидки с высокими оценками пользователей Steam."],
  favorites: ["МОЯ БИБЛИОТЕКА", "Избранные игры", "Сохранённые игры остаются на этом компьютере и доступны без сети."],
  viewed: ["ИСТОРИЯ", "Просмотренные игры", "Карточки, которые вы недавно открывали."],
  compare: ["СРАВНЕНИЕ", "Сравнить игры", "Цены, отзывы и требования рядом — удобно выбирать перед покупкой."],
  sales: ["КАЛЕНДАРЬ", "Распродажи и фестивали", "Ближайшие официально объявленные события Steam с обратным отсчётом."],
  news: ["НОВОСТИ", "Новости избранных игр", "Последние обновления игр, которые вы добавили в избранное."],
  support: ["ПОМОЩЬ", "Поддержка Steam Hunter", "Отправьте сообщение разработчику или откройте сообщения владельца."],
  settings: ["ПАРАМЕТРЫ", "Настройки Windows-версии", "Тема, уведомления, обновления и локальные данные приложения."],
};

const sales = [
  ["Фестиваль фишек и шариков", "Фестиваль", "2026-08-17", "2026-08-20"],
  ["Фестиваль PvE-выживания", "Фестиваль", "2026-08-31", "2026-09-07"],
  ["Фестиваль программирования", "Фестиваль", "2026-09-10", "2026-09-14"],
  ["Фестиваль партийных RPG", "Фестиваль", "2026-09-14", "2026-09-21"],
  ["Осенняя распродажа Steam", "Большая распродажа", "2026-10-01", "2026-10-08"],
  ["Кулинарный фестиваль", "Фестиваль", "2026-10-12", "2026-10-19"],
  ["Steam Next Fest", "Фестиваль демоверсий", "2026-10-19", "2026-10-26"],
  ["Steam Scream V", "Фестиваль", "2026-10-26", "2026-11-02"],
  ["Зимняя распродажа Steam", "Большая распродажа", "2026-12-17", "2027-01-04"],
  ["Весенняя распродажа Steam", "Большая распродажа", "2027-03-18", "2027-03-25"],
  ["Steam Next Fest: июнь", "Фестиваль демоверсий", "2027-06-14", "2027-06-21"],
  ["Летняя распродажа Steam", "Большая распродажа", "2027-06-24", "2027-07-08"],
];

let store = null;
let state = { view: "free", games: [], total: 0, start: 0, loading: false, query: "", discount: 0, genre: "", sort: "Reviews_DESC" };

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);
}

function toast(message) {
  const item = document.createElement("div");
  item.className = "toast";
  item.textContent = message;
  document.querySelector("#toasts").append(item);
  setTimeout(() => item.remove(), 3500);
}

function showError(error) {
  toast(error?.message || String(error) || "Произошла ошибка");
}

async function save(patch) {
  store = await api.writeStore(patch);
  updateCounters();
  return store;
}

function updateCounters() {
  document.querySelector("#favorite-count").textContent = store?.favorites?.length || 0;
  document.querySelector("#compare-count").textContent = store?.compare?.length || 0;
}

function setTheme(theme) {
  document.body.classList.toggle("theme-light", theme === "light");
  document.body.classList.toggle("theme-dark", theme !== "light");
}

function setHeading(view) {
  const [kicker, title, subtitle] = viewInfo[view];
  document.querySelector("#section-kicker").textContent = kicker;
  document.querySelector("#section-title").textContent = title;
  document.querySelector("#section-subtitle").textContent = subtitle;
  const catalogView = ["free", "discounts", "all", "best", "weekends"].includes(view);
  document.querySelector("#filterbar").classList.toggle("hidden", !catalogView);
  document.querySelector("#refresh").classList.toggle("hidden", !catalogView);
  document.querySelector("#search").placeholder = catalogView ? "Найти игру в Steam…" : "Поиск доступен в каталоге";
  document.querySelector("#search").disabled = !catalogView;
}

function catalogMode() {
  return ["free", "discounts", "all", "best", "weekends"].includes(state.view) ? state.view : "discounts";
}

async function loadCatalog(reset = true) {
  if (state.loading) return;
  state.loading = true;
  if (reset) {
    state.start = 0;
    state.games = [];
    content.innerHTML = '<div class="loading-panel"><div class="spinner"></div><p>Получаем данные Steam…</p></div>';
  }
  document.querySelector("#refresh").textContent = "⟳";
  try {
    const result = await api.catalog({
      mode: catalogMode(), query: state.query, start: state.start, sort: state.sort,
      tag: state.genre, minimumDiscount: state.view === "best" ? Math.max(70, state.discount) : state.discount,
    });
    state.games = reset ? result.games : [...state.games, ...result.games];
    state.total = result.total;
    state.start = result.nextStart;
    const cachedCatalog = { ...(store.cachedCatalog || {}), [state.view]: { games: state.games, total: state.total, savedAt: Date.now() } };
    await save({ cachedCatalog });
    renderGames();
    if (state.view === "free") {
      document.querySelector("#free-count").textContent = state.games.length;
    }
  } catch (error) {
    const cached = store.cachedCatalog?.[state.view];
    if (cached?.games?.length) {
      state.games = cached.games;
      state.total = cached.total;
      renderGames();
      toast("Нет связи со Steam — показаны сохранённые данные");
    } else {
      content.innerHTML = `<div class="empty"><h2>Не удалось загрузить игры</h2><p>${escapeHtml(error.message)}</p><button class="primary" id="retry">Повторить</button></div>`;
      document.querySelector("#retry").onclick = () => loadCatalog(true);
    }
  } finally {
    state.loading = false;
    document.querySelector("#refresh").textContent = "↻";
  }
}

function gameCard(game) {
  const favorite = store.favorites.some((item) => item.appId === game.appId);
  const compared = store.compare.some((item) => item.appId === game.appId);
  const free = game.discount === 100;
  return `<article class="game-card" data-appid="${game.appId}">
    <div class="cover"><img src="${escapeHtml(game.image)}" alt="Обложка ${escapeHtml(game.title)}" loading="lazy">
      ${game.discount ? `<span class="badge">−${game.discount}%</span>` : ""}
      <div class="card-tools"><button class="favorite ${favorite ? "active" : ""}" title="В избранное">${favorite ? "♥" : "♡"}</button><button class="compare ${compared ? "active" : ""}" title="Сравнить">⇄</button></div>
    </div>
    <div class="card-body"><span class="card-label">${game.freeWeekend ? "БЕСПЛАТНЫЕ ВЫХОДНЫЕ · ВРЕМЕННЫЙ ДОСТУП" : free ? "МОЖНО ЗАБРАТЬ БЕСПЛАТНО" : game.discount ? `СКИДКА ${game.discount}%` : "ИГРА STEAM"}</span>
      <h3 title="${escapeHtml(game.title)}">${escapeHtml(game.title)}</h3>
      <div class="price-line"><span>Цена сейчас</span><strong>${free ? "Бесплатно" : escapeHtml(game.price)}</strong></div>
      <div class="card-actions"><button class="details-btn">Подробнее</button><button class="steam-btn" title="Открыть в Steam">↗</button></div>
    </div></article>`;
}

function renderGames(list = state.games) {
  document.querySelector("#result-count").textContent = `Найдено в Steam: ${state.total.toLocaleString("ru-RU")}`;
  if (!list.length) {
    content.innerHTML = '<div class="empty"><h2>Ничего не найдено</h2><p>Измените запрос или фильтры и попробуйте ещё раз.</p></div>';
    return;
  }
  content.innerHTML = `<div class="games-grid">${list.map(gameCard).join("")}</div>${state.games.length < state.total ? '<button class="load-more" id="load-more">Загрузить ещё 50 игр</button>' : ""}`;
  bindCards(list);
  const more = document.querySelector("#load-more");
  if (more) more.onclick = () => loadCatalog(false);
}

function bindCards(list) {
  content.querySelectorAll(".game-card").forEach((card) => {
    const game = list.find((item) => item.appId === card.dataset.appid);
    card.querySelector(".details-btn").onclick = () => openDetails(game);
    card.querySelector(".steam-btn").onclick = () => api.openSteam(game.appId).catch(showError);
    card.querySelector(".favorite").onclick = () => toggleFavorite(game);
    card.querySelector(".compare").onclick = () => toggleCompare(game);
  });
}

async function toggleFavorite(game) {
  const exists = store.favorites.some((item) => item.appId === game.appId);
  await save({ favorites: exists ? store.favorites.filter((item) => item.appId !== game.appId) : [game, ...store.favorites] });
  toast(exists ? "Удалено из избранного" : "Добавлено в избранное");
  renderCurrent();
}

async function toggleCompare(game) {
  const exists = store.compare.some((item) => item.appId === game.appId);
  if (!exists && store.compare.length >= 3) return toast("Можно сравнивать не больше трёх игр");
  await save({ compare: exists ? store.compare.filter((item) => item.appId !== game.appId) : [...store.compare, game] });
  toast(exists ? "Убрано из сравнения" : "Добавлено к сравнению");
  renderCurrent();
}

function openModal(html) {
  modal.innerHTML = `<button class="modal-close" aria-label="Закрыть">×</button>${html}`;
  modalLayer.classList.remove("hidden");
  modal.querySelector(".modal-close").onclick = closeModal;
}

function closeModal() { modalLayer.classList.add("hidden"); modal.innerHTML = ""; }
modalLayer.addEventListener("click", (event) => { if (event.target === modalLayer) closeModal(); });

async function openDetails(game) {
  openModal('<div class="loading-panel"><div class="spinner"></div><p>Загружаем карточку игры…</p></div>');
  try {
    const [details, history] = await Promise.all([api.details(game.appId), api.priceHistory(game.appId, "us").catch(() => ({ points: [] }))]);
    const viewed = [game, ...store.viewed.filter((item) => item.appId !== game.appId)].slice(0, 100);
    await save({ viewed });
    openModal(`<div class="detail-hero"><img src="${escapeHtml(details.image)}" alt=""><div class="detail-title"><h2>${escapeHtml(details.title)}</h2><p>${escapeHtml(details.genres.join(" • ") || "Жанр не указан")}</p></div></div>
      <div class="detail-body"><p class="detail-description">${escapeHtml(details.description)}</p>
      <div class="price-grid">${Object.entries(details.prices).map(([region, price]) => `<div class="price-box"><small>${escapeHtml(region)}</small><strong>${escapeHtml(price)}</strong></div>`).join("")}</div>
      ${renderPriceHistory(history.points || [])}
      <h3>Отзывы Steam</h3><div class="review-score">${details.reviews.percent}% положительных <small>из ${details.reviews.total.toLocaleString("ru-RU")}</small></div>
      ${details.reviews.samples.map((review) => `<div class="review">${review.positive ? "👍" : "👎"} ${escapeHtml(review.text)}</div>`).join("")}
      <h3>Системные требования</h3><p><strong>Минимальные:</strong> ${escapeHtml(details.requirements.minimum)}</p><p><strong>Рекомендуемые:</strong> ${escapeHtml(details.requirements.recommended)}</p>
      <div class="detail-actions"><button class="primary" id="detail-steam">Открыть в Steam</button><button class="secondary" id="detail-favorite">${store.favorites.some((item) => item.appId === game.appId) ? "Убрать из избранного" : "В избранное"}</button><button class="secondary" id="detail-share">Поделиться</button></div></div>`);
    document.querySelector("#detail-steam").onclick = () => api.openSteam(game.appId);
    document.querySelector("#detail-favorite").onclick = async () => { await toggleFavorite(game); closeModal(); };
    document.querySelector("#detail-share").onclick = async () => {
      await api.copy(`${details.title} — ${game.discount ? `скидка ${game.discount}%` : "в Steam"}: ${game.steamUrl}`);
      toast("Ссылка скопирована");
    };
  } catch (error) { closeModal(); showError(error); }
}

function renderPriceHistory(points) {
  if (!points.length) return '<h3>История цены</h3><p class="detail-description">Первая точка цены сохранена. График появится после следующих проверок.</p>';
  const max = Math.max(...points.map((item) => Number(item.initialCents || item.finalCents || 1)), 1);
  const last = points[points.length - 1];
  return `<h3>История цены · США</h3><div class="price-chart">${points.slice(-30).map((item) => `<i title="${new Date(item.capturedAt).toLocaleString("ru-RU")}" style="height:${Math.max(5, Math.round(Number(item.finalCents || 0) / max * 90))}px"></i>`).join("")}</div><p class="detail-description">Последняя цена: ${(Number(last.finalCents || 0) / 100).toFixed(2)} ${escapeHtml(last.currency)}</p>`;
}

function renderLocalGames(kind) {
  const list = kind === "favorites" ? store.favorites : store.viewed;
  if (!list.length) {
    content.innerHTML = `<div class="empty"><h2>${kind === "favorites" ? "Избранное пока пусто" : "История пока пустая"}</h2><p>Откройте карточку игры или нажмите сердечко в каталоге.</p></div>`;
  } else {
    content.innerHTML = `<div class="games-grid">${list.map(gameCard).join("")}</div>`;
    bindCards(list);
  }
}

async function renderCompare() {
  if (!store.compare.length) {
    content.innerHTML = '<div class="empty"><h2>Добавьте до трёх игр</h2><p>Нажмите ⇄ на карточках в каталоге.</p></div>';
    return;
  }
  content.innerHTML = '<div class="loading-panel"><div class="spinner"></div><p>Собираем сравнение…</p></div>';
  try {
    const details = await Promise.all(store.compare.map((game) => api.details(game.appId)));
    content.innerHTML = `<div class="dashboard-grid">${details.map((game, index) => `<article class="info-card"><img src="${escapeHtml(game.image)}" style="width:100%;border-radius:10px"><h2>${escapeHtml(game.title)}</h2><div class="review-score">${game.reviews.percent}%</div><p>${escapeHtml(game.genres.join(", "))}</p><h3>Цена в США</h3><p>${escapeHtml(game.prices["США"])}</p><h3>Рекомендуемые требования</h3><p>${escapeHtml(game.requirements.recommended)}</p><button class="danger remove-compare" data-index="${index}">Убрать</button></article>`).join("")}</div>`;
    content.querySelectorAll(".remove-compare").forEach((button) => button.onclick = async () => { await save({ compare: store.compare.filter((_item, index) => index !== Number(button.dataset.index)) }); renderCompare(); });
  } catch (error) { showError(error); }
}

function countdown(start, end) {
  const now = Date.now();
  if (now >= start && now <= end) return "Идёт сейчас";
  const days = Math.max(0, Math.ceil((start - now) / 86400000));
  return days ? `Через ${days} дн.` : "Сегодня";
}

function renderSales() {
  const now = Date.now();
  const current = sales.filter((item) => new Date(`${item[3]}T23:59:00+05:00`).getTime() >= now);
  content.innerHTML = `<div class="dashboard-grid">${current.map(([title, type, start, end]) => {
    const date = new Date(`${start}T22:00:00+05:00`);
    return `<article class="info-card sale-card"><div class="date-box"><strong>${date.getDate()}</strong><small>${date.toLocaleDateString("ru-RU", { month: "short" })}</small></div><div><strong>${escapeHtml(title)}</strong><small>${escapeHtml(type)} · ${new Date(start).toLocaleDateString("ru-RU")} — ${new Date(end).toLocaleDateString("ru-RU")}</small></div><span class="countdown">${countdown(date.getTime(), new Date(`${end}T23:59:00+05:00`).getTime())}</span></article>`;
  }).join("")}</div>`;
}

async function renderNews() {
  content.innerHTML = '<div class="loading-panel"><div class="spinner"></div><p>Собираем новости игр…</p></div>';
  try {
    const sources = store.favorites.slice(0, 6);
    if (!sources.length) {
      content.innerHTML = '<div class="empty"><h2>Добавьте игры в избранное</h2><p>После этого здесь появятся новости выбранных игр.</p></div>';
      return;
    }
    const groups = await Promise.all(sources.map(async (game) => ({ game, news: (await api.steamNews(game.appId)).news || [] })));
    const items = groups.flatMap(({ game, news }) => news.map((item) => ({ ...item, game }))).sort((a, b) => Number(b.date || 0) - Number(a.date || 0)).slice(0, 30);
    content.innerHTML = `<div class="dashboard-grid">${items.map((item) => `<article class="info-card news-card"><span class="card-label">${escapeHtml(item.game.title)}</span><h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.contents || "")}</p><small>${new Date(Number(item.date) * 1000).toLocaleDateString("ru-RU")}</small><button class="secondary news-open" data-url="${escapeHtml(item.url)}">Открыть новость</button></article>`).join("")}</div>`;
    content.querySelectorAll(".news-open").forEach((button) => button.onclick = () => api.external(button.dataset.url));
  } catch (error) { content.innerHTML = `<div class="empty"><h2>Новости временно недоступны</h2><p>${escapeHtml(error.message)}</p></div>`; }
}

function renderSupport() {
  content.innerHTML = `<div class="support-layout"><article class="info-card"><h2>Написать разработчику</h2><p>Опишите проблему или предложите новую функцию.</p><form class="form" id="support-form"><input name="name" required maxlength="50" placeholder="Ваше имя"><input name="contact" maxlength="100" placeholder="Контакт для ответа (необязательно)"><textarea name="message" required maxlength="2000" placeholder="Сообщение"></textarea><button class="primary">Отправить</button></form></article>
    <article class="info-card"><h2>Сообщения пользователей</h2><p>Этот раздел предназначен только для владельца Steam Hunter.</p><form class="form" id="admin-form"><input name="key" type="password" required placeholder="Код владельца"><button class="secondary">Открыть сообщения</button></form><div id="admin-messages"></div></article></div>`;
  document.querySelector("#support-form").onsubmit = async (event) => {
    event.preventDefault(); const form = new FormData(event.target);
    try { await api.supportSend({ name: form.get("name"), contact: form.get("contact"), message: form.get("message") }); event.target.reset(); toast("Сообщение отправлено"); } catch (error) { showError(error); }
  };
  document.querySelector("#admin-form").onsubmit = async (event) => {
    event.preventDefault(); const key = new FormData(event.target).get("key");
    try { const result = await api.supportMessages(key); document.querySelector("#admin-messages").innerHTML = (result.messages || []).map((item) => `<div class="review"><strong>${escapeHtml(item.name)}</strong> · ${new Date(item.createdAt).toLocaleString("ru-RU")}<br>${escapeHtml(item.contact)}<p>${escapeHtml(item.message)}</p></div>`).join("") || "<p>Новых сообщений нет.</p>"; } catch (error) { showError(error); }
  };
}

function renderSettings() {
  const light = store.settings.theme === "light";
  const notifications = store.settings.notifications;
  content.innerHTML = `<div class="settings-grid"><article class="info-card"><h2>Оформление</h2><div class="setting-row"><div><strong>Светлая тема</strong><p>Переключить зелёно-чёрный интерфейс на светлый.</p></div><button class="switch ${light ? "on" : ""}" id="theme-switch"></button></div><div class="setting-row"><div><strong>Анимация запуска</strong><p>Показывается только при запуске программы.</p></div><span>Включена</span></div></article>
    <article class="info-card"><h2>Уведомления</h2><div class="setting-row"><div><strong>Бесплатные игры</strong><p>Windows сообщит о новой скидке 100%.</p></div><button class="switch ${notifications ? "on" : ""}" id="notification-switch"></button></div><div class="setting-row"><div><strong>Проверка</strong><p>Каталог обновляется каждый час, пока приложение запущено.</p></div><span>1 час</span></div></article>
    <article class="info-card"><h2>Обновления</h2><p>Установщик новой версии можно скачать без удаления программы.</p><button class="primary" id="check-update">Проверить обновление</button><p id="update-result"></p></article>
    <article class="info-card"><h2>Локальные данные</h2><p>Избранное: ${store.favorites.length} · Просмотрено: ${store.viewed.length} · История поиска: ${store.searchHistory.length}</p><button class="danger" id="clear-history">Очистить историю и просмотренные</button><p>Версия Windows: ${api.version}</p></article></div>`;
  document.querySelector("#theme-switch").onclick = async () => { const theme = light ? "dark" : "light"; await save({ settings: { ...store.settings, theme } }); setTheme(theme); renderSettings(); };
  document.querySelector("#notification-switch").onclick = async () => { await save({ settings: { ...store.settings, notifications: !notifications } }); renderSettings(); };
  document.querySelector("#clear-history").onclick = async () => { await save({ viewed: [], searchHistory: [] }); renderSettings(); toast("История очищена"); };
  document.querySelector("#check-update").onclick = async () => {
    const box = document.querySelector("#update-result"); box.textContent = "Проверяем…";
    try { const result = await api.checkUpdate(); box.innerHTML = result.latest === result.current ? "Установлена последняя версия." : `Доступна версия ${escapeHtml(result.latest)}. <button class="secondary" id="open-release">Скачать</button>`; if (result.latest !== result.current) document.querySelector("#open-release").onclick = () => api.external(result.url); } catch (error) { box.textContent = error.message; }
  };
}

function enhanceDesktopSettings() {
  const grid = content.querySelector(".settings-grid");
  if (!grid) return;
  grid.insertAdjacentHTML("beforeend", `<article class="info-card"><h2>Работа в Windows</h2>
    <div class="setting-row"><div><strong>Системный трей</strong><p>Кнопка закрытия прячет Steam Hunter возле часов.</p></div><button class="switch ${store.settings.tray ? "on" : ""}" id="tray-switch"></button></div>
    <div class="setting-row"><div><strong>Запуск вместе с Windows</strong><p>Автоматически искать новые предложения после входа.</p></div><button class="switch ${store.settings.autoStart ? "on" : ""}" id="autostart-switch"></button></div>
    <div class="setting-row"><div><strong>Глобальные клавиши</strong><p>Ctrl+Shift+F — поиск, Ctrl+Shift+R — обновить.</p></div><button class="switch ${store.settings.globalHotkeys ? "on" : ""}" id="hotkeys-switch"></button></div></article>`);
  const changeDesktop = async (key) => {
    store = await api.desktopSettings({ [key]: !store.settings[key] });
    renderSettings(); enhanceDesktopSettings();
  };
  document.querySelector("#tray-switch").onclick = () => changeDesktop("tray");
  document.querySelector("#autostart-switch").onclick = () => changeDesktop("autoStart");
  document.querySelector("#hotkeys-switch").onclick = () => changeDesktop("globalHotkeys");
  document.querySelector("#check-update").onclick = async () => {
    const box = document.querySelector("#update-result"); box.textContent = "Проверяем обновление…";
    try { await api.checkUpdate(); } catch (error) { box.textContent = error.message; }
  };
}

function renderCurrent() {
  if (["free", "discounts", "all", "best", "weekends"].includes(state.view)) return renderGames();
  if (["favorites", "viewed"].includes(state.view)) return renderLocalGames(state.view);
  if (state.view === "compare") return renderCompare();
  if (state.view === "sales") return renderSales();
  if (state.view === "news") return renderNews();
  if (state.view === "support") return renderSupport();
  if (state.view === "settings") { renderSettings(); enhanceDesktopSettings(); }
}

async function switchView(view) {
  state.view = view;
  document.querySelectorAll("#main-nav button").forEach((button) => button.classList.toggle("active", button.dataset.view === view));
  setHeading(view);
  if (["free", "discounts", "all", "best", "weekends"].includes(view)) await loadCatalog(true); else renderCurrent();
}

document.querySelectorAll("[data-window]").forEach((button) => button.onclick = () => api.windowAction(button.dataset.window));
document.querySelectorAll("#main-nav button[data-view]").forEach((button) => button.onclick = () => switchView(button.dataset.view));
document.querySelector("#refresh").onclick = () => loadCatalog(true);
document.querySelector("#discount-filters").onclick = (event) => {
  const button = event.target.closest("button"); if (!button) return;
  document.querySelectorAll("#discount-filters button").forEach((item) => item.classList.toggle("active", item === button));
  state.discount = Number(button.dataset.discount); loadCatalog(true);
};
document.querySelector("#genre").onchange = (event) => { state.genre = event.target.value; loadCatalog(true); };
document.querySelector("#sort").onchange = (event) => { state.sort = event.target.value; loadCatalog(true); };
document.querySelector("#search").onkeydown = async (event) => {
  if (event.key !== "Enter") return;
  state.query = event.target.value.trim();
  if (state.query) await save({ searchHistory: [state.query, ...store.searchHistory.filter((item) => item !== state.query)].slice(0, 20) });
  await loadCatalog(true);
};
api.onShortcutSearch(() => { switchView("discounts"); const search = document.querySelector("#search"); search.disabled = false; search.focus(); });
api.onShortcutRefresh(() => { if (["free", "discounts", "all", "best", "weekends"].includes(state.view)) loadCatalog(true); else switchView("free"); });
api.onUpdateStatus((status) => {
  const box = document.querySelector("#update-result");
  if (!box) return;
  if (status.state === "checking") box.textContent = "Проверяем обновление…";
  if (status.state === "available") box.textContent = `Найдена версия ${status.version}. Начинаем загрузку…`;
  if (status.state === "downloading") box.textContent = `Загрузка обновления: ${status.percent}%`;
  if (status.state === "current") box.textContent = "Установлена последняя версия.";
  if (status.state === "error") box.textContent = `Ошибка обновления: ${status.message}`;
  if (status.state === "downloaded") { box.innerHTML = `Версия ${escapeHtml(status.version)} готова. <button class="primary" id="install-ready-update">Перезапустить и установить</button>`; document.querySelector("#install-ready-update").onclick = () => api.installUpdate(); }
});

async function init() {
  store = await api.readStore();
  setTheme(store.settings.theme);
  updateCounters();
  await loadCatalog(true);
  setTimeout(() => document.querySelector("#splash").classList.add("hidden"), 900);
}

init().catch((error) => { document.querySelector("#splash").classList.add("hidden"); showError(error); });
