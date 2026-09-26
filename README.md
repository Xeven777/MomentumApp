# Momentum 🔥

Momentum is a habit tracker for Android. Build routines, watch your streak grow, and keep your
data on your own device — there is no account, no server, and no tracking.

## Features

- **Habit tracking** — add, edit and delete habits with an emoji, all stored locally.
- **Streak with a daily goal** — set a goal ("3 habits a day", or _all habits_ by default) and a day
  counts toward your streak once you reach it. Adding a habit no longer wipes out your streak.
- **Stats screen** — current and best streak, a six-month contribution grid, an eight-week trend,
  per-habit 30-day rates, and badge unlocks (3/7/30/100-day streaks plus a "perfect week"). Tap the
  streak card on the home screen to open it.
- **Schedule habits** — pick which weekdays a habit applies to. Streaks, reminders and the widget all
  respect it, so a Mon/Wed/Fri habit never makes a Tuesday look like a miss.
- **Month calendar** — tap the day strip to expand a full month view with a per-day completion ring,
  swipe to change month, tap a day to select it. Collapsed by default so it costs no screen space.
- **Home screen widget** — a progress ring with your streak and today's completion, plus a
  12-week GitHub-style contribution grid. The `+` button ticks your next pending habit without
  opening the app.
- **Reminders** — per-habit reminder times, re-armed automatically after a reboot, a package
  update, or a clock/timezone change.
- **AI assistant** — bring your own API key and point the app at any OpenAI-compatible provider
  (OpenRouter, Groq, OpenAI, Gemini, Claude). Suggest habits from the questionnaire, from your own free-text description, or both.
- **Fully offline** — habits live in a Room database on the device. The only network call in the
  app is the AI request you trigger yourself.

## Screenshots

<table>
  <tr>
      <td><img src="https://github.com/user-attachments/assets/42594889-e533-494e-80ff-d08c403980d0" width="200"></td>
    <td><img src="https://github.com/user-attachments/assets/1acfcbc5-3031-4f0f-8cc9-3ee7399b1db9" width="200"></td>
    <td><img src="https://github.com/user-attachments/assets/11dba4f3-7665-4fd7-a1f9-00854b75146b" width="200"></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/8590fec3-1086-47be-8549-118be8dc8828" width="200"></td>
    <td><img src="https://github.com/user-attachments/assets/6f140309-54f0-4c9b-b9ad-803021a7a644" width="200"></td>
    <td><img src="https://github.com/user-attachments/assets/796214ad-85af-4e93-ac91-4fe87e0daee1" width="200"></td>
    
  </tr>
</table>

## Getting started

### Requirements

- JDK 17 or newer. If your system `java` is a JRE without `javac`, the Gradle build will
  auto-provision a JDK 17 toolchain for you (the `foojay-resolver-convention` plugin in
  `settings.gradle.kts`), so nothing to install manually.
- Android SDK with platform 36. Point the build at it with either the `ANDROID_HOME` (or
  `ANDROID_SDK_ROOT`) environment variable, or a `local.properties` in the repo root containing
  `sdk.dir=/path/to/Android/sdk`.

### Build and run

```bash
git clone https://github.com/Xeven777/Momentum.git
cd Momentum

./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew installDebug           # build + install on a connected device
```

There is nothing to configure. No API keys in the repo, no `google-services.json`, no Firebase
project.

## Testing on a device over USB

1. **Enable developer options** on the phone: Settings → About phone → tap **Build number** seven
   times.
2. **Enable USB debugging**: Settings → Developer options → **USB debugging**.
3. **Connect the phone** over USB and accept the _Allow USB debugging?_ prompt on the device.
   Tick **Always allow from this computer** if you want to stop being asked.
4. **Verify the connection**:

   ```bash
   adb devices -l
   ```

   Your device should show up as `device`, not `unauthorized` or `offline`. If it is missing,
   try a different USB cable/port (some are charge-only) or `adb kill-server && adb devices`.

5. **Install and launch**:

   ```bash
   ./gradlew installDebug
   adb shell am start -n com.anish.momentum/.LauncherActivity
   ```

   Or open the app from the launcher like normal.

6. **Useful while debugging**:

   ```bash
   adb logcat -s StreakWidget WidgetActionReceiver AiActivity ApiKeyStore:* MainActivity:*
   adb shell dumpsys activity com.anish.momentum      # is it running / any crash history
   adb shell pm list packages | grep momentum
   ```

7. **Uninstall** when you are done:

   ```bash
   adb uninstall com.anish.momentum
   ```

### Tests

```bash
./gradlew testDebugUnitTest        # pure JVM logic, no device needed
./gradlew connectedDebugAndroidTest  # instrumented tests, needs a device
```

The unit tests cover the streak/goal arithmetic, the day-of-week schedule masks, the badge rules, the
AI base-URL normalisation, the AI reply parser and the widget's contribution-grid alignment.

## Configuring the AI assistant

The app ships with no key and no endpoint baked in. Open **Settings → AI Assistant** and set:

| Setting           | Notes                                                                                                                |
| ----------------- | -------------------------------------------------------------------------------------------------------------------- |
| **API key**       | Stored encrypted with a key held in the Android Keystore. Never leaves the device, never in the build.               |
| **Base URL**      | Any OpenAI-compatible endpoint. Defaults to `https://openrouter.ai/api/v1`. A missing trailing `/` is added for you. |
| **Model**         | Whatever your provider calls it, e.g. `mistralai/mistral-small` or `google/gemini-2.0-flash-exp:free`.               |
| **System prompt** | Editable — control the format the model returns.                                                                     |
| **Temperature**   | 0.0 – 2.0.                                                                                                           |

**Test connection** calls `GET /models` and tells you how many models the endpoint exposes, which
is the quickest way to check the key and base URL before spending a completion. If OpenRouter is
your provider, a free key is enough to get started.

## Technologies

- **Kotlin**, **Room** (habits + completions), **DataStore** (settings)
- **AndroidX Keystore** for the API key, **Retrofit**/**OkHttp**/**Gson** for the AI calls
- **Material Design 3**, **Lottie** (flame and loader animations)
- **R8** with resource shrinking on release builds

## Project structure

```
app/src/main/java/com/anish/momentum/
  MainActivity.kt        # habit list, calendar strip, streak
  AiActivity.kt          # AI suggestions → habits
  QuestionsActivity.kt   # the AI questionnaire
  SettingsActivity.kt    # name, daily goal, AI config
  StatsActivity.kt       # streaks, heatmap, trend, per-habit rates, badges
  data/                  # Room entities, DAOs, repository, DataStore, schedule + badge rules
  ai/                    # OpenAI-compatible client, config, key storage
  models/                # Habit, DateTaskStatus
  ui/                    # shared drawing: Artwork, RingView, HeatmapView, BarChartView, month grid
  utils/                 # reminders, boot receiver, adapters, date helpers, weekday toggles
  widgets/               # home screen widget + its bitmap artwork
```

## Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you would
like to change.

## Credits

Built with ❤️ by [Anish Biswas](https://github.com/Xeven777) · [anish7.me](https://anish7.me)

Uses [Google Sans](https://github.com/googlefonts/googlesans) under the
[SIL Open Font License 1.1](licenses/GoogleSans-OFL.txt), subsetted and instanced for this app.

## License

[MIT](LICENSE)
