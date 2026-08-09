package com.steamhunter.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SupportClient {
    public static final String ENDPOINT = "https://steam-hunter-games.pagrishaevich.chatgpt.site/api/support";

    public void send(String deviceId, String name, String contact, String message) throws Exception {
        JSONObject body = new JSONObject();
        body.put("deviceId", deviceId);
        body.put("name", name);
        body.put("contact", contact);
        body.put("message", message);
        request("POST", "", body.toString());
    }

    public List<Message> messages(String adminKey) throws Exception {
        JSONObject root = new JSONObject(request("GET", adminKey, null));
        JSONArray array = root.optJSONArray("messages");
        List<Message> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            result.add(new Message(item.optString("name"), item.optString("contact"),
                    item.optString("message"), item.optLong("createdAt")));
        }
        return result;
    }

    private String request(String method, String adminKey, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (!adminKey.isEmpty()) connection.setRequestProperty("X-Admin-Key", adminKey);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = connection.getResponseCode();
        java.io.InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) result.append(line);
        } finally { connection.disconnect(); }
        if (code < 200 || code >= 300) {
            String message = new JSONObject(result.toString()).optString("error", "Ошибка сервера " + code);
            throw new IllegalStateException(message);
        }
        return result.toString();
    }

    public static class Message {
        public final String name;
        public final String contact;
        public final String message;
        public final long createdAt;

        Message(String name, String contact, String message, long createdAt) {
            this.name = name;
            this.contact = contact;
            this.message = message;
            this.createdAt = createdAt;
        }
    }
}
