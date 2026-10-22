package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * The home line's weather (Extras.HOME_WEATHER), read from the Weather add-on's provider: it keeps the city and
 * talks to Open-Meteo, so the launcher only asks it. Runs off the main thread; null when the add-on is missing,
 * has no city yet, or could not reach Open-Meteo.
 */
final class HomeWeather {
    static final Uri URI = Uri.parse("content://io.github.sumyabazar1.weather.home");
    /** How often the line is refreshed while Home is on screen; the add-on caches for as long. */
    static final long REFRESH_MS = 30 * 60 * 1000L;

    private HomeWeather() {}

    /** "Москва  +13° Переменная облачность · ощущается +11° · ↑+18° ↓+11°" */
    static String line(Context ctx) {
        try (Cursor c = ctx.getContentResolver().query(URI, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            String place = c.getString(c.getColumnIndexOrThrow("place"));
            int temp = c.getInt(c.getColumnIndexOrThrow("temp"));
            int feels = c.getInt(c.getColumnIndexOrThrow("feels"));
            String sky = c.getString(c.getColumnIndexOrThrow("sky"));
            int max = c.getInt(c.getColumnIndexOrThrow("max"));
            int min = c.getInt(c.getColumnIndexOrThrow("min"));
            StringBuilder line = new StringBuilder();
            line.append(place).append("  ").append(degrees(temp)).append(' ').append(sky);
            if (feels != temp) line.append(" · ").append(ctx.getString(R.string.weather_feels, degrees(feels)));
            if (max != Integer.MIN_VALUE && min != Integer.MIN_VALUE) {
                line.append(" · ↑").append(degrees(max)).append(" ↓").append(degrees(min));
            }
            return line.toString();
        } catch (RuntimeException e) {
            return null;   // add-on not installed (unknown authority) or an older version of it
        }
    }

    private static String degrees(int t) {
        return (t > 0 ? "+" : t < 0 ? "−" : "") + Math.abs(t) + "°";
    }
}
