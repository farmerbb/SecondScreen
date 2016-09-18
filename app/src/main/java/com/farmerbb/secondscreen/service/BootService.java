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

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;

import com.farmerbb.secondscreen.util.U;

import java.io.File;
import java.util.Arrays;

// Service launched by BootReceiver.  Certain profile options (backlight off, vibration off, etc)
// do not stick after a device reboot; this service takes care of re-running any needed commands.
public final class BootService extends IntentService {

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public BootService() {
        super("BootService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Load preferences
        SharedPreferences prefCurrent = U.getPrefCurrent(this);
        SharedPreferences prefMain = U.getPrefMain(this);

        // Run superuser commands on boot
        final int safeModeDensityCommand = 0;
        final int safeModeSizeCommand = 1;
        final int rotationPreCommand = 2;
        final int rotationCommand = 3;
        final int rotationPostCommand = 4;
        final int vibrationCommand = 5;
        final int backlightCommand = 6;

        // Initialize su array
        String[] su = new String[backlightCommand + 1];
        Arrays.fill(su, "");

        if("auto-rotate".equals(prefCurrent.getString("rotation_lock_new", "do-nothing"))) {
            su[rotationCommand] = U.rotationCommand + Integer.toString(Intent.EXTRA_DOCK_STATE_DESK);
            if(Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 0) == 1
                    && Settings.Secure.getInt(getContentResolver(), "screensaver_activate_on_dock", 0) == 1) {
                su[rotationPreCommand] = U.rotationPrePostCommands + "0";
                su[rotationPostCommand] = U.rotationPrePostCommands + "1";
            }
        }

        if(prefCurrent.getBoolean("vibration_off", false)) {
            // Set vibration command
            for(File vibrationOff : U.vibrationOff) {
                if(vibrationOff.exists()) {
                    su[vibrationCommand] = "echo 0 > " + vibrationOff.getAbsolutePath();
                    break;
                }
            }
        }

        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();

        if(prefCurrent.getBoolean("backlight_off", false)
                && displays[displays.length - 1].getDisplayId() != Display.DEFAULT_DISPLAY) {
            // Turn auto-brightness off so it doesn't mess with things
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

            // Attempt to set screen brightness to 0 first to avoid complications later
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 0);

            // Set backlight command
            for(File backlightOff : U.backlightOff) {
                if(backlightOff.exists()) {
                    su[backlightCommand] = "sleep 2 && echo 0 > " + backlightOff.getAbsolutePath();
                    break;
                }
            }
        }

        if(prefMain.getBoolean("safe_mode", false) && "activity-manager".equals(prefCurrent.getString("ui_refresh", "do-nothing"))) {
            su[safeModeSizeCommand] = U.safeModeSizeCommand + "null";
            su[safeModeDensityCommand] = U.safeModeDensityCommand + "null";

            SharedPreferences.Editor editor = prefCurrent.edit();
            editor.putString("ui_refresh", "activity-manager-safe-mode");
            editor.commit();
        }

        // Run superuser commands
        for(String command : su) {
            if(!command.equals("")) {
                U.runCommands(this, su);
                break;
            }
        }

        // Send broadcast to start Taskbar
        if(prefCurrent.getBoolean("taskbar", false))
            sendBroadcast(new Intent("com.farmerbb.taskbar.START"));
    }
}
