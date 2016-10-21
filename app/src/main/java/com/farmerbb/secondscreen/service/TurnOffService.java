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
import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.view.Surface;
import android.widget.Toast;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.UnableToStartActivity;
import com.farmerbb.secondscreen.activity.TaskerConditionActivity;
import com.farmerbb.secondscreen.util.ShowToast;
import com.farmerbb.secondscreen.util.U;

import java.io.File;
import java.util.Arrays;

// This service is run whenever the user requests the currently running profile to be turned off.
// The TurnOffService runs in a similar manner as the ProfileLoadService. It reads current.xml
// (generated previously by ProfileLoadService) to determine which actions were previously run by
// that service, and essentially reverses these actions, to restore the device to the state it was
// in before the profile was loaded.  It will also stop the NotificationService.

public final class TurnOffService extends IntentService {

    Handler showToast;

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public TurnOffService() {
        super("TurnOffService");
        showToast = new Handler();
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences prefCurrent = U.getPrefCurrent(this);

        if(U.hasRoot(this))
            turnOffProfile(prefCurrent);
        else {
            SharedPreferences.Editor editor = prefCurrent.edit();
            editor.putString("filename", prefCurrent.getString("filename_backup", "0"));
            editor.remove("filename_backup");
            editor.commit();

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                showToast.post(new ShowToast(this, R.string.no_superuser, Toast.LENGTH_LONG));
            else {
                Intent intent2 = new Intent(this, UnableToStartActivity.class);
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent2);
            }

            // Refresh list of profiles
            U.listProfilesBroadcast(this);
        }
    }

    @SuppressLint("CommitPrefEdits")
    private void turnOffProfile(SharedPreferences prefCurrent) {
        SharedPreferences.Editor editor = prefCurrent.edit();

        // Show brief "Turning off profile" notification
        if(!U.isInNonRootMode(this))
            showToast.post(new ShowToast(this, R.string.turning_off_profile, Toast.LENGTH_SHORT));

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
        final int stayOnCommand = 15;
        final int showTouchesCommand = 16;
        final int uiRefreshCommand = 17;
        final int uiRefreshCommand2 = 18;
        final int vibrationCommand = 19;
        final int backlightCommand = 20;

        // Initialize su array
        String[] su = new String[backlightCommand + 1];
        Arrays.fill(su, "");

        // Bluetooth
        if(prefCurrent.getBoolean("bluetooth_on", true)) {
            BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
            if(prefCurrent.getBoolean("bluetooth_on_system", false))
                bluetooth.enable();
            else
                bluetooth.disable();
        }

        // Wi-Fi
        if(prefCurrent.getBoolean("wifi_on", true)) {
            WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wifi.setWifiEnabled(prefCurrent.getBoolean("wifi_on_system", false));
        }

        // Resolution and density

        // Determine if CyanogenMod workaround is needed
        // Recent builds of CyanogenMod require the "Restart ActivityManager" UI refresh method
        // to be set, to work around the automatic reboot when the DPI is changed.
        boolean cmWorkaround = false;
        if(getPackageManager().hasSystemFeature("com.cyanogenmod.android")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            cmWorkaround = true;

        String uiRefresh = U.isInNonRootMode(this) || cmWorkaround
                ? "activity-manager"
                : prefCurrent.getString("ui_refresh", "do-nothing");

        boolean shouldRunSizeCommand = U.runSizeCommand(this, "reset");
        boolean shouldRunDensityCommand = U.runDensityCommand(this, "reset");

        boolean runSizeCommand = uiRefresh.contains("activity-manager") || shouldRunSizeCommand;
        boolean runDensityCommand = uiRefresh.contains("activity-manager") || shouldRunDensityCommand;

        if(runSizeCommand) {
            if(uiRefresh.equals("activity-manager")
                    || uiRefresh.equals("activity-manager-safe-mode")
                    || cmWorkaround)
                // Run a different command if we are restarting the ActivityManager
                su[sizeCommand] = U.safeModeSizeCommand("null");
            else
                su[sizeCommand] = U.sizeCommand("reset");
        }

        if(runDensityCommand) {
            if((uiRefresh.equals("activity-manager")
                    || uiRefresh.equals("activity-manager-safe-mode"))
                    && !cmWorkaround)
                // Run a different command if we are restarting the ActivityManager
                su[densityCommand] = U.safeModeDensityCommand("null");
            else {
                su[densityCommand] = U.densityCommand("reset");

                // We run the density command twice, for reliability
                su[densityCommand2] = su[densityCommand];
            }
        }

        // Overscan
        if((Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1) && prefCurrent.getBoolean("overscan", true))
            su[overscanCommand] = U.overscanCommand + "reset";

        // Screen rotation
        Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, prefCurrent.getInt("user_rotation", Surface.ROTATION_0));
        Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, prefCurrent.getInt("rotation_setting", 1));

        // Run checks to determine if rotation command needs to be run.
        // Don't run this command if we don't need to.
        boolean runRotationCommand = true;

        if(prefCurrent.getInt("dock_mode", Intent.EXTRA_DOCK_STATE_UNDOCKED) == prefCurrent.getInt("dock_mode_current", Intent.EXTRA_DOCK_STATE_UNDOCKED))
            runRotationCommand = false;

        if(runRotationCommand) {
            su[rotationCommand] = U.rotationCommand + Integer.toString(prefCurrent.getInt("dock_mode", Intent.EXTRA_DOCK_STATE_UNDOCKED));

            // Workaround for if Daydreams is enabled and we are enabling dock mode
            if(prefCurrent.getInt("dock_mode", Intent.EXTRA_DOCK_STATE_UNDOCKED) == Intent.EXTRA_DOCK_STATE_DESK
                    && Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 0) == 1
                    && Settings.Secure.getInt(getContentResolver(), "screensaver_activate_on_dock", 0) == 1)
            {
                su[rotationPreCommand] = U.rotationPrePostCommands + "0";
                su[rotationPostCommand] = U.rotationPrePostCommands + "1";
            }
        }

        // Screen timeout
        switch(prefCurrent.getString("screen_timeout", "do-nothing")) {
            case "always-on":
                Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, prefCurrent.getInt("screen_timeout_system", 60000));
                break;
            case "always-on-charging":
                su[stayOnCommand] = U.stayOnCommand + Integer.toString(prefCurrent.getInt("stay_on_while_plugged_in_system", 0));
                break;
        }

        // Chrome
        int channel = 0;

        // If multiple versions of Chrome are installed on the device,
        // assume that the user is running the newest version.
        try {
            getPackageManager().getPackageInfo("com.chrome.canary", 0);
            channel = 3;
        } catch (PackageManager.NameNotFoundException e) {
            try {
                getPackageManager().getPackageInfo("com.chrome.dev", 0);
                channel = 2;
            } catch (PackageManager.NameNotFoundException e1) {
                try {
                    getPackageManager().getPackageInfo("com.chrome.beta", 0);
                    channel = 1;
                } catch (PackageManager.NameNotFoundException e2) {
                    try {
                        getPackageManager().getPackageInfo("com.android.chrome", 0);
                    } catch (PackageManager.NameNotFoundException e3) { /* Gracefully fail */ }
                }
            }
        }

        if(prefCurrent.getBoolean("chrome", true)) {
            su[chromeCommand] = U.chromeCommandRemove;
            su[chromeCommand2] = U.chromeCommand2(channel);
        }

        // Daydreams
        if(prefCurrent.getBoolean("daydreams_on", true)) {
            su[daydreamsCommand] = U.daydreamsCommand(prefCurrent.getBoolean("daydreams_on_system", false));
            su[daydreamsChargingCommand] = U.daydreamsChargingCommand(prefCurrent.getBoolean("daydreams_while_charging", false));
        }

        // Vibration off
        if(prefCurrent.getBoolean("vibration_off", false) && prefCurrent.getInt("vibration_value", -1) != -1) {
            for(File vibrationOff : U.vibrationOff) {
                if(vibrationOff.exists())
                    su[vibrationCommand] = "echo " + Integer.toString(prefCurrent.getInt("vibration_value", -1)) + " > " + vibrationOff.getAbsolutePath();
            }
        }

        // Backlight off
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

        // Show touches
        if(prefCurrent.getBoolean("show_touches", true))
            su[showTouchesCommand] = U.showTouchesCommand(prefCurrent.getBoolean("show_touches_system", false));

        // Navigation bar
        if(getPackageManager().hasSystemFeature("com.cyanogenmod.android")
                && prefCurrent.getBoolean("navbar", true)) {
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

        // Immersive mode
        if(!"do-nothing".equals(prefCurrent.getString("immersive_new", "do-nothing")))
            su[immersiveCommand] = U.immersiveCommand("do-nothing");

        // Freeform windows
        boolean rebootRequired = shouldRunSizeCommand || shouldRunDensityCommand;

        if(prefCurrent.getBoolean("freeform", true)) {
            su[freeformCommand] = U.freeformCommand(prefCurrent.getBoolean("freeform_system", false));
            rebootRequired = true;
        }

        // HDMI rotation
        if(prefCurrent.getString("hdmi_rotation", "landscape").equals("portrait"))
            su[hdmiRotationCommand] = U.hdmiRotationCommand + prefCurrent.getString("hdmi_rotation_system", "landscape");

        // UI refresh
        switch(uiRefresh) {
            case "system-ui":
                su[uiRefreshCommand] = U.uiRefreshCommand(this, false);
                su[uiRefreshCommand2] = U.uiRefreshCommand2(this);
                break;
            case "activity-manager":
            case "activity-manager-safe-mode":
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

        // Determine if we need to stop Taskbar
        boolean shouldStopTaskbar = false;

        if(prefCurrent.getBoolean("taskbar", false))
            shouldStopTaskbar = true;

        // Clear preferences and commit (for reliability)
        editor.clear();
        editor.commit();

        // Clear quick_actions.xml
        SharedPreferences prefSaved = U.getPrefQuickActions(this);
        SharedPreferences.Editor prefSavedEditor = prefSaved.edit();
        prefSavedEditor.clear();
        prefSavedEditor.commit();

        // Run superuser commands
        U.runCommands(this, su, rebootRequired);

        // Refresh list of profiles
        U.listProfilesBroadcast(this);

        // Send broadcast to request Tasker query
        Intent query = new Intent(com.twofortyfouram.locale.api.Intent.ACTION_REQUEST_QUERY)
                .putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_STRING_ACTIVITY_CLASS_NAME, TaskerConditionActivity.class.getName());

        sendBroadcast(query);

        // Send broadcast to stop Taskbar
        if(shouldStopTaskbar)
            sendBroadcast(new Intent("com.farmerbb.taskbar.QUIT"));

        // Stop NotificationService
        Intent serviceIntent = new Intent(this, NotificationService.class);
        stopService(serviceIntent);
    }
}
