"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

type Deal = {
  appId: string;
  title: string;
  image: string;
  steamUrl: string;
};

type DealsResponse = {
  deals: Deal[];
  updatedAt: string;
  source: "steam" | "demo";
  error?: string;
};

const HOUR = 60 * 60 * 1000;
const NOTIFICATION_KEY = "steam-hunter-notifications";
const SEEN_KEY = "steam-hunter-seen-games";

function formatUpdateTime(value: string | null) {
  if (!value) return "ещё не обновлялось";
  return new Intl.DateTimeFormat("ru-RU", {
    hour: "2-digit",
    minute: "2-digit",
    day: "numeric",
    month: "long",
  }).format(new Date(value));
}

export function DealsDashboard() {
  const [deals, setDeals] = useState<Deal[]>([]);
  const [updatedAt, setUpdatedAt] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [query, setQuery] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const [notificationsEnabled, setNotificationsEnabled] = useState(false);

  const loadDeals = useCallback(async (manual = false) => {
    manual ? setRefreshing(true) : setLoading(true);
    try {
      const response = await fetch(`/api/deals${manual ? "?refresh=1" : ""}`);
      if (!response.ok) throw new Error("Не удалось получить список");
      const data = (await response.json()) as DealsResponse;
      setDeals(data.deals);
      setUpdatedAt(data.updatedAt);

      const notificationsOn = localStorage.getItem(NOTIFICATION_KEY) === "on";
      const seen = JSON.parse(localStorage.getItem(SEEN_KEY) || "[]") as string[];
      const newDeals = seen.length
        ? data.deals.filter((deal) => !seen.includes(deal.appId))
        : [];

      if (notificationsOn && "Notification" in window && Notification.permission === "granted") {
        newDeals.slice(0, 3).forEach((deal) => {
          new Notification("Новая бесплатная игра в Steam!", {
            body: `${deal.title} можно забрать со скидкой 100%.`,
            icon: deal.image,
          });
        });
      }

      localStorage.setItem(
        SEEN_KEY,
        JSON.stringify(data.deals.map((deal) => deal.appId)),
      );
      setNotice(
        manual
          ? data.deals.length
            ? "Список обновлён по данным Steam"
            : "Новых раздач сейчас не найдено"
          : data.error || null,
      );
    } catch {
      setNotice("Не получилось связаться со Steam. Попробуем ещё раз позже.");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    setNotificationsEnabled(localStorage.getItem(NOTIFICATION_KEY) === "on");
    loadDeals();
    const timer = window.setInterval(() => loadDeals(), HOUR);
    return () => window.clearInterval(timer);
  }, [loadDeals]);

  async function enableNotifications() {
    if (!("Notification" in window)) {
      setNotice("Этот браузер не поддерживает уведомления.");
      return;
    }

    if (notificationsEnabled) {
      localStorage.removeItem(NOTIFICATION_KEY);
      setNotificationsEnabled(false);
      setNotice("Уведомления выключены");
      return;
    }

    const permission = await Notification.requestPermission();
    if (permission === "granted") {
      localStorage.setItem(NOTIFICATION_KEY, "on");
      setNotificationsEnabled(true);
      setNotice("Готово! Сообщим, когда появится новая раздача.");
    } else {
      setNotice("Браузер не разрешил уведомления.");
    }
  }

  function explainSteamLogin() {
    setNotice(
      "Безопасное подключение Steam добавим через Steam OpenID. Пароль от Steam здесь вводить не нужно.",
    );
  }

  const visibleDeals = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("ru");
    if (!normalized) return deals;
    return deals.filter((deal) =>
      deal.title.toLocaleLowerCase("ru").includes(normalized),
    );
  }, [deals, query]);

  return (
    <main>
      <nav className="topbar" aria-label="Главная навигация">
        <a className="brand" href="#top" aria-label="Steam Hunter — на главную">
          <span className="brand-mark">S</span>
          <span>STEAM HUNTER</span>
        </a>
        <div className="nav-actions">
          <button type="button" className="ghost-button" onClick={enableNotifications}>
            <span className="notification-dot" aria-hidden="true" />
            {notificationsEnabled ? "Уведомления включены" : "Включить уведомления"}
          </button>
          <button type="button" className="steam-login" onClick={explainSteamLogin}>
            Подключить Steam
          </button>
        </div>
      </nav>

      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="eyebrow"><span /> Ловим скидки, пока они не исчезли</p>
          <h1>Игры, которые<br />можно забрать <em>бесплатно</em></h1>
          <p className="hero-description">
            Только настоящие скидки 100% в Steam. Никаких обычных Free-to-Play —
            только игры, которые временно отдают бесплатно.
          </p>
          <div className="hero-actions">
            <a className="primary-button" href="#deals">Смотреть раздачи <span>↓</span></a>
            <span className="update-label">
              <i aria-hidden="true" /> Обновляем каждый час
            </span>
          </div>
        </div>
        <div className="radar" aria-hidden="true">
          <div className="radar-ring ring-one" />
          <div className="radar-ring ring-two" />
          <div className="radar-ring ring-three" />
          <div className="radar-sweep" />
          <div className="radar-cross horizontal" />
          <div className="radar-cross vertical" />
          <div className="radar-center"><span>100%</span><small>БЕСПЛАТНО</small></div>
          <span className="radar-blip blip-one" />
          <span className="radar-blip blip-two" />
          <span className="radar-blip blip-three" />
        </div>
      </section>

      <section className="deals-section" id="deals">
        <div className="section-heading">
          <div>
            <p className="section-kicker">АКТИВНЫЕ ПРЕДЛОЖЕНИЯ</p>
            <h2>Можно забрать прямо сейчас</h2>
          </div>
          <div className="section-tools">
            <label className="search-box">
              <span aria-hidden="true">⌕</span>
              <span className="sr-only">Поиск игры</span>
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Найти игру..."
              />
            </label>
            <button
              type="button"
              className="refresh-button"
              onClick={() => loadDeals(true)}
              disabled={refreshing}
            >
              <span className={refreshing ? "spinning" : ""}>↻</span>
              {refreshing ? "Проверяем..." : "Обновить"}
            </button>
          </div>
        </div>

        <div className="status-row">
          <span><i /> Данные Steam</span>
          <span>Последняя проверка: {formatUpdateTime(updatedAt)}</span>
        </div>

        {notice && (
          <div className="notice" role="status">
            <span>{notice}</span>
            <button type="button" onClick={() => setNotice(null)} aria-label="Закрыть сообщение">×</button>
          </div>
        )}

        {loading ? (
          <div className="cards-grid" aria-label="Загрузка игр">
            {[1, 2].map((item) => <div className="game-card skeleton" key={item} />)}
          </div>
        ) : visibleDeals.length ? (
          <div className="cards-grid">
            {visibleDeals.map((deal) => (
              <article className="game-card" key={deal.appId}>
                <div className="game-image-wrap">
                  <img src={deal.image} alt={`Обложка игры ${deal.title}`} width="460" height="215" />
                  <span className="discount-badge">−100%</span>
                </div>
                <div className="game-info">
                  <p className="free-label"><span>✓</span> МОЖНО ЗАБРАТЬ БЕСПЛАТНО</p>
                  <h3>{deal.title}</h3>
                  <a
                    className="claim-button"
                    href={deal.steamUrl}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Забрать в Steam <span>↗</span>
                  </a>
                  <p className="safe-note">Покупка подтверждается на сайте Steam</p>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="empty-state">
            <span>◎</span>
            <h3>{query ? "Такой игры в раздачах нет" : "Радар пока ничего не поймал"}</h3>
            <p>{query ? "Попробуйте изменить запрос." : "Оставьте уведомления включёнными — сообщим о новой скидке 100%."}</p>
          </div>
        )}
      </section>

      <footer>
        <span>Steam Hunter не связан с Valve Corporation.</span>
        <span>Никаких паролей · Только безопасные ссылки Steam</span>
      </footer>
    </main>
  );
}
