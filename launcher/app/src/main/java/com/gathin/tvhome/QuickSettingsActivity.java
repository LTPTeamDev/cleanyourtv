package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService), and the
 * header Settings pill when Extras.SETTINGS_PANEL is on: a few glass rows under the Settings pill, over Home or
 * whatever app is playing. Translucent and in a task of its own, so the app behind keeps running and Back
 * returns to it. Pressing Settings again closes the panel.
 */
public final class QuickSettingsActivity extends Activity {

    /** Google TV's Settings: pairing a remote or another Bluetooth accessory. */
    private static final ComponentName ADD_ACCESSORY = new ComponentName(
            "com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity");

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        Extras extras = Extras.load(this);
        // A fixed width: the separator line would otherwise stretch the wrap_content panel across the screen.
        findViewById(R.id.panel).getLayoutParams().width =
                (int) (340 * getResources().getDisplayMetrics().density);
        boolean added = false;
        if (extras.on(Extras.SLEEP_TIMER)) {
            addSleepRow(rows);
            added = true;
        }
        // Android 14 lets an app stop only its own background processes, so the row would always free 0 MB.
        if (extras.on(Extras.FREE_MEMORY) && android.os.Build.VERSION.SDK_INT < 34) {
            addMemoryRow(rows);
            added = true;
        }
        if (extras.on(Extras.QUICK_TOGGLES)) {
            addRow(rows, R.drawable.ic_picture, R.string.quick_display_sound, 0,
                    SystemSettings.intent(this, Settings.ACTION_SOUND_SETTINGS));
            addRow(rows, R.drawable.ic_bluetooth, R.string.quick_bluetooth, 0, bluetoothIntent());
            added = true;
        }
        if (added) addSeparator(rows);
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0, SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        if (!extras.on(Extras.QUICK_TOGGLES)) {
            addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0, SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS));
        }
        addRow(rows, R.drawable.ic_wifi, R.string.quick_network, 0, SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        rows.getChildAt(0).requestFocus();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private Intent bluetoothIntent() {
        Intent accessory = new Intent().setComponent(ADD_ACCESSORY);
        if (getPackageManager().resolveActivity(accessory, PackageManager.MATCH_DEFAULT_ONLY) != null) return accessory;
        return SystemSettings.intent(this, Settings.ACTION_BLUETOOTH_SETTINGS);
    }

    private void addSleepRow(LinearLayout rows) {
        View row = inflateRow(rows, R.drawable.ic_timer, R.string.sleep_timer);
        TextView status = row.findViewById(R.id.status);
        showSleep(status);
        row.setOnClickListener(v -> {
            int minutes = SleepTimer.nextStep(this);
            SleepTimer.set(this, minutes);
            showSleep(status);
        });
        rows.addView(row);
    }

    private void showSleep(TextView status) {
        int left = SleepTimer.minutesLeft(this);
        status.setText(left == 0 ? getString(R.string.off) : getString(R.string.sleep_in, left));
        status.setVisibility(View.VISIBLE);
    }

    private void addMemoryRow(LinearLayout rows) {
        View row = inflateRow(rows, R.drawable.ic_memory, R.string.free_memory);
        TextView status = row.findViewById(R.id.status);
        status.setVisibility(View.GONE);
        boolean[] busy = {false};   // not setEnabled(false): that would take the focus off the row
        row.setOnClickListener(v -> {
            if (busy[0]) return;
            busy[0] = true;
            status.setText(R.string.free_memory_running);
            status.setVisibility(View.VISIBLE);
            new Thread(() -> {
                long freed = MemoryCleaner.clean(getApplicationContext());
                runOnUiThread(() -> {
                    busy[0] = false;
                    status.setText(getString(R.string.free_memory_done, freed));
                });
            }, "free-memory").start();
        });
        rows.addView(row);
    }

    private View inflateRow(LinearLayout rows, int icon, int title) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        return row;
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = inflateRow(rows, icon, title);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }
}
