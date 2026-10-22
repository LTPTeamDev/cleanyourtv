package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads what other apps publish to the TV provider: the system "Continue watching" row (Watch Next) and the
 * preview channels' programs. Needs READ_TV_LISTINGS (granted with `pm grant`); without it, or on a TV with
 * no provider, the lists are empty. No selection is passed: a caller without the EPG permission gets a
 * SecurityException for one (docs/watch-next.md), so rows are filtered here.
 */
final class WatchNext {
    private static final String TAG = "TvHome.WatchNext";
    /** Continue watching shows only what was watched within this long. */
    private static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;

    static final class Item {
        String pkg;
        String title;
        String poster;
        String thumbnail;
        String intentUri;
        long position;
        long duration;
        long engaged;

        /** Percent watched, or -1 when the app gave no position. */
        int percent() {
            if (position <= 0 || duration <= 0) return -1;
            return (int) Math.min(99, Math.max(1, position * 100 / duration));
        }
    }

    private WatchNext() {}

    /** The Continue watching row, most recently watched first. Runs off the main thread. */
    static List<Item> continueWatching(Context ctx, int limit) {
        String[] projection = {
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_BROWSABLE,
        };
        List<Item> items = read(ctx, TvContract.WatchNextPrograms.CONTENT_URI, projection, true);
        // Apps rarely clean up after themselves (RUTUBE left 86 rows from 2024 here), so only what was watched
        // recently counts; a row with no engagement time is treated as old.
        long since = System.currentTimeMillis() - MAX_AGE_MS;
        items.removeIf(item -> item.engaged < since);
        Collections.sort(items, (a, b) -> Long.compare(b.engaged, a.engaged));
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }

    /** Programs of every preview channel (Lampa's rows among them), for the poster screensaver. */
    static List<Item> previews(Context ctx, int limit) {
        String[] projection = {
                TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
                TvContract.PreviewPrograms.COLUMN_TITLE,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
                TvContract.PreviewPrograms.COLUMN_BROWSABLE,
        };
        List<Item> items = read(ctx, TvContract.PreviewPrograms.CONTENT_URI, projection, false);
        return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
    }

    private static List<Item> read(Context ctx, Uri uri, String[] projection, boolean watchNext) {
        List<Item> items = new ArrayList<>();
        try (Cursor c = ctx.getContentResolver().query(uri, projection, null, null, null)) {
            if (c == null) return items;
            while (c.moveToNext()) {
                if (c.getInt(c.getColumnIndexOrThrow(TvContract.WatchNextPrograms.COLUMN_BROWSABLE)) == 0) continue;
                Item item = new Item();
                item.pkg = string(c, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
                item.title = string(c, TvContract.WatchNextPrograms.COLUMN_TITLE);
                item.poster = string(c, TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
                item.thumbnail = string(c, TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI);
                item.intentUri = string(c, TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
                item.position = c.getLong(c.getColumnIndexOrThrow(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS));
                item.duration = c.getLong(c.getColumnIndexOrThrow(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS));
                if (watchNext) item.engaged = c.getLong(c.getColumnIndexOrThrow(
                        TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS));
                if (item.title == null || item.title.isEmpty()) continue;
                items.add(item);
            }
        } catch (RuntimeException e) {
            // No permission, no provider, or a column this firmware lacks: nothing to show.
            Log.w(TAG, "query " + uri + " failed: " + e);
        }
        return items;
    }

    private static String string(Cursor c, String column) {
        int i = c.getColumnIndex(column);
        return i < 0 || c.isNull(i) ? null : c.getString(i);
    }

    /** What opening the item starts: the app's own deep link, or the app itself. */
    static Intent intent(Context ctx, Item item) {
        if (item.intentUri != null) {
            try {
                return Intent.parseUri(item.intentUri, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ignored) {
                // fall through to the app
            }
        }
        if (item.pkg == null) return null;
        Intent launch = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(item.pkg);
        return launch != null ? launch : ctx.getPackageManager().getLaunchIntentForPackage(item.pkg);
    }
}
