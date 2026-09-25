package com.samboucher.kyoceracalendarbridge;

import android.content.Context;
import android.content.SharedPreferences;

final class BridgePrefs {
    private static final String FILE = "calendar_bridge";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_TARGET_ID = "target_calendar_id";
    private static final String KEY_BASELINE_ID = "baseline_event_id";
    private static final String KEY_BASELINE_READY = "baseline_initialized";

    private final SharedPreferences prefs;

    BridgePrefs(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    long getTargetCalendarId() {
        return prefs.getLong(KEY_TARGET_ID, -1L);
    }

    void setTargetCalendarId(long id) {
        prefs.edit().putLong(KEY_TARGET_ID, id).apply();
    }

    long getBaselineEventId() {
        return prefs.getLong(KEY_BASELINE_ID, 0L);
    }

    boolean isBaselineInitialized() {
        return prefs.getBoolean(KEY_BASELINE_READY, false);
    }

    void setBaselineEventId(long id) {
        prefs.edit()
                .putLong(KEY_BASELINE_ID, Math.max(0L, id))
                .putBoolean(KEY_BASELINE_READY, true)
                .apply();
    }
}
