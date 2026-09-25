package com.samboucher.kyoceracalendarbridge;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

public class BridgeJobService extends JobService {
    private static final String TAG = "KyoceraCalendarBridge";

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                BridgePrefs prefs = new BridgePrefs(this);
                if (prefs.isEnabled()) {
                    BridgeEngine.Result result = BridgeEngine.migrateNewEvents(this);
                    Log.i(TAG, result.message);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Bridge job failed", t);
            } finally {
                // Trigger-content jobs cannot be persisted/periodic. Android's documented
                // pattern is to schedule a fresh trigger job after processing completes.
                if (new BridgePrefs(this).isEnabled()) {
                    BridgeScheduler.schedule(this);
                }
            }
        }, "calendar-bridge").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
