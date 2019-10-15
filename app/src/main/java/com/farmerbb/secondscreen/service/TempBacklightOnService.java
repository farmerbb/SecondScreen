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

import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.farmerbb.secondscreen.util.U;

import java.io.File;

// The TempBacklightOnService is responsible for turning the backlight off again if the user happens
// to turn their device off and back on.  It will turn the backlight off once the user has unlocked
// their device.
// This service is only run if the user is mirroring their display to Chromecast.  For all other
// scenarios, the ScreenOnService is run instead.
public final class TempBacklightOnService extends SecondScreenIntentService {

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public TempBacklightOnService() {
        super("TempBacklightOnService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        super.onHandleIntent(intent);

        SharedPreferences prefCurrent = U.getPrefCurrent(this);
        if(!prefCurrent.getBoolean("not_active", true)
                && prefCurrent.getBoolean("backlight_off", false)
                && prefCurrent.getInt("backlight_value", -1) != -1) {
            // Restore the saved values for backlight and auto-brightness
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, prefCurrent.getInt("backlight_value", -1));
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, prefCurrent.getInt("auto_brightness", Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));

            if(prefCurrent.getInt("backlight_value", -1) <= 10) {
                // Manually update the sysfs value to guarantee that the backlight will restore
                for(File backlightOff : U.backlightOff) {
                    if(backlightOff.exists()) {
                        U.runCommand(this, "echo " + Integer.toString(prefCurrent.getInt("backlight_value", -1)) + " > " + backlightOff.getAbsolutePath());
                        break;
                    }
                }
            }
        }
    }
}
