package com.steamhunter.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

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
