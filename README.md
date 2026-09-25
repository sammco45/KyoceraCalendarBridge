# Kyocera Calendar Bridge

A tiny Android app for the Kyocera DIGNO/A202KC calendar setup where the stock Kyocera Calendar can *display* DAVx5 calendars but always creates new events in Kyocera's local calendar (`jp.kyocera.localcalendar`).

The bridge watches Android's Calendar Provider. When the stock app creates a **new** event in the Kyocera-local calendar, it recreates that event in the selected writable DAVx5 calendar and removes the local original in one Calendar Provider batch. DAVx5 can then upload the resulting dirty event to CalDAV/iCloud.

## Safety / first-run behavior

Enabling automatic mode records the current highest Kyocera-local event ID and only bridges events created **after** that point. Existing local events are not moved automatically.

Use **Migrate existing local events** if you intentionally want to move old local events (including the `BRIDGETEST` test event) into DAVx5.

## What is copied

- title
- location
- description
- start/end time
- time zones
- all-day state
- recurrence rules/dates and exception rules/dates
- availability/access flags
- guest-edit flags
- reminders

Attendee/invitation rows and non-standard extended properties are not copied in v1.

## Build

The project intentionally has no external runtime dependencies.

### GitHub Actions

Push the entire project, **including `.github/`**, to a GitHub repository. The included `Build APK` workflow builds `app-debug.apk` and publishes it as the `KyoceraCalendarBridge-debug` workflow artifact.

### Android Studio / local Gradle

Use JDK 17, Android SDK 35, Android Gradle Plugin 8.7.x, and Gradle 8.9. Then run:

```bash
gradle :app:assembleDebug
```

The APK will be at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup on the phone

1. Open **Calendar Bridge**.
2. Grant calendar read/write access.
3. Confirm the source says the Kyocera local calendar.
4. Choose your DAVx5 `Default` calendar as the destination.
5. Enable **Bridge new Kyocera events automatically**.
6. Create a new event in the stock Kyocera Calendar.
7. Within a few seconds, the local event should be replaced by the DAVx5-backed copy.
8. DAVx5 should notice the Calendar Provider change; force a DAVx5 sync for the first test if you want immediate verification in iCloud.

## Useful ADB checks

```bash
adb shell content query \
  --uri content://com.android.calendar/calendars \
  --projection "_id:calendar_displayName:account_name:account_type:calendar_access_level"
```

Check a test event:

```bash
adb shell content query \
  --uri content://com.android.calendar/events \
  --projection "_id:title:calendar_id:account_name:account_type:dirty:_sync_id" \
  | grep -F "YOUR TEST TITLE"
```

After the bridge runs, the event should belong to `account_type=bitfire.at.davdroid`, not `jp.kyocera.localcalendar`.

For bridge logs:

```bash
adb logcat -s KyoceraCalendarBridge
```

## Implementation detail

The background watcher uses `JobScheduler` with `JobInfo.TriggerContentUri` on `CalendarContract.Events.CONTENT_URI`. Trigger-content jobs cannot be persisted, so the app reschedules the watcher after each run and again after boot/package replacement.
