/* Copyright 2015 Braden Farmer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.farmerbb.secondscreen.activity;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.service.NotificationService;
import com.farmerbb.secondscreen.util.U;

public final class NotificationSettingsActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    String lastValue;
    String lastValue2;
    
    boolean restartNotificationService = false;

    @SuppressWarnings("deprecation")
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setTitle(getResources().getString(R.string.notification_settings));

        SharedPreferences prefNew = U.getPrefNew(this);
        SharedPreferences.Editor editor = prefNew.edit();
        SharedPreferences prefMain = U.getPrefMain(this);

        editor.putBoolean("hide_notification", prefMain.getBoolean("hide_notification", false));
        editor.putString("notification_action", prefMain.getString("notification_action", "lock-device"));
        editor.putString("notification_action_2", prefMain.getString("notification_action_2", "turn-off"));
        editor.apply();

        // Set lastValue variables, for use with onSharedPreferenceChanged()
        lastValue = prefMain.getString("notification_action", "lock-device");
        lastValue2 = prefMain.getString("notification_action_2", "turn-off");

        // Add preferences
        addPreferencesFromResource(R.xml.notification_settings);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        U.bindPreferenceSummaryToValue(findPreference("notification_action"), opcl);
        U.bindPreferenceSummaryToValue(findPreference("notification_action_2"), opcl);

        if(U.isInNonRootMode(this)) {
            getPreferenceScreen().findPreference("notification_action").setEnabled(false);
            getPreferenceScreen().findPreference("notification_action_2").setEnabled(false);
        }
        
        Preference systemNotificationSettings = getPreferenceScreen().findPreference("system_notification_settings");
        
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            systemNotificationSettings.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent();
                    intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                    intent.putExtra("app_package", getPackageName());
                    intent.putExtra("app_uid", getApplicationInfo().uid);

                    try {
                        startActivity(intent);
                        restartNotificationService = true;
                    } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
                    
                    return true;
                }
            }); 
        } else
            systemNotificationSettings.setEnabled(false);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Register listener to check for changed preferences
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        if(restartNotificationService) {
            restartNotificationService = false;

            if(isNotificationServiceRunning()) {
                Intent serviceIntent = new Intent(this, NotificationService.class);
                stopService(serviceIntent);
                startService(serviceIntent);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Unregister listener
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

        SharedPreferences prefNew = U.getPrefNew(this);
        SharedPreferences prefCurrent = U.getPrefCurrent(this);
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences.Editor editor = prefMain.edit();

        Intent serviceIntent = new Intent(this, NotificationService.class);
        boolean restartNotification = false;

        if((prefNew.getBoolean("hide_notification", false) != prefMain.getBoolean("hide_notification", false)
                || !prefNew.getString("notification_action", "lock-device").equals(prefMain.getString("notification_action", "lock-device"))
                || !prefNew.getString("notification_action_2", "turn-off").equals(prefMain.getString("notification_action_2", "turn-off")))
                && !prefCurrent.getBoolean("not_active", true)) {
            stopService(serviceIntent);
            restartNotification = true;
        }

        editor.putBoolean("hide_notification", prefNew.getBoolean("hide_notification", false));
        editor.putString("notification_action", prefNew.getString("notification_action", "lock-device"));
        editor.putString("notification_action_2", prefNew.getString("notification_action_2", "turn-off"));
        editor.apply();

        // Cleanup
        SharedPreferences.Editor prefNewEditor = prefNew.edit();
        prefNewEditor.remove("hide_notification");
        prefNewEditor.remove("notification_action");
        prefNewEditor.remove("notification_action_2");
        prefNewEditor.apply();

        if(restartNotification)
            startService(serviceIntent);
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener opcl = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if(preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }

            return true;
        }
    };

    @SuppressWarnings("deprecation")
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.contains("notification_action")) {
            boolean unsupported = false;
            String value = sharedPreferences.getString(key, "null");

            switch(value) {
                case "temp_chrome":
                    try {
                        getPackageManager().getPackageInfo("com.chrome.canary", 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        try {
                            getPackageManager().getPackageInfo("com.chrome.dev", 0);
                        } catch (PackageManager.NameNotFoundException e1) {
                            try {
                                getPackageManager().getPackageInfo("com.chrome.beta", 0);
                            } catch (PackageManager.NameNotFoundException e2) {
                                try {
                                    getPackageManager().getPackageInfo("com.android.chrome", 0);
                                } catch (PackageManager.NameNotFoundException e3) {
                                    unsupported = true;
                                }
                            }
                        }
                    }
                    break;
                case "temp_immersive":
                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                        unsupported = true;
                    break;
                case "temp_overscan":
                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
                        unsupported = true;
                    break;
                case "temp_vibration_off":
                    if(!U.filesExist(U.vibrationOff))
                        unsupported = true;
                    break;
            }

            if(unsupported) {
                switch(key) {
                    case "notification_action":
                        value = lastValue;
                        break;
                    case "notification_action_2":
                        value = lastValue2;
                        break;
                }

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(key, value);
                editor.apply();

                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) findPreference(key);
                int index = listPreference.findIndexOfValue(value);

                // Set the summary to reflect the new value.
                listPreference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
                listPreference.setValue(value);

                U.showToast(this, R.string.not_compatible);
            } else {
                switch(key) {
                    case "notification_action":
                        lastValue = value;
                        break;
                    case "notification_action_2":
                        lastValue2 = value;
                        break;
                }
            }
        }
    }
    
    private boolean isNotificationServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for(ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(NotificationService.class.getName().equals(service.service.getClassName()))
                return true;
        }

        return false;
    }
}
