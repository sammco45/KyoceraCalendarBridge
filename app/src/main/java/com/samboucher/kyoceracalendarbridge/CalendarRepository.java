package com.samboucher.kyoceracalendarbridge;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.List;

final class CalendarRepository {
    static final String KYOCERA_ACCOUNT_TYPE = "jp.kyocera.localcalendar";
    static final String DAVX_ACCOUNT_TYPE = "bitfire.at.davdroid";

    private CalendarRepository() {}

    static CalendarInfo findKyoceraCalendar(Context context) {
        List<CalendarInfo> calendars = queryCalendars(context);
        CalendarInfo fallback = null;
        for (CalendarInfo info : calendars) {
            if (KYOCERA_ACCOUNT_TYPE.equals(info.accountType)) {
                if ("Calendar".equalsIgnoreCase(info.displayName)) {
                    return info;
                }
                if (fallback == null) fallback = info;
            }
        }
        return fallback;
    }

    static List<CalendarInfo> findDavxCalendars(Context context) {
        List<CalendarInfo> result = new ArrayList<>();
        for (CalendarInfo info : queryCalendars(context)) {
            if (DAVX_ACCOUNT_TYPE.equals(info.accountType)
                    && info.accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                result.add(info);
            }
        }
        return result;
    }

    static CalendarInfo findCalendarById(Context context, long id) {
        for (CalendarInfo info : queryCalendars(context)) {
            if (info.id == id) return info;
        }
        return null;
    }

    static CalendarInfo chooseDefaultDavx(Context context) {
        List<CalendarInfo> davx = findDavxCalendars(context);
        CalendarInfo firstWritable = null;
        for (CalendarInfo info : davx) {
            if (firstWritable == null) firstWritable = info;
            if ("Default".equalsIgnoreCase(info.displayName)) {
                return info;
            }
        }
        return firstWritable;
    }

    static long getMaxEventId(Context context, long calendarId) {
        ContentResolver resolver = context.getContentResolver();
        long max = -1L;
        try (Cursor c = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID},
                CalendarContract.Events.CALENDAR_ID + "=?",
                new String[]{Long.toString(calendarId)},
                CalendarContract.Events._ID + " DESC")) {
            if (c != null && c.moveToFirst()) {
                max = c.getLong(0);
            }
        }
        return max;
    }

    private static List<CalendarInfo> queryCalendars(Context context) {
        List<CalendarInfo> result = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        };

        try (Cursor c = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Calendars._ID + " ASC")) {
            if (c == null) return result;
            while (c.moveToNext()) {
                result.add(new CalendarInfo(
                        c.getLong(0),
                        c.isNull(1) ? "(unnamed)" : c.getString(1),
                        c.isNull(2) ? "" : c.getString(2),
                        c.isNull(3) ? "" : c.getString(3),
                        c.isNull(4) ? 0 : c.getInt(4)));
            }
        }
        return result;
    }
}
