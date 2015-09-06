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

import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.fragment.dialog.QuickActionsDialogFragment;
import com.farmerbb.secondscreen.service.LockDeviceService;
import com.farmerbb.secondscreen.util.PluginBundleManagerQuickActions;
import com.farmerbb.secondscreen.util.U;

import java.util.Scanner;

// This is the Quick Actions dialog, accessible by pressing the Quick Actions item in the action
// bar in MainActivity, the Quick Actions button in the notification bar, or by choosing the
// "SecondScreen - Quick Actions" plugin in Tasker.
// Quick Actions are implemented as temporary profiles that are run using the same engine as
// other profiles that the user may have created.  This is a PreferenceActivity that generates a
// quick_actions.xml file to store the temporary profile.  This xml file is cleared when the
// profile is turned off.
// Each option that is selected adds/modifies one entry into the xml file.  The ProfileLoadService
// is then invoked immediately after the option is selected, which loads the Quick Actions profile
// settings.  In the event that a user-created profile is already in effect, a copy of the running
// profile is made and used as the basis to build the Quick Actions profile off of.
public final class TaskerQuickActionsActivity extends PreferenceActivity implements
OnPreferenceClickListener,
SharedPreferences.OnSharedPreferenceChangeListener {

    String filename = "quick_actions";
    boolean launchShortcut = false;
    boolean launchedFromApp = false;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Close notification drawer
        Intent closeDrawer = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(closeDrawer);

        String key = "Null";
        String value = "Null";

        // Handle intents
        Intent quickLaunchIntent = getIntent();
        if((quickLaunchIntent.getStringExtra(U.KEY) != null) && (quickLaunchIntent.getStringExtra(U.VALUE) != null)) {
            key = quickLaunchIntent.getStringExtra(U.KEY);
            value = quickLaunchIntent.getStringExtra(U.VALUE);
            launchShortcut = true;
        }

        if(quickLaunchIntent.getBooleanExtra("launched-from-app", false))
            launchedFromApp = true;

        SharedPreferences prefMain = U.getPrefMain(this);
        if(!prefMain.getBoolean("first-run", false))
            finish();
        else if(launchShortcut) {
            if(key.equals("lock_device")) {
                Intent intent = new Intent(this, LockDeviceService.class);
                startService(intent);
                finish();
            } else if(key.equals("turn_off"))
                runResetSettings();
            else if(!key.equals("Null") && !value.equals("Null"))
                runQuickAction(key, value);
            else
                finish();
        } else {
            setTitle(getResources().getStringArray(R.array.pref_notification_action_list)[1]);

            if(launchedFromApp) {
                // Show dialog on first start
                if(!prefMain.getBoolean("quick_actions_dialog", false)) {
                    SharedPreferences.Editor editor = prefMain.edit();
                    editor.putBoolean("quick_actions_dialog", true);
                    editor.apply();

                    if(getFragmentManager().findFragmentByTag("quick_actions") == null) {
                        DialogFragment quickActionsFragment = new QuickActionsDialogFragment();
                        quickActionsFragment.show(getFragmentManager(), "quick_actions");
                    }
                }

                SharedPreferences prefSaved = U.getPrefQuickActions(this);
                SharedPreferences prefCurrent = U.getPrefCurrent(this);
                loadCurrentProfile(prefSaved, prefCurrent);
            }

            // Add preferences
            addPreferencesFromResource(R.xml.quick_actions_preferences);

            // Modifications for certain scenarios
            if(prefMain.getBoolean("landscape", false)) {
                ListPreference size = (ListPreference) findPreference("temp_size");
                size.setEntryValues(R.array.pref_resolution_list_values_landscape);
            }

            // Set title and OnClickListener for "Lock Device"
            findPreference("lock_device").setTitle(getResources().getStringArray(R.array.pref_notification_action_list)[2]);
            findPreference("lock_device").setOnPreferenceClickListener(this);

            // Set OnClickListener for "Reset settings"
            findPreference("turn_off").setOnPreferenceClickListener(this);

            // Disable unsupported preferences
            if(!U.filesExist(U.backlightOff))
                getPreferenceScreen().findPreference("temp_backlight_off").setEnabled(false);

            if(!U.filesExist(U.vibrationOff))
                getPreferenceScreen().findPreference("temp_vibration_off").setEnabled(false);

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
                getPreferenceScreen().findPreference("temp_overscan").setEnabled(false);

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                getPreferenceScreen().findPreference("temp_immersive_new").setEnabled(false);

            try {
                getPackageManager().getPackageInfo("com.chrome.dev", 0);
            } catch (NameNotFoundException e) {
                try {
                    getPackageManager().getPackageInfo("com.chrome.beta", 0);
                } catch (NameNotFoundException e1) {
                    try {
                        getPackageManager().getPackageInfo("com.android.chrome", 0);
                    } catch (NameNotFoundException e2) {
                        getPreferenceScreen().findPreference("temp_chrome").setEnabled(false);
                    }
                }
            }

            // Set active state of "Reset settings" button
            if(launchedFromApp)
                resetSettingsButton();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register listener to check for changed preferences
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister listener
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

        // Clear any "temp" preferences
        SharedPreferences prefNew = U.getPrefNew(this);
        SharedPreferences.Editor prefNewEditor = prefNew.edit();

        prefNewEditor.putString("temp_immersive_new", "Null");
        prefNewEditor.putString("temp_overscan", "Null");
        prefNewEditor.putString("temp_chrome", "Null");
        prefNewEditor.putString("temp_backlight_off", "Null");
        prefNewEditor.putString("temp_vibration_off", "Null");
        prefNewEditor.putString("temp_size", "Null");
        prefNewEditor.putString("temp_density", "Null");
        prefNewEditor.putString("temp_rotation_lock_new", "Null");

        prefNewEditor.apply();
    }

    @SuppressWarnings("deprecation")
    private void resetSettingsButton() {

        SharedPreferences prefSaved = U.getPrefQuickActions(this);
        if(prefSaved.getBoolean("quick_actions_active", false))
            getPreferenceScreen().findPreference("turn_off").setEnabled(true);
        else
            getPreferenceScreen().findPreference("turn_off").setEnabled(false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(launchedFromApp)
            runQuickAction(key, sharedPreferences.getString(key, "Null"));
        else
            doStuff(key, sharedPreferences.getString(key, "Null"));
    }

    private void doStuff(String key, String value) {
        PackageManager pm;
        pm = getPackageManager();

        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        i.addCategory(Intent.CATEGORY_DEFAULT);

        final ResolveInfo mInfo = pm.resolveActivity(i, 0);

        try {
            if(mInfo.activityInfo.applicationInfo.packageName.equals(getCallingPackage()))
                doLauncherStuff(key, value);
            else
                doTaskerStuff(key, value);
        } catch (NullPointerException e) {
            finish();
        }
    }

    private void doLauncherStuff(String key, String value) {
        // The meat of our shortcut
        Intent shortcutIntent = new Intent (this, TaskerQuickActionsActivity.class);
        shortcutIntent.setAction(Intent.ACTION_MAIN);
        shortcutIntent.putExtra(U.KEY, key);
        shortcutIntent.putExtra(U.VALUE, value);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Intent.ShortcutIconResource iconResource = Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher);

        // The result we are passing back from this activity
        Intent resultIntent = new Intent();
        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, U.generateBlurb(this, key, value, false));

        setResult(RESULT_OK, resultIntent);

        finish();
    }

    private void doTaskerStuff(String key, String value) {
        final Intent resultIntent = new Intent();

        /*
         * This extra is the data to ourselves: either for the Activity or the BroadcastReceiver. Note
         * that anything placed in this Bundle must be available to Locale's class loader. So storing
         * String, int, and other standard objects will work just fine. Parcelable objects are not
         * acceptable, unless they also implement Serializable. Serializable objects must be standard
         * Android platform objects (A Serializable class private to this plug-in's APK cannot be
         * stored in the Bundle, as Locale's classloader will not recognize it).
         */
        final Bundle resultBundle = PluginBundleManagerQuickActions.generateBundle(this, key, value);
        resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_BUNDLE, resultBundle);

        /*
         * The blurb is concise status text to be displayed in the host's UI.
         */

        resultIntent.putExtra(com.twofortyfouram.locale.Intent.EXTRA_STRING_BLURB, U.generateBlurb(this, key, value, false));

        setResult(RESULT_OK, resultIntent);

        finish();
    }

    private void runQuickAction(String key, String value) {
            SharedPreferences prefSaved = U.getPrefQuickActions(this);
            SharedPreferences prefCurrent = U.getPrefCurrent(this);
            SharedPreferences prefMain = U.getPrefMain(this);

            SharedPreferences.Editor editor = prefSaved.edit();
            SharedPreferences.Editor editorCurrent = prefCurrent.edit();

            if(!launchedFromApp)
                loadCurrentProfile(prefSaved, prefCurrent);

            boolean blacklisted = false;
            boolean reset = false;

            // Convert String preferences back to booleans if necessary, and save preferences to "quick_actions" XML file
            if(value.equals("Toggle")) {
                if(key.replace("temp_", "").equals(prefCurrent.getString("toggle", "null")))
                    reset = true;
                else if("null".equals(prefCurrent.getString("toggle", "null"))) {
                    if(prefCurrent.getBoolean("not_active", true)
                            || !"quick_actions".equals(prefCurrent.getString("filename", "0")))
                        editorCurrent.putString("toggle", key.replace("temp_", ""));
                } else
                    editorCurrent.remove("toggle");

                if(!reset)
                    editorCurrent.putString("toggle", key.replace("temp_", ""));

                editorCurrent.apply();
            } else {
                if(!"null".equals(prefSaved.getString("toggle", "null")))
                    editor.remove("toggle");

                switch(key) {
                    case "temp_overscan":
                        switch(value) {
                            case "Off":
                                editor.putBoolean("overscan", false);
                                break;
                            case "20%":
                                editor.putBoolean("overscan", true);
                                editor.putInt("overscan_left", 10);
                                editor.putInt("overscan_right", 10);
                                editor.putInt("overscan_top", 10);
                                editor.putInt("overscan_bottom", 10);
                                break;
                            case "40%":
                                editor.putBoolean("overscan", true);
                                editor.putInt("overscan_left", 20);
                                editor.putInt("overscan_right", 20);
                                editor.putInt("overscan_top", 20);
                                editor.putInt("overscan_bottom", 20);
                                break;
                            case "60%":
                                editor.putBoolean("overscan", true);
                                editor.putInt("overscan_left", 30);
                                editor.putInt("overscan_right", 30);
                                editor.putInt("overscan_top", 30);
                                editor.putInt("overscan_bottom", 30);
                                break;
                            case "80%":
                                editor.putBoolean("overscan", true);
                                editor.putInt("overscan_left", 40);
                                editor.putInt("overscan_right", 40);
                                editor.putInt("overscan_top", 40);
                                editor.putInt("overscan_bottom", 40);
                                break;
                            case "100%":
                                editor.putBoolean("overscan", true);
                                editor.putInt("overscan_left", 50);
                                editor.putInt("overscan_right", 50);
                                editor.putInt("overscan_top", 50);
                                editor.putInt("overscan_bottom", 50);
                                break;
                        }
                        break;
                    case "temp_chrome":
                        switch(value) {
                            case "On":
                                editor.putBoolean("chrome", true);
                                break;
                            case "Off":
                                editor.putBoolean("chrome", false);
                                break;
                        }
                        break;
                    case "temp_immersive":
                        switch(value) {
                            case "On":
                                editor.putString("immersive_new", "immersive-mode");
                                break;
                            case "Off":
                                editor.putString("immersive_new", "do-nothing");
                                break;
                        }
                        break;
                    case "temp_immersive_new":
                        editor.putString("immersive_new", value);
                        break;
                    case "temp_backlight_off":
                        switch(value) {
                            case "Off":
                                editor.putBoolean("backlight_off", true);
                                break;
                            case "On":
                                editor.putBoolean("backlight_off", false);
                                break;
                        }
                        break;
                    case "temp_vibration_off":
                        switch(value) {
                            case "Off":
                                editor.putBoolean("vibration_off", true);
                                break;
                            case "On":
                                editor.putBoolean("vibration_off", false);
                                break;
                        }
                        break;
                    case "temp_size":
                    case "temp_density":
                        DisplayMetrics metrics = new DisplayMetrics();
                        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                        Display disp = wm.getDefaultDisplay();
                        disp.getRealMetrics(metrics);

                        String requestedRes = " ";
                        String requestedDpi = " ";
                        int currentHeight = 0;
                        int currentWidth = 0;
                        int currentDpi;

                        switch(key) {
                            case "temp_size":
                                requestedRes = value;
                                requestedDpi = prefSaved.getString("density", "reset");
                                break;
                            case "temp_density":
                                requestedRes = prefSaved.getString("size", "reset");
                                requestedDpi = value;
                                break;
                        }

                        if(prefMain.getBoolean("debug_mode", false)) {
                            String size = prefCurrent.getString("size", "reset");
                            String density = prefCurrent.getString("density", "reset");

                            if("reset".equals(size)) {
                                currentHeight = prefMain.getInt("height", 0);
                                currentWidth = prefMain.getInt("width", 0);
                            } else {
                                Scanner scanner = new Scanner(size);
                                scanner.useDelimiter("x");

                                if(prefMain.getBoolean("landscape", false)) {
                                    currentHeight = scanner.nextInt();
                                    currentWidth = scanner.nextInt();
                                } else {
                                    currentWidth = scanner.nextInt();
                                    currentHeight = scanner.nextInt();
                                }

                                scanner.close();
                            }

                            if("reset".equals(density))
                                currentDpi = prefMain.getInt("density", 0);
                            else
                                currentDpi = Integer.parseInt(density);
                        } else {
                            if((getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && !prefMain.getBoolean("landscape", false))
                                    || (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && prefMain.getBoolean("landscape", false))) {
                                currentHeight = metrics.heightPixels;
                                currentWidth = metrics.widthPixels;
                            } else if((getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && !prefMain.getBoolean("landscape", false))
                                    || (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && prefMain.getBoolean("landscape", false))) {
                                currentHeight = metrics.widthPixels;
                                currentWidth = metrics.heightPixels;
                            }

                            currentDpi = metrics.densityDpi;
                        }

                        // Check to see if the user is trying to set a blacklisted resolution/DPI combo
                        blacklisted = U.isBlacklisted(requestedRes, requestedDpi, currentHeight, currentWidth, currentDpi, prefMain.getBoolean("landscape", false));

                        if(blacklisted && !prefMain.getBoolean("expert_mode", false))
                            U.showToastLong(this, R.string.blacklisted);
                        else
                            switch(key) {
                                case "temp_size":
                                    editor.putString("size", value);
                                    if("0".equals(prefSaved.getString("original_filename", "0"))) {
                                        editor.putString("ui_refresh", "system-ui");
                                        editorCurrent.putBoolean("force_safe_mode", true);
                                        editorCurrent.apply();
                                    }
                                    break;
                                case "temp_density":
                                    editor.putString("density", value);
                                    if("0".equals(prefSaved.getString("original_filename", "0"))) {
                                        editor.putString("ui_refresh", "system-ui");
                                        editorCurrent.putBoolean("force_safe_mode", true);
                                        editorCurrent.apply();
                                    }
                                    break;
                            }
                        break;
                    case "temp_rotation_lock_new":
                        editor.putString("rotation_lock_new", value);
                        break;
                }
            }

            if(!reset) {
                if(!prefSaved.getBoolean("quick_actions_active", false))
                    editor.putBoolean("quick_actions_active", true);

                if("0".equals(prefSaved.getString("original_filename", "0")))
                    editor.putString("profile_name", getResources().getString(R.string.bullet) + " " + U.generateBlurb(this, key, value, true) + " " + getResources().getString(R.string.bullet));
            }

            editor.apply();

            if(reset)
                runResetSettings();
            else {
                // Start quick actions profile
                if(prefMain.getBoolean("first-run", false)
                        && !(blacklisted && !prefMain.getBoolean("expert_mode", false)))
                    U.loadProfile(this, "quick_actions");

                // Set active state of "Reset settings" button
                if(launchedFromApp)
                    resetSettingsButton();

                finish();
        }
    }

    @Override
    public boolean onPreferenceClick(Preference p) {
        if(launchedFromApp) {
            if(p.getKey().equals("lock_device")) {
                Intent intent = new Intent(this, LockDeviceService.class);
                startService(intent);
                finish();
            } else if(p.getKey().equals("turn_off"))
                runResetSettings();
        } else
            doStuff(p.getKey(), "Null");

        return true;
    }

    private void runResetSettings() {
            SharedPreferences prefCurrent = U.getPrefCurrent(this);

            // Get original profile filename
            SharedPreferences prefSaved2 = U.getPrefQuickActions(this);
            filename = prefSaved2.getString("original_filename", "quick_actions");

            // Clear quick_actions.xml
            SharedPreferences.Editor prefSavedEditor = prefSaved2.edit();
            prefSavedEditor.clear();
            prefSavedEditor.apply();

            // Set active state of "Reset settings" button
            if(launchedFromApp)
                resetSettingsButton();

            if(filename.equals("quick_actions")) {
                if(!prefCurrent.getBoolean("not_active", true))
                    U.turnOffProfile(this);
            } else
                U.loadProfile(this, filename);

            finish();
    }

    private void loadCurrentProfile(SharedPreferences prefSaved, SharedPreferences prefCurrent) {
        SharedPreferences.Editor editor = prefSaved.edit();

        // If there is no profile active, clear the quick_actions.xml file
        if(prefCurrent.getBoolean("not_active", true)) {
            editor.clear();
            editor.apply();
        } else {
            // If there already is a profile active (non-Quick Actions), copy that profile's xml file to quick_actions.xml.
            if(!"quick_actions".equals(prefCurrent.getString("filename", "0"))) {
                SharedPreferences prefActive = getSharedPreferences(prefCurrent.getString("filename", "0"), Context.MODE_PRIVATE);

                editor.putString("original_filename", prefCurrent.getString("filename", "0"));

                if("fallback".equals(prefActive.getString("rotation_lock_new", "fallback")) && prefActive.getBoolean("rotation_lock", false))
                    editor.putString("rotation_lock_new", "landscape");
                else
                    editor.putString("rotation_lock_new", prefActive.getString("rotation_lock_new", "do-nothing"));

                if("fallback".equals(prefActive.getString("immersive_new", "fallback")) && prefActive.getBoolean("immersive", false))
                    editor.putString("immersive_new", "immersive-mode");
                else
                    editor.putString("immersive_new", prefActive.getString("immersive_new", "do-nothing"));

                if(prefActive.getBoolean("overscan", false)) {
                    editor.putBoolean("overscan", true);
                    editor.putInt("overscan_left", prefActive.getInt("overscan_left", 20));
                    editor.putInt("overscan_right", prefActive.getInt("overscan_right", 20));
                    editor.putInt("overscan_top", prefActive.getInt("overscan_top", 20));
                    editor.putInt("overscan_bottom", prefActive.getInt("overscan_bottom", 20));
                } else {
                    editor.putBoolean("overscan", false);
                    editor.putInt("overscan_left", 20);
                    editor.putInt("overscan_right", 20);
                    editor.putInt("overscan_top", 20);
                    editor.putInt("overscan_bottom", 20);
                }

                editor.putString("profile_name", prefActive.getString("profile_name", getResources().getString(R.string.action_new)));
                editor.putBoolean("bluetooth_on", prefActive.getBoolean("bluetooth_on", false));
                editor.putBoolean("wifi_on", prefActive.getBoolean("wifi_on", false));
                editor.putBoolean("daydreams_on", prefActive.getBoolean("daydreams_on", false));
                editor.putBoolean("show_touches", prefActive.getBoolean("show_touches", false));
                editor.putBoolean("backlight_off", prefActive.getBoolean("backlight_off", false));
                editor.putBoolean("vibration_off", prefActive.getBoolean("vibration_off", false));
                editor.putString("size", prefActive.getString("size", "reset"));
                editor.putString("density", prefActive.getString("density", "reset"));
                editor.putBoolean("chrome", prefActive.getBoolean("chrome", false));
                editor.putString("ui_refresh", prefActive.getString("ui_refresh", "do-nothing"));
                editor.putBoolean("navbar", prefActive.getBoolean("navbar", false));
                editor.putString("screen_timeout", prefActive.getString("screen_timeout", "do-nothing"));
                editor.apply();
            }
        }
    }
}