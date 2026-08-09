package com.steamhunter.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SteamAccountClient {
    private static final String BASE = "https://steam-hunter-games.pagrishaevich.chatgpt.site/api/steam";

    public String loginUrl(String deviceId) throws Exception {
        return BASE + "/login?device_id=" + URLEncoder.encode(deviceId, StandardCharsets.UTF_8.name());
    }

    public Profile me(String token) throws Exception {
        JSONObject profile = request("GET", "/me", token).getJSONObject("profile");
        return parseProfile(profile);
    }

    public FriendsResult friends(String token) throws Exception {
        JSONObject root = request("GET", "/friends", token);
        JSONArray array = root.optJSONArray("friends");
        List<Profile> friends = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) friends.add(parseProfile(array.getJSONObject(i)));
        return new FriendsResult(friends, root.optBoolean("isPrivate", false));
    }

    public void logout(String token) throws Exception { request("DELETE", "/me", token); }

    private JSONObject request(String method, String path, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(25_000);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        int code = connection.getResponseCode();
        java.io.InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) body.append(line);
        } finally { connection.disconnect(); }
        JSONObject json = new JSONObject(body.toString());
        if (code < 200 || code >= 300) throw new IllegalStateException(json.optString("error", "Ошибка сервера " + code));
        return json;
    }

    private Profile parseProfile(JSONObject value) {
        return new Profile(value.optString("steamid"), value.optString("personaname", "Пользователь Steam"),
                value.optString("avatarfull"), value.optString("profileurl"), value.optInt("personastate", 0),
                value.optString("gameextrainfo"));
    }

    public static class Profile {
        public final String steamId;
        public final String name;
        public final String avatarUrl;
        public final String profileUrl;
        public final int state;
        public final String currentGame;

        Profile(String steamId, String name, String avatarUrl, String profileUrl, int state, String currentGame) {
            this.steamId = steamId; this.name = name; this.avatarUrl = avatarUrl;
            this.profileUrl = profileUrl; this.state = state; this.currentGame = currentGame;
        }

        public String status() {
            if (!currentGame.isEmpty()) return "Играет в " + currentGame;
            if (state > 0) return "В сети";
            return "Не в сети";
        }
    }

    public static class FriendsResult {
        public final List<Profile> friends;
        public final boolean isPrivate;
        FriendsResult(List<Profile> friends, boolean isPrivate) { this.friends = friends; this.isPrivate = isPrivate; }
    }
}
