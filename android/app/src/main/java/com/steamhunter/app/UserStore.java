package com.steamhunter.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserStore {
    public static final String PREFS = "steam_hunter_preferences";
    private final SharedPreferences preferences;

    public UserStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isDark() { return preferences.getBoolean("dark_theme", true); }
    public void setDark(boolean value) { preferences.edit().putBoolean("dark_theme", value).apply(); }


    public Set<String> favorites() { return new HashSet<>(preferences.getStringSet("favorites", new HashSet<>())); }
    public boolean isFavorite(String appId) { return favorites().contains(appId); }
    public void toggleFavorite(StoreClient.GameDeal game) {
        Set<String> values = favorites();
        if (values.contains(game.appId)) values.remove(game.appId); else values.add(game.appId);
        preferences.edit().putStringSet("favorites", values).putString("favorite_title_" + game.appId, game.title).apply();
    }
    public String favoriteTitle(String appId) { return preferences.getString("favorite_title_" + appId, "Игра " + appId); }

    // В Steam Hunter избранное одновременно является личным списком желаемого.
    public Set<String> wishlist() { return favorites(); }
    public boolean isWishlisted(String appId) { return isFavorite(appId); }
    public void toggleWishlist(StoreClient.GameDeal game) { toggleFavorite(game); }

    public void saveSmartAlert(StoreClient.GameDeal game, int discount) {
        Set<String> ids = smartAlertIds();
        ids.add(game.appId);
        preferences.edit().putStringSet("smart_alert_ids", ids)
                .putInt("smart_alert_discount_" + game.appId, discount)
                .putString("favorite_title_" + game.appId, game.title).apply();
    }

    public void removeSmartAlert(String appId) {
        Set<String> ids = smartAlertIds();
        ids.remove(appId);
        preferences.edit().putStringSet("smart_alert_ids", ids)
                .remove("smart_alert_discount_" + appId).apply();
    }

    public Set<String> smartAlertIds() { return new HashSet<>(preferences.getStringSet("smart_alert_ids", new HashSet<>())); }
    public int smartAlertDiscount(String appId) { return preferences.getInt("smart_alert_discount_" + appId, 70); }
    public int lastAlertDiscount(String appId) { return preferences.getInt("last_alert_discount_" + appId, 0); }
    public void saveLastAlertDiscount(String appId, int value) { preferences.edit().putInt("last_alert_discount_" + appId, value).apply(); }

    public List<String> comparisonIds() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString("comparison_ids", "[]"));
            for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
        } catch (Exception ignored) { }
        return result;
    }

    public void toggleComparison(StoreClient.GameDeal game) {
        List<String> ids = comparisonIds();
        if (ids.contains(game.appId)) ids.remove(game.appId);
        else {
            if (ids.size() == 2) ids.remove(0);
            ids.add(game.appId);
        }
        JSONArray array = new JSONArray();
        for (String id : ids) array.put(id);
        preferences.edit().putString("comparison_ids", array.toString())
                .putString("favorite_title_" + game.appId, game.title).apply();
    }

    public void clearComparison() { preferences.edit().remove("comparison_ids").apply(); }

    public void saveOfflinePage(String mode, StoreClient.SearchResult result) {
        try {
            JSONObject root = new JSONObject();
            root.put("total", result.totalCount);
            JSONArray games = new JSONArray();
            for (StoreClient.GameDeal game : result.games) {
                JSONObject item = new JSONObject();
                item.put("id", game.appId);
                item.put("title", game.title);
                item.put("image", game.imageUrl);
                item.put("discount", game.discount);
                item.put("price", game.currentPrice);
                item.put("weekend", game.freeWeekend);
                games.put(item);
            }
            root.put("games", games);
            root.put("saved", System.currentTimeMillis());
            preferences.edit().putString("offline_" + mode, root.toString()).apply();
        } catch (Exception ignored) { }
    }

    public StoreClient.SearchResult offlinePage(String mode) {
        List<StoreClient.GameDeal> games = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(preferences.getString("offline_" + mode, "{}"));
            JSONArray array = root.optJSONArray("games");
            if (array == null) return null;
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                games.add(new StoreClient.GameDeal(item.optString("id"), item.optString("title"),
                        item.optString("image"), item.optInt("discount"), item.optString("price"), item.optBoolean("weekend")));
            }
            return new StoreClient.SearchResult(games, root.optInt("total", games.size()));
        } catch (Exception ignored) { return null; }
    }

    public Set<String> viewed() { return new HashSet<>(preferences.getStringSet("viewed", new HashSet<>())); }
    public boolean isViewed(String appId) { return viewed().contains(appId); }
    public void markViewed(String appId) {
        Set<String> values = viewed(); values.add(appId);
        preferences.edit().putStringSet("viewed", values).apply();
    }

    public void addHistory(String query) {
        if (query.trim().isEmpty()) return;
        List<String> history = history();
        history.remove(query); history.add(0, query);
        while (history.size() > 8) history.remove(history.size() - 1);
        JSONArray array = new JSONArray();
        for (String item : history) array.put(item);
        preferences.edit().putString("search_history", array.toString()).apply();
    }

    public List<String> history() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString("search_history", "[]"));
            for (int i = 0; i < array.length(); i++) result.add(array.getString(i));
        } catch (Exception ignored) { }
        return result;
    }

    public boolean notifyFree() { return preferences.getBoolean("notify_free", true); }
    public boolean notifyDiscounts() { return preferences.getBoolean("notify_discounts", false); }
    public boolean notifyFavoritesOnly() { return preferences.getBoolean("notify_favorites_only", false); }
    public int notifyThreshold() { return preferences.getInt("notify_threshold", 80); }
    public boolean quietHours() { return preferences.getBoolean("quiet_hours", true); }

    public void saveNotificationSettings(boolean free, boolean discounts, boolean favoritesOnly, int threshold, boolean quiet) {
        preferences.edit()
                .putBoolean("notify_free", free)
                .putBoolean("notify_discounts", discounts)
                .putBoolean("notify_favorites_only", favoritesOnly)
                .putInt("notify_threshold", threshold)
                .putBoolean("quiet_hours", quiet)
                .apply();
    }

    public Set<String> seen(String key) { return new HashSet<>(preferences.getStringSet(key, new HashSet<>())); }
    public void saveSeen(String key, Set<String> values) { preferences.edit().putStringSet(key, values).apply(); }

}
