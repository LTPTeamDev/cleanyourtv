package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Screensaver with the artwork of what other apps publish to the TV provider: Lampa's preview channels and the
 * Continue watching row, one picture at a time, crossfading every {@link #SLIDE_MS}, with the clock and the
 * title. Falls back to the clock alone when there is nothing to show. Pick it in Settings → Screensaver
 * ("Posters"); ClockDream stays the plain one.
 */
public class PosterDream extends DreamService {
    private static final long SLIDE_MS = 12_000;
    private static final int MAX_ITEMS = 40;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<WatchNext.Item> items = new ArrayList<>();
    private ImageView[] pictures;
    private View scrim;
    private TextView title;
    private int front;
    private int next;
    private boolean running;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        pictures = new ImageView[]{picture(root), picture(root)};

        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x66000000, 0x00000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(360), Gravity.BOTTOM));

        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("H:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        caption.addView(clock);
        title = new TextView(this);
        title.setTextColor(0xCCFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.addView(title);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        lp.setMargins(dp(64), 0, dp(64), dp(48));
        root.addView(caption, lp);
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        running = true;
        new Thread(() -> {
            List<WatchNext.Item> found = new ArrayList<>(WatchNext.previews(this, MAX_ITEMS));
            found.addAll(WatchNext.continueWatching(this, 10));
            // One picture per title, in random order.
            Map<String, WatchNext.Item> unique = new LinkedHashMap<>();
            for (WatchNext.Item item : found) {
                if (art(item) != null && !unique.containsKey(item.title)) unique.put(item.title, item);
            }
            List<WatchNext.Item> list = new ArrayList<>(unique.values());
            Collections.shuffle(list);
            handler.post(() -> {
                items.clear();
                items.addAll(list);
                if (running) showNext();
            });
        }, "poster-dream").start();
    }

    @Override
    public void onDreamingStopped() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    private void showNext() {
        if (!running || items.isEmpty()) return;
        WatchNext.Item item = items.get(next++ % items.size());
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        Posters.load(this, art(item), w / 2, h / 2, bitmap -> {
            if (!running) return;
            if (bitmap != null) crossfade(bitmap, item.title);
            handler.postDelayed(this::showNext, bitmap != null ? SLIDE_MS : 500);
        });
    }

    private void crossfade(Bitmap bitmap, String name) {
        ImageView incoming = pictures[1 - front];
        ImageView outgoing = pictures[front];
        incoming.setImageBitmap(bitmap);
        incoming.setAlpha(0f);
        incoming.setScaleX(1.04f);
        incoming.setScaleY(1.04f);
        incoming.bringToFront();
        incoming.animate().alpha(1f).setDuration(1200).start();
        // A slow push-in while it is up, cheap on the GPU.
        incoming.animate().scaleX(1f).scaleY(1f).setDuration(SLIDE_MS).start();
        outgoing.animate().alpha(0f).setDuration(1200).start();
        front = 1 - front;
        title.setText(name);
        // Keep the shade and the caption above the pictures.
        scrim.bringToFront();
        ((View) title.getParent()).bringToFront();
    }

    /** Wide art first: the picture fills a 16:9 screen better than a 2:3 poster. */
    private static String art(WatchNext.Item item) {
        if (item.thumbnail != null && !item.thumbnail.isEmpty()) return item.thumbnail;
        return item.poster;
    }

    private ImageView picture(FrameLayout root) {
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setAlpha(0f);
        root.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
