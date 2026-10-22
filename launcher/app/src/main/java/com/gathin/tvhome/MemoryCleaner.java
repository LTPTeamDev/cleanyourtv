package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.List;

/**
 * "Free memory" in the quick panel (Extras.FREE_MEMORY): asks Android to stop the background processes of
 * installed apps (killBackgroundProcesses, a normal permission). The app on screen, this launcher and
 * Android's own core packages are left alone; anything an app needs comes back when it is opened again.
 * This is not a force stop, so apps can still restart themselves later.
 */
final class MemoryCleaner {
    private MemoryCleaner() {}

    /** Runs off the main thread. Returns the megabytes that became available (may be 0). */
    static long clean(Context ctx) {
        ActivityManager am = ctx.getSystemService(ActivityManager.class);
        long before = available(am);
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        for (ApplicationInfo app : apps) {
            if (app.packageName.equals(ctx.getPackageName())) continue;
            if ((app.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) continue;
            boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            // Plain system packages (Play services, the TV provider, Bluetooth, the keyboard) restart at once and
            // cost more than they free; user apps and updated system apps (YouTube, Play Store) are the ones
            // that linger.
            if (system && pm.getLeanbackLaunchIntentForPackage(app.packageName) == null) continue;
            try {
                am.killBackgroundProcesses(app.packageName);
            } catch (SecurityException ignored) {
                // Some firmware refuses it for particular packages; skip them.
            }
        }
        try {
            Thread.sleep(800);   // let the memory accounting catch up
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        long after = available(am);
        return Math.max(0, (after - before) / (1024 * 1024));
    }

    private static long available(ActivityManager am) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.availMem;
    }
}
