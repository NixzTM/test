package com.nixz.autopilot2d;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Gesture-only accessibility service with a persistent floating Match-3 controller. */
public final class BotAccessibilityService extends AccessibilityService {
    private static volatile BotAccessibilityService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private LinearLayout overlay;
    private TextView overlayStatus;
    private Button overlayButton;
    private WindowManager.LayoutParams overlayParams;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        BotRuntime.accessibilityReady = true;
        BotRuntime.setStatus(BotRuntime.captureReady ? "Ready — open the game" : "Tap control enabled — prepare capture");
        // Important: controller must exist regardless of MediaProjection startup order.
        mainHandler.post(this::showOverlay);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        BotRuntime.accessibilityReady = false;
        ProjectionService.stopBotFromAnywhere("Tap control interrupted — stopped");
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        instance = null;
        BotRuntime.accessibilityReady = false;
        ProjectionService.stopBotFromAnywhere("Tap control disabled — stopped");
        super.onDestroy();
    }

    public static boolean tapNormalized(float normalizedX, float normalizedY) {
        BotAccessibilityService service = instance;
        return service != null && service.queueTap(normalizedX, normalizedY);
    }

    public static boolean tapPixels(float x, float y, long durationMs) {
        BotAccessibilityService service = instance;
        if (service == null || !BotRuntime.running) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(1, durationMs));
        return service.dispatchGameGesture(new GestureDescription.Builder().addStroke(stroke).build());
    }

    public static boolean swipePixels(float x1, float y1, float x2, float y2, long durationMs) {
        BotAccessibilityService service = instance;
        if (service == null || !BotRuntime.running) return false;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, Math.max(40, durationMs));
        return service.dispatchGameGesture(new GestureDescription.Builder().addStroke(stroke).build());
    }

    public static void showController() {
        BotAccessibilityService service = instance;
        if (service != null) service.mainHandler.post(service::showOverlay);
    }

    public static void hideController() {
        BotAccessibilityService service = instance;
        if (service != null) service.mainHandler.post(service::hideOverlay);
    }

    public static void refreshOverlay() {
        BotAccessibilityService service = instance;
        if (service != null) service.mainHandler.post(service::updateOverlay);
    }

    private boolean queueTap(float normalizedX, float normalizedY) {
        if (!BotRuntime.running) return false;
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Rect bounds;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = manager.getMaximumWindowMetrics();
            bounds = metrics.getBounds();
        } else {
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            manager.getDefaultDisplay().getRealMetrics(metrics);
            bounds = new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
        }
        float x = bounds.left + bounds.width() * normalizedX;
        float y = bounds.top + bounds.height() * normalizedY;
        Path path = new Path(); path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 18);
        return dispatchGameGesture(new GestureDescription.Builder().addStroke(stroke).build());
    }

    private boolean dispatchGameGesture(GestureDescription gesture) {
        setOverlayPassThrough(true);
        boolean ok = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                mainHandler.postDelayed(() -> setOverlayPassThrough(false), 45);
            }
            @Override public void onCancelled(GestureDescription g) {
                mainHandler.postDelayed(() -> setOverlayPassThrough(false), 45);
            }
        }, null);
        if (!ok) mainHandler.post(() -> setOverlayPassThrough(false));
        return ok;
    }

    private void setOverlayPassThrough(boolean pass) {
        if (overlay == null || overlayParams == null || windowManager == null) return;
        int old = overlayParams.flags;
        if (pass) overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else overlayParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (old != overlayParams.flags) {
            try { windowManager.updateViewLayout(overlay, overlayParams); }
            catch (RuntimeException ignored) {}
        }
    }

    private void showOverlay() {
        if (overlay != null) { updateOverlay(); return; }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int pad = dp(8);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(235, 15, 23, 34));
        background.setStroke(dp(1), Color.rgb(77, 163, 255));
        background.setCornerRadius(dp(12));
        overlay.setBackground(background);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("MATCH-3 AI");
        title.setTextColor(Color.rgb(244, 247, 251));
        title.setTextSize(11);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(28), 1));

        TextView close = new TextView(this);
        close.setText("×");
        close.setGravity(Gravity.CENTER);
        close.setTextColor(Color.rgb(170, 182, 198));
        close.setTextSize(20);
        close.setOnClickListener(view -> {
            ProjectionService.stopBotFromAnywhere("Stopped — controller hidden");
            hideOverlay();
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(30), dp(28)));
        overlay.addView(header, new LinearLayout.LayoutParams(dp(158), dp(28)));

        overlayStatus = new TextView(this);
        overlayStatus.setTextColor(Color.rgb(170, 182, 198));
        overlayStatus.setTextSize(10);
        overlayStatus.setMaxLines(3);
        overlayStatus.setPadding(0, dp(2), 0, dp(6));
        overlay.addView(overlayStatus, new LinearLayout.LayoutParams(dp(158), dp(52)));

        overlayButton = new Button(this);
        overlayButton.setTextSize(12);
        overlayButton.setTextColor(Color.WHITE);
        overlayButton.setAllCaps(false);
        overlayButton.setOnClickListener(view -> {
            if (BotRuntime.running) {
                ProjectionService.stopBotFromAnywhere("Stopped by you — no restart");
            } else if (BotRuntime.captureReady) {
                ProjectionService.startBotFromAnywhere();
            } else {
                Toast.makeText(this, "Prepare screen capture in AutoPilot 2D first", Toast.LENGTH_LONG).show();
            }
            updateOverlay();
        });
        overlay.addView(overlayButton, new LinearLayout.LayoutParams(dp(158), dp(46)));

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.END;
        overlayParams.x = dp(10);
        overlayParams.y = dp(150);

        final float[] dragStart = new float[4];
        title.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragStart[0] = event.getRawX(); dragStart[1] = event.getRawY();
                dragStart[2] = overlayParams.x; dragStart[3] = overlayParams.y;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                overlayParams.x = Math.max(0, (int)(dragStart[2] - (event.getRawX() - dragStart[0])));
                overlayParams.y = Math.max(0, (int)(dragStart[3] + (event.getRawY() - dragStart[1])));
                try { windowManager.updateViewLayout(overlay, overlayParams); } catch (RuntimeException ignored) {}
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_UP;
        });

        try {
            windowManager.addView(overlay, overlayParams);
            updateOverlay();
        } catch (RuntimeException e) {
            overlay = null; overlayStatus = null; overlayButton = null;
            BotRuntime.setStatus("Controller overlay failed — toggle accessibility service");
        }
    }

    private void updateOverlay() {
        if (overlay == null || overlayStatus == null || overlayButton == null) return;
        overlayStatus.setText(BotRuntime.status);
        overlayButton.setText(BotRuntime.running ? "■  STOP" : "▶  START BOT");
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(BotRuntime.running ? Color.rgb(190,55,65) : Color.rgb(40,122,210));
        buttonBackground.setCornerRadius(dp(9));
        overlayButton.setBackground(buttonBackground);
    }

    private void hideOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (RuntimeException ignored) {}
        }
        overlay = null; overlayStatus = null; overlayButton = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
