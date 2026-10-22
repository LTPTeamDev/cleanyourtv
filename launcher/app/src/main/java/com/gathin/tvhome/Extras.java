package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Local additions to the launcher, each one a switch: config.xml sets the default, a theme's config.json can
 * override it, and a choice made in Launcher settings on the TV wins over both. Kept apart from {@link Config}
 * so the upstream class stays untouched.
 */
final class Extras {
    /** The header Settings pill opens the quick panel instead of the TV's Settings. */
    static final String SETTINGS_PANEL = "settings_pill_panel";
    /** Sleep timer row in the quick panel. */
    static final String SLEEP_TIMER = "sleep_timer";
    /** "Free memory" row in the quick panel. */
    static final String FREE_MEMORY = "free_memory";
    /** Display & sound and Bluetooth rows in the quick panel. */
    static final String QUICK_TOGGLES = "quick_toggles";
    /** "Continue" pill with the Watch Next rows other apps publish (Lampa does). */
    static final String CONTINUE_WATCHING = "continue_watching";
    /** The weather line under the clock, from the Weather add-on. */
    static final String HOME_WEATHER = "home_weather";

    static final String[] KEYS = {SETTINGS_PANEL, SLEEP_TIMER, FREE_MEMORY, QUICK_TOGGLES, CONTINUE_WATCHING, HOME_WEATHER};

    private static final String PREF_PREFIX = "extra_";

    private final boolean[] values = new boolean[KEYS.length];

    private Extras() {}

    static Extras load(Context ctx) {
        Extras extras = new Extras();
        Resources res = ctx.getResources();
        JSONObject theme = readTheme(ctx);
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        for (int i = 0; i < KEYS.length; i++) {
            boolean value = res.getBoolean(defaultRes(KEYS[i]));
            if (theme != null && theme.opt(KEYS[i]) instanceof Boolean) value = theme.optBoolean(KEYS[i]);
            extras.values[i] = choices.getBoolean(PREF_PREFIX + KEYS[i], value);
        }
        return extras;
    }

    boolean on(String key) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return values[i];
        return false;
    }

    /** Saves an on-TV choice; the caller bumps the settings revision so Home rebuilds. */
    static void choose(Context ctx, String key, boolean value) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(PREF_PREFIX + key, value).apply();
    }

    /** True when any switch here was set on the TV (for "Follow theme"). */
    static boolean hasChoices(SharedPreferences choices) {
        for (String key : KEYS) if (choices.contains(PREF_PREFIX + key)) return true;
        return false;
    }

    static void clearChoices(SharedPreferences.Editor editor) {
        for (String key : KEYS) editor.remove(PREF_PREFIX + key);
    }

    private static int defaultRes(String key) {
        switch (key) {
            case SETTINGS_PANEL: return R.bool.settings_pill_panel;
            case SLEEP_TIMER: return R.bool.sleep_timer;
            case FREE_MEMORY: return R.bool.free_memory;
            case QUICK_TOGGLES: return R.bool.quick_toggles;
            case HOME_WEATHER: return R.bool.home_weather;
            default: return R.bool.continue_watching;
        }
    }

    private static JSONObject readTheme(Context ctx) {
        File dir = Config.dir(ctx);
        if (dir == null) return null;
        File file = new File(dir, Config.FILE_NAME);
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 256 * 1024)];
            int n = 0;
            while (n < data.length) {
                int r = in.read(data, n, data.length - n);
                if (r < 0) break;
                n += r;
            }
            return new JSONObject(new String(data, 0, n, StandardCharsets.UTF_8));
        } catch (IOException | org.json.JSONException e) {
            return null;
        }
    }
}
