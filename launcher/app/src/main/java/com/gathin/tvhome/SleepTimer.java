package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Sleep timer for the quick panel (Extras.SLEEP_TIMER). An alarm fires {@link Receiver}, which asks
 * InputKeyService to lock the screen: on Android TV that puts the box to sleep, and with HDMI-CEC the TV
 * follows. Without the service running there is no way for an app to sleep the box, so the timer says so.
 */
final class SleepTimer {
    /** Choices the row cycles through, in minutes; 0 is off. */
    static final int[] STEPS = {0, 15, 30, 60, 90, 120};

    private static final String PREFS = "sleep_timer";
    private static final String KEY_AT = "at";

    private SleepTimer() {}

    /** Wall-clock time the box goes to sleep, or 0 when no timer is set. */
    static long endsAt(Context ctx) {
        long at = prefs(ctx).getLong(KEY_AT, 0);
        if (at <= System.currentTimeMillis()) return 0;
        // Updating the app (or a force stop) drops its alarms; then the saved time means nothing.
        PendingIntent alive = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, Receiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (alive == null) {
            prefs(ctx).edit().remove(KEY_AT).apply();
            return 0;
        }
        return at;
    }

    /** Minutes left, rounded up; 0 when off. */
    static int minutesLeft(Context ctx) {
        long at = endsAt(ctx);
        if (at == 0) return 0;
        return (int) Math.max(1, (at - System.currentTimeMillis() + 59_999) / 60_000);
    }

    /** The next step after the one closest to what is left now: Off → 15 → 30 → … → Off. */
    static int nextStep(Context ctx) {
        int left = minutesLeft(ctx);
        for (int step : STEPS) if (step > left) return step;
        return 0;
    }

    static void set(Context ctx, int minutes) {
        AlarmManager alarms = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = pending(ctx);
        alarms.cancel(pi);
        if (minutes <= 0) {
            prefs(ctx).edit().remove(KEY_AT).apply();
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        long trigger = SystemClock.elapsedRealtime() + minutes * 60_000L;
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
        }
        prefs(ctx).edit().putLong(KEY_AT, at).apply();
    }

    private static PendingIntent pending(Context ctx) {
        return PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, Receiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The alarm. Not exported: only this app's own PendingIntent reaches it. */
    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            prefs(ctx).edit().remove(KEY_AT).apply();
            if (!InputKeyService.sleepNow()) {
                Toast.makeText(ctx, R.string.sleep_no_service, Toast.LENGTH_LONG).show();
            }
        }
    }
}
