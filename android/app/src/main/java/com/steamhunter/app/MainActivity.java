package com.steamhunter.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.core.splashscreen.SplashScreen;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final long HOUR_MS = 60L * 60L * 1000L;
    private static final String[] GENRE_NAMES = {"Все жанры", "Экшен", "Стратегии", "RPG", "Приключения", "Инди", "Казуальные", "Симуляторы", "Гонки"};
    private static final String[] GENRE_TAGS = {"", "19", "9", "122", "21", "492", "597", "599", "699"};
    private static final String[] SORT_NAMES = {"Популярные", "Цена: дешевле", "По названию", "Сначала новинки"};
    private static final String[] SORT_VALUES = {"Reviews_DESC", "Price_ASC", "Name_ASC", "Released_DESC"};
    private static final Integer[] DISCOUNT_VALUES = {1, 50, 70, 80, 90, 100};

    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StoreClient client = new StoreClient();
    private UserStore store;
    private boolean dark;
    private int background;
    private int panel;
    private int panelStrong;
    private int accent;
    private int primaryText;
    private int secondaryText;
    private int border;

    private LinearLayout gamesContainer;
    private LinearLayout searchPanel;
    private TextView statusText;
    private TextView sectionTitle;
    private ProgressBar progressBar;
    private Button refreshButton;
    private Button loadMoreButton;
    private Button freeTab;
    private Button discountsTab;
    private Button allTab;
    private EditText searchInput;
    private Spinner historySpinner;
    private Spinner genreSpinner;
    private Spinner sortSpinner;
    private Spinner discountSpinner;
    private String currentMode = StoreClient.MODE_FREE;
    private String currentQuery = "";
    private int currentStart;
    private int loadedCount;
    private int totalCount;

    private final Runnable hourlyUpdate = new Runnable() {
        @Override public void run() {
            loadDeals(true, false);
            handler.postDelayed(this, HOUR_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splash.setOnExitAnimationListener(provider -> provider.getView().animate()
                .alpha(0f).scaleX(1.08f).scaleY(1.08f).setDuration(420)
                .withEndAction(provider::remove).start());
        store = new UserStore(this);
        applyPalette();
        DealWorker.schedule(this);
        setContentView(createScreen());
        selectTab(StoreClient.MODE_FREE);
        handler.postDelayed(hourlyUpdate, HOUR_MS);
    }

    private void applyPalette() {
        dark = store.isDark();
        background = dark ? Color.rgb(5, 8, 6) : Color.rgb(244, 248, 246);
        panel = dark ? Color.rgb(11, 24, 18) : Color.WHITE;
        panelStrong = dark ? Color.rgb(17, 52, 40) : Color.rgb(224, 235, 229);
        accent = dark ? Color.rgb(185, 255, 44) : Color.rgb(89, 137, 0);
        primaryText = dark ? Color.rgb(239, 247, 242) : Color.rgb(8, 17, 15);
        secondaryText = dark ? Color.rgb(137, 181, 104) : Color.rgb(82, 100, 94);
        border = dark ? Color.rgb(30, 75, 57) : Color.rgb(204, 220, 212);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private View createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        LinearLayout page = vertical();
        page.setPadding(dp(17), dp(20), dp(17), dp(44));

        LinearLayout top = horizontal();
        TextView brand = text("◉  STEAM HUNTER", 18, accent, Typeface.BOLD);
        top.addView(brand, weighted(-2, 1f, 0));
        Button favorites = smallButton("★ Избранное");
        favorites.setOnClickListener(v -> showFavorites());
        top.addView(favorites, wrap(0));
        Button settings = smallButton("⚙");
        settings.setContentDescription("Настройки");
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings, wrap(7));
        page.addView(top);

        page.addView(text("БЕСПЛАТНЫЕ ИГРЫ, СКИДКИ И ЦЕНЫ", 10, secondaryText, Typeface.BOLD), margins(-1, -2, 0, 27, 0, 7));
        page.addView(text("Весь Steam на одном радаре", 33, primaryText, Typeface.BOLD));
        page.addView(text("Ищите игры, сравнивайте четыре региона и сохраняйте лучшие предложения.", 14, secondaryText, Typeface.NORMAL), margins(-1, -2, 0, 10, 0, 20));

        LinearLayout tabs = horizontal();
        freeTab = button("Бесплатно", panel, accent);
        discountsTab = button("Скидки", panel, accent);
        allTab = button("Все игры", panel, accent);
        freeTab.setOnClickListener(v -> selectTab(StoreClient.MODE_FREE));
        discountsTab.setOnClickListener(v -> selectTab(StoreClient.MODE_DISCOUNTS));
        allTab.setOnClickListener(v -> selectTab(StoreClient.MODE_ALL));
        tabs.addView(freeTab, weighted(46, 1f, 0));
        tabs.addView(discountsTab, weighted(46, 1f, 7));
        tabs.addView(allTab, weighted(46, 1f, 7));
        page.addView(tabs);

        searchPanel = vertical();
        LinearLayout searchRow = horizontal();
        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Например, Mortal Kombat 1");
        searchInput.setHintTextColor(secondaryText);
        searchInput.setTextColor(primaryText);
        searchInput.setTextSize(14);
        searchInput.setPadding(dp(13), 0, dp(10), 0);
        searchInput.setBackground(rounded(panel, 9, border));
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((v, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) { performSearch(); return true; }
            return false;
        });
        searchRow.addView(searchInput, weighted(50, 1f, 0));
        Button search = button("Найти", accent, dark ? Color.BLACK : Color.WHITE);
        search.setOnClickListener(v -> performSearch());
        searchRow.addView(search, fixed(84, 50, 7));
        searchPanel.addView(searchRow);
        historySpinner = spinner(new String[]{"История поиска"});
        searchPanel.addView(historySpinner, margins(-1, 45, 0, 7, 0, 0));
        page.addView(searchPanel, margins(-1, -2, 0, 13, 0, 0));
        updateHistory();

        LinearLayout filters = vertical();
        filters.setPadding(dp(10), dp(9), dp(10), dp(10));
        filters.setBackground(rounded(panel, 10, border));
        filters.addView(text("ФИЛЬТРЫ И СОРТИРОВКА", 10, accent, Typeface.BOLD));
        LinearLayout filterRow = horizontal();
        genreSpinner = spinner(GENRE_NAMES);
        sortSpinner = spinner(SORT_NAMES);
        filterRow.addView(genreSpinner, weighted(46, 1f, 0));
        filterRow.addView(sortSpinner, weighted(46, 1f, 6));
        filters.addView(filterRow);
        discountSpinner = spinner(new String[]{"Любая скидка", "От 50%", "От 70%", "От 80%", "От 90%", "Только 100%"});
        filters.addView(discountSpinner, margins(-1, 46, 0, 5, 0, 0));
        Button apply = button("Применить фильтры", panelStrong, primaryText);
        apply.setOnClickListener(v -> loadDeals(true, false));
        filters.addView(apply, margins(-1, 44, 0, 5, 0, 0));
        page.addView(filters, margins(-1, -2, 0, 12, 0, 0));

        LinearLayout tools = horizontal();
        refreshButton = button("↻ Обновить", panelStrong, primaryText);
        refreshButton.setOnClickListener(v -> loadDeals(true, true));
        tools.addView(refreshButton, weighted(44, 1f, 0));
        Button notification = button("🔔 Настройки", panel, accent);
        notification.setOnClickListener(v -> showSettings());
        tools.addView(notification, weighted(44, 1f, 7));
        page.addView(tools, margins(-1, -2, 0, 11, 0, 0));

        sectionTitle = text("Бесплатно прямо сейчас", 23, primaryText, Typeface.BOLD);
        page.addView(sectionTitle, margins(-1, -2, 0, 24, 0, 5));
        statusText = text("Подключаемся к Steam…", 12, secondaryText, Typeface.NORMAL);
        page.addView(statusText, margins(-1, -2, 0, 0, 0, 12));
        progressBar = new ProgressBar(this);
        page.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(38)));
        gamesContainer = vertical();
        page.addView(gamesContainer);
        loadMoreButton = button("Загрузить следующие 50", panelStrong, accent);
        loadMoreButton.setOnClickListener(v -> loadDeals(false, false));
        page.addView(loadMoreButton, margins(-1, 50, 0, 7, 0, 0));
        loadMoreButton.setVisibility(View.GONE);
        scroll.addView(page);
        return scroll;
    }

    private void selectTab(String mode) {
        currentMode = mode;
        currentQuery = "";
        searchInput.setText("");
        styleTab(freeTab, StoreClient.MODE_FREE.equals(mode));
        styleTab(discountsTab, StoreClient.MODE_DISCOUNTS.equals(mode));
        styleTab(allTab, StoreClient.MODE_ALL.equals(mode));
        searchPanel.setVisibility(StoreClient.MODE_FREE.equals(mode) ? View.GONE : View.VISIBLE);
        discountSpinner.setVisibility(StoreClient.MODE_DISCOUNTS.equals(mode) ? View.VISIBLE : View.GONE);
        sectionTitle.setText(StoreClient.MODE_FREE.equals(mode) ? "Бесплатно прямо сейчас" : StoreClient.MODE_DISCOUNTS.equals(mode) ? "Все скидки Steam" : "Все игры Steam");
        loadDeals(true, false);
    }

    private void styleTab(Button button, boolean selected) {
        button.setBackground(rounded(selected ? accent : panel, 9, selected ? accent : border));
        button.setTextColor(selected ? (dark ? Color.BLACK : Color.WHITE) : accent);
    }

    private void performSearch() {
        currentQuery = searchInput.getText().toString().trim();
        store.addHistory(currentQuery);
        updateHistory();
        sectionTitle.setText(currentQuery.isEmpty() ? "Результаты Steam" : "Результаты: «" + currentQuery + "»");
        loadDeals(true, false);
    }

    private void updateHistory() {
        if (historySpinner == null) return;
        List<String> values = new ArrayList<>();
        values.add("История поиска");
        values.addAll(store.history());
        historySpinner.setAdapter(adapter(values.toArray(new String[0])));
        historySpinner.setOnItemSelectedListener(new SimpleItemListener(position -> {
            if (position > 0) {
                searchInput.setText(values.get(position));
                currentQuery = values.get(position);
                loadDeals(true, false);
            }
        }));
    }

    private void loadDeals(boolean reset, boolean manual) {
        if (reset) { currentStart = 0; loadedCount = 0; gamesContainer.removeAllViews(); }
        progressBar.setVisibility(View.VISIBLE);
        refreshButton.setEnabled(false);
        loadMoreButton.setEnabled(false);
        statusText.setText(manual ? "Проверяем Steam…" : "Загружаем предложения…");
        String mode = currentMode;
        String query = currentQuery;
        int start = currentStart;
        String sort = SORT_VALUES[sortSpinner.getSelectedItemPosition()];
        String tag = GENRE_TAGS[genreSpinner.getSelectedItemPosition()];
        int minimum = StoreClient.MODE_DISCOUNTS.equals(mode) ? DISCOUNT_VALUES[discountSpinner.getSelectedItemPosition()] : 0;

        executor.execute(() -> {
            try {
                StoreClient.SearchResult result = client.search(mode, query, start, sort, tag, minimum);
                runOnUiThread(() -> { if (mode.equals(currentMode) && query.equals(currentQuery)) showDeals(result, reset); });
            } catch (Exception error) { runOnUiThread(this::showError); }
        });
    }

    private void showDeals(StoreClient.SearchResult result, boolean reset) {
        progressBar.setVisibility(View.GONE);
        refreshButton.setEnabled(true);
        loadMoreButton.setEnabled(true);
        loadedCount += result.games.size();
        totalCount = result.totalCount;
        currentStart += 50;
        String time = DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("ru", "RU")).format(new Date());
        statusText.setText("Показано: " + loadedCount + " · В Steam найдено: " + totalCount + " · " + time);
        if (result.games.isEmpty() && reset) {
            String message = currentQuery.isEmpty() ? "Подходящих игр пока не найдено." : "«" + currentQuery + "» не найдена с выбранными фильтрами.";
            TextView empty = text(message, 16, secondaryText, Typeface.BOLD);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(15), dp(50), dp(15), dp(50));
            gamesContainer.addView(empty);
        } else {
            for (StoreClient.GameDeal game : result.games) gamesContainer.addView(createGameCard(game));
        }
        loadMoreButton.setVisibility(currentStart < totalCount ? View.VISIBLE : View.GONE);
    }

    private View createGameCard(StoreClient.GameDeal game) {
        LinearLayout card = vertical();
        card.setPadding(dp(11), dp(11), dp(11), dp(11));
        card.setBackground(rounded(panel, 14, border));
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(panelStrong);
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(145)));
        loadImage(game.imageUrl, image);

        String badge = game.discount > 0 ? "−" + game.discount + "%  ·  " + game.currentPrice : "БЕЗ СКИДКИ  ·  " + game.currentPrice;
        card.addView(text(badge, 11, game.discount > 0 ? accent : secondaryText, Typeface.BOLD), margins(-1, -2, 0, 13, 0, 5));
        card.addView(text(game.title, 23, primaryText, Typeface.BOLD));
        if (store.isViewed(game.appId)) card.addView(text("✓ Просмотрено", 10, secondaryText, Typeface.BOLD));

        LinearLayout mainActions = horizontal();
        Button details = button("Подробнее", panelStrong, primaryText);
        details.setOnClickListener(v -> showDetails(game));
        mainActions.addView(details, weighted(46, 1f, 0));
        Button steam = button("Открыть в Steam ↗", accent, dark ? Color.BLACK : Color.WHITE);
        steam.setOnClickListener(v -> openSteam(game));
        mainActions.addView(steam, weighted(46, 1.25f, 7));
        card.addView(mainActions, margins(-1, -2, 0, 13, 0, 0));

        LinearLayout secondary = horizontal();
        Button favorite = smallButton(store.isFavorite(game.appId) ? "★ В избранном" : "☆ В избранное");
        favorite.setOnClickListener(v -> { store.toggleFavorite(game); favorite.setText(store.isFavorite(game.appId) ? "★ В избранном" : "☆ В избранное"); });
        secondary.addView(favorite, weighted(40, 1f, 0));
        Button share = smallButton("Поделиться");
        share.setOnClickListener(v -> shareGame(game));
        secondary.addView(share, weighted(40, 1f, 7));
        card.addView(secondary, margins(-1, -2, 0, 7, 0, 0));
        card.setLayoutParams(margins(-1, -2, 0, 0, 0, 14));
        return card;
    }

    private void showDetails(StoreClient.GameDeal game) {
        store.markViewed(game.appId);
        AlertDialog loading = new AlertDialog.Builder(this).setTitle(game.title).setMessage("Загружаем описание и цены четырёх регионов…").setNegativeButton("Закрыть", null).show();
        executor.execute(() -> {
            try {
                StoreClient.GameDetails details = client.details(game.appId);
                runOnUiThread(() -> { loading.dismiss(); showDetailsDialog(game, details); });
            } catch (Exception error) {
                runOnUiThread(() -> { loading.dismiss(); new AlertDialog.Builder(this).setTitle("Не удалось загрузить подробности").setMessage("Steam временно не вернул данные. Попробуйте позже.").setPositiveButton("Понятно", null).show(); });
            }
        });
    }

    private void showDetailsDialog(StoreClient.GameDeal game, StoreClient.GameDetails details) {
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(6), dp(18), dp(8));
        content.addView(text(details.genres, 13, Color.rgb(70, 112, 52), Typeface.BOLD));
        content.addView(text(details.description, 14, Color.DKGRAY, Typeface.NORMAL), margins(-1, -2, 0, 8, 0, 13));
        content.addView(text("Цены по регионам", 18, Color.BLACK, Typeface.BOLD));
        for (Map.Entry<String, String> entry : details.regionalPrices.entrySet()) {
            content.addView(text(entry.getKey() + ":  " + entry.getValue(), 15, Color.DKGRAY, Typeface.NORMAL), margins(-1, -2, 0, 5, 0, 0));
        }
        content.addView(text("Цены могут отличаться из-за региональных правил Steam. Покупка подтверждается только в Steam.", 11, Color.GRAY, Typeface.NORMAL), margins(-1, -2, 0, 12, 0, 0));
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(details.title).setView(scroll)
                .setPositiveButton("Открыть в Steam", (d, w) -> openSteam(game))
                .setNeutralButton("Поделиться", (d, w) -> shareGame(game))
                .setNegativeButton(store.isFavorite(game.appId) ? "Убрать из избранного" : "В избранное", (d, w) -> store.toggleFavorite(game)).show();
    }

    private void showFavorites() {
        Set<String> favorites = store.favorites();
        if (favorites.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Избранное").setMessage("Нажмите ☆ на карточке игры, и она появится здесь.").setPositiveButton("Понятно", null).show();
            return;
        }
        List<String> ids = new ArrayList<>(favorites);
        String[] titles = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) titles[i] = store.favoriteTitle(ids.get(i));
        new AlertDialog.Builder(this).setTitle("Избранные игры").setItems(titles, (dialog, index) -> {
            String id = ids.get(index);
            showDetails(new StoreClient.GameDeal(id, titles[index], "", 0, "Откройте подробности"));
        }).setNegativeButton("Закрыть", null).show();
    }

    private void showSettings() {
        LinearLayout content = vertical(); content.setPadding(dp(20), dp(4), dp(20), 0);
        Switch theme = new Switch(this); theme.setText("Тёмная тема"); theme.setChecked(store.isDark()); content.addView(theme);
        CheckBox free = checkbox("Бесплатные раздачи", store.notifyFree()); content.addView(free);
        CheckBox discounts = checkbox("Скидки", store.notifyDiscounts()); content.addView(discounts);
        CheckBox favoritesOnly = checkbox("Только избранные игры", store.notifyFavoritesOnly()); content.addView(favoritesOnly);
        CheckBox quiet = checkbox("Не беспокоить с 23:00 до 08:00", store.quietHours()); content.addView(quiet);
        content.addView(text("Минимальная скидка для уведомлений", 13, Color.DKGRAY, Typeface.BOLD), margins(-1, -2, 0, 10, 0, 3));
        Spinner threshold = spinner(new String[]{"50%", "70%", "80%", "90%", "100%"});
        int[] values = {50, 70, 80, 90, 100};
        int selected = 2;
        for (int i = 0; i < values.length; i++) if (values[i] == store.notifyThreshold()) selected = i;
        threshold.setSelection(selected); content.addView(threshold);
        int finalSelected = selected;
        new AlertDialog.Builder(this).setTitle("Настройки").setView(content).setPositiveButton("Сохранить", (dialog, which) -> {
            int thresholdValue = values[threshold.getSelectedItemPosition()];
            store.saveNotificationSettings(free.isChecked(), discounts.isChecked(), favoritesOnly.isChecked(), thresholdValue, quiet.isChecked());
            if ((free.isChecked() || discounts.isChecked()) && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
            DealWorker.schedule(this);
            if (theme.isChecked() != store.isDark()) { store.setDark(theme.isChecked()); recreate(); }
        }).setNegativeButton("Отмена", null).show();
    }

    private CheckBox checkbox(String label, boolean checked) {
        CheckBox box = new CheckBox(this); box.setText(label); box.setChecked(checked); return box;
    }

    private void openSteam(StoreClient.GameDeal game) {
        store.markViewed(game.appId);
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/app/" + game.appId + "/")));
    }

    private void shareGame(StoreClient.GameDeal game) {
        String discount = game.discount > 0 ? "Скидка −" + game.discount + "% · " + game.currentPrice : "Цена: " + game.currentPrice;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, game.title + "\n" + discount + "\nhttps://store.steampowered.com/app/" + game.appId + "/");
        startActivity(Intent.createChooser(share, "Поделиться скидкой"));
    }

    private void loadImage(String url, ImageView image) {
        if (url == null || url.isEmpty()) return;
        executor.execute(() -> {
            try (InputStream stream = new java.net.URL(url).openStream()) {
                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                runOnUiThread(() -> image.setImageBitmap(bitmap));
            } catch (Exception ignored) { }
        });
    }

    private void showError() {
        progressBar.setVisibility(View.GONE); refreshButton.setEnabled(true); loadMoreButton.setEnabled(true);
        statusText.setText("Steam временно не отвечает. Нажмите «Обновить» позже.");
    }

    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String value, int size, int color, int style) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style); return v; }
    private Button button(String value, int bg, int fg) { Button b = new Button(this); b.setText(value); b.setTextColor(fg); b.setTextSize(11); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setAllCaps(false); b.setBackground(rounded(bg, 9, bg)); return b; }
    private Button smallButton(String value) { return button(value, panel, accent); }
    private Spinner spinner(String[] items) { Spinner s = new Spinner(this); s.setAdapter(adapter(items)); s.setBackground(rounded(panel, 8, border)); return s; }
    private ArrayAdapter<String> adapter(String[] items) { return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items); }
    private GradientDrawable rounded(int bg, int radius, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(bg); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d; }
    private LinearLayout.LayoutParams weighted(int height, float weight, int left) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height > 0 ? dp(height) : height, weight); p.setMargins(dp(left), 0, 0, 0); return p; }
    private LinearLayout.LayoutParams fixed(int width, int height, int left) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(width), dp(height)); p.setMargins(dp(left), 0, 0, 0); return p; }
    private LinearLayout.LayoutParams wrap(int left) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40)); p.setMargins(dp(left), 0, 0, 0); return p; }
    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width > 0 ? dp(width) : width, height > 0 ? dp(height) : height); p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() { handler.removeCallbacks(hourlyUpdate); executor.shutdownNow(); super.onDestroy(); }

    private interface PositionCallback { void selected(int position); }
    private static class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final PositionCallback callback;
        SimpleItemListener(PositionCallback callback) { this.callback = callback; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { callback.selected(position); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
