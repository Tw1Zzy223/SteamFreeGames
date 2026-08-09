package com.steamhunter.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BLACK = Color.rgb(5, 8, 6);
    private static final int PANEL = Color.rgb(11, 24, 18);
    private static final int FOREST = Color.rgb(17, 52, 40);
    private static final int ACID = Color.rgb(185, 255, 44);
    private static final int SOFT_GREEN = Color.rgb(137, 181, 104);
    private static final int WHITE = Color.rgb(239, 247, 242);
    private static final String TAB_FREE = "free";
    private static final String TAB_DISCOUNTS = "discounts";
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final String CHANNEL_ID = "steam_hunter_deals";
    private static final String PREFS = "steam_hunter_preferences";
    private static final String SEEN_IDS = "seen_game_ids";
    private static final String SEARCH_BASE = "https://store.steampowered.com/search/results/";

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout gamesContainer;
    private LinearLayout searchPanel;
    private TextView statusText;
    private TextView sectionTitle;
    private ProgressBar progressBar;
    private Button refreshButton;
    private Button freeTabButton;
    private Button discountsTabButton;
    private Button loadMoreButton;
    private EditText searchInput;
    private String currentTab = TAB_FREE;
    private String currentQuery = "";
    private int currentStart = 0;
    private int loadedCount = 0;
    private int totalCount = 0;

    private final Runnable hourlyUpdate = new Runnable() {
        @Override public void run() {
            loadDeals(true, false);
            handler.postDelayed(this, HOUR_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        setContentView(createScreen());
        selectTab(TAB_FREE);
        handler.postDelayed(hourlyUpdate, HOUR_MS);
    }

    private View createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BLACK);

        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(22), dp(18), dp(42));
        page.addView(text("◉  STEAM HUNTER", 18, ACID, Typeface.BOLD));

        TextView eyebrow = text("РАДАР ИГРОВЫХ СКИДОК", 11, SOFT_GREEN, Typeface.BOLD);
        page.addView(eyebrow, margins(-1, -2, 0, 32, 0, 8));

        TextView title = text("Находим игры дешевле", 35, WHITE, Typeface.BOLD);
        title.setLineSpacing(0, 0.92f);
        page.addView(title);
        TextView description = text("Бесплатные раздачи и все действующие скидки Steam в одном приложении.", 15, SOFT_GREEN, Typeface.NORMAL);
        description.setLineSpacing(dp(3), 1f);
        page.addView(description, margins(-1, -2, 0, 14, 0, 22));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        freeTabButton = button("Бесплатно", PANEL, ACID);
        discountsTabButton = button("Скидки", PANEL, ACID);
        freeTabButton.setOnClickListener(v -> selectTab(TAB_FREE));
        discountsTabButton.setOnClickListener(v -> selectTab(TAB_DISCOUNTS));
        tabs.addView(freeTabButton, weighted(48, 0));
        tabs.addView(discountsTabButton, weighted(48, 8));
        page.addView(tabs);

        searchPanel = new LinearLayout(this);
        searchPanel.setOrientation(LinearLayout.HORIZONTAL);
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Например, Mortal Kombat 1");
        searchInput.setHintTextColor(Color.rgb(91, 118, 102));
        searchInput.setTextColor(WHITE);
        searchInput.setTextSize(14);
        searchInput.setPadding(dp(13), 0, dp(10), 0);
        searchInput.setBackground(rounded(PANEL, 9, FOREST));
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performSearch(); return true; }
            return false;
        });
        searchPanel.addView(searchInput, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button searchButton = button("Найти", ACID, BLACK);
        searchButton.setOnClickListener(v -> performSearch());
        searchPanel.addView(searchButton, margins(86, 50, 8, 0, 0, 0));
        page.addView(searchPanel, margins(-1, -2, 0, 14, 0, 0));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        refreshButton = button("Обновить", FOREST, WHITE);
        refreshButton.setOnClickListener(v -> loadDeals(true, true));
        tools.addView(refreshButton, weighted(46, 0));
        Button notifications = button("Уведомления", PANEL, ACID);
        notifications.setOnClickListener(v -> requestNotifications());
        tools.addView(notifications, weighted(46, 8));
        page.addView(tools, margins(-1, -2, 0, 14, 0, 0));

        sectionTitle = text("Бесплатно прямо сейчас", 23, WHITE, Typeface.BOLD);
        page.addView(sectionTitle, margins(-1, -2, 0, 26, 0, 6));
        statusText = text("Подключаемся к Steam…", 12, SOFT_GREEN, Typeface.NORMAL);
        page.addView(statusText, margins(-1, -2, 0, 0, 0, 14));

        progressBar = new ProgressBar(this);
        page.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(38)));
        gamesContainer = vertical();
        page.addView(gamesContainer);

        loadMoreButton = button("Загрузить ещё", FOREST, ACID);
        loadMoreButton.setOnClickListener(v -> loadDeals(false, false));
        page.addView(loadMoreButton, margins(-1, 50, 0, 8, 0, 0));
        loadMoreButton.setVisibility(View.GONE);

        scroll.addView(page);
        return scroll;
    }

    private void selectTab(String tab) {
        currentTab = tab;
        currentQuery = "";
        searchInput.setText("");
        boolean free = TAB_FREE.equals(tab);
        freeTabButton.setBackground(rounded(free ? ACID : PANEL, 9, free ? ACID : FOREST));
        freeTabButton.setTextColor(free ? BLACK : ACID);
        discountsTabButton.setBackground(rounded(free ? PANEL : ACID, 9, free ? FOREST : ACID));
        discountsTabButton.setTextColor(free ? ACID : BLACK);
        searchPanel.setVisibility(free ? View.GONE : View.VISIBLE);
        sectionTitle.setText(free ? "Бесплатно прямо сейчас" : "Все скидки Steam");
        loadDeals(true, false);
    }

    private void performSearch() {
        currentQuery = searchInput.getText().toString().trim();
        sectionTitle.setText(currentQuery.isEmpty() ? "Все скидки Steam" : "Результаты: «" + currentQuery + "»");
        loadDeals(true, false);
    }

    private void loadDeals(boolean reset, boolean manual) {
        if (reset) { currentStart = 0; loadedCount = 0; }
        progressBar.setVisibility(View.VISIBLE);
        refreshButton.setEnabled(false);
        loadMoreButton.setEnabled(false);
        statusText.setText(manual ? "Проверяем Steam…" : "Загружаем предложения…");
        String tabAtRequest = currentTab;
        String queryAtRequest = currentQuery;
        int startAtRequest = currentStart;

        executor.execute(() -> {
            try {
                SearchResult result = fetchDeals(tabAtRequest, queryAtRequest, startAtRequest);
                runOnUiThread(() -> {
                    if (tabAtRequest.equals(currentTab) && queryAtRequest.equals(currentQuery)) showDeals(result, reset);
                });
            } catch (Exception error) {
                runOnUiThread(this::showError);
            }
        });
    }

    private SearchResult fetchDeals(String tab, String query, int start) throws Exception {
        StringBuilder url = new StringBuilder(SEARCH_BASE)
                .append("?start=").append(start)
                .append("&count=50&dynamic_data=&sort_by=_ASC&specials=1&category1=998&supportedlang=russian&infinite=1");
        if (TAB_FREE.equals(tab)) url.append("&maxprice=free");
        if (!query.isEmpty()) url.append("&term=").append(URLEncoder.encode(query, StandardCharsets.UTF_8.name()));

        HttpURLConnection connection = (HttpURLConnection) new URL(url.toString()).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9");
        connection.setRequestProperty("User-Agent", "SteamHunter-Android/0.2");
        if (connection.getResponseCode() != 200) throw new IllegalStateException("Steam ответил кодом " + connection.getResponseCode());

        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        } finally { connection.disconnect(); }

        JSONObject payload = new JSONObject(json.toString());
        String html = payload.optString("results_html", "");
        int total = payload.optInt("total_count", 0);
        Matcher rows = Pattern.compile("<a\\s+href=[\\s\\S]*?</a>").matcher(html);
        List<Deal> deals = new ArrayList<>();

        while (rows.find()) {
            String row = rows.group();
            String appId = match(row, "data-ds-appid=\"(\\d+)\"");
            String title = match(row, "<span class=\"title\">([\\s\\S]*?)</span>");
            String image = match(row, "class=\"search_capsule\"><img src=\"([^\"]+)\"");
            String discountText = match(row, "data-discount=\"(\\d+)\"");
            String finalPrice = match(row, "data-price-final=\"(\\d+)\"");
            if (appId == null || title == null || image == null || discountText == null) continue;
            int discount = Integer.parseInt(discountText);
            boolean accepted = TAB_FREE.equals(tab) ? discount == 100 && "0".equals(finalPrice) : discount > 0;
            if (accepted) deals.add(new Deal(appId, decodeHtml(title.trim()), decodeHtml(image), discount));
        }
        return new SearchResult(deals, total);
    }

    private String match(String source, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String decodeHtml(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

    private void showDeals(SearchResult result, boolean reset) {
        progressBar.setVisibility(View.GONE);
        refreshButton.setEnabled(true);
        loadMoreButton.setEnabled(true);
        if (reset) gamesContainer.removeAllViews();
        loadedCount += result.deals.size();
        totalCount = result.totalCount;
        currentStart += 50;
        String time = DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("ru", "RU")).format(new Date());
        statusText.setText("Показано: " + loadedCount + " из " + totalCount + "  ·  Проверено в " + time);

        if (TAB_FREE.equals(currentTab)) notifyAboutNewDeals(result.deals);
        if (result.deals.isEmpty() && reset) {
            String message = currentQuery.isEmpty()
                    ? "Сейчас подходящих предложений не найдено."
                    : "«" + currentQuery + "» сейчас не участвует в скидках Steam.";
            TextView empty = text(message, 17, SOFT_GREEN, Typeface.BOLD);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(56), dp(18), dp(56));
            gamesContainer.addView(empty);
        } else {
            for (Deal deal : result.deals) gamesContainer.addView(createDealCard(deal));
        }
        loadMoreButton.setVisibility(TAB_DISCOUNTS.equals(currentTab) && loadedCount < totalCount ? View.VISIBLE : View.GONE);
    }

    private View createDealCard(Deal deal) {
        LinearLayout card = vertical();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(rounded(PANEL, 14, FOREST));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(FOREST);
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(150)));
        loadImage(deal.imageUrl, image);

        String badgeText = deal.discount == 100 ? "−100%  МОЖНО ЗАБРАТЬ БЕСПЛАТНО" : "−" + deal.discount + "%  СКИДКА В STEAM";
        TextView badge = text(badgeText, 11, ACID, Typeface.BOLD);
        card.addView(badge, margins(-1, -2, 0, 15, 0, 7));
        card.addView(text(deal.title, 24, WHITE, Typeface.BOLD));

        Button open = button("Открыть в Steam  ↗", ACID, BLACK);
        open.setOnClickListener(v -> openSteam(deal.appId));
        card.addView(open, margins(-1, 48, 0, 15, 0, 0));
        card.setLayoutParams(margins(-1, -2, 0, 0, 0, 15));
        return card;
    }

    private void loadImage(String imageUrl, ImageView imageView) {
        executor.execute(() -> {
            try (InputStream stream = new URL(imageUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                runOnUiThread(() -> imageView.setImageBitmap(bitmap));
            } catch (Exception ignored) { }
        });
    }

    private void openSteam(String appId) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/app/" + appId + "/")));
    }

    private void notifyAboutNewDeals(List<Deal> deals) {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> seen = new HashSet<>(preferences.getStringSet(SEEN_IDS, new HashSet<>()));
        boolean firstCheck = seen.isEmpty();
        for (Deal deal : deals) {
            if (!firstCheck && !seen.contains(deal.appId)) sendNotification(deal);
            seen.add(deal.appId);
        }
        preferences.edit().putStringSet(SEEN_IDS, seen).apply();
    }

    private void sendNotification(Deal deal) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/app/" + deal.appId + "/"));
        PendingIntent pending = PendingIntent.getActivity(this, Integer.parseInt(deal.appId), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(android.R.drawable.star_big_on)
                .setContentTitle("Новая бесплатная игра!")
                .setContentText(deal.title + " можно забрать со скидкой 100%")
                .setContentIntent(pending).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(Integer.parseInt(deal.appId), notification);
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        } else { statusText.setText("Уведомления уже включены"); }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Новые бесплатные игры", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Сообщает о новых играх Steam со скидкой 100%");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void showError() {
        progressBar.setVisibility(View.GONE);
        refreshButton.setEnabled(true);
        loadMoreButton.setEnabled(true);
        statusText.setText("Не получилось связаться со Steam. Попробуйте обновить позже.");
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value); button.setTextColor(foreground); button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setAllCaps(false);
        button.setBackground(rounded(background, 9, background));
        return button;
    }

    private GradientDrawable rounded(int background, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(background); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), border);
        return drawable;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        int actualWidth = width > 0 ? dp(width) : width;
        int actualHeight = height > 0 ? dp(height) : height;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(actualWidth, actualHeight);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams weighted(int height, int left) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1f);
        params.setMargins(dp(left), 0, 0, 0);
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        handler.removeCallbacks(hourlyUpdate); executor.shutdownNow(); super.onDestroy();
    }

    private static class Deal {
        final String appId; final String title; final String imageUrl; final int discount;
        Deal(String appId, String title, String imageUrl, int discount) {
            this.appId = appId; this.title = title; this.imageUrl = imageUrl; this.discount = discount;
        }
    }

    private static class SearchResult {
        final List<Deal> deals; final int totalCount;
        SearchResult(List<Deal> deals, int totalCount) { this.deals = deals; this.totalCount = totalCount; }
    }
}
