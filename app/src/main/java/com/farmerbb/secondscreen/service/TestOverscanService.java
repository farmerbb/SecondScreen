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

import com.farmerbb.secondscreen.util.U;

// This service is launched via OverscanActivity to temporarily test (and restore) overscan values.
public final class TestOverscanService extends SecondScreenIntentService {

    /**
     * A constructor is required, and must call the super IntentService(String)
     * constructor with a name for the worker thread.
     */
    public TestOverscanService() {
        super("TestOverscanService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        super.onHandleIntent(intent);

        String overscanValues;

        SharedPreferences prefNew = U.getPrefNew(this);
        SharedPreferences prefMain = U.getPrefMain(this);

        if(intent.getBooleanExtra("test_overscan", true)) {
            if(prefMain.getBoolean("landscape", false)) {
                overscanValues = Integer.toString(prefNew.getInt("overscan_left", 0)) + ","
                        + Integer.toString(prefNew.getInt("overscan_top", 0)) + ","
                        + Integer.toString(prefNew.getInt("overscan_right", 0)) + ","
                        + Integer.toString(prefNew.getInt("overscan_bottom", 0));
            } else {
                overscanValues = Integer.toString(prefNew.getInt("overscan_bottom", 0)) + ","
                        + Integer.toString(prefNew.getInt("overscan_left", 0)) + ","
                        + Integer.toString(prefNew.getInt("overscan_top", 0)) + ","
                        + Integer.toString(prefNew.getInt("overscan_right", 0));
            }
        } else {
            SharedPreferences prefCurrent = U.getPrefCurrent(this);
            if(prefCurrent.getBoolean("not_active", true)) {
                overscanValues = "reset";
            } else {
                SharedPreferences prefSaved = U.getPrefSaved(this, prefCurrent.getString("filename", "0"));
                if(prefSaved.getBoolean("overscan", false)) {
                    if(prefMain.getBoolean("landscape", false)) {
                        overscanValues = Integer.toString(prefSaved.getInt("overscan_left", 0)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_top", 0)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_right", 0)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_bottom", 0));
                    } else {
                        overscanValues = Integer.toString(prefSaved.getInt("overscan_bottom", 0)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_left", 0)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_top", 0)) + ","
                                + Integer.toString(prefSaved.getInt("overscan_right", 0));
                    }
                } else
                    overscanValues = "reset";
            }
        }

        // Fix overscan values if notch compatibility mode is enabled
        if(prefMain.getBoolean("notch_compat_mode", false)
                && !overscanValues.equals("reset")) {
            String[] splitValues = overscanValues.split(",");
            overscanValues = splitValues[1] + ","
                    + splitValues[2] + ","
                    + splitValues[3] + ","
                    + splitValues[0];
        }

        U.runCommand(this, U.overscanCommand + overscanValues);
    }
}
