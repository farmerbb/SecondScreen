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

// The ScreenOnService is responsible for turning the backlight off again if the user happens to
// turn their device off and back on.
// This service will turn the backlight off immediately after the user wakes their device from sleep.
// (If the user is mirroring their display to Chromecast, the TempBacklightOnService is run instead.)
public final class ScreenOnService extends IntentService {

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public ScreenOnService() {
        super("ScreenOnService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Turn the backlight back off after the device wakes up
        SharedPreferences prefCurrent = U.getPrefCurrent(this);
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();

        if(!prefCurrent.getBoolean("not_active", true)
            && prefCurrent.getBoolean("backlight_off", false)
            && displays[displays.length - 1].getDisplayId() != Display.DEFAULT_DISPLAY) {

            // Turn auto-brightness off so it doesn't mess with things
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

            // Attempt to set screen brightness to 0 first to avoid complications later
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 0);

            // Run superuser command to blank screen again after device was turned off
            for(File backlightOff : U.backlightOff) {
                if(backlightOff.exists()) {
                    U.runCommand(this, "sleep 2 && echo 0 > " + backlightOff.getAbsolutePath());
                    break;
                }
            }
        }
    }
}
