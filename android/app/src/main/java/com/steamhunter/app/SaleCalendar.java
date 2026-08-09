package com.steamhunter.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/** Официально объявленные распродажи и фестивали Steam. */
public final class SaleCalendar {
    private SaleCalendar() { }

    public static List<Event> upcoming() {
        List<Event> events = new ArrayList<>();
        add(events, "Фестиваль фишек и шариков", "Фестиваль", 2026, 8, 17, 2026, 8, 20);
        add(events, "Фестиваль PvE-выживания", "Фестиваль", 2026, 8, 31, 2026, 9, 7);
        add(events, "Фестиваль программирования", "Фестиваль", 2026, 9, 10, 2026, 9, 14);
        add(events, "Фестиваль партийных RPG", "Фестиваль", 2026, 9, 14, 2026, 9, 21);
        add(events, "Осенняя распродажа Steam", "Большая распродажа", 2026, 10, 1, 2026, 10, 8);
        add(events, "Кулинарный фестиваль", "Фестиваль", 2026, 10, 12, 2026, 10, 19);
        add(events, "Steam Next Fest", "Фестиваль демоверсий", 2026, 10, 19, 2026, 10, 26);
        add(events, "Steam Scream V", "Фестиваль", 2026, 10, 26, 2026, 11, 2);
        add(events, "Фестиваль авто-баттлер RPG", "Фестиваль", 2026, 11, 16, 2026, 11, 23);
        add(events, "Зимняя распродажа Steam", "Большая распродажа", 2026, 12, 17, 2027, 1, 4);
        add(events, "Next Fest: февраль", "Фестиваль демоверсий", 2027, 2, 22, 2027, 3, 1);
        add(events, "Весенняя распродажа Steam", "Большая распродажа", 2027, 3, 18, 2027, 3, 25);
        add(events, "Next Fest: июнь", "Фестиваль демоверсий", 2027, 6, 14, 2027, 6, 21);
        add(events, "Летняя распродажа Steam", "Большая распродажа", 2027, 6, 24, 2027, 7, 8);
        long now = System.currentTimeMillis();
        events.removeIf(event -> event.end.getTime() < now);
        return events;
    }

    private static void add(List<Event> target, String title, String type,
                            int sy, int sm, int sd, int ey, int em, int ed) {
        target.add(new Event(title, type, date(sy, sm, sd), date(ey, em, ed)));
    }

    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Yekaterinburg"));
        calendar.set(year, month - 1, day, 22, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static class Event {
        public final String title;
        public final String type;
        public final Date start;
        public final Date end;

        Event(String title, String type, Date start, Date end) {
            this.title = title;
            this.type = type;
            this.start = start;
            this.end = end;
        }

        public String dates() {
            SimpleDateFormat format = new SimpleDateFormat("d MMMM yyyy", new Locale("ru", "RU"));
            return format.format(start) + " — " + format.format(end);
        }

        public String countdown() {
            long now = System.currentTimeMillis();
            if (now >= start.getTime() && now <= end.getTime()) return "Идёт прямо сейчас";
            long days = Math.max(0, TimeUnit.MILLISECONDS.toDays(start.getTime() - now));
            return days == 0 ? "Начнётся сегодня" : "До начала: " + days + " дн.";
        }
    }
}
