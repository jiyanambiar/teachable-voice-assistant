package com.morrow.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    static final String PREFS = "morrow_android";
    static final String ROUTINES = "routines";
    private static final int INK = Color.rgb(25, 35, 67);
    private static final int MUTED = Color.rgb(101, 115, 145);
    private static final int BLUE = Color.rgb(53, 111, 224);
    private static final int PURPLE = Color.rgb(111, 78, 214);
    private static final int PINK = Color.rgb(222, 83, 159);
    private static final int TEAL = Color.rgb(35, 166, 161);
    private static final int PAGE = Color.rgb(247, 246, 253);
    private EditText command;
    private LinearLayout routineList;
    private LinearLayout setupCard;

    private static final class TargetApp {
        final String packageName;
        final String label;
        TargetApp(String packageName, String label) { this.packageName = packageName; this.label = label; }
        @Override public String toString() { return label; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAGE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        if (setupCard != null) setupCard.setVisibility(serviceEnabled() ? View.GONE : View.VISIBLE);
        if (routineList != null) refreshRoutines();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable shape(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable outlinedShape(int color, float radius, int strokeColor) {
        GradientDrawable drawable = shape(color, radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable gradientShape(int[] colors, float radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(primary ? Color.WHITE : INK);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(48));
        button.setStateListAnimator(null);
        int base = primary ? BLUE : Color.WHITE;
        int pressed = primary ? Color.rgb(39, 83, 180) : Color.rgb(233, 238, 249);
        Drawable content = primary ? gradientShape(new int[]{BLUE, PURPLE}, 16) : shape(base, 16);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(pressed), content, null));
        return button;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(16), dp(22), dp(26));
        page.setBackgroundColor(PAGE);
        scroll.addView(page);
        setContentView(scroll);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = label("✦", 18, Color.WHITE);
        mark.setText("F");
        mark.setTextSize(22);
        mark.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(gradientShape(new int[]{BLUE, PURPLE, PINK}, 16));
        brandRow.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView brand = label("FRIDAY", 24, INK);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-2, -2);
        brandParams.leftMargin = dp(12);
        brandRow.addView(brand, brandParams);
        page.addView(brandRow, params(-1, dp(54), 0, 0));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(22), dp(24), dp(22), dp(22));
        GradientDrawable heroBackground = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(43, 93, 190), Color.rgb(112, 76, 205), Color.rgb(205, 83, 171)});
        heroBackground.setCornerRadius(dp(26));
        hero.setBackground(heroBackground);
        TextView eyebrow = label("YOUR PHONE, ON AUTOPILOT", 11, Color.rgb(224, 219, 255));
        eyebrow.setLetterSpacing(.12f);
        hero.addView(eyebrow);
        TextView title = label("Teach it once.\nSay it anytime.", 30, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, dp(12), 0, dp(8));
        hero.addView(title);
        TextView intro = label("Teach FRIDAY once, then choose any saved task to run whenever you need it.", 14, Color.rgb(239, 237, 255));
        intro.setLineSpacing(dp(3), 1f);
        hero.addView(intro);
        page.addView(hero, params(-1, -2, 0, dp(20)));
        hero.setVisibility(View.GONE);

        setupCard = new LinearLayout(this);
        setupCard.setOrientation(LinearLayout.VERTICAL);
        setupCard.setPadding(dp(16), dp(15), dp(16), dp(14));
        setupCard.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.rgb(235, 232, 251)),
                outlinedShape(Color.rgb(246, 244, 255), 18, Color.rgb(225, 220, 245)), null));
        TextView setupTitle = label("One-time setup", 15, INK);
        setupTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setupCard.addView(setupTitle);
        TextView setup = label("Allow FRIDAY to observe and replay steps in other apps.", 13, MUTED);
        setup.setPadding(0, dp(4), 0, dp(12));
        setupCard.addView(setup);
        Button access = button("Enable accessibility", false);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        setupCard.addView(access, new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(setupCard, params(-1, -2, 0, dp(14)));
        setupCard.setVisibility(serviceEnabled() ? View.GONE : View.VISIBLE);

        addSectionTitle(page, "Teach a routine", dp(18));
        Button teach = button("Teach a task", true);
        teach.setOnClickListener(v -> showTeachDialog());
        page.addView(teach, params(-1, dp(50), 0, dp(8)));

        addSectionTitle(page, "Run a routine", dp(18));
        command = new EditText(this);
        command.setSingleLine(true);
        command.setTextSize(15);
        command.setHintTextColor(Color.rgb(153, 151, 170));
        command.setHint("Type or say a task name");
        command.setTextColor(INK);
        command.setPadding(dp(16), 0, dp(16), 0);
        command.setBackground(outlinedShape(Color.WHITE, 16, Color.rgb(218, 216, 238)));
        page.addView(command, params(-1, dp(50), 0, dp(8)));

        LinearLayout commandButtons = new LinearLayout(this);
        commandButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button run = button("Run", true);
        run.setOnClickListener(v -> runCommand(command.getText().toString()));
        Button speak = button("Voice", false);
        speak.setOnClickListener(v -> listen());
        LinearLayout.LayoutParams firstHalf = new LinearLayout.LayoutParams(0, dp(50), 1);
        firstHalf.rightMargin = dp(6);
        commandButtons.addView(run, firstHalf);
        LinearLayout.LayoutParams secondHalf = new LinearLayout.LayoutParams(0, dp(50), 1);
        secondHalf.leftMargin = dp(6);
        commandButtons.addView(speak, secondHalf);
        page.addView(commandButtons, params(-1, -2, 0, dp(8)));

        addSectionTitle(page, "Your routines", dp(18));
        routineList = new LinearLayout(this);
        routineList.setOrientation(LinearLayout.VERTICAL);
        page.addView(routineList);
        refreshRoutines();
    }

    private void addSectionTitle(LinearLayout parent, String titleText, int top) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View accent = new View(this);
        accent.setBackground(gradientShape(new int[]{BLUE, PURPLE, PINK}, 3));
        row.addView(accent, new LinearLayout.LayoutParams(dp(4), dp(20)));
        TextView title = label(titleText, 18, INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.leftMargin = dp(9);
        row.addView(title, titleParams);
        parent.addView(row, params(-1, dp(24), 0, top));
    }

    private void addSectionHeading(LinearLayout parent, String titleText, String subtitle, int top) {
        TextView title = label(titleText, 19, INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        parent.addView(title, params(-1, -2, 0, top));
        TextView hint = label(subtitle, 13, MUTED);
        hint.setLineSpacing(dp(2), 1f);
        parent.addView(hint, params(-1, -2, 0, dp(4)));
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(width, height);
        layoutParams.setMargins(left, top, 0, 0);
        return layoutParams;
    }

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.toLowerCase(Locale.ROOT).contains(getPackageName().toLowerCase(Locale.ROOT));
    }

    private void showTeachDialog() {
        LinearLayout fields = new LinearLayout(this);
        fields.setPadding(dp(20), dp(8), dp(20), 0);
        fields.setOrientation(LinearLayout.VERTICAL);
        ArrayList<TargetApp> targetApps = getTargetApps();
        ArrayList<String> appChoices = new ArrayList<>();
        appChoices.add("Choose app (optional)");
        for (TargetApp app : targetApps) appChoices.add(app.label);
        Spinner appPicker = new Spinner(this);
        ArrayAdapter<String> appAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, appChoices);
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        appPicker.setAdapter(appAdapter);
        fields.addView(appPicker);
        EditText name = new EditText(this);
        name.setHint("Routine name, e.g. My burger");
        fields.addView(name);
        EditText phrase = new EditText(this);
        phrase.setHint("Command, e.g. Add my burger to cart");
        fields.addView(phrase);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("What should FRIDAY learn?")
                .setMessage("You can choose an app, or leave it blank for automatic detection. FRIDAY will return to your home screen; open the app, do the task, then tap Finish.")
                .setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Start teaching", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String routineName = name.getText().toString().trim();
            if (routineName.isEmpty()) { toast("Add a routine name first."); return; }
            int selectedApp = appPicker.getSelectedItemPosition();
            if (!serviceEnabled()) {
                toast("Enable FRIDAY workflow access in Accessibility settings first.");
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            String routinePhrase = phrase.getText().toString().trim();
            dialog.dismiss();
            String targetPackage = selectedApp > 0 && selectedApp <= targetApps.size()
                    ? targetApps.get(selectedApp - 1).packageName : "";
            startTeaching(routineName, routinePhrase.isEmpty() ? routineName : routinePhrase, targetPackage);
        }));
        dialog.show();
    }

    private ArrayList<TargetApp> getTargetApps() {
        ArrayList<TargetApp> apps = new ArrayList<>();
        for (ApplicationInfo info : getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (getPackageName().equals(info.packageName) || getPackageManager().getLaunchIntentForPackage(info.packageName) == null) continue;
            CharSequence label = getPackageManager().getApplicationLabel(info);
            if (label != null && label.length() > 0) apps.add(new TargetApp(info.packageName, label.toString()));
        }
        Collections.sort(apps, (first, second) -> first.label.compareToIgnoreCase(second.label));
        return apps;
    }

    private void startTeaching(String routineName, String routinePhrase, String targetPackage) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("recording", true)
                .putString("draft_id", UUID.randomUUID().toString())
                .putString("draft_name", routineName)
                .putString("draft_phrase", routinePhrase)
                .putString("draft_target_package", targetPackage)
                .putString("draft_actions", "[]").apply();
        Intent changed = new Intent(WorkflowAccessibilityService.ACTION_RECORDING_CHANGED);
        changed.setPackage(getPackageName());
        sendBroadcast(changed);
        Intent goHome = new Intent(WorkflowAccessibilityService.ACTION_GO_HOME);
        goHome.setPackage(getPackageName());
        sendBroadcast(goHome);
        if (targetPackage.isEmpty()) toast("Teaching started. Open the app you want to teach, do the task, then tap Finish.");
        else toast("Teaching started. Open " + routineTargetAppForPackage(targetPackage) + ", do the task, then tap Finish.");
    }

    private String routineTargetAppForPackage(String packageName) {
        try {
            CharSequence label = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0));
            if (label != null && label.length() > 0) return label.toString();
        } catch (PackageManager.NameNotFoundException ignored) { }
        return packageName;
    }

    private void refreshRoutines() {
        if (routineList == null) return;
        routineList.removeAllViews();
        JSONArray routines = getRoutines();
        if (routines.length() == 0) {
            LinearLayout emptyCard = new LinearLayout(this);
            emptyCard.setOrientation(LinearLayout.VERTICAL);
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setPadding(dp(20), dp(22), dp(20), dp(22));
            emptyCard.setBackground(outlinedShape(Color.WHITE, 20, Color.rgb(235, 233, 243)));
            TextView glyph = label("✧", 26, PURPLE);
            glyph.setGravity(Gravity.CENTER);
            emptyCard.addView(glyph);
            TextView emptyTitle = label("Your first routine starts here", 15, INK);
            emptyTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            emptyTitle.setGravity(Gravity.CENTER);
            emptyTitle.setPadding(0, dp(6), 0, dp(4));
            emptyCard.addView(emptyTitle);
            TextView empty = label("Teach FRIDAY a task and it will be saved here.", 13, MUTED);
            empty.setGravity(Gravity.CENTER);
            emptyCard.addView(empty);
            routineList.addView(emptyCard, params(-1, -2, 0, dp(12)));
            return;
        }

        int[] routineAccents = {BLUE, PURPLE, PINK, TEAL};
        for (int i = 0; i < routines.length(); i++) {
            JSONObject routine = routines.optJSONObject(i);
            if (routine == null) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(15), dp(16), dp(14));
            card.setBackground(outlinedShape(Color.WHITE, 20, Color.rgb(226, 223, 242)));
            View accent = new View(this);
            accent.setBackground(gradientShape(new int[]{routineAccents[i % routineAccents.length], PURPLE}, 3));
            card.addView(accent, new LinearLayout.LayoutParams(-1, dp(4)));
            TextView name = label(routine.optString("name"), 16, INK);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            card.addView(name);
            JSONArray actions = routine.optJSONArray("actions");
            int stepCount = actions == null ? 0 : actions.length();
            String stepLabel = stepCount == 1 ? " step" : " steps";
            TextView phrase = label("“" + routine.optString("phrase") + "”  ·  " + stepCount + stepLabel, 13, MUTED);
            phrase.setPadding(0, dp(5), 0, dp(12));
            card.addView(phrase);
            TextView target = label("Opens " + routineTargetApp(routine), 12, MUTED);
            target.setPadding(0, 0, 0, dp(10));
            card.addView(target);

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            String id = routine.optString("id");
            Button run = button("Run task", true);
            run.setOnClickListener(v -> runRoutine(id));
            Button delete = button("Delete", false);
            delete.setOnClickListener(v -> deleteRoutine(id));
            LinearLayout.LayoutParams runParams = new LinearLayout.LayoutParams(0, dp(46), 1);
            runParams.rightMargin = dp(6);
            row.addView(run, runParams);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(46), 1);
            deleteParams.leftMargin = dp(6);
            row.addView(delete, deleteParams);
            card.addView(row);
            routineList.addView(card, params(-1, -2, 0, dp(10)));
        }
    }

    private JSONArray getRoutines() {
        try { return new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(ROUTINES, "[]")); }
        catch (Exception exception) { return new JSONArray(); }
    }

    private void runCommand(String text) {
        String query = normalize(text);
        if (query.isEmpty()) { toast("Type or say a routine command."); return; }
        JSONArray all = getRoutines();
        JSONObject best = null;
        double max = 0, secondBest = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject routine = all.optJSONObject(i);
            if (routine == null) continue;
            String name = normalize(routine.optString("name"));
            String phrase = normalize(routine.optString("phrase"));
            double score = Math.max(commandSimilarity(query, name), commandSimilarity(query, phrase));
            if (score > max) { secondBest = max; max = score; best = routine; }
            else if (score > secondBest) secondBest = score;
        }
        if (best == null || max < .72 || (secondBest >= .72 && max - secondBest < .12)) {
            toast("I couldn't match that command clearly. Say the task name or teach a clearer phrase.");
            return;
        }
        runRoutine(best.optString("id"));
    }

    private double commandSimilarity(String heard, String saved) {
        if (heard.isEmpty() || saved.isEmpty()) return 0;
        if (heard.equals(saved)) return 1;
        String[] heardWords = heard.split(" ");
        String[] savedWords = saved.split(" ");
        int matched = 0;
        for (String heardWord : heardWords) {
            if (heardWord.length() < 3 || isFiller(heardWord)) continue;
            for (String savedWord : savedWords) {
                if ((savedWord.length() >= 3 && !isFiller(savedWord) && heardWord.equals(savedWord)) || sameIntentWord(heardWord, savedWord)) {
                    matched++;
                    break;
                }
            }
        }
        int heardCount = 0, savedCount = 0;
        for (String word : heardWords) if (word.length() >= 3 && !isFiller(word)) heardCount++;
        for (String word : savedWords) if (word.length() >= 3 && !isFiller(word)) savedCount++;
        return heardCount + savedCount == 0 ? 0 : (2.0 * matched) / (heardCount + savedCount);
    }

    private boolean isFiller(String word) {
        return word.equals("please") || word.equals("could") || word.equals("would") || word.equals("you") || word.equals("can") || word.equals("hey") || word.equals("friday") || word.equals("the") || word.equals("a") || word.equals("my") || word.equals("to") || word.equals("for") || word.equals("me");
    }

    private boolean sameIntentWord(String first, String second) {
        return (isOneOf(first, "open", "launch", "start") && isOneOf(second, "open", "launch", "start"))
                || (isOneOf(first, "run", "do", "execute") && isOneOf(second, "run", "do", "execute"))
                || (isOneOf(first, "send", "message", "text") && isOneOf(second, "send", "message", "text"))
                || (isOneOf(first, "add", "put", "place") && isOneOf(second, "add", "put", "place"))
                || (isOneOf(first, "cart", "basket", "bag") && isOneOf(second, "cart", "basket", "bag"));
    }

    private boolean isOneOf(String word, String... options) {
        for (String option : options) if (word.equals(option)) return true;
        return false;
    }

    private String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private void runRoutine(String id) {
        if (!serviceEnabled()) {
            toast("Enable FRIDAY workflow access in Accessibility settings first.");
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        JSONObject selected = null;
        JSONArray list = getRoutines();
        for (int i = 0; i < list.length(); i++) {
            JSONObject routine = list.optJSONObject(i);
            if (routine != null && id.equals(routine.optString("id"))) selected = routine;
        }
        if (selected == null) return;
        final JSONObject routine = selected;
        new AlertDialog.Builder(this).setTitle("Run “" + routine.optString("name") + "”? ")
                .setMessage("Target app: " + routineTargetApp(routine) + "\n\nReview any changing text before FRIDAY opens the app. It will pause if it reaches sign-in, payment, or a step it cannot identify.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", (dialog, which) -> preparePlayback(routine)).show();
    }

    private String routineTargetPackage(JSONObject routine) {
        String savedPackage = routine.optString("targetPackage", "").trim();
        if (!savedPackage.isEmpty()) return savedPackage;
        JSONArray actions = routine.optJSONArray("actions");
        if (actions == null) return "";
        String keyboardComponent = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        String keyboardPackage = keyboardComponent == null ? "" : keyboardComponent.split("/", 2)[0].toLowerCase(Locale.ROOT);
        Map<String, Integer> interactionCounts = new LinkedHashMap<>();
        String firstLaunchPackage = "";
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action == null) continue;
            String packageName = action.optString("package", "").toLowerCase(Locale.ROOT);
            if (packageName.isEmpty() || packageName.equals(keyboardPackage)) continue;
            String type = action.optString("type");
            if ("launch".equals(type) && firstLaunchPackage.isEmpty()) firstLaunchPackage = packageName;
            if ("click".equals(type) || "text".equals(type) || "scroll".equals(type) || "ime".equals(type)) {
                int count = interactionCounts.containsKey(packageName) ? interactionCounts.get(packageName) : 0;
                interactionCounts.put(packageName, count + 1);
            }
        }
        String mostUsedPackage = "";
        int highestCount = 0;
        for (Map.Entry<String, Integer> entry : interactionCounts.entrySet()) {
            if (entry.getValue() > highestCount) {
                mostUsedPackage = entry.getKey();
                highestCount = entry.getValue();
            }
        }
        return highestCount > 0 ? mostUsedPackage : firstLaunchPackage;
    }

    private String routineTargetApp(JSONObject routine) {
        String packageName = routineTargetPackage(routine);
        if (packageName.isEmpty()) return "an unknown app";
        try {
            CharSequence appLabel = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0));
            if (appLabel != null && appLabel.length() > 0) return appLabel.toString();
        } catch (PackageManager.NameNotFoundException ignored) { }
        return packageName;
    }

    private void preparePlayback(JSONObject routine) {
        JSONArray source = routine.optJSONArray("actions");
        JSONArray actions = source == null ? new JSONArray() : source;
        LinearLayout fields = new LinearLayout(this);
        fields.setPadding(dp(20), dp(8), dp(20), 0);
        fields.setOrientation(LinearLayout.VERTICAL);
        ArrayList<EditText> values = new ArrayList<>();
        ArrayList<Integer> actionIndexes = new ArrayList<>();
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.optJSONObject(i);
            if (action == null || !"text".equals(action.optString("type"))) continue;
            String oldValue = action.optString("value", "");
            TextView label = label("Text step " + (values.size() + 1), 13, MUTED);
            fields.addView(label);
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setText(oldValue);
            input.setHint("Enter text for this run");
            fields.addView(input);
            values.add(input);
            actionIndexes.add(i);
        }
        Runnable launch = () -> {
            JSONArray runActions = new JSONArray();
            for (int i = 0; i < actions.length(); i++) {
                JSONObject original = actions.optJSONObject(i);
                if (original == null) continue;
                JSONObject copy;
                try { copy = new JSONObject(original.toString()); } catch (Exception ignored) { continue; }
                int slot = actionIndexes.indexOf(i);
                if (slot >= 0) {
                    try { copy.put("value", values.get(slot).getText().toString()); } catch (Exception ignored) { }
                }
                runActions.put(copy);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("play_actions", runActions.toString())
                    .putString("play_target_package", routineTargetPackage(routine))
                    .putBoolean("playing", true).apply();
            Intent play = new Intent(WorkflowAccessibilityService.ACTION_PLAY);
            play.setPackage(getPackageName());
            sendBroadcast(play);
            toast("Running “" + routine.optString("name") + "”…");
        };
        if (values.isEmpty()) { launch.run(); return; }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(fields);
        new AlertDialog.Builder(this).setTitle("Update changing details")
                .setMessage("Values are prefilled from teaching. Edit any that should change for this run.")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Run routine", (dialog, which) -> launch.run()).show();
    }

    private void deleteRoutine(String id) {
        JSONArray old = getRoutines();
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject routine = old.optJSONObject(i);
            if (routine != null && !id.equals(routine.optString("id"))) next.put(routine);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(ROUTINES, next.toString()).apply();
        refreshRoutines();
    }

    private void listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Voice input is unavailable on this phone. Use text instead.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 12);
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { toast("Listening…"); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) { toast("I didn't catch that. Try typing the command."); recognizer.destroy(); }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    command.setText(matches.get(0));
                    runCommand(matches.get(0));
                }
                recognizer.destroy();
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
        recognizer.startListening(intent);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
