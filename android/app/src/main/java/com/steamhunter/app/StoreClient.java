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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StoreClient {
    public static final int PAGE_SIZE = 50;
    public static final String MODE_FREE = "free";
    public static final String MODE_DISCOUNTS = "discounts";
    public static final String MODE_BEST = "best";
    public static final String MODE_ALL = "all";
    public static final String MODE_WEEKENDS = "weekends";
    private static final String SEARCH_URL = "https://store.steampowered.com/search/results/";

    public SearchResult search(String mode, String query, int start, String sort, String tag, int minimumDiscount) throws Exception {
        StringBuilder url = new StringBuilder(SEARCH_URL)
                .append("?start=").append(start)
                .append("&count=").append(PAGE_SIZE)
                // category1=998 означает именно игры. Язык не ограничиваем,
                // поэтому в каталоге доступны все игры Steam, а не только русскоязычные.
                .append("&dynamic_data=&category1=998&infinite=1&cc=us")
                .append("&sort_by=").append(encode(sort));
        if (MODE_FREE.equals(mode) || MODE_WEEKENDS.equals(mode)) url.append("&specials=1&maxprice=free");
        if (MODE_DISCOUNTS.equals(mode) || MODE_BEST.equals(mode)) url.append("&specials=1");
        if (!query.isEmpty()) url.append("&term=").append(encode(query));
        if (!tag.isEmpty()) url.append("&tags=").append(encode(tag));

        JSONObject payload = readJson(url.toString());
        String html = payload.optString("results_html", "");
        int total = payload.optInt("total_count", 0);
        Matcher rows = Pattern.compile("<a\\s+href=[\\s\\S]*?</a>").matcher(html);
        List<GameDeal> games = new ArrayList<>();

        while (rows.find()) {
            String row = rows.group();
            String appId = match(row, "data-ds-appid=\"(\\d+)\"");
            String title = match(row, "<span class=\"title\">([\\s\\S]*?)</span>");
            String image = match(row, "class=\"search_capsule\"><img src=\"([^\"]+)\"");
            String discountRaw = match(row, "data-discount=\"(\\d+)\"");
            String finalPriceRaw = match(row, "<div class=\"discount_final_price\">([\\s\\S]*?)</div>");
            int discount = discountRaw == null ? 0 : Integer.parseInt(discountRaw);
            String rowText = cleanText(row).toLowerCase();
            boolean freeWeekend = rowText.contains("free weekend") || rowText.contains("play for free")
                    || rowText.contains("бесплатные выходные") || rowText.contains("играть бесплатно");

            if (appId == null || title == null || image == null) continue;
            if (MODE_FREE.equals(mode) && discount != 100) continue;
            if (MODE_WEEKENDS.equals(mode) && !freeWeekend) continue;
            if ((MODE_DISCOUNTS.equals(mode) || MODE_BEST.equals(mode)) && discount < minimumDiscount) continue;
            String price = finalPriceRaw == null ? "Цена не указана" : cleanText(finalPriceRaw);
            games.add(new GameDeal(appId, cleanText(title), decodeHtml(image), discount, price, freeWeekend));
        }
        return new SearchResult(games, total);
    }

    public GameDetails details(String appId) throws Exception {
        Map<String, String> prices = new LinkedHashMap<>();
        JSONObject mainData = null;
        String[][] regions = {{"us", "США"}, {"de", "Германия"}, {"ru", "Россия"}, {"kz", "Казахстан"}};

        for (String[] region : regions) {
            JSONObject root = readJson("https://store.steampowered.com/api/appdetails?appids=" + appId + "&cc=" + region[0] + "&l=russian");
            JSONObject app = root.optJSONObject(appId);
            JSONObject data = app != null && app.optBoolean("success") ? app.optJSONObject("data") : null;
            if (mainData == null && data != null) mainData = data;
            prices.put(region[1], regionPrice(data));
        }

        if (mainData == null) throw new IllegalStateException("Steam не вернул подробности");
        StringBuilder genres = new StringBuilder();
        JSONArray genreArray = mainData.optJSONArray("genres");
        if (genreArray != null) {
            for (int i = 0; i < genreArray.length(); i++) {
                if (i > 0) genres.append(", ");
                genres.append(genreArray.getJSONObject(i).optString("description"));
            }
        }
        ReviewSummary reviews = reviews(appId);
        JSONObject requirements = mainData.optJSONObject("pc_requirements");
        String minimum = requirements == null ? "Не указаны" : cleanText(requirements.optString("minimum", "Не указаны"));
        String recommended = requirements == null ? "Не указаны" : cleanText(requirements.optString("recommended", "Не указаны"));
        return new GameDetails(
                mainData.optString("name", "Игра Steam"),
                mainData.optString("short_description", "Описание пока недоступно."),
                genres.length() == 0 ? "Жанр не указан" : genres.toString(),
                mainData.optString("header_image", ""),
                prices,
                reviews,
                minimum,
                recommended
        );
    }

    public ReviewSummary reviews(String appId) throws Exception {
        JSONObject root = readJson("https://store.steampowered.com/appreviews/" + appId
                + "?json=1&language=all&purchase_type=all&num_per_page=3");
        JSONObject summary = root.optJSONObject("query_summary");
        int positive = summary == null ? 0 : summary.optInt("total_positive", 0);
        int total = summary == null ? 0 : summary.optInt("total_reviews", 0);
        int percent = total == 0 ? 0 : Math.round(positive * 100f / total);
        List<String> samples = new ArrayList<>();
        JSONArray array = root.optJSONArray("reviews");
        if (array != null) {
            for (int i = 0; i < Math.min(3, array.length()); i++) {
                JSONObject review = array.optJSONObject(i);
                if (review == null) continue;
                String body = review.optString("review", "").replaceAll("\\s+", " ").trim();
                if (body.length() > 220) body = body.substring(0, 217) + "…";
                if (!body.isEmpty()) samples.add((review.optBoolean("voted_up") ? "👍 " : "👎 ") + body);
            }
        }
        return new ReviewSummary(percent, total, samples);
    }

    private String regionPrice(JSONObject data) {
        if (data == null) return "недоступно";
        if (data.optBoolean("is_free")) return "бесплатно";
        JSONObject price = data.optJSONObject("price_overview");
        if (price == null) return "нет цены";
        String formatted = price.optString("final_formatted", "нет цены");
        int discount = price.optInt("discount_percent", 0);
        return discount > 0 ? formatted + " (−" + discount + "%)" : formatted;
    }

    private JSONObject readJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(25_000);
        connection.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9");
        connection.setRequestProperty("User-Agent", "SteamHunter-Android/0.4");
        if (connection.getResponseCode() != 200) throw new IllegalStateException("Steam ответил кодом " + connection.getResponseCode());
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        } finally {
            connection.disconnect();
        }
        return new JSONObject(body.toString());
    }

    private String match(String source, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String cleanText(String value) {
        return decodeHtml(value.replaceAll("<[^>]+>", "").trim()).replaceAll("\\s+", " ");
    }

    private String decodeHtml(String value) {
        return value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    public static class GameDeal {
        public final String appId;
        public final String title;
        public final String imageUrl;
        public final int discount;
        public final String currentPrice;
        public final boolean freeWeekend;

        public GameDeal(String appId, String title, String imageUrl, int discount, String currentPrice) {
            this(appId, title, imageUrl, discount, currentPrice, false);
        }

        public GameDeal(String appId, String title, String imageUrl, int discount, String currentPrice, boolean freeWeekend) {
            this.appId = appId;
            this.title = title;
            this.imageUrl = imageUrl;
            this.discount = discount;
            this.currentPrice = currentPrice;
            this.freeWeekend = freeWeekend;
        }
    }

    public static class SearchResult {
        public final List<GameDeal> games;
        public final int totalCount;
        public SearchResult(List<GameDeal> games, int totalCount) { this.games = games; this.totalCount = totalCount; }
    }

    public static class GameDetails {
        public final String title;
        public final String description;
        public final String genres;
        public final String imageUrl;
        public final Map<String, String> regionalPrices;
        public final ReviewSummary reviews;
        public final String minimumRequirements;
        public final String recommendedRequirements;

        public GameDetails(String title, String description, String genres, String imageUrl, Map<String, String> regionalPrices,
                           ReviewSummary reviews, String minimumRequirements, String recommendedRequirements) {
            this.title = title;
            this.description = description;
            this.genres = genres;
            this.imageUrl = imageUrl;
            this.regionalPrices = regionalPrices;
            this.reviews = reviews;
            this.minimumRequirements = minimumRequirements;
            this.recommendedRequirements = recommendedRequirements;
        }
    }

    public static class ReviewSummary {
        public final int positivePercent;
        public final int totalReviews;
        public final List<String> samples;

        public ReviewSummary(int positivePercent, int totalReviews, List<String> samples) {
            this.positivePercent = positivePercent;
            this.totalReviews = totalReviews;
            this.samples = samples;
        }
    }
}
