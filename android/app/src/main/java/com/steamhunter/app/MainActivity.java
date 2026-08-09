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
import android.widget.Button;
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
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final String CHANNEL_ID = "steam_hunter_deals";
    private static final String PREFS = "steam_hunter_preferences";
    private static final String SEEN_IDS = "seen_game_ids";
    private static final String STEAM_SEARCH = "https://store.steampowered.com/search/results/?query&start=0&count=50&dynamic_data=&sort_by=_ASC&specials=1&maxprice=free&category1=998&supportedlang=russian&infinite=1";

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout gamesContainer;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button refreshButton;

    private final Runnable hourlyUpdate = new Runnable() {
        @Override
        public void run() {
            loadDeals(false);
            handler.postDelayed(this, HOUR_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        setContentView(createScreen());
        loadDeals(false);
        handler.postDelayed(hourlyUpdate, HOUR_MS);
    }

    private View createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 248, 246));

        LinearLayout page = vertical();
        page.setPadding(dp(20), dp(22), dp(20), dp(36));

        page.addView(text("S  STEAM HUNTER", 18, Color.rgb(8, 17, 15), Typeface.BOLD));
        TextView eyebrow = text("РАДАР БЕСПЛАТНЫХ ИГР", 11, Color.rgb(86, 137, 0), Typeface.BOLD);
        page.addView(eyebrow, margins(-1, -2, 0, 34, 0, 8));

        TextView title = text("Игры, которые можно забрать бесплатно", 34, Color.rgb(8, 17, 15), Typeface.BOLD);
        title.setLineSpacing(0, 0.92f);
        page.addView(title);

        TextView description = text("Только временные скидки 100% в Steam. Обычные Free-to-Play игры сюда не попадают.", 15, Color.rgb(82, 100, 94), Typeface.NORMAL);
        description.setLineSpacing(dp(3), 1f);
        page.addView(description, margins(-1, -2, 0, 16, 0, 22));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        refreshButton = button("Обновить сейчас", Color.rgb(8, 17, 15), Color.WHITE);
        refreshButton.setOnClickListener(v -> loadDeals(true));
        actions.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button notifications = button("Уведомления", Color.rgb(226, 237, 232), Color.rgb(8, 17, 15));
        notifications.setOnClickListener(v -> requestNotifications());
        actions.addView(notifications, margins(0, 48, 10, 0, 0, 0));
        page.addView(actions);

        statusText = text("Ищем раздачи Steam…", 12, Color.rgb(102, 115, 110), Typeface.NORMAL);
        page.addView(statusText, margins(-1, -2, 0, 20, 0, 18));

        progressBar = new ProgressBar(this);
        page.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(38)));

        gamesContainer = vertical();
        page.addView(gamesContainer);
        scroll.addView(page);
        return scroll;
    }

    private void loadDeals(boolean manual) {
        progressBar.setVisibility(View.VISIBLE);
        refreshButton.setEnabled(false);
        statusText.setText(manual ? "Проверяем Steam…" : "Обновляем список…");

        executor.execute(() -> {
            try {
                List<Deal> deals = fetchDeals();
                runOnUiThread(() -> showDeals(deals));
            } catch (Exception error) {
                runOnUiThread(this::showError);
            }
        });
    }

    private List<Deal> fetchDeals() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(STEAM_SEARCH).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9");
        connection.setRequestProperty("User-Agent", "SteamHunter-Android/0.1");

        if (connection.getResponseCode() != 200) {
            throw new IllegalStateException("Steam ответил кодом " + connection.getResponseCode());
        }

        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        } finally {
            connection.disconnect();
        }

        String html = new JSONObject(json.toString()).optString("results_html", "");
        Matcher rows = Pattern.compile("<a\\s+href=[\\s\\S]*?</a>").matcher(html);
        List<Deal> deals = new ArrayList<>();

        while (rows.find()) {
            String row = rows.group();
            String appId = match(row, "data-ds-appid=\"(\\d+)\"");
            String title = match(row, "<span class=\"title\">([\\s\\S]*?)</span>");
            String image = match(row, "class=\"search_capsule\"><img src=\"([^\"]+)\"");
            String discount = match(row, "data-discount=\"(\\d+)\"");
            String finalPrice = match(row, "data-price-final=\"(\\d+)\"");

            if (appId != null && title != null && image != null && "100".equals(discount) && "0".equals(finalPrice)) {
                deals.add(new Deal(appId, decodeHtml(title.trim()), decodeHtml(image)));
            }
        }
        return deals;
    }

    private String match(String source, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String decodeHtml(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

    private void showDeals(List<Deal> deals) {
        progressBar.setVisibility(View.GONE);
        refreshButton.setEnabled(true);
        gamesContainer.removeAllViews();
        String time = DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("ru", "RU")).format(new Date());
        statusText.setText("Найдено: " + deals.size() + "  ·  Проверено в " + time + "  ·  Следующая проверка через час");

        notifyAboutNewDeals(deals);
        if (deals.isEmpty()) {
            TextView empty = text("Радар пока ничего не поймал\n\nКогда появится скидка 100%, игра будет здесь.", 17, Color.rgb(82, 100, 94), Typeface.BOLD);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(60), dp(18), dp(60));
            gamesContainer.addView(empty);
            return;
        }
        for (Deal deal : deals) gamesContainer.addView(createDealCard(deal));
    }

    private View createDealCard(Deal deal) {
        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(Color.WHITE, 14, Color.rgb(220, 230, 225)));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.rgb(13, 42, 34));
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(150)));
        loadImage(deal.imageUrl, image);

        TextView badge = text("✓  СКИДКА 100% — МОЖНО ЗАБРАТЬ БЕСПЛАТНО", 10, Color.rgb(85, 135, 0), Typeface.BOLD);
        card.addView(badge, margins(-1, -2, 0, 16, 0, 7));
        card.addView(text(deal.title, 24, Color.rgb(8, 17, 15), Typeface.BOLD));

        Button claim = button("Забрать в Steam  ↗", Color.rgb(8, 17, 15), Color.WHITE);
        claim.setOnClickListener(v -> openSteam(deal.appId));
        card.addView(claim, margins(-1, 48, 0, 16, 0, 0));
        card.setLayoutParams(margins(-1, -2, 0, 0, 0, 16));
        return card;
    }

    private void loadImage(String imageUrl, ImageView imageView) {
        executor.execute(() -> {
            try (InputStream stream = new URL(imageUrl).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                runOnUiThread(() -> imageView.setImageBitmap(bitmap));
            } catch (Exception ignored) {
                // Карточка останется с тёмным фоном, если изображение временно недоступно.
            }
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
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.star_big_on)
                .setContentTitle("Новая бесплатная игра!")
                .setContentText(deal.title + " можно забрать со скидкой 100%")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(Integer.parseInt(deal.appId), notification);
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        } else {
            statusText.setText("Уведомления уже включены");
        }
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
        statusText.setText("Не получилось связаться со Steam. Нажмите «Обновить сейчас» позже.");
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(foreground);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackground(rounded(background, 9, background));
        return button;
    }

    private GradientDrawable rounded(int background, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(background);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), border);
        return drawable;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(hourlyUpdate);
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class Deal {
        final String appId;
        final String title;
        final String imageUrl;

        Deal(String appId, String title, String imageUrl) {
            this.appId = appId;
            this.title = title;
            this.imageUrl = imageUrl;
        }
    }
}
