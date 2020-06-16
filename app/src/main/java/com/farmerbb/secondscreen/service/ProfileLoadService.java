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

package com.farmerbb.secondscreen.service;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.Display;
import android.view.Surface;
import android.widget.Toast;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.UnableToStartActivity;
import com.farmerbb.secondscreen.activity.TaskerConditionActivity;
import com.farmerbb.secondscreen.util.CommandDispatcher;
import com.farmerbb.secondscreen.util.ShowToast;
import com.farmerbb.secondscreen.util.U;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

// The ProfileLoadService is an important service that is responsible for loading all profiles.
// It loads a xml file created by ProfileEditService, and will execute the actions based on
// what options are set.  For actions requiring superuser access, it will instead generate a list
// of commands to be run by superuser right after the non-root actions are performed.
// This service also generates a current.xml file representing the current state of the active profile.
// If a profile is loaded while another one is currently active, the profile's xml file is compared
// to the current.xml file, so that only actions that differ from those already performed are
// executed.  Lastly, the ProfileLoadService starts (or restarts) the NotificationService so that
// the user is always informed of what profile is currently active.
public final class ProfileLoadService extends SecondScreenIntentService {

    String filename;
    Handler showToast;

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public ProfileLoadService() {
        super("ProfileLoadService");
        showToast = new Handler();
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onHandleIntent(Intent intent) {
        super.onHandleIntent(intent);

        if(intent.getStringExtra(U.NAME) != null)
            filename = intent.getStringExtra(U.NAME);

        SharedPreferences prefCurrent = U.getPrefCurrent(this);

        // Check for root and then load profile
        if(U.hasElevatedPermissions(this, true))
            loadProfile(prefCurrent);
        else {
            SharedPreferences.Editor editor = prefCurrent.edit();
            editor.remove("filename");
            editor.commit();

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                showToast.post(new ShowToast(this, R.string.no_superuser, Toast.LENGTH_LONG));
            else {
                Intent intent2 = new Intent(this, UnableToStartActivity.class);
                intent2.putExtra("action", "load-profile");
                intent2.putExtra("filename", filename);
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent2);
            }

            // Refresh list of profiles
            U.listProfilesBroadcast(this);
        }
    }

    @SuppressLint("CommitPrefEdits")
    private void loadProfile(SharedPreferences prefCurrent) {
        // Load preferences
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences prefSaved = U.getPrefSaved(this, filename);
        SharedPreferences.Editor editor = prefCurrent.edit();

        // Show brief "Loading profile" notification
        showToast.post(new ShowToast(this, R.string.loading_profile, Toast.LENGTH_SHORT));

        // Handle toggling of certain values
        String toggle = prefCurrent.getString("toggle", "null");
        if(!"null".equals(toggle) && filename.equals("quick_actions")) {
            SharedPreferences.Editor editorSaved = prefSaved.edit();

            if("immersive_new".equals(toggle)) {
                toggle = "immersive";
                editorSaved.remove("immersive_new");
            }

            editorSaved.putBoolean(toggle, !prefSaved.getBoolean(toggle, false));
            editorSaved.commit();

            editor.remove("toggle");
        }

        // Build commands to pass to su

        // Commands will be run in this order (except if "Restart ActivityManager" is selected)
        final int densityCommand = 0;
        final int densityCommand2 = 1;
        final int sizeCommand = 2;
        final int overscanCommand = 3;
        final int rotationPreCommand = 4;
        final int rotationCommand = 5;
        final int rotationPostCommand = 6;
        final int chromeCommand = 7;
        final int chromeCommand2 = 8;
        final int immersiveCommand = 9;
        final int freeformCommand = 10;
        final int hdmiRotationCommand = 11;
        final int navbarCommand = 12;
        final int daydreamsCommand = 13;
        final int daydreamsChargingCommand = 14;
        final int safeModeDensityCommand = 15;
        final int safeModeSizeCommand = 16;
        final int uiRefreshCommand = 17;
        final int uiRefreshCommand2 = 18;
        final int stayOnCommand = 19;
        final int showTouchesCommand = 20;
        final int vibrationCommand = 21;
        final int backlightCommand = 22;

        // Initialize su array
        String[] su = new String[backlightCommand + 1];
        Arrays.fill(su, "");

        // Bluetooth
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
            if(bluetooth != null) {
                if(prefCurrent.getBoolean("not_active", true))
                    editor.putBoolean("bluetooth_on_system", bluetooth.isEnabled());

                if(prefSaved.getBoolean("bluetooth_on", false)) {
                    if(prefCurrent.getBoolean("not_active", true))
                        bluetooth.enable();
                    else {
                        if(!prefCurrent.getBoolean("bluetooth_on", false))
                            bluetooth.enable();
                    }
                } else {
                    if(!prefCurrent.getBoolean("not_active", true))
                        if(prefCurrent.getBoolean("bluetooth_on", false)) {
                            if(prefCurrent.getBoolean("bluetooth_on_system", false))
                                bluetooth.enable();
                            else
                                bluetooth.disable();
                        }
                }
            }
        }

        // Wi-Fi
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if(prefCurrent.getBoolean("not_active", true))
                editor.putBoolean("wifi_on_system", wifi.isWifiEnabled());

            if(prefSaved.getBoolean("wifi_on", false)) {
                if(prefCurrent.getBoolean("not_active", true))
                    wifi.setWifiEnabled(true);
                else {
                    if(!prefCurrent.getBoolean("wifi_on", false))
                        wifi.setWifiEnabled(true);
                }
            } else {
                if(!prefCurrent.getBoolean("not_active", true))
                    if(prefCurrent.getBoolean("wifi_on", false))
                        wifi.setWifiEnabled(prefCurrent.getBoolean("wifi_on_system", false));
            }
        }

        // Clear default home
        boolean shouldEnableTaskbarHome = false;
        boolean shouldDisableTaskbarHome = false;

        if(!prefCurrent.getBoolean("clear_home", false) && prefSaved.getBoolean("clear_home", false))
            shouldEnableTaskbarHome = true;

        if(prefCurrent.getBoolean("clear_home", false) && !prefSaved.getBoolean("clear_home", false))
            shouldDisableTaskbarHome = true;

        boolean shouldClearHome = shouldEnableTaskbarHome || shouldDisableTaskbarHome;
        if(shouldClearHome) {
            Intent taskbarIntent = null;
            String taskbarPackageName = U.getTaskbarPackageName(this);

            if(taskbarPackageName != null) {
                if(shouldEnableTaskbarHome)
                    taskbarIntent = new Intent("com.farmerbb.taskbar.ENABLE_HOME");
                else if(shouldDisableTaskbarHome)
                    taskbarIntent = new Intent("com.farmerbb.taskbar.DISABLE_HOME");
            }

            if(taskbarIntent != null) {
                taskbarIntent.setPackage(taskbarPackageName);
                taskbarIntent.putExtra("secondscreen", true);
                sendBroadcast(taskbarIntent);
            } else {
                U.clearDefaultHome(this);

                shouldEnableTaskbarHome = false;
                shouldDisableTaskbarHome = false;
            }
        }

        // Freeform windows
        boolean rebootRequired = false;

        if(prefCurrent.getBoolean("not_active", true))
            editor.putBoolean("freeform_system", U.hasFreeformSupport(this));

        if(prefSaved.getBoolean("freeform", false)) {
            if(prefCurrent.getBoolean("not_active", true)) {
                su[freeformCommand] = U.freeformCommand(true);
                if(!U.hasFreeformSupport(this))
                    rebootRequired = true;
            } else {
                if(!prefCurrent.getBoolean("freeform", false)) {
                    su[freeformCommand] = U.freeformCommand(true);
                    if(!U.hasFreeformSupport(this))
                        rebootRequired = true;
                }
            }
        } else {
            if(!prefCurrent.getBoolean("not_active", true))
                if(prefCurrent.getBoolean("freeform", false)) {
                    boolean freeformSystem = prefCurrent.getBoolean("freeform_system", false);
                    su[freeformCommand] = U.freeformCommand(freeformSystem);
                    if(U.hasFreeformSupport(this) != freeformSystem)
                        rebootRequired = true;
                }
        }

        // Resolution and density

        // Determine if CyanogenMod workaround is needed
        // Recent builds of CyanogenMod require the "Restart ActivityManager" UI refresh method
        // to be set, to work around the automatic reboot when the DPI is changed.
        boolean cmWorkaround = false;
        if(getPackageManager().hasSystemFeature("com.cyanogenmod.android")
                && Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1)
            cmWorkaround = true;

        String uiRefresh = (cmWorkaround || rebootRequired)
                ? "activity-manager"
                : prefSaved.getString("ui_refresh", "do-nothing");

        boolean shouldRunSizeCommand = U.runSizeCommand(this, prefSaved.getString("size", "reset"));
        boolean shouldRunDensityCommand = U.runDensityCommand(this, prefSaved.getString("density", "reset"));

        boolean runSizeCommand = uiRefresh.contains("activity-manager") || shouldRunSizeCommand;
        boolean runDensityCommand = uiRefresh.contains("activity-manager") || shouldRunDensityCommand;

        if(runSizeCommand) {
            String size = prefSaved.getString("size", "reset");

            // Swap height and width under certain conditions
            if(!"reset".equals(size) && (prefMain.getBoolean("notch_compat_mode", false)
                    || (U.isDesktopModeActive(this) && !prefMain.getBoolean("landscape", false)))) {
                String[] splitSize = size.split("x");
                size = splitSize[1] + "x" + splitSize[0];
            }

            if(uiRefresh.equals("activity-manager") || cmWorkaround) {
                // Run a different command if we are restarting the ActivityManager
                if("reset".equals(size))
                    su[sizeCommand] = U.safeModeSizeCommand("null");
                else
                    su[sizeCommand] = U.safeModeSizeCommand(size.replace('x', ','));
            } else
                su[sizeCommand] = U.sizeCommand(this, size);
        }

        if(runDensityCommand) {
            String density = prefSaved.getString("density", "reset");
            if(uiRefresh.equals("activity-manager") && !cmWorkaround) {
                // Run a different command if we are restarting the ActivityManager
                if("reset".equals(density))
                    su[densityCommand] = U.safeModeDensityCommand("null");
                else
                    su[densityCommand] = U.safeModeDensityCommand(density);
            } else {
                su[densityCommand] = U.densityCommand(this, density);

                // We run the density command twice, for reliability
                su[densityCommand2] = su[densityCommand];
            }
        }

        if(!rebootRequired) {
            rebootRequired = uiRefresh.contains("activity-manager")
                    && (shouldRunSizeCommand || shouldRunDensityCommand);
        }

        // Overscan
        if(U.canEnableOverscan()) {
            if(prefSaved.getBoolean("overscan", false)) {
                String overscanValues = "";

                if(prefCurrent.getBoolean("not_active", true)) {
                    overscanValues = Integer.toString(prefSaved.getInt("overscan_bottom", 20)) + ","
                            + Integer.toString(prefSaved.getInt("overscan_left", 20)) + ","
                            + Integer.toString(prefSaved.getInt("overscan_top", 20)) + ","
                            + Integer.toString(prefSaved.getInt("overscan_right", 20));
                } else {
                    if(prefCurrent.getBoolean("overscan", false)) {
                        // Check saved overscan integers against current overscan integers
                        if((prefSaved.getInt("overscan_bottom", 0) != prefCurrent.getInt("overscan_bottom", 20))
                        || (prefSaved.getInt("overscan_left", 0) != prefCurrent.getInt("overscan_left", 20))
                        || (prefSaved.getInt("overscan_top", 0) != prefCurrent.getInt("overscan_top", 20))
                        || (prefSaved.getInt("overscan_right", 0) != prefCurrent.getInt("overscan_right", 20))) {
                            if(prefMain.getBoolean("landscape", false)) {
                                overscanValues = Integer.toString(prefSaved.getInt("overscan_left", 20)) + ","
                                        + Integer.toString(prefSaved.getInt("overscan_top", 20)) + ","
                                        + Integer.toString(prefSaved.getInt("overscan_right", 20)) + ","
                                        + Integer.toString(prefSaved.getInt("overscan_bottom", 20));
                            } else {
                                overscanValues = Integer.toString(prefSaved.getInt("overscan_bottom", 20)) + ","
                                        + Integer.toString(prefSaved.getInt("overscan_left", 20)) + ","
                                        + Integer.toString(prefSaved.getInt("overscan_top", 20)) + ","
                                        + Integer.toString(prefSaved.getInt("overscan_right", 20));
                            }
                        }
                    } else if(prefMain.getBoolean("landscape", false)) {
                        overscanValues = Integer.toString(prefSaved.getInt("overscan_left", 20)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_top", 20)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_right", 20)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_bottom", 20));
                    } else {
                        overscanValues = Integer.toString(prefSaved.getInt("overscan_bottom", 20)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_left", 20)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_top", 20)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_right", 20));
                    }
                }

                if(!overscanValues.isEmpty()) {
                    // Fix overscan values under certain conditions
                    if(prefMain.getBoolean("notch_compat_mode", false)
                            || (U.isDesktopModeActive(this) && !prefMain.getBoolean("landscape", false))) {
                        String[] splitValues = overscanValues.split(",");
                        overscanValues = splitValues[1] + ","
                                + splitValues[2] + ","
                                + splitValues[3] + ","
                                + splitValues[0];
                    }

                    su[overscanCommand] = U.overscanCommand(this, overscanValues);
                }
            } else if(!prefCurrent.getBoolean("not_active", true) && prefCurrent.getBoolean("overscan", false))
                su[overscanCommand] = U.overscanCommand(this, "reset");
        }

        // Screen rotation
        if("fallback".equals(prefSaved.getString("rotation_lock_new", "fallback")) && prefSaved.getBoolean("rotation_lock", false))
            editor.putString("rotation_lock_new", "landscape");
        else
            editor.putString("rotation_lock_new", prefSaved.getString("rotation_lock_new", "do-nothing"));

        try {
            if(prefCurrent.getBoolean("not_active", true)) {
                editor.putInt("user_rotation", Settings.System.getInt(getContentResolver(), Settings.System.USER_ROTATION));
                editor.putInt("rotation_setting", Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION));
            }
        } catch (SettingNotFoundException e) { /* Gracefully fail */ }

        int dockMode;
        if(prefCurrent.getBoolean("not_active", true)) {
            // Get current UI mode
            UiModeManager mUiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
            int uiMode = mUiModeManager.getCurrentModeType();

            // Determine current dock state, based on the current UI mode
            switch(uiMode) {
                case Configuration.UI_MODE_TYPE_DESK:
                    dockMode = Intent.EXTRA_DOCK_STATE_DESK;
                    break;
                case Configuration.UI_MODE_TYPE_CAR:
                    dockMode = Intent.EXTRA_DOCK_STATE_CAR;
                    break;
                default:
                    dockMode = Intent.EXTRA_DOCK_STATE_UNDOCKED;
            }

            editor.putInt("dock_mode", dockMode);
            editor.putInt("dock_mode_current", dockMode);
        } else
            dockMode = prefCurrent.getInt("dock_mode_current", Intent.EXTRA_DOCK_STATE_UNDOCKED);

        switch(prefSaved.getString("rotation_lock_new", "fallback")) {
            case "fallback":
                if(prefSaved.getBoolean("rotation_lock", false)) {
                    dockMode = Intent.EXTRA_DOCK_STATE_UNDOCKED;

                    if(prefMain.getBoolean("landscape", false))
                        Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_0);
                    else
                        Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_90);

                    Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                } else {
                    dockMode = prefCurrent.getInt("dock_mode", Intent.EXTRA_DOCK_STATE_UNDOCKED);
                    Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, prefCurrent.getInt("user_rotation", Surface.ROTATION_0));
                    Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, prefCurrent.getInt("rotation_setting", 1));
                }
                break;
            case "auto-rotate":
                dockMode = Intent.EXTRA_DOCK_STATE_DESK;
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
                break;
            case "landscape":
                dockMode = Intent.EXTRA_DOCK_STATE_UNDOCKED;

                if(prefMain.getBoolean("landscape", false))
                    Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_0);
                else
                    Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_90);

                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                break;
            case "portrait":
                dockMode = Intent.EXTRA_DOCK_STATE_UNDOCKED;

                if(prefMain.getBoolean("landscape", false))
                    Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_90);
                else
                    Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_0);

                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
                break;
            case "do-nothing":
                dockMode = prefCurrent.getInt("dock_mode", Intent.EXTRA_DOCK_STATE_UNDOCKED);
                Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, prefCurrent.getInt("user_rotation", Surface.ROTATION_0));
                Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, prefCurrent.getInt("rotation_setting", 1));
                break;
        }

        // Run checks to determine if rotation command needs to be run.
        // Don't run this command if we don't need to.
        boolean runRotationCommand = true;

        if(dockMode == prefCurrent.getInt("dock_mode_current", Intent.EXTRA_DOCK_STATE_UNDOCKED))
            runRotationCommand = false;
        else
            editor.putInt("dock_mode_current", dockMode);

        if(U.isDesktopModeActive(this))
            runRotationCommand = false;

        if(runRotationCommand) {
            su[rotationCommand] = U.rotationCommand + Integer.toString(dockMode);

            // Workaround for if Daydreams is enabled and we are enabling dock mode
            if(dockMode == Intent.EXTRA_DOCK_STATE_DESK
                    && Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 0) == 1
                    && Settings.Secure.getInt(getContentResolver(), "screensaver_activate_on_dock", 0) == 1)
            {
                su[rotationPreCommand] = U.rotationPrePostCommands + "0";
                su[rotationPostCommand] = U.rotationPrePostCommands + "1";
            }
        }

        // Screen timeout
        if(prefCurrent.getBoolean("not_active", true)) {
            editor.putInt("screen_timeout_system", Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 60000));
            editor.putInt("stay_on_plugged_in_system", Settings.Global.getInt(getContentResolver(), Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0));
        }

        switch(prefSaved.getString("screen_timeout", "do-nothing")) {
            case "always-on":
                if(!"always-on".equals(prefCurrent.getString("screen_timeout", "null"))) {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 2147482000);
                    if(!prefCurrent.getBoolean("not_active", true))
                        su[stayOnCommand] = U.stayOnCommand + Integer.toString(prefCurrent.getInt("stay_on_while_plugged_in_system", 0));
                }
                break;
            case "always-on-charging":
                if(!"always-on-charging".equals(prefCurrent.getString("screen_timeout", "null"))) {
                    su[stayOnCommand] = U.stayOnCommand + Integer.toString(BatteryManager.BATTERY_PLUGGED_AC
                            | BatteryManager.BATTERY_PLUGGED_USB | BatteryManager.BATTERY_PLUGGED_WIRELESS);
                    if(!prefCurrent.getBoolean("not_active", true))
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, prefCurrent.getInt("screen_timeout_system", 60000));
                }
                break;
            case "do-nothing":
                if(!"do-nothing".equals(prefCurrent.getString("screen_timeout", "null")) && !prefCurrent.getBoolean("not_active", true)) {
                    Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, prefCurrent.getInt("screen_timeout_system", 60000));
                    su[stayOnCommand] = U.stayOnCommand + Integer.toString(prefCurrent.getInt("stay_on_while_plugged_in_system", 0));
                }
                break;
        }

        // Chrome desktop mode
        if(prefSaved.getBoolean("chrome", false)) {
            if(prefCurrent.getBoolean("not_active", true)) {
                su[chromeCommand] = U.chromeCommand(this);
                su[chromeCommand2] = U.chromeCommand2(this);
            } else {
                if(!prefCurrent.getBoolean("chrome", false)) {
                    su[chromeCommand] = U.chromeCommand(this);
                    su[chromeCommand2] = U.chromeCommand2(this);
                }
            }
        } else {
            if(!prefCurrent.getBoolean("not_active", true))
                if(prefCurrent.getBoolean("chrome", false)) {
                    su[chromeCommand] = U.chromeCommandRemove;
                    su[chromeCommand2] = U.chromeCommand2(this);
                }
        }

        // Daydreams
        if(prefCurrent.getBoolean("not_active", true)) {
            if(Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 0) == 1)
                editor.putBoolean("daydreams_on_system", true);
            else if(Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 0) == 0)
                editor.putBoolean("daydreams_on_system", false);

            if(Settings.Secure.getInt(getContentResolver(), "screensaver_activate_on_sleep", 0) == 1)
                editor.putBoolean("daydreams_while_charging", true);
            else if(Settings.Secure.getInt(getContentResolver(), "screensaver_activate_on_sleep", 0) == 0)
                editor.putBoolean("daydreams_while_charging", false);
        }

        if(prefSaved.getBoolean("daydreams_on", false)) {
            if(prefCurrent.getBoolean("not_active", true)) {
                su[daydreamsCommand] = U.daydreamsCommand(true);
                su[daydreamsChargingCommand] = U.daydreamsChargingCommand(true);
            } else {
                if(!prefCurrent.getBoolean("daydreams_on", false)) {
                    su[daydreamsCommand] = U.daydreamsCommand(true);
                    su[daydreamsChargingCommand] = U.daydreamsChargingCommand(true);
                }
            }
        } else {
            if(!prefCurrent.getBoolean("not_active", true))
                if(prefCurrent.getBoolean("daydreams_on", false)) {
                    su[daydreamsCommand] = U.daydreamsCommand(prefCurrent.getBoolean("daydreams_on_system", false));
                    su[daydreamsChargingCommand] = U.daydreamsChargingCommand(prefCurrent.getBoolean("daydreams_while_charging", false));
                }
        }

        // Vibration off
        String vibrationValue = "-1";

        if(prefCurrent.getBoolean("not_active", true)) {
            editor.putInt("haptic_feedback_enabled_system",
                    Settings.System.getInt(getContentResolver(), "haptic_feedback_enabled", 0));
        }

        // If user has set "vibration off" in profile
        if(prefSaved.getBoolean("vibration_off", false)) {
            // Check if one of the correct vibration files exist on device, read from it, then get the current value.
            // Also, set the vibration command now if UI refresh command is "do nothing"
            if(U.filesExist(U.vibrationOff)) {
                try {
                    // Open the file on disk
                    FileInputStream input1 = null;
                    for(File vibrationOff : U.vibrationOff) {
                        if(vibrationOff.exists())
                            input1 = new FileInputStream(vibrationOff);
                    }

                    InputStreamReader reader1 = new InputStreamReader(input1);
                    BufferedReader buffer1 = new BufferedReader(reader1);

                    // Load the file
                    vibrationValue = buffer1.readLine();

                    // Close file on disk
                    reader1.close();

                    if(prefCurrent.getBoolean("not_active", true)) {
                        for(File vibrationOff : U.vibrationOff) {
                            if(vibrationOff.exists())
                                su[vibrationCommand] = "echo 0 > " + vibrationOff.getAbsolutePath();
                        }
                    } else if(!prefCurrent.getBoolean("vibration_off", false)) {
                        for(File vibrationOff : U.vibrationOff) {
                            if(vibrationOff.exists())
                                su[vibrationCommand] = "echo 0 > " + vibrationOff.getAbsolutePath();
                        }
                    }
                } catch (IOException e1) { /* Gracefully fail */ }
            }

            // Save the current vibration value for future use, if NOT 0 (vibration already off) or -1 (unsupported device).
            // This should always be valid, because "vibration off" can only be set on a supported device (should never be -1)
            if(!(vibrationValue.equals("0") || vibrationValue.equals("-1")))
                editor.putInt("vibration_value", Integer.parseInt(vibrationValue));

            // Change haptic feedback system preference for devices that don't support disabling via sysfs
            if(prefCurrent.getBoolean("not_active", true)
                    || !prefCurrent.getBoolean("vibration_off", false))
                Settings.System.putInt(getContentResolver(), "haptic_feedback_enabled", 0);
        }

        // If user has NOT set vibration off in profile
        else {
            // Check to see if correct vibration files exist
            // If so, check to see if a valid vibration value exists (returns -1 if invalid)
            // If this check also passes: set the vibration command to use the saved value (from before any profiles were applied)
            // Also, set the saved vibration value to -1 (invalid)
            if(prefCurrent.getInt("vibration_value", -1) != -1) {
                for(File vibrationOff : U.vibrationOff) {
                    if(vibrationOff.exists())
                        su[vibrationCommand] = "echo " + Integer.toString(prefCurrent.getInt("vibration_value", -1)) + " > " + vibrationOff.getAbsolutePath();
                }

                editor.putInt("vibration_value", -1);
            }

            // Change haptic feedback system preference for devices that don't support disabling via sysfs
            if(!prefCurrent.getBoolean("not_active", true)
                    && prefCurrent.getBoolean("vibration_off", false)
                    && prefCurrent.getInt("haptic_feedback_enabled_system", -1) != -1)
                Settings.System.putInt(getContentResolver(), "haptic_feedback_enabled",
                        prefCurrent.getInt("haptic_feedback_enabled_system", -1));
        }

        // Backlight off

        // If user has set "backlight off" in profile
        if(prefSaved.getBoolean("backlight_off", false)) {
            // Save current auto-brightness value for future use, if the current state of "backlight off" is false
            try {
                if(!prefCurrent.getBoolean("backlight_off", false))
                    editor.putInt("auto_brightness", Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE));
            } catch (SettingNotFoundException e) { /* Gracefully fail */ }

            // Save current backlight value for future use, if the current state of "backlight off" is false
            try {
                if(!prefCurrent.getBoolean("backlight_off", false))
                    editor.putInt("backlight_value", Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS));
            } catch (SettingNotFoundException e1) { /* Gracefully fail */ }

            if(!uiRefresh.equals("activity-manager")) {
                // Check to see if Chromecast screen mirroring is active.
                // If it is, and user has "Restart SystemUI" as their UI refresh method,
                // then don't immediately dim the screen.
                // However, if we are currently using Chromecast screen mirroring
                // and we are switching to a different profile that uses the
                // "Restart SystemUI" UI refresh method, then temporarily undim the screen.

                DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
                Display[] displays = dm.getDisplays();

                if((displays[displays.length - 1].getDisplayId() != Display.DEFAULT_DISPLAY
                        || prefMain.getBoolean("force_backlight_off", false))) {
                    if(U.castScreenActive(this)
                        && uiRefresh.equals("system-ui")
                        && ((runSizeCommand || runDensityCommand)
                        || prefCurrent.getBoolean("not_active", true)
                        || prefCurrent.getBoolean("force_ui_refresh", false))) {
                        if(prefCurrent.getBoolean("backlight_off", false)
                            && prefCurrent.getInt("backlight_value", -1) != -1) {
                            if(prefCurrent.getInt("backlight_value", -1) <= 10) {
                                // Manually update the sysfs value to guarantee that the backlight will restore
                                for(File backlightOff : U.backlightOff) {
                                    if(backlightOff.exists()) {
                                        su[backlightCommand] = "echo " + Integer.toString(prefCurrent.getInt("backlight_value", -1)) + " > " + backlightOff.getAbsolutePath();
                                        break;
                                    }
                                }
                            }

                            // Restore the saved values for backlight and auto-brightness
                            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, prefCurrent.getInt("backlight_value", -1));
                            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, prefCurrent.getInt("auto_brightness", Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
                        }
                    } else {
                        // Turn auto-brightness off so it doesn't mess with things
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

                        // Attempt to set screen brightness to 0 first to avoid complications later
                        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 0);

                        // Set backlight command (delay will be handled later)
                        if(prefCurrent.getBoolean("not_active", true)) {
                            for(File backlightOff : U.backlightOff) {
                                if(backlightOff.exists()) {
                                    su[backlightCommand] = "echo 0 > " + backlightOff.getAbsolutePath();
                                    break;
                                }
                            }
                        } else if(!prefCurrent.getBoolean("backlight_off", false)) {
                            for(File backlightOff : U.backlightOff) {
                                if(backlightOff.exists()) {
                                    su[backlightCommand] = "echo 0 > " + backlightOff.getAbsolutePath();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // If user has NOT set backlight off in profile
        else {
            // Check to see if a valid backlight value was saved previously (returns -1 if invalid)
            if(prefCurrent.getInt("backlight_value", -1) != -1) {
                if(prefCurrent.getInt("backlight_value", -1) <= 10) {
                    // Manually update the sysfs value to guarantee that the backlight will restore
                    for(File backlightOff : U.backlightOff) {
                        if(backlightOff.exists()) {
                            su[backlightCommand] = "echo " + Integer.toString(prefCurrent.getInt("backlight_value", -1)) + " > " + backlightOff.getAbsolutePath();
                            break;
                        }
                    }
                }

                // Restore the saved values for backlight and auto-brightness
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, prefCurrent.getInt("backlight_value", -1));
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, prefCurrent.getInt("auto_brightness", Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));

                // Set the saved backlight value to -1 (invalid)
                editor.putInt("backlight_value", -1);
            }
        }

        // Show touches
        if(prefCurrent.getBoolean("not_active", true)) {
            if(Settings.System.getInt(getContentResolver(), "show_touches", 0) == 1)
                editor.putBoolean("show_touches_system", true);
            else if(Settings.System.getInt(getContentResolver(), "show_touches", 0) == 0)
                editor.putBoolean("show_touches_system", false);
        }

        if(prefSaved.getBoolean("show_touches", false)) {
            if(prefCurrent.getBoolean("not_active", true))
                su[showTouchesCommand] = U.showTouchesCommand(true);
            else {
                if(!prefCurrent.getBoolean("show_touches", false))
                    su[showTouchesCommand] = U.showTouchesCommand(true);
            }
        } else {
            if(!prefCurrent.getBoolean("not_active", true))
                if(prefCurrent.getBoolean("show_touches", false))
                    su[showTouchesCommand] = U.showTouchesCommand(prefCurrent.getBoolean("show_touches_system", false));
        }

        if(CommandDispatcher.getInstance().addCommand(this, su[showTouchesCommand])
                || U.isInNonRootMode(this))
            su[showTouchesCommand] = "";

        // Navigation bar
        if(getPackageManager().hasSystemFeature("com.cyanogenmod.android")) {
            if(prefCurrent.getBoolean("not_active", true)) {
                if(Settings.System.getInt(getContentResolver(), "dev_force_show_navbar", 0) == 1)
                    editor.putBoolean("navbar_system", true);
                else if(Settings.System.getInt(getContentResolver(), "dev_force_show_navbar", 0) == 0)
                    editor.putBoolean("navbar_system", false);
            }

            if(prefSaved.getBoolean("navbar", false)) {
                if(prefCurrent.getBoolean("not_active", true))
                    try {
                        Settings.System.putInt(getContentResolver(), "dev_force_show_navbar", 1);
                    } catch (SecurityException e) {
                        su[navbarCommand] = U.navbarCommand(true);
                    }
                else {
                    if(!prefCurrent.getBoolean("navbar", false))
                        try {
                            Settings.System.putInt(getContentResolver(), "dev_force_show_navbar", 1);
                        } catch (SecurityException e) {
                            su[navbarCommand] = U.navbarCommand(true);
                        }
                }
            } else {
                if(!prefCurrent.getBoolean("not_active", true))
                    if(prefCurrent.getBoolean("navbar", false))
                        if(prefCurrent.getBoolean("navbar_system", false))
                            try {
                                Settings.System.putInt(getContentResolver(), "dev_force_show_navbar", 1);
                            } catch (SecurityException e) {
                                su[navbarCommand] = U.navbarCommand(true);
                            }
                        else
                            try {
                                Settings.System.putInt(getContentResolver(), "dev_force_show_navbar", 0);
                            } catch (SecurityException e) {
                                su[navbarCommand] = U.navbarCommand(false);
                            }
            }
        }

        // Immersive mode
        if(U.canEnableImmersiveMode()) {
            if("fallback".equals(prefSaved.getString("immersive_new", "fallback")) && prefSaved.getBoolean("immersive", false))
                editor.putString("immersive_new", "immersive-mode");
            else
                editor.putString("immersive_new", prefSaved.getString("immersive_new", "do-nothing"));

            switch(prefSaved.getString("immersive_new", "fallback")) {
                case "fallback":
                    if(prefSaved.getBoolean("immersive", false)) {
                        if(!"immersive-mode".equals(prefCurrent.getString("immersive_new", "do-nothing"))) {
                            su[immersiveCommand] = U.immersiveCommand("immersive-mode");
                        }
                    } else {
                        if(!"do-nothing".equals(prefCurrent.getString("immersive_new", "do-nothing")) && !prefCurrent.getBoolean("not_active", true))
                            su[immersiveCommand] = U.immersiveCommand("do-nothing");
                    }
                    break;
                case "status-only":
                    if(!"status-only".equals(prefCurrent.getString("immersive_new", "do-nothing"))) {
                        su[immersiveCommand] = U.immersiveCommand("status-only");
                    }
                    break;
                case "immersive-mode":
                    if(!"immersive-mode".equals(prefCurrent.getString("immersive_new", "do-nothing"))) {
                        su[immersiveCommand] = U.immersiveCommand("immersive-mode");
                    }
                    break;
                case "do-nothing":
                    if(!"do-nothing".equals(prefCurrent.getString("immersive_new", "do-nothing")) && !prefCurrent.getBoolean("not_active", true))
                        su[immersiveCommand] = U.immersiveCommand("do-nothing");
                    break;
            }
        }

        // HDMI rotation
        if(prefCurrent.getBoolean("not_active", true)) {
            if("portrait".equals(U.getSystemProperty("persist.demo.hdmirotation")))
                editor.putString("hdmi_rotation_system", "portrait");
            else
                editor.putString("hdmi_rotation_system", "landscape");
        }

        if(prefSaved.getString("hdmi_rotation", "landscape").equals("portrait")) {
            if(prefCurrent.getBoolean("not_active", true))
                su[hdmiRotationCommand] = U.hdmiRotationCommand + prefSaved.getString("hdmi_rotation", "landscape");
            else {
                if(!prefCurrent.getString("hdmi_rotation", "landscape").equals("portrait"))
                    su[hdmiRotationCommand] = U.hdmiRotationCommand + prefSaved.getString("hdmi_rotation", "landscape");
            }
        } else {
            if(!prefCurrent.getBoolean("not_active", true))
                if(prefCurrent.getString("hdmi_rotation", "landscape").equals("portrait"))
                    su[hdmiRotationCommand] = U.hdmiRotationCommand + prefCurrent.getString("hdmi_rotation_system", "landscape");
        }

        // UI refresh

        // If a UI refresh command was run on the current profile, and we are loading a different
        // profile without a UI refresh command, run the previous one to restore things back to normal
        if(!prefCurrent.getBoolean("not_active", true)
                && !"do-nothing".equals(prefCurrent.getString("ui_refresh", "do-nothing"))
                && "do-nothing".equals(uiRefresh)) {
            if(prefCurrent.getString("ui_refresh", "do-nothing").equals("activity-manager-safe-mode"))
                uiRefresh = "activity-manager";
            else
                uiRefresh = prefCurrent.getString("ui_refresh", "do-nothing");
        }

        // Only refresh the UI if any of these conditions are met:
        // * Size and density commands need to be run
        // * A profile is not already active
        // * The user has changed the UI refresh method in the currently running profile
        if((runSizeCommand || runDensityCommand)
                || prefCurrent.getBoolean("not_active", true)
                || prefCurrent.getBoolean("force_ui_refresh", false)) {
            switch(uiRefresh) {
                case "do-nothing":
                    if(prefMain.getBoolean("safe_mode", false)) {
                        if(runSizeCommand)
                            su[safeModeSizeCommand] = U.safeModeSizeCommand("null");

                        if(runDensityCommand)
                            su[safeModeDensityCommand] = U.safeModeDensityCommand("null");
                    }
                    break;
                case "system-ui":
                    if(prefMain.getBoolean("safe_mode", false)) {
                        if(runSizeCommand)
                            su[safeModeSizeCommand] = U.safeModeSizeCommand("null");

                        if(runDensityCommand)
                            su[safeModeDensityCommand] = U.safeModeDensityCommand("null");
                    }

                    su[uiRefreshCommand] = U.uiRefreshCommand(this, false);
                    su[uiRefreshCommand2] = U.uiRefreshCommand2(this, shouldClearHome);
                    break;
                case "activity-manager":
                    su[uiRefreshCommand] = U.uiRefreshCommand(this, true);

                    // We run the superuser commands in a different order if this option is selected,
                    // so re-create the command array.
                    // Remaining commands will be handled by the BootService
                    if(cmWorkaround) {
                        su[uiRefreshCommand] = su[uiRefreshCommand].replace('1', '5');
                        su = new String[]{
                                su[sizeCommand],
                                su[overscanCommand],
                                su[chromeCommand],
                                su[chromeCommand2],
                                su[immersiveCommand],
                                su[freeformCommand],
                                su[hdmiRotationCommand],
                                su[navbarCommand],
                                su[daydreamsCommand],
                                su[daydreamsChargingCommand],
                                su[stayOnCommand],
                                su[showTouchesCommand],
                                su[densityCommand],
                                su[uiRefreshCommand]};
                    } else
                        su = new String[]{
                                su[densityCommand],
                                su[sizeCommand],
                                su[overscanCommand],
                                su[chromeCommand],
                                su[chromeCommand2],
                                su[immersiveCommand],
                                su[freeformCommand],
                                su[hdmiRotationCommand],
                                su[navbarCommand],
                                su[daydreamsCommand],
                                su[daydreamsChargingCommand],
                                su[stayOnCommand],
                                su[showTouchesCommand],
                                su[uiRefreshCommand]};
                    break;
            }
        }

        // Handle backlight command delay
        if(prefSaved.getBoolean("backlight_off", false)
                && !"activity-manager".equals(uiRefresh)
                && su[uiRefreshCommand].equals("")
                && !su[backlightCommand].equals(""))
            su[backlightCommand] = "sleep 2 && " + su[backlightCommand];

        // Remove any special preferences that are not needed after profile load
        if(prefCurrent.getBoolean("force_safe_mode", false)) {
            editor.remove("force_safe_mode");

            if(!"activity-manager".equals(uiRefresh)) {
                su[safeModeSizeCommand] = U.safeModeSizeCommand("null");
                su[safeModeDensityCommand] = U.safeModeDensityCommand("null");
            }
        }

        if(prefCurrent.getBoolean("force_ui_refresh", false))
            editor.remove("force_ui_refresh");

        // Determine if we need to start or stop Taskbar
        boolean shouldEnableFreeform = false;
        boolean shouldDisableFreeform = false;

        if(!prefCurrent.getBoolean("freeform", false) && prefSaved.getBoolean("freeform", false))
            shouldEnableFreeform = true;

        if(prefCurrent.getBoolean("freeform", false) && !prefSaved.getBoolean("freeform", false))
            shouldDisableFreeform = true;

        boolean shouldStartTaskbar = false;
        boolean shouldStopTaskbar = false;

        if(!prefCurrent.getBoolean("taskbar", false) && prefSaved.getBoolean("taskbar", false))
            shouldStartTaskbar = true;

        if(prefCurrent.getBoolean("taskbar", false) && !prefSaved.getBoolean("taskbar", false))
            shouldStopTaskbar = true;

        // Save preferences for future use
        editor.putString("profile_name", prefSaved.getString("profile_name", getResources().getString(R.string.action_new)));
        editor.putString("size", prefSaved.getString("size", "reset"));
        editor.putString("density", prefSaved.getString("density", "reset"));
        editor.putString("ui_refresh", uiRefresh);
        editor.putString("screen_timeout", prefSaved.getString("screen_timeout", "do-nothing"));
        editor.putString("hdmi_rotation", prefSaved.getString("hdmi_rotation", "landscape"));
        editor.putBoolean("vibration_off", prefSaved.getBoolean("vibration_off", false));
        editor.putBoolean("backlight_off", prefSaved.getBoolean("backlight_off", false));
        editor.putBoolean("overscan", prefSaved.getBoolean("overscan", false));
        editor.putBoolean("chrome", prefSaved.getBoolean("chrome", false));
        editor.putBoolean("show_touches", prefSaved.getBoolean("show_touches", false));
        editor.putBoolean("daydreams_on", prefSaved.getBoolean("daydreams_on", false));
        editor.putBoolean("wifi_on", prefSaved.getBoolean("wifi_on", false));
        editor.putBoolean("bluetooth_on", prefSaved.getBoolean("bluetooth_on", false));
        editor.putBoolean("navbar", prefSaved.getBoolean("navbar", false));
        editor.putBoolean("freeform", prefSaved.getBoolean("freeform", false));
        editor.putBoolean("taskbar", prefSaved.getBoolean("taskbar", false));
        editor.putBoolean("clear_home", prefSaved.getBoolean("clear_home", false));
        editor.putInt("overscan_left", prefSaved.getInt("overscan_left", 20));
        editor.putInt("overscan_right", prefSaved.getInt("overscan_right", 20));
        editor.putInt("overscan_top", prefSaved.getInt("overscan_top", 20));
        editor.putInt("overscan_bottom", prefSaved.getInt("overscan_bottom", 20));
        editor.putInt("external_display_id", U.getExternalDisplayID(this));

        // Set "not_active" status to false
        if(prefCurrent.getBoolean("not_active", true)) {
            editor.putBoolean("not_active", false);
            editor.putLong("time_of_profile_start", System.currentTimeMillis());
        }

        // Commit settings (for reliability)
        editor.commit();

        // Clear quick_actions.xml if profile being loaded is not a Quick Action
        if(!filename.equals("quick_actions")) {
            SharedPreferences prefSaved2 = U.getPrefQuickActions(this);
            SharedPreferences.Editor prefSavedEditor = prefSaved2.edit();
            prefSavedEditor.clear();
            prefSavedEditor.commit();
        }

        // Run superuser commands
        U.runCommands(this, su, rebootRequired);

        // Refresh list of profiles
        U.listProfilesBroadcast(this);

        // Send broadcast to request Tasker query
        Intent query = new Intent(com.twofortyfouram.locale.api.Intent.ACTION_REQUEST_QUERY)
                .putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_STRING_ACTIVITY_CLASS_NAME, TaskerConditionActivity.class.getName());

        sendBroadcast(query);

        // Send broadcast to start or stop Taskbar
        Intent freeformIntent = null;

        if(shouldEnableFreeform)
            freeformIntent = new Intent("com.farmerbb.taskbar.ENABLE_FREEFORM_MODE");
        else if(shouldDisableFreeform)
            freeformIntent = new Intent("com.farmerbb.taskbar.DISABLE_FREEFORM_MODE");

        if(freeformIntent != null) {
            freeformIntent.setPackage(U.getTaskbarPackageName(this));
            freeformIntent.putExtra("secondscreen", true);
            sendBroadcast(freeformIntent);
        }

        Intent taskbarIntent = null;

        if(shouldStartTaskbar)
            taskbarIntent = new Intent("com.farmerbb.taskbar.START");
        else if(shouldStopTaskbar)
            taskbarIntent = new Intent("com.farmerbb.taskbar.QUIT");

        if(taskbarIntent != null) {
            taskbarIntent.setPackage(U.getTaskbarPackageName(this));
            taskbarIntent.putExtra("secondscreen", true);
            sendBroadcast(taskbarIntent);
        }

        if((shouldEnableTaskbarHome || shouldDisableTaskbarHome)
                && !(U.isInNonRootMode(this) && rebootRequired))
            U.goHome(this);

        // Start (or restart) NotificationService
        Intent serviceIntent = new Intent(this, NotificationService.class);
        stopService(serviceIntent);
        U.startService(this, serviceIntent);
    }
}