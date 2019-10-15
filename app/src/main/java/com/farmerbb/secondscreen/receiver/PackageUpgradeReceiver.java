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

import com.farmerbb.secondscreen.service.DisplayConnectionService;
import com.farmerbb.secondscreen.service.NotificationService;
import com.farmerbb.secondscreen.util.U;

// This receiver is responsible for restarting the DisplayConnectionService and/or
// NotificationService when SecondScreen is upgraded.
public final class PackageUpgradeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Load preferences
        SharedPreferences prefCurrent = U.getPrefCurrent(context);
        SharedPreferences prefMain = U.getPrefMain(context);
        boolean isDebugMode = prefMain.getBoolean("debug_mode", false);

        if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()) || isDebugMode) {
            // Restore DisplayConnectionService
            if(prefMain.getBoolean("hdmi", true) && prefMain.getBoolean("first-run", false)) {
                Intent serviceIntent = new Intent(context, DisplayConnectionService.class);
                U.startService(context, serviceIntent);
            }

            // Restore NotificationService
            if(!prefCurrent.getBoolean("not_active", true)) {
                Intent serviceIntent = new Intent(context, NotificationService.class);
                U.startService(context, serviceIntent);
            }
        }
    }
}