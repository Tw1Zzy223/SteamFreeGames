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

    public String steamToken() { return preferences.getString("steam_session_token", ""); }
    public void saveSteamToken(String token) { preferences.edit().putString("steam_session_token", token).apply(); }
    public void clearSteamToken() { preferences.edit().remove("steam_session_token").apply(); }

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

    public JSONObject exportSyncData() {
        JSONObject root = new JSONObject();
        try {
            root.put("favorites", gamesArray(favorites()));
            root.put("viewed", gamesArray(viewed()));
            root.put("compare", gamesArray(new HashSet<>(comparisonIds())));
            JSONArray searches = new JSONArray(); for (String item : history()) searches.put(item);
            root.put("searchHistory", searches);
            JSONObject settings = new JSONObject();
            settings.put("theme", isDark() ? "dark" : "light");
            settings.put("notifications", notifyFree() || notifyDiscounts());
            settings.put("notifyFree", notifyFree()); settings.put("notifyDiscounts", notifyDiscounts());
            settings.put("threshold", notifyThreshold()); settings.put("quietHours", quietHours());
            root.put("settings", settings);
        } catch (Exception ignored) { }
        return root;
    }

    private JSONArray gamesArray(Set<String> ids) {
        JSONArray array = new JSONArray();
        for (String id : ids) {
            JSONObject item = new JSONObject();
            try { item.put("appId", id); item.put("title", favoriteTitle(id)); } catch (Exception ignored) { }
            array.put(item);
        }
        return array;
    }

    public void mergeSyncData(JSONObject remote) {
        if (remote == null) return;
        Set<String> favoriteIds = favorites();
        Set<String> viewedIds = viewed();
        List<String> compareIds = comparisonIds();
        SharedPreferences.Editor editor = preferences.edit();
        mergeGameArray(remote.optJSONArray("favorites"), favoriteIds, editor);
        mergeGameArray(remote.optJSONArray("viewed"), viewedIds, editor);
        JSONArray compare = remote.optJSONArray("compare");
        if (compare != null) for (int i = 0; i < compare.length() && compareIds.size() < 2; i++) {
            String id = compare.optJSONObject(i) == null ? "" : compare.optJSONObject(i).optString("appId");
            if (!id.isEmpty() && !compareIds.contains(id)) compareIds.add(id);
        }
        JSONArray compareJson = new JSONArray(); for (String id : compareIds) compareJson.put(id);
        List<String> searches = history(); JSONArray remoteSearch = remote.optJSONArray("searchHistory");
        if (remoteSearch != null) for (int i = 0; i < remoteSearch.length(); i++) {
            String value = remoteSearch.optString(i); if (!value.isEmpty() && !searches.contains(value)) searches.add(value);
        }
        while (searches.size() > 20) searches.remove(searches.size() - 1);
        JSONArray searchJson = new JSONArray(); for (String value : searches) searchJson.put(value);
        editor.putStringSet("favorites", favoriteIds).putStringSet("viewed", viewedIds)
                .putString("comparison_ids", compareJson.toString()).putString("search_history", searchJson.toString());
        JSONObject settings = remote.optJSONObject("settings");
        if (settings != null) {
            if (settings.has("theme")) editor.putBoolean("dark_theme", !"light".equals(settings.optString("theme")));
            if (settings.has("notifyFree")) editor.putBoolean("notify_free", settings.optBoolean("notifyFree"));
            if (settings.has("notifyDiscounts")) editor.putBoolean("notify_discounts", settings.optBoolean("notifyDiscounts"));
            if (settings.has("threshold")) editor.putInt("notify_threshold", settings.optInt("threshold", 80));
            if (settings.has("quietHours")) editor.putBoolean("quiet_hours", settings.optBoolean("quietHours", true));
        }
        editor.apply();
    }

    private void mergeGameArray(JSONArray array, Set<String> target, SharedPreferences.Editor editor) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i); if (item == null) continue;
            String id = item.optString("appId"); if (id.isEmpty()) continue;
            target.add(id); String title = item.optString("title");
            if (!title.isEmpty()) editor.putString("favorite_title_" + id, title);
        }
    }
}
