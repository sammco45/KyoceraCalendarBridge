package com.samboucher.kyoceracalendarbridge;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

final class BridgeEngine {
    private static final String TAG = "KyoceraCalendarBridge";

    static final class Result {
        final int moved;
        final int failed;
        final String message;

        Result(int moved, int failed, String message) {
            this.moved = moved;
            this.failed = failed;
            this.message = message;
        }
    }

    private static final String[] COPY_COLUMNS = {
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.EVENT_END_TIMEZONE,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.EXRULE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.GUESTS_CAN_MODIFY,
            CalendarContract.Events.GUESTS_CAN_INVITE_OTHERS,
            CalendarContract.Events.GUESTS_CAN_SEE_GUESTS
    };

    private BridgeEngine() {}

    static Result migrateNewEvents(Context context) {
        BridgePrefs prefs = new BridgePrefs(context);
        CalendarInfo source = CalendarRepository.findKyoceraCalendar(context);
        if (source == null) {
            return new Result(0, 1, "Kyocera local calendar was not found.");
        }

        CalendarInfo target = resolveTarget(context, prefs);
        if (target == null) {
            return new Result(0, 1, "No writable DAVx5 calendar was found.");
        }

        long baseline = prefs.getBaselineEventId();
        if (!prefs.isBaselineInitialized()) {
            baseline = Math.max(0L, CalendarRepository.getMaxEventId(context, source.id));
            prefs.setBaselineEventId(baseline);
            return new Result(0, 0, "Bridge baseline initialized at event ID " + baseline + ".");
        }

        List<Long> ids = querySourceEventIds(context, source.id, baseline);
        int moved = 0;
        int failed = 0;
        long lastSuccessful = baseline;

        for (long id : ids) {
            try {
                if (moveEvent(context, id, source.id, target.id)) {
                    moved++;
                    lastSuccessful = id;
                    prefs.setBaselineEventId(lastSuccessful);
                }
            } catch (Exception e) {
                failed++;
                Log.e(TAG, "Couldn't migrate local event " + id, e);
                // Keep the baseline before this row so the next trigger retries it.
                break;
            }
        }

        String message = "Moved " + moved + " new event(s) to " + target.displayName;
        if (failed > 0) message += "; " + failed + " failed and will be retried";
        return new Result(moved, failed, message + ".");
    }

    static Result migrateAllExisting(Context context) {
        BridgePrefs prefs = new BridgePrefs(context);
        CalendarInfo source = CalendarRepository.findKyoceraCalendar(context);
        if (source == null) {
            return new Result(0, 1, "Kyocera local calendar was not found.");
        }

        CalendarInfo target = resolveTarget(context, prefs);
        if (target == null) {
            return new Result(0, 1, "No writable DAVx5 calendar was found.");
        }

        List<Long> ids = querySourceEventIds(context, source.id, -1L);
        int moved = 0;
        int failed = 0;
        long baseline = prefs.isBaselineInitialized() ? prefs.getBaselineEventId() : 0L;

        for (long id : ids) {
            try {
                if (moveEvent(context, id, source.id, target.id)) {
                    moved++;
                    baseline = Math.max(baseline, id);
                }
            } catch (Exception e) {
                failed++;
                Log.e(TAG, "Couldn't migrate existing local event " + id, e);
            }
        }
        prefs.setBaselineEventId(Math.max(baseline,
                CalendarRepository.getMaxEventId(context, source.id)));

        String message = "Moved " + moved + " existing event(s) to " + target.displayName;
        if (failed > 0) message += "; " + failed + " failed";
        return new Result(moved, failed, message + ".");
    }

    private static CalendarInfo resolveTarget(Context context, BridgePrefs prefs) {
        long targetId = prefs.getTargetCalendarId();
        if (targetId >= 0L) {
            CalendarInfo selected = CalendarRepository.findCalendarById(context, targetId);
            if (selected != null
                    && CalendarRepository.DAVX_ACCOUNT_TYPE.equals(selected.accountType)
                    && selected.accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                return selected;
            }
        }

        CalendarInfo fallback = CalendarRepository.chooseDefaultDavx(context);
        if (fallback != null) prefs.setTargetCalendarId(fallback.id);
        return fallback;
    }

    private static List<Long> querySourceEventIds(Context context, long sourceCalendarId, long afterId) {
        List<Long> result = new ArrayList<>();
        String selection = CalendarContract.Events.CALENDAR_ID + "=?";
        List<String> args = new ArrayList<>();
        args.add(Long.toString(sourceCalendarId));
        if (afterId >= 0L) {
            selection += " AND " + CalendarContract.Events._ID + ">?";
            args.add(Long.toString(afterId));
        }

        try (Cursor c = context.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID},
                selection,
                args.toArray(new String[0]),
                CalendarContract.Events._ID + " ASC")) {
            if (c == null) return result;
            while (c.moveToNext()) result.add(c.getLong(0));
        }
        return result;
    }

    private static boolean moveEvent(Context context, long sourceEventId,
                                     long expectedSourceCalendarId, long targetCalendarId) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        Uri sourceUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, sourceEventId);

        String[] projection = new String[COPY_COLUMNS.length + 2];
        projection[0] = CalendarContract.Events._ID;
        projection[1] = CalendarContract.Events.CALENDAR_ID;
        System.arraycopy(COPY_COLUMNS, 0, projection, 2, COPY_COLUMNS.length);

        ContentValues targetValues = new ContentValues();
        String title = "(untitled)";

        try (Cursor c = resolver.query(sourceUri, projection, null, null, null)) {
            if (c == null || !c.moveToFirst()) return false;
            long actualCalendarId = c.getLong(1);
            if (actualCalendarId != expectedSourceCalendarId) return false;

            targetValues.put(CalendarContract.Events.CALENDAR_ID, targetCalendarId);
            for (int i = 0; i < COPY_COLUMNS.length; i++) {
                copyCursorValue(c, i + 2, COPY_COLUMNS[i], targetValues);
            }
            int titleIndex = c.getColumnIndex(CalendarContract.Events.TITLE);
            if (titleIndex >= 0 && !c.isNull(titleIndex)) title = c.getString(titleIndex);
        }

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                .withValues(targetValues)
                .build());

        try (Cursor reminders = resolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                new String[]{
                        CalendarContract.Reminders.MINUTES,
                        CalendarContract.Reminders.METHOD
                },
                CalendarContract.Reminders.EVENT_ID + "=?",
                new String[]{Long.toString(sourceEventId)},
                null)) {
            if (reminders != null) {
                while (reminders.moveToNext()) {
                    ContentProviderOperation.Builder reminder =
                            ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                                    .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0);
                    if (!reminders.isNull(0)) {
                        reminder.withValue(CalendarContract.Reminders.MINUTES, reminders.getInt(0));
                    }
                    if (!reminders.isNull(1)) {
                        reminder.withValue(CalendarContract.Reminders.METHOD, reminders.getInt(1));
                    }
                    ops.add(reminder.build());
                }
            }
        }

        // Delete the Kyocera-local original in the same provider transaction. This prevents
        // duplicate display while leaving the newly-created DAVx5 event visible to Kyocera Calendar.
        ops.add(ContentProviderOperation.newDelete(sourceUri).build());

        ContentProviderResult[] results = resolver.applyBatch(CalendarContract.AUTHORITY, ops);
        if (results.length == 0 || results[0].uri == null) {
            throw new IllegalStateException("Calendar Provider did not return the migrated event URI");
        }
        Log.i(TAG, "Moved event '" + title + "' from local ID " + sourceEventId
                + " to " + results[0].uri);
        return true;
    }

    private static void copyCursorValue(Cursor c, int index, String key, ContentValues values) {
        if (c.isNull(index)) return;
        switch (c.getType(index)) {
            case Cursor.FIELD_TYPE_INTEGER:
                values.put(key, c.getLong(index));
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                values.put(key, c.getDouble(index));
                break;
            case Cursor.FIELD_TYPE_BLOB:
                values.put(key, c.getBlob(index));
                break;
            case Cursor.FIELD_TYPE_STRING:
            default:
                values.put(key, c.getString(index));
                break;
        }
    }
}
