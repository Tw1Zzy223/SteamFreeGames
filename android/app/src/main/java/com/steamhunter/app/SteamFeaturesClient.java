package com.steamhunter.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SteamFeaturesClient {
    private static final String BASE = "https://steam-hunter-games.pagrishaevich.chatgpt.site";

    public List<NewsItem> news(String appId) throws Exception {
        JSONObject root = request("GET", "/api/steam/news?app_id=" + encode(appId), "", null);
        JSONArray array = root.optJSONArray("news"); List<NewsItem> result = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            result.add(new NewsItem(item.optString("title"), item.optString("contents"), item.optString("url"), item.optLong("date")));
        }
        return result;
    }

    public List<PricePoint> priceHistory(String appId, String region) throws Exception {
        JSONObject root = request("GET", "/api/price-history?app_id=" + encode(appId) + "&region=" + encode(region), "", null);
        JSONArray array = root.optJSONArray("points"); List<PricePoint> result = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            result.add(new PricePoint(item.optString("currency"), item.optInt("finalCents"), item.optInt("discountPercent"), item.optLong("capturedAt")));
        }
        return result;
    }

    public LatestRelease latestRelease() throws Exception {
        JSONObject root = requestAbsolute("GET", "https://api.github.com/repos/Tw1Zzy223/SteamFreeGames/releases/latest", "", null);
        String version = root.optString("tag_name").replaceFirst("^v", "");
        String apk = ""; JSONArray assets = root.optJSONArray("assets");
        if (assets != null) for (int i = 0; i < assets.length(); i++) {
            JSONObject item = assets.getJSONObject(i);
            if (item.optString("name").toLowerCase().endsWith(".apk")) { apk = item.optString("browser_download_url"); break; }
        }
        return new LatestRelease(version, apk, root.optString("html_url"));
    }

    private JSONObject request(String method, String path, String token, String body) throws Exception {
        return requestAbsolute(method, BASE + path, token, body);
    }

    private JSONObject requestAbsolute(String method, String address, String token, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod(method); connection.setConnectTimeout(15_000); connection.setReadTimeout(30_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "SteamHunter-Android/0.4");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) { output.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = connection.getResponseCode();
        java.io.InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) text.append(line);
        } finally { connection.disconnect(); }
        JSONObject result = new JSONObject(text.length() == 0 ? "{}" : text.toString());
        if (code < 200 || code >= 300) throw new IllegalStateException(result.optString("error", "Ошибка сервера " + code));
        return result;
    }

    private String encode(String value) throws Exception { return URLEncoder.encode(value, StandardCharsets.UTF_8.name()); }

    public static class NewsItem {
        public final String title, contents, url; public final long date;
        NewsItem(String title, String contents, String url, long date) { this.title = title; this.contents = contents; this.url = url; this.date = date; }
    }
    public static class PricePoint {
        public final String currency; public final int finalCents, discount; public final long capturedAt;
        PricePoint(String currency, int finalCents, int discount, long capturedAt) { this.currency = currency; this.finalCents = finalCents; this.discount = discount; this.capturedAt = capturedAt; }
    }
    public static class LatestRelease {
        public final String version, apkUrl, pageUrl;
        LatestRelease(String version, String apkUrl, String pageUrl) { this.version = version; this.apkUrl = apkUrl; this.pageUrl = pageUrl; }
    }
}
