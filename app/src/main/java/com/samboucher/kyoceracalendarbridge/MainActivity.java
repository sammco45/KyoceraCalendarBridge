package com.samboucher.kyoceracalendarbridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_CALENDAR = 100;

    private TextView sourceText;
    private TextView statusText;
    private Spinner targetSpinner;
    private Switch enableSwitch;
    private Button migrateExistingButton;
    private Button runNowButton;
    private Button permissionButton;

    private final List<CalendarInfo> davxCalendars = new ArrayList<>();
    private boolean updatingUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private View buildUi() {
        int pad = dp(18);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Kyocera Calendar Bridge");
        title.setTextSize(24f);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("Moves events created in Kyocera's local calendar into a DAVx5 calendar, so DAVx5 can sync them to iCloud. Incoming DAVx5 events are left alone.");
        explanation.setTextSize(15f);
        explanation.setPadding(0, 0, 0, dp(18));
        root.addView(explanation);

        permissionButton = new Button(this);
        permissionButton.setText("Grant calendar access");
        permissionButton.setOnClickListener(v -> requestCalendarPermission());
        root.addView(permissionButton);

        sourceText = new TextView(this);
        sourceText.setPadding(0, dp(14), 0, dp(8));
        root.addView(sourceText);

        TextView targetLabel = new TextView(this);
        targetLabel.setText("DAVx5 destination:");
        targetLabel.setPadding(0, dp(8), 0, dp(4));
        root.addView(targetLabel);

        targetSpinner = new Spinner(this);
        root.addView(targetSpinner);

        Button saveTarget = new Button(this);
        saveTarget.setText("Use selected destination");
        saveTarget.setOnClickListener(v -> saveSelectedTarget());
        root.addView(saveTarget);

        enableSwitch = new Switch(this);
        enableSwitch.setText("Bridge new Kyocera events automatically");
        enableSwitch.setPadding(0, dp(16), 0, dp(8));
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingUi) return;
            setBridgeEnabled(isChecked);
        });
        root.addView(enableSwitch);

        runNowButton = new Button(this);
        runNowButton.setText("Run bridge now");
        runNowButton.setOnClickListener(v -> runBridge(false));
        root.addView(runNowButton);

        migrateExistingButton = new Button(this);
        migrateExistingButton.setText("Migrate existing local events");
        migrateExistingButton.setOnClickListener(v -> confirmMigrateExisting());
        root.addView(migrateExistingButton);

        Button refreshButton = new Button(this);
        refreshButton.setText("Refresh status");
        refreshButton.setOnClickListener(v -> refreshUi());
        root.addView(refreshButton);

        statusText = new TextView(this);
        statusText.setPadding(0, dp(18), 0, 0);
        statusText.setGravity(Gravity.START);
        statusText.setTextSize(14f);
        root.addView(statusText);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        return scroll;
    }

    private void refreshUi() {
        updatingUi = true;
        boolean granted = hasCalendarPermission();
        permissionButton.setVisibility(granted ? View.GONE : View.VISIBLE);
        targetSpinner.setEnabled(granted);
        enableSwitch.setEnabled(granted);
        runNowButton.setEnabled(granted);
        migrateExistingButton.setEnabled(granted);

        if (!granted) {
            sourceText.setText("Calendar access is required.");
            statusText.setText("Grant READ_CALENDAR and WRITE_CALENDAR to continue.");
            davxCalendars.clear();
            targetSpinner.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Permission required"}));
            enableSwitch.setChecked(false);
            updatingUi = false;
            return;
        }

        CalendarInfo source = CalendarRepository.findKyoceraCalendar(this);
        if (source == null) {
            sourceText.setText("Kyocera source: not found");
        } else {
            sourceText.setText("Kyocera source: " + source.displayName + " (calendar " + source.id + ")");
        }

        davxCalendars.clear();
        davxCalendars.addAll(CalendarRepository.findDavxCalendars(this));
        List<String> labels = new ArrayList<>();
        for (CalendarInfo info : davxCalendars) labels.add(info.toString());
        if (labels.isEmpty()) labels.add("No writable DAVx5 calendars found");
        targetSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));

        BridgePrefs prefs = new BridgePrefs(this);
        long selectedId = prefs.getTargetCalendarId();
        int selectedPosition = -1;
        for (int i = 0; i < davxCalendars.size(); i++) {
            if (davxCalendars.get(i).id == selectedId) {
                selectedPosition = i;
                break;
            }
        }
        if (selectedPosition < 0) {
            for (int i = 0; i < davxCalendars.size(); i++) {
                if ("Default".equalsIgnoreCase(davxCalendars.get(i).displayName)) {
                    selectedPosition = i;
                    prefs.setTargetCalendarId(davxCalendars.get(i).id);
                    break;
                }
            }
        }
        if (selectedPosition < 0 && !davxCalendars.isEmpty()) {
            selectedPosition = 0;
            prefs.setTargetCalendarId(davxCalendars.get(0).id);
        }
        if (selectedPosition >= 0) targetSpinner.setSelection(selectedPosition);

        enableSwitch.setChecked(prefs.isEnabled());
        CalendarInfo target = selectedPosition >= 0 ? davxCalendars.get(selectedPosition) : null;
        String targetDescription = target == null
                ? "none"
                : target.displayName + " (calendar " + target.id + ", " + target.accountName + ")";
        statusText.setText(
                "Destination: " + targetDescription + "\n" +
                "Automatic bridge: " + (prefs.isEnabled() ? "ON" : "OFF") + "\n" +
                "Baseline local event ID: " + (prefs.isBaselineInitialized() ? prefs.getBaselineEventId() : "not initialized") + "\n\n" +
                "New events are recreated in DAVx5 and the Kyocera-local original is removed, preventing duplicate entries.");
        updatingUi = false;
    }

    private void saveSelectedTarget() {
        if (!hasCalendarPermission() || davxCalendars.isEmpty()) return;
        int pos = targetSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= davxCalendars.size()) return;
        CalendarInfo target = davxCalendars.get(pos);
        new BridgePrefs(this).setTargetCalendarId(target.id);
        Toast.makeText(this, "Destination set to " + target.displayName, Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void setBridgeEnabled(boolean enabled) {
        if (!hasCalendarPermission()) {
            requestCalendarPermission();
            refreshUi();
            return;
        }

        BridgePrefs prefs = new BridgePrefs(this);
        if (enabled) {
            if (davxCalendars.isEmpty()) {
                Toast.makeText(this, "No writable DAVx5 calendar found.", Toast.LENGTH_LONG).show();
                refreshUi();
                return;
            }
            saveSelectedTarget();
            CalendarInfo source = CalendarRepository.findKyoceraCalendar(this);
            if (source == null) {
                Toast.makeText(this, "Kyocera local calendar not found.", Toast.LENGTH_LONG).show();
                refreshUi();
                return;
            }

            // New-event mode starts from the current highest local event ID. Old local
            // events remain untouched until the user explicitly chooses to migrate them.
            prefs.setBaselineEventId(Math.max(0L, CalendarRepository.getMaxEventId(this, source.id)));
            prefs.setEnabled(true);
            BridgeScheduler.schedule(this);
            Toast.makeText(this, "Automatic bridge enabled.", Toast.LENGTH_SHORT).show();
        } else {
            prefs.setEnabled(false);
            BridgeScheduler.cancel(this);
            Toast.makeText(this, "Automatic bridge disabled.", Toast.LENGTH_SHORT).show();
        }
        refreshUi();
    }

    private void runBridge(boolean migrateAll) {
        if (!hasCalendarPermission()) return;
        saveSelectedTarget();
        statusText.setText(migrateAll ? "Migrating existing events…" : "Checking for new events…");
        new Thread(() -> {
            BridgeEngine.Result result = migrateAll
                    ? BridgeEngine.migrateAllExisting(this)
                    : BridgeEngine.migrateNewEvents(this);
            runOnUiThread(() -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                refreshUi();
            });
        }, "calendar-bridge-manual").start();
    }

    private void confirmMigrateExisting() {
        if (davxCalendars.isEmpty()) return;
        int pos = targetSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= davxCalendars.size()) return;
        CalendarInfo target = davxCalendars.get(pos);
        new AlertDialog.Builder(this)
                .setTitle("Migrate existing local events?")
                .setMessage("This will recreate every event still stored in Kyocera's local calendar inside '"
                        + target.displayName
                        + "' and then remove the local originals. DAVx5 can then upload them. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Migrate", (dialog, which) -> runBridge(true))
                .show();
    }

    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCalendarPermission() {
        requestPermissions(new String[]{
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
        }, REQ_CALENDAR);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR) refreshUi();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
