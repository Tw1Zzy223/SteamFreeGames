package com.steamhunter.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DealWorker extends Worker {
    private static final String CHANNEL_ID = "steam_hunter_deals";

    public DealWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(DealWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "steam-hunter-hourly-check", ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        UserStore store = new UserStore(getApplicationContext());
        if (store.quietHours()) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (hour >= 23 || hour < 8) return Result.success();
        }

        try {
            StoreClient client = new StoreClient();
            if (store.notifyFree()) {
                StoreClient.SearchResult free = client.search(StoreClient.MODE_FREE, "", 0, "_ASC", "", 100);
                process(free.games, "seen_worker_free", store, true);
            }
            if (store.notifyDiscounts()) {
                StoreClient.SearchResult discounts = client.search(StoreClient.MODE_DISCOUNTS, "", 0, "Reviews_DESC", "", store.notifyThreshold());
                process(discounts.games, "seen_worker_discounts", store, false);
            }
            return Result.success();
        } catch (Exception error) {
            return Result.retry();
        }
    }

    private void process(List<StoreClient.GameDeal> games, String key, UserStore store, boolean free) {
        Set<String> seen = store.seen(key);
        boolean firstRun = seen.isEmpty();
        Set<String> favorites = store.favorites();
        for (StoreClient.GameDeal game : games) {
            boolean allowed = !store.notifyFavoritesOnly() || favorites.contains(game.appId);
            if (!firstRun && !seen.contains(game.appId) && allowed) notifyGame(game, free);
            seen.add(game.appId);
        }
        store.saveSeen(key, new HashSet<>(seen));
    }

    private void notifyGame(StoreClient.GameDeal game, boolean free) {
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Игровые скидки", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/app/" + game.appId + "/"));
        PendingIntent pending = PendingIntent.getActivity(context, game.appId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        Notification notification = builder.setSmallIcon(android.R.drawable.star_big_on)
                .setContentTitle(free ? "Новую игру можно забрать бесплатно!" : "Новая скидка −" + game.discount + "%")
                .setContentText(game.title + " · " + game.currentPrice)
                .setContentIntent(pending).setAutoCancel(true).build();
        manager.notify(game.appId.hashCode(), notification);
    }
}
