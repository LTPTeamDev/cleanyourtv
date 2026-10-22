package io.github.sumyabazar1.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.SystemClock;

/**
 * The weather now for the Clean Your TV launcher's home line (content://io.github.sumyabazar1.weather.home):
 * one row with the place, the temperature, the words for the sky, feels-like, today's high and low and the
 * air quality. It reads Open-Meteo itself when its copy is older than {@link #FRESH_MS}, on the caller's
 * binder thread, so the launcher asks from a background thread. Only the launcher gets an answer.
 */
public final class HomeProvider extends ContentProvider {
    static final String LAUNCHER = "com.gathin.tvhome";
    private static final long FRESH_MS = 30 * 60 * 1000L;
    private static final String[] COLUMNS = {"place", "temp", "feels", "code", "sky", "max", "min", "aqi", "aqi_label", "updated"};

    private static Forecast.Report cached;
    private static long cachedAt;
    private static long cachedUpdatedWall;
    private static String cachedFor;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!LAUNCHER.equals(getCallingPackage())) return null;
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        SharedPreferences place = getContext().getSharedPreferences(WeatherActivity.PREFS, android.content.Context.MODE_PRIVATE);
        String name = place.getString("name", "");
        if (name.isEmpty()) return cursor;   // no city chosen yet
        double lat = Double.longBitsToDouble(place.getLong("lat", 0));
        double lon = Double.longBitsToDouble(place.getLong("lon", 0));
        String key = lat + "," + lon;
        Forecast.Report report;
        long updated;
        synchronized (HomeProvider.class) {
            if (cached == null || !key.equals(cachedFor) || SystemClock.elapsedRealtime() - cachedAt > FRESH_MS) {
                try {
                    cached = Forecast.fetch(lat, lon);
                    cachedAt = SystemClock.elapsedRealtime();
                    cachedUpdatedWall = System.currentTimeMillis();
                    cachedFor = key;
                } catch (Exception e) {
                    // Offline: keep showing the last good reading if it is for this place.
                    if (cached == null || !key.equals(cachedFor)) return cursor;
                }
            }
            report = cached;
            updated = cachedUpdatedWall;
        }
        String sky = getContext().getString(Forecast.sky(report.code, report.day));
        String aqiLabel = report.aqi >= 0 ? getContext().getString(Forecast.aqiLabel(report.aqi)) : "";
        cursor.addRow(new Object[]{name, report.tempC, report.feelsC, report.code, sky,
                report.todayMaxC, report.todayMinC, report.aqi, aqiLabel, updated});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.io.github.sumyabazar1.weather.now";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
