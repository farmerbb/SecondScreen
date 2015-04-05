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

import com.farmerbb.secondscreen.util.U;

import eu.chainfire.libsuperuser.Shell;

// This service is launched whenever the safe mode option has been changed in the app settings,
// and a user-created profile is currently active.
public final class SafeModeToggleService extends IntentService {

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public SafeModeToggleService() {
        super("SafeModeToggleService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Superuser commands to run
        final int safeModeDensityCommand = 0;
        final int safeModeSizeCommand = 1;

        // Initialize su array
        String[] su = new String[safeModeSizeCommand + 1];

        if(intent.getBooleanExtra("safe_mode", false)) {
            su[safeModeDensityCommand] = U.safeModeDensityCommand + "null";
            su[safeModeSizeCommand] = U.safeModeSizeCommand + "null";
        } else {
            SharedPreferences prefCurrent = U.getPrefCurrent(this);

            String density = prefCurrent.getString("density", "reset");
            if(density.equals("reset"))
                su[safeModeDensityCommand] = U.safeModeDensityCommand + "null";
            else
                su[safeModeDensityCommand] = U.safeModeDensityCommand + density;

            String size = prefCurrent.getString("size", "reset");
            if(size.equals("reset"))
                su[safeModeSizeCommand] = U.safeModeSizeCommand + "null";
            else
                su[safeModeSizeCommand] = U.safeModeSizeCommand + size.replace('x', ',');
        }

        // Run superuser commands
        Shell.SU.run(su);
    }
}

