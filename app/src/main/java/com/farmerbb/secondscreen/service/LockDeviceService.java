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

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.provider.Settings;

import com.farmerbb.secondscreen.util.U;

// Service launched by pressing the "Lock Device" button via either Quick Actions or the notification
// action button.  It will either turn off the device directly, or start the system Daydream service
// (if the device is charging and the Daydreams feature is enabled).
// If necessary, this service will also temporarily set the user's screen lock timeout preference
// to 0, to ensure that the device is locked immediately.  The TimeoutService is run sometime after
// to restore the original value.
public final class LockDeviceService extends IntentService {

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public LockDeviceService() {
        super("LockDeviceService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Close the notification drawer
        Intent closeDrawer = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(closeDrawer);

        // Determine current charging status
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        // Get current UI mode
        UiModeManager mUiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        int uiMode = mUiModeManager.getCurrentModeType();

        // Determine current dock state, based on the current UI mode
        boolean isDocked;
        switch(uiMode) {
            case Configuration.UI_MODE_TYPE_DESK:
                isDocked = true;
                break;
            case Configuration.UI_MODE_TYPE_CAR:
                isDocked = true;
                break;
            default:
                isDocked = false;
        }

        // In order to ensure that the device locks itself when the following code is run,
        // we need to temporarily set the lock screen lock after timeout value.
        // For a smooth transition into the daydream, we set this value to one millisecond,
        // locking the device at the soonest opportunity after the transition completes.
        int timeout = Settings.Secure.getInt(getContentResolver(), "lock_screen_lock_after_timeout", 5000);
        if(timeout != 1) {
            SharedPreferences prefMain = U.getPrefMain(this);
            SharedPreferences.Editor editor = prefMain.edit();
            editor.putInt("timeout", Settings.Secure.getInt(getContentResolver(), "lock_screen_lock_after_timeout", 5000));
            editor.apply();

            U.runCommand(this, U.timeoutCommand + "1");
        }

        // Schedule TimeoutService to reset lock screen timeout to original value
        Intent timeoutService = new Intent(this, TimeoutService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this, 123456, timeoutService, PendingIntent.FLAG_CANCEL_CURRENT);

        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        manager.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent);

        // If Daydreams is enabled and the device is charging, then lock the device by launching the daydream.
        if(isCharging
                && !U.castScreenActive(this)
                && Settings.Secure.getInt(getContentResolver(), "screensaver_enabled", 0) == 1
                && ((Settings.Secure.getInt(getContentResolver(), "screensaver_activate_on_dock", 0) == 1 && isDocked)
                || Settings.Secure.getInt(getContentResolver(), "screensaver_activate_on_sleep", 0) == 1)) {
            // Send intent to launch the current daydream manually
            Intent lockIntent = new Intent(Intent.ACTION_MAIN);
            lockIntent.setComponent(ComponentName.unflattenFromString("com.android.systemui/.Somnambulator"));
            lockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                startActivity(lockIntent);
            } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
        } else
            // Otherwise, send a power button keystroke to lock the device normally
            U.lockDevice(this);
    }
}
