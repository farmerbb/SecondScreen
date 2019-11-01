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

package com.farmerbb.secondscreen.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import com.farmerbb.secondscreen.service.BootService;
import com.farmerbb.secondscreen.service.DisplayConnectionService;
import com.farmerbb.secondscreen.service.NotificationService;
import com.farmerbb.secondscreen.util.U;

// This receiver is responsible for recreating the DisplayConectionService and/or
// NotificationService, where applicable, after a device reboot.
// It is also responsible for launching the TurnOffService if safe mode is enabled and a profile
// was active before the device last shut down/restarted.
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Load preferences
        SharedPreferences prefCurrent = U.getPrefCurrent(context);
        SharedPreferences prefMain = U.getPrefMain(context);
        boolean isDebugMode = prefMain.getBoolean("debug_mode", false);

        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || isDebugMode) {
            // Initialize preferences on BlissOS
            if(U.isBlissOs(context) && !prefMain.getBoolean("first-run", false))
                U.initPrefs(context);

            // Restore DisplayConnectionService
            if(prefMain.getBoolean("hdmi", true) && prefMain.getBoolean("first-run", false)) {
                Intent serviceIntent = new Intent(context, DisplayConnectionService.class);
                U.startService(context, serviceIntent);
            }

            if(prefCurrent.getInt("external_display_id", -1) > 1)
                prefCurrent.edit().putInt("external_display_id", 1).apply();

            if(!prefCurrent.getBoolean("not_active", true)) {
                if(prefMain.getBoolean("safe_mode", false)
                        && !"activity-manager".equals(prefCurrent.getString("ui_refresh", "do-nothing"))
                        && !prefCurrent.getBoolean("reboot_required", false)
                        && prefCurrent.getLong("time_of_profile_start", 0)
                        < (System.currentTimeMillis() - (isDebugMode ? 0 : SystemClock.elapsedRealtime()))) {
                    SharedPreferences.Editor editor = prefCurrent.edit();
                    editor.putString("ui_refresh", "do-nothing");
                    editor.apply();

                    U.turnOffProfile(context);
                } else if("quick_actions".equals(prefCurrent.getString("filename", "0"))) {
                    SharedPreferences prefSaved = U.getPrefQuickActions(context);
                    if("0".equals(prefSaved.getString("original_filename", "0"))
                            && !prefCurrent.getBoolean("reboot_required", false)
                            && prefCurrent.getLong("time_of_profile_start", 0)
                            < (System.currentTimeMillis() - (isDebugMode ? 0 : SystemClock.elapsedRealtime()))) {
                        SharedPreferences.Editor editor = prefCurrent.edit();
                        editor.putString("ui_refresh", "do-nothing");
                        editor.apply();

                        U.turnOffProfile(context);
                    } else {
                        prefCurrent.edit().putBoolean("reboot_required", false).apply();

                        // Restore NotificationService
                        Intent serviceIntent = new Intent(context, NotificationService.class);
                        U.startService(context, serviceIntent);

                        // Start BootService to run superuser commands
                        Intent serviceIntent2 = new Intent(context, BootService.class);
                        U.startService(context, serviceIntent2);
                    }
                } else {
                    prefCurrent.edit().putBoolean("reboot_required", false).apply();

                    // Restore NotificationService
                    Intent serviceIntent = new Intent(context, NotificationService.class);
                    U.startService(context, serviceIntent);

                    // Start BootService to run superuser commands
                    Intent serviceIntent2 = new Intent(context, BootService.class);
                    U.startService(context, serviceIntent2);
                }
            }
        }
    }
}