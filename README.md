# FRIDAY for Android

FRIDAY records a task you perform in another Android app and replays those steps from a typed or spoken command. The Android Studio project is in `android-app/`. The app supports Android 8.0 (API 26) and newer.

## Build and install

1. Open `android-app/` in Android Studio and let Gradle sync.
2. If prompted, install the Android SDK platform required by the project.
3. Choose **Build > Build APK(s)**. Android Studio writes the APK to `android-app/app/build/outputs/apk/debug/app-debug.apk`.
4. Install the APK on your phone, open FRIDAY, and enable **FRIDAY workflow access** from Android Accessibility settings.

A prebuilt debug APK is also included as `Friday.apk` in the repository root.

## Teach and run a routine

1. Tap **Teach a task**, enter a routine name and command, and optionally choose the app to teach. Leaving the app picker blank lets FRIDAY detect the app when teaching starts.
2. FRIDAY returns to the home screen. Open the target app, perform the steps you want to repeat, then tap **Finish** in the floating bar.
3. Run the routine from its card or type/say its command. Review any changing text before confirming playback.

Routines are stored locally on the phone. Voice input needs microphone permission. Accessibility access is used to inspect controls and replay taps, text entry, and scrolling. Playback pauses on screens that look like sign-in or payment. Password fields are not recorded. Supervise routines while they run, especially in shopping apps.

The best results come from short routines with accessible controls. App screens can change, so review the target app and routine steps before running them.
