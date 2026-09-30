# FRIDAY for Android

FRIDAY lets you teach a short task in an Android app, save it as a routine, and replay it later with a button or a spoken command. For example, you can teach it to search for an item in a shopping app and add that item to a cart.

FRIDAY replays the steps you teach. It does not decide what to do next or search the internet on its own.

## What is in this repository?

- `android-app/` — the Android Studio project and Gradle build files.
- `Friday.apk` — a prebuilt debug APK you can install on an Android phone.
- `FRIDAY-Presentation.pptx` — the project presentation.
- `Friday-demo.mp4` — a video demonstration of FRIDAY.
- `README.md` — this guide.

## Project presentation

Download the [FRIDAY presentation](FRIDAY-Presentation.pptx).

## Demo video

Watch [`Friday-demo.mp4`](Friday-demo.mp4) to see the app in action.

## What you need

- Android Studio.
- An Android phone or emulator running Android 8.0 (API 26) or newer.
- The app you want FRIDAY to use installed on that phone.

## Build or install FRIDAY

### Build with Android Studio

1. Open the `android-app/` folder in Android Studio.
2. Let Gradle sync. If Android Studio asks, install the Android SDK platform required by the project.
3. To make an APK, choose **Build > Build APK(s)**. Android Studio creates it at `android-app/app/build/outputs/apk/debug/app-debug.apk`.
4. To install directly, connect and unlock your phone, select it in Android Studio, and press **Run**.

### Install the included APK

Install `Friday.apk` from the repository root on your phone. If Android blocks installation, review the Android security prompt and allow installation from the app you used to open the APK.

## Set up FRIDAY once

1. Open FRIDAY and tap **Enable accessibility**.
2. In Android Settings, enable **FRIDAY workflow access** and accept the system prompt. FRIDAY needs this access to observe and replay actions in other apps.
3. Tap **Voice** and allow microphone access if you want to run routines by speaking. You can also type a routine command instead.

## Teach a routine

1. Tap **Teach a task**.
2. Enter a routine name and a command you will remember, such as `Add mango juice to my cart`.
3. Choosing an app is optional. Select one to pin the routine to that app. Leave the picker blank and FRIDAY will detect the app you open while teaching.
4. Tap **Start teaching**. FRIDAY goes to the home screen and shows a small floating bar.
5. Open the target app and perform the complete task you want FRIDAY to repeat. For example: open the store, search for an item, open its result, and tap **Add to cart**.
6. Tap **Finish** in the floating bar. FRIDAY saves the routine on your phone.

Teach each routine in one app. Keep routines short and finish teaching before checkout or payment.

## Run a routine

You can tap **Run task** on a saved routine, type its command and tap **Run**, or tap **Voice** and say the command. FRIDAY shows the routine name and target app before it runs; review these and tap **Continue**.

If the routine contains text entry, FRIDAY shows those saved text values before playback. You can change an item name or other text for that run. FRIDAY then opens the target app and replays the recorded actions in order.

## How replay works

While teaching, FRIDAY's Accessibility service records the target app and the steps it can observe, such as taps, text entry, scrolling, and the keyboard's Search or Go action. It saves these steps with the routine in FRIDAY's app data on the phone.

During playback, FRIDAY first tries to find controls by their accessibility label or ID. If the app does not expose a usable control, FRIDAY taps the screen position recorded during teaching. It uses the gaps between recorded steps to give screens time to respond, up to five seconds per step. Each routine stays with its target app so a step recorded in another app will not switch playback to that app.

If FRIDAY reaches a screen that looks like sign-in or payment, or cannot identify an expected screen, playback pauses. You can retry the step, skip it, or stop the routine. Review the screen before continuing.

## Data and permissions

- Routines are stored locally in FRIDAY's app data; they are not synced between phones by FRIDAY.
- Accessibility access lets FRIDAY read visible app controls during teaching and perform the steps you ask it to replay.
- Microphone access is only needed for spoken commands. Speech recognition is provided by Android's speech-recognition service and may depend on the phone's settings and connection.
- Password-style fields are not saved as routine text.

## Tips and known limits

- Keep the target app signed in to the same account and start from a screen similar to the one used during teaching.
- If a shopping app changes its layout, update or teach the routine again. Screen-position taps may stop matching after app or screen-size changes.
- FRIDAY replays taught steps; it cannot reliably adapt to every changing screen, choose between unexpected products, or complete tasks that require new decisions.
- Supervise routines in shopping and messaging apps. Check the target app and changing text before playback, and stop before any order or payment you do not want to make.

## Project files

- `android-app/app/src/main/java/com/morrow/assistant/MainActivity.java` — FRIDAY's screen, routine setup, command matching, and speech input.
- `android-app/app/src/main/java/com/morrow/assistant/WorkflowAccessibilityService.java` — task recording and playback.
- `android-app/app/src/main/res/xml/accessibility_service_config.xml` — Android Accessibility service configuration.
- `android-app/app/src/main/AndroidManifest.xml` — app entry point and Android permissions.
