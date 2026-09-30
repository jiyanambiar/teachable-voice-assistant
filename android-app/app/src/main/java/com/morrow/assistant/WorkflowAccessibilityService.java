package com.morrow.assistant;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class WorkflowAccessibilityService extends AccessibilityService {
    static final String ACTION_RECORDING_CHANGED = "com.morrow.assistant.RECORDING_CHANGED";
    static final String ACTION_PLAY = "com.morrow.assistant.PLAY";
    static final String ACTION_GO_HOME = "com.morrow.assistant.GO_HOME";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View overlay;
    private String recordedForegroundPackage = "";
    private String playbackPackage = "";
    private String playbackTargetPackage = "";
    private JSONArray playbackActions;
    private int playbackIndex;
    private boolean playbackPaused;
    private int expectedPackageRetries;
    private long lastPlaybackActionTime;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_PLAY.equals(intent.getAction())) startPlayback();
            if (ACTION_RECORDING_CHANGED.equals(intent.getAction())) recordedForegroundPackage = "";
            syncRecordingOverlay();
            if (ACTION_GO_HOME.equals(intent.getAction())) handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_HOME), 250);
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected(); windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        IntentFilter filter = new IntentFilter(); filter.addAction(ACTION_RECORDING_CHANGED); filter.addAction(ACTION_PLAY); filter.addAction(ACTION_GO_HOME);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        syncRecordingOverlay();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null || event.getPackageName().toString().equals(getPackageName())) return;
        String eventPackage = event.getPackageName().toString().toLowerCase(java.util.Locale.ROOT);
        int type = event.getEventType();
        if (isCurrentKeyboardPackage(eventPackage)) {
            if (type == AccessibilityEvent.TYPE_VIEW_CLICKED
                    && getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getBoolean("recording", false)) {
                recordKeyboardSubmit(event);
            }
            return;
        }
        if (eventPackage.contains("launcher")) {
            if (getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getBoolean("recording", false)
                    && getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getString("draft_target_package", "").isEmpty()) {
                recordedForegroundPackage = "";
            }
            return;
        }
        if (eventPackage.contains("systemui") || eventPackage.contains("permissioncontroller")) return;
        if (!getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getBoolean("recording", false)) return;
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            android.content.SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            String targetPackage = prefs.getString("draft_target_package", "");
            if (targetPackage.isEmpty()) {
                if (!isLaunchableApp(eventPackage)) return;
                targetPackage = eventPackage;
                prefs.edit().putString("draft_target_package", targetPackage).apply();
                recordedForegroundPackage = targetPackage;
                appendLaunchAction(targetPackage);
            } else if (targetPackage.equals(eventPackage) && !targetPackage.equals(recordedForegroundPackage)) {
                recordedForegroundPackage = targetPackage;
                appendLaunchAction(targetPackage);
            }
            return;
        }
        if (!eventPackage.equals(recordedForegroundPackage)) return;
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED && type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;
        AccessibilityNodeInfo source = event.getSource(); if (source == null) return;
        try {
            // Never persist password-style input, even when the app exposes it as a text event.
            if (source.isPassword()) return;
            JSONObject action = new JSONObject();
            String packageName = String.valueOf(event.getPackageName());
            String viewId = source.getViewIdResourceName() == null ? "" : source.getViewIdResourceName();
            String text = source.getText() == null ? "" : source.getText().toString();
            String desc = source.getContentDescription() == null ? "" : source.getContentDescription().toString();
            Rect bounds = new Rect(); source.getBoundsInScreen(bounds);
            String actionType = type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ? "text"
                    : type == AccessibilityEvent.TYPE_VIEW_SCROLLED ? "scroll" : "click";
            action.put("type", actionType);
            action.put("package", packageName); action.put("viewId", viewId);
            action.put("label", !text.trim().isEmpty() ? text : desc);
            action.put("value", type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ? text : "");
            action.put("class", source.getClassName() == null ? "" : source.getClassName().toString());
            action.put("x", bounds.centerX() / (float)getResources().getDisplayMetrics().widthPixels);
            action.put("y", bounds.centerY() / (float)getResources().getDisplayMetrics().heightPixels);
            if ("scroll".equals(actionType)) {
                int delta = Build.VERSION.SDK_INT >= 28 ? event.getScrollDeltaY() : event.getScrollY();
                action.put("direction", delta < 0 ? "backward" : "forward");
                action.put("height", bounds.height() / (float)getResources().getDisplayMetrics().heightPixels);
            }
            action.put("time", System.currentTimeMillis());
            appendAction(action);
        } catch (Exception ignored) { }
        finally { source.recycle(); }
    }

    private boolean isCurrentKeyboardPackage(String eventPackage) {
        String component = android.provider.Settings.Secure.getString(getContentResolver(),
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        if (component == null) return false;
        int separator = component.indexOf('/');
        String keyboardPackage = (separator < 0 ? component : component.substring(0, separator))
                .toLowerCase(java.util.Locale.ROOT);
        return eventPackage.equals(keyboardPackage);
    }

    private boolean isLaunchableApp(String packageName) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            return launch != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void recordKeyboardSubmit(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;
        try {
            String text = source.getText() == null ? "" : source.getText().toString();
            String description = source.getContentDescription() == null ? "" : source.getContentDescription().toString();
            String label = !text.trim().isEmpty() ? text : description;
            String normalized = label.toLowerCase(java.util.Locale.ROOT).trim();
            if (!normalized.equals("search") && !normalized.equals("go") && !normalized.equals("done")
                    && !normalized.equals("enter") && !normalized.equals("send")) return;
            if (recordedForegroundPackage.isEmpty()) return;
            Rect bounds = new Rect();
            source.getBoundsInScreen(bounds);
            JSONObject action = new JSONObject();
            action.put("type", "ime");
            action.put("package", recordedForegroundPackage);
            action.put("label", label);
            action.put("x", bounds.centerX() / (float)getResources().getDisplayMetrics().widthPixels);
            action.put("y", bounds.centerY() / (float)getResources().getDisplayMetrics().heightPixels);
            action.put("time", System.currentTimeMillis());
            appendAction(action);
        } catch (Exception ignored) { }
        finally { source.recycle(); }
    }

    private void appendLaunchAction(String packageName) {
        try {
            JSONObject action = new JSONObject();
            action.put("type", "launch");
            action.put("package", packageName);
            action.put("time", System.currentTimeMillis());
            appendAction(action);
        } catch (Exception ignored) { }
    }

    private void appendAction(JSONObject action) {
        android.content.SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        try {
            JSONArray actions = new JSONArray(prefs.getString("draft_actions", "[]"));
            if ("text".equals(action.optString("type"))) {
                int last = actions.length() - 1;
                JSONObject previous = last >= 0 ? actions.optJSONObject(last) : null;
                if (previous != null && "text".equals(previous.optString("type")) && sameTextTarget(previous, action)) {
                    actions.put(last, action); prefs.edit().putString("draft_actions", actions.toString()).apply(); return;
                }
            }
            actions.put(action); prefs.edit().putString("draft_actions", actions.toString()).apply();
        } catch (Exception ignored) { }
    }

    private boolean sameTextTarget(JSONObject first, JSONObject second) {
        if (!first.optString("package").equals(second.optString("package"))) return false;
        String firstId = first.optString("viewId", "");
        String secondId = second.optString("viewId", "");
        if (!firstId.isEmpty() || !secondId.isEmpty()) return firstId.equals(secondId);
        if (!first.optString("class").equals(second.optString("class"))) return false;
        return Math.abs(first.optDouble("x", -1) - second.optDouble("x", -2)) < .04
                && Math.abs(first.optDouble("y", -1) - second.optDouble("y", -2)) < .04;
    }

    private void syncRecordingOverlay() {
        if (windowManager == null) return;
        boolean recording = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getBoolean("recording", false);
        if (recording && overlay == null) showOverlay();
        else if (!recording && overlay != null) { windowManager.removeView(overlay); overlay = null; }
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void showOverlay() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(14), dp(7), dp(8), dp(7)); bar.setBackgroundColor(0xff252d51);
        TextView title = new TextView(this); title.setText("FRIDAY is learning"); title.setTextColor(0xffffffff); title.setTextSize(13); bar.addView(title);
        Button finish = new Button(this); finish.setText("Finish"); finish.setAllCaps(false); finish.setTextSize(12); finish.setOnClickListener(v -> finishRecording(true)); bar.addView(finish);
        Button cancel = new Button(this); cancel.setText("Cancel"); cancel.setAllCaps(false); cancel.setTextSize(12); cancel.setOnClickListener(v -> finishRecording(false)); bar.addView(cancel);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; params.y = dp(30);
        overlay = bar; windowManager.addView(overlay, params);
    }

    private void finishRecording(boolean save) {
        android.content.SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        JSONArray actions;
        try { actions = new JSONArray(prefs.getString("draft_actions", "[]")); } catch (Exception e) { actions = new JSONArray(); }
        if (save && actions.length() > 0) {
            try {
                JSONObject routine = new JSONObject(); routine.put("id", prefs.getString("draft_id", ""));
                routine.put("name", prefs.getString("draft_name", "Routine")); routine.put("phrase", prefs.getString("draft_phrase", "Routine"));
                routine.put("targetPackage", prefs.getString("draft_target_package", ""));
                routine.put("actions", actions); routine.put("createdAt", System.currentTimeMillis());
                JSONArray saved = new JSONArray(prefs.getString(MainActivity.ROUTINES, "[]"));
                JSONArray all = new JSONArray();
                all.put(routine);
                for (int i = 0; i < saved.length(); i++) {
                    JSONObject existing = saved.optJSONObject(i);
                    if (existing != null) all.put(existing);
                }
                prefs.edit().putString(MainActivity.ROUTINES, all.toString()).apply();
                android.widget.Toast.makeText(this, "Saved " + routine.optString("name") + " (" + actions.length() + " steps)", android.widget.Toast.LENGTH_LONG).show();
            } catch (Exception ignored) { }
        } else android.widget.Toast.makeText(this, save ? "No steps were captured. Try again." : "Teaching canceled.", android.widget.Toast.LENGTH_LONG).show();
        prefs.edit().putBoolean("recording", false).putString("draft_actions", "[]")
                .putString("draft_target_package", "").apply(); syncRecordingOverlay();
    }

    private void startPlayback() {
        android.content.SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        try { playbackActions = new JSONArray(prefs.getString("play_actions", "[]")); }
        catch (Exception e) { playbackActions = new JSONArray(); }
        playbackIndex = 0; playbackPackage = ""; playbackPaused = false; expectedPackageRetries = 0; lastPlaybackActionTime = 0;
        playbackTargetPackage = prefs.getString("play_target_package", "").toLowerCase(java.util.Locale.ROOT);
        if (playbackTargetPackage.isEmpty()) {
            for (int i = 0; i < playbackActions.length(); i++) {
                JSONObject action = playbackActions.optJSONObject(i);
                if (action == null || !"launch".equals(action.optString("type"))) continue;
                String candidate = action.optString("package", "").toLowerCase(java.util.Locale.ROOT);
                if (!candidate.isEmpty() && !isCurrentKeyboardPackage(candidate)) {
                    playbackTargetPackage = candidate;
                    break;
                }
            }
        }
        if (playbackActions.length() == 0) { prefs.edit().putBoolean("playing", false).apply(); return; }
        stepPlayback();
    }

    private void stepPlayback() {
        if (playbackActions == null || playbackIndex >= playbackActions.length()) {
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putBoolean("playing", false).apply();
            android.widget.Toast.makeText(this, "Routine finished.", android.widget.Toast.LENGTH_SHORT).show(); return;
        }
        JSONObject action = playbackActions.optJSONObject(playbackIndex++); if (action == null) { handler.post(this::stepPlayback); return; }
        String pkg = action.optString("package", "");
        if (!pkg.isEmpty() && isCurrentKeyboardPackage(pkg.toLowerCase(java.util.Locale.ROOT))) {
            handler.post(this::stepPlayback);
            return;
        }
        if (!playbackTargetPackage.isEmpty() && !pkg.isEmpty()
                && !playbackTargetPackage.equals(pkg.toLowerCase(java.util.Locale.ROOT))) {
            handler.post(this::stepPlayback);
            return;
        }
        long recordedTime = action.optLong("time", 0);
        long recordedDelay = lastPlaybackActionTime == 0 || recordedTime <= lastPlaybackActionTime
                ? 350 : Math.max(350, Math.min(5000, recordedTime - lastPlaybackActionTime));
        lastPlaybackActionTime = recordedTime;
        if (!pkg.isEmpty() && !pkg.equals(playbackPackage)) {
            playbackPackage = pkg;
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) { launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); }
            } catch (Exception ignored) { }
            handler.postDelayed(() -> performAction(action), Math.max(1600, recordedDelay)); return;
        }
        handler.postDelayed(() -> performAction(action), recordedDelay);
    }

    private void performAction(JSONObject action) {
        if ("launch".equals(action.optString("type"))) {
            expectedPackageRetries = 0;
            handler.postDelayed(this::stepPlayback, 350);
            return;
        }
        if ("ime".equals(action.optString("type"))) {
            boolean submitted = false;
            if (Build.VERSION.SDK_INT >= 30) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                AccessibilityNodeInfo focused = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null) {
                    submitted = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
                    focused.recycle();
                }
                if (root != null) root.recycle();
            }
            if (submitted) handler.postDelayed(this::stepPlayback, 700);
            else performRecordedGesture(action);
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow(); boolean performed = false;
        String expectedPackage = action.optString("package", "");
        if (!expectedPackage.isEmpty() && (root == null || !expectedPackage.equals(String.valueOf(root.getPackageName())))) {
            if (root != null) root.recycle();
            if (++expectedPackageRetries <= 8) {
                playbackIndex = Math.max(0, playbackIndex - 1);
                handler.postDelayed(this::stepPlayback, 300);
            } else pausePlayback(action, "The expected app screen did not open. Open it yourself, then retry or skip this step.");
            return;
        }
        expectedPackageRetries = 0;
        if (root != null && isSensitiveScreen(root)) {
            root.recycle();
            pausePlayback(action, "Sign-in or payment screen detected. Complete it yourself, then resume.");
            return;
        }
        if (root != null) {
            String id = action.optString("viewId", "");
            if (!id.isEmpty()) {
                try { List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(id); if (found != null && !found.isEmpty()) performed = applyToNode(found.get(0), action); }
                catch (Exception ignored) { }
            }
            if (!performed && !action.optString("label", "").trim().isEmpty()) {
                try {
                    List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(action.optString("label"));
                    if (found != null) for (AccessibilityNodeInfo node : found) { if (applyToNode(node, action)) { performed = true; break; } }
                } catch (Exception ignored) { }
            }
            root.recycle();
        }
        if (!performed && ("click".equals(action.optString("type")) || "text".equals(action.optString("type"))
                || "scroll".equals(action.optString("type")))) {
            performRecordedGesture(action);
            return;
        }
        handler.postDelayed(this::stepPlayback, "click".equals(action.optString("type")) ? 650 : 350);
    }

    private void performRecordedGesture(JSONObject action) {
        if (Build.VERSION.SDK_INT < 24) {
            pausePlayback(action, "This Android version cannot replay recorded screen taps.");
            return;
        }
        float x = (float) (action.optDouble("x", .5) * getResources().getDisplayMetrics().widthPixels);
        float y = (float) (action.optDouble("y", .5) * getResources().getDisplayMetrics().heightPixels);
        Path path = new Path();
        boolean scrolling = "scroll".equals(action.optString("type"));
        if (scrolling) {
            float distance = Math.max(dp(120), (float)(action.optDouble("height", .5) * getResources().getDisplayMetrics().heightPixels * .45));
            float startY = "backward".equals(action.optString("direction")) ? y - distance / 2 : y + distance / 2;
            float endY = "backward".equals(action.optString("direction")) ? y + distance / 2 : y - distance / 2;
            path.moveTo(x, Math.max(dp(20), startY));
            path.lineTo(x, Math.min(getResources().getDisplayMetrics().heightPixels - dp(20), endY));
        } else path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, scrolling ? 420 : 80));
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if ("text".equals(action.optString("type"))) {
                    handler.postDelayed(() -> setTextOnFocusedField(action), 280);
                } else {
                    handler.postDelayed(WorkflowAccessibilityService.this::stepPlayback, 450);
                }
            }

            @Override public void onCancelled(GestureDescription gesture) {
                pausePlayback(action, "The recorded tap could not be replayed. Retry this step or skip it.");
            }
        }, handler);
    }

    private void setTextOnFocusedField(JSONObject action) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        AccessibilityNodeInfo focused = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        boolean updated = false;
        if (focused != null) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.optString("value", ""));
            updated = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            focused.recycle();
        }
        if (root != null) root.recycle();
        if (updated) handler.postDelayed(this::stepPlayback, 250);
        else pausePlayback(action, "I couldn't enter the recorded text. Tap the field yourself, then retry or skip this step.");
    }

    private boolean isSensitiveScreen(AccessibilityNodeInfo root) {
        StringBuilder visibleText = new StringBuilder();
        collectVisibleText(root, visibleText, 0);
        String screen = visibleText.toString().toLowerCase(java.util.Locale.ROOT);
        String[] markers = {"sign in", "log in", "login", "password", "passcode", "one-time code", "verification code", "authenticate", "payment", "checkout", "credit card", "debit card", "cvv", "cvc", "pay now"};
        for (String marker : markers) if (screen.contains(marker)) return true;
        return false;
    }

    private void collectVisibleText(AccessibilityNodeInfo node, StringBuilder output, int depth) {
        if (node == null || depth > 12) return;
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (text != null) output.append(' ').append(text);
        if (description != null) output.append(' ').append(description);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { collectVisibleText(child, output, depth + 1); child.recycle(); }
        }
    }

    private void pausePlayback(JSONObject action, String message) {
        if (playbackPaused || windowManager == null) return;
        playbackPaused = true;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(16), dp(10));
        bar.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xff315fba, 0xff704ed6, 0xffcd53ab}));
        ((android.graphics.drawable.GradientDrawable) bar.getBackground()).setCornerRadius(dp(18));
        TextView title = new TextView(this); title.setText("FRIDAY paused"); title.setTextColor(0xffffffff); title.setTextSize(15);
        TextView detail = new TextView(this); detail.setText(message); detail.setTextColor(0xffe7e4ff); detail.setTextSize(13); detail.setPadding(0, dp(5), 0, dp(8));
        TextView countdown = new TextView(this); countdown.setText("Waiting for you to retry, skip, or stop"); countdown.setTextColor(0xffe7e4ff); countdown.setTextSize(12); countdown.setPadding(0, 0, 0, dp(8));
        LinearLayout buttons = new LinearLayout(this);
        Button retry = new Button(this); retry.setText("Retry step"); retry.setAllCaps(false);
        retry.setOnClickListener(v -> { windowManager.removeView(bar); overlay = null; playbackPaused = false; playbackIndex = Math.max(0, playbackIndex - 1); stepPlayback(); });
        Button skip = new Button(this); skip.setText("Skip step"); skip.setAllCaps(false);
        skip.setOnClickListener(v -> { windowManager.removeView(bar); overlay = null; playbackPaused = false; handler.post(this::stepPlayback); });
        Button stop = new Button(this); stop.setText("Stop"); stop.setAllCaps(false);
        stop.setOnClickListener(v -> { windowManager.removeView(bar); overlay = null; playbackPaused = false; playbackActions = null; getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putBoolean("playing", false).apply(); });
        buttons.addView(retry); buttons.addView(skip); buttons.addView(stop);
        bar.addView(title); bar.addView(detail); bar.addView(countdown); bar.addView(buttons);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; params.y = dp(30);
        overlay = bar; windowManager.addView(overlay, params);
    }

    private void performTextAt(JSONObject action) {
        if (Build.VERSION.SDK_INT < 24) return;
        float x = (float)(action.optDouble("x", .5) * getResources().getDisplayMetrics().widthPixels);
        float y = (float)(action.optDouble("y", .5) * getResources().getDisplayMetrics().heightPixels);
        Path path = new Path(); path.moveTo(x, y); GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 70));
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                handler.postDelayed(() -> {
                    try {
                        AccessibilityNodeInfo root = getRootInActiveWindow();
                        AccessibilityNodeInfo focused = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                        if (focused != null) {
                            Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.optString("value", ""));
                            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args); focused.recycle();
                        }
                        if (root != null) root.recycle();
                    } catch (Exception ignored) { }
                }, 300);
            }
        }, null);
    }

    private boolean applyToNode(AccessibilityNodeInfo node, JSONObject action) {
        if (node == null) return false;
        if ("text".equals(action.optString("type"))) {
            Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.optString("value", ""));
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true;
        } else if ("scroll".equals(action.optString("type"))) {
            int scrollAction = "backward".equals(action.optString("direction"))
                    ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
            if (node.performAction(scrollAction)) return true;
        } else {
            AccessibilityNodeInfo target = node;
            while (target != null && !target.isClickable()) target = target.getParent();
            if (target != null) { boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK); if (target != node) target.recycle(); if (ok) return true; }
        }
        return false;
    }

    private void performTap(double x, double y) {
        if (Build.VERSION.SDK_INT < 24) return;
        float px = (float)(x * getResources().getDisplayMetrics().widthPixels), py = (float)(y * getResources().getDisplayMetrics().heightPixels);
        Path path = new Path(); path.moveTo(px, py); GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 80)); dispatchGesture(builder.build(), null, null);
    }

    @Override public void onInterrupt() { }
    @Override public void onDestroy() { try { unregisterReceiver(receiver); } catch (Exception ignored) {} if (overlay != null && windowManager != null) windowManager.removeView(overlay); super.onDestroy(); }
}
