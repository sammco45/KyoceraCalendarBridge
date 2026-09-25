package com.samboucher.kyoceracalendarbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        BridgePrefs prefs = new BridgePrefs(context);
        if (prefs.isEnabled()) {
            BridgeScheduler.schedule(context);
        }
    }
}
