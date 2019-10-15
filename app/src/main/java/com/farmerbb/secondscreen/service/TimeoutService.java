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

// This service is run after LockDeviceService, to restore the user's previous screen lock timeout
// preference which is set to 0 in order to lock the device immediately.
public final class TimeoutService extends SecondScreenIntentService {

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public TimeoutService() {
        super("TimeoutService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        super.onHandleIntent(intent);

        // Check to see if we need to reset the lock screen lock after timeout value
        SharedPreferences prefMain = U.getPrefMain(this);
        int timeout = prefMain.getInt("timeout", -1);
        if(timeout != -1) {
            SharedPreferences.Editor editor = prefMain.edit();
            editor.remove("timeout");
            editor.apply();

            U.runCommand(this, U.timeoutCommand + Integer.toString(timeout));
        }
    }
}
