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

package com.farmerbb.secondscreen.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.service.DisplayConnectionService;
import com.farmerbb.secondscreen.service.NotificationService;
import com.farmerbb.secondscreen.util.U;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// The top secret debugging menu, which can be accessed by enabling debug mode (10 taps right below
// the action bar in the ProfileListFragment), then long-pressing on the red area that appears
// when debug mode is enabled.
public class DebugModeActivity extends PreferenceActivity implements OnPreferenceClickListener {

    @SuppressWarnings("deprecation")
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if(U.getPrefMain(this).getBoolean("debug_mode", false)) {
            // Add preferences
            addPreferencesFromResource(R.xml.debug_mode_preferences);

            // Set OnClickListeners for certain preferences
            findPreference("show_simulated_size_density").setOnPreferenceClickListener(this);
            findPreference("simulate_reboot").setOnPreferenceClickListener(this);
            findPreference("simulate_app_upgrade").setOnPreferenceClickListener(this);

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                findPreference("dump_app_state").setOnPreferenceClickListener(this);
            else
                findPreference("dump_app_state").setEnabled(false);
        } else
            finish();
    }

    @Override
    public boolean onPreferenceClick(Preference p) {
        Intent intent = new Intent(this, DisplayConnectionService.class);
        Intent intent2 = new Intent(this, NotificationService.class);
        Intent intent3 = new Intent(U.SIMULATE_REBOOT);
        Intent intent4 = new Intent(U.SIMULATE_APP_UPGRADE);

        switch(p.getKey()) {
            case "show_simulated_size_density":
                SharedPreferences prefCurrent = U.getPrefCurrent(this);
                Toast toast = Toast.makeText(
                        this, getResources().getString(R.string.pref_title_resolution) + ": " + prefCurrent.getString("size", "reset") + "\n"
                        + getResources().getString(R.string.pref_title_dpi) + ": " + prefCurrent.getString("density", "reset"), Toast.LENGTH_LONG);
                toast.show();
                break;
            case "simulate_reboot":
                stopService(intent);
                stopService(intent2);
                sendBroadcast(intent3);
                break;
            case "simulate_app_upgrade":
                stopService(intent);
                stopService(intent2);
                sendBroadcast(intent4);
                break;
            case "dump_app_state":
                dumpAppState("current", "prefCurrent");
                dumpAppState(getPackageName() + "_preferences", "prefNew");
                dumpAppState(U.getPrefCurrent(this).getString("filename", "0"), "prefSaved");

                if(getPackageName().equals("com.farmerbb.secondscreen"))
                    dumpAppState(MainActivity.class.getName().replace("com.farmerbb.secondscreen.", ""), "prefMain");
                else
                    dumpAppState(MainActivity.class.getName(), "prefMain");

                break;
        }

        finish();

        return true;
    }

    private void dumpAppState(String name, String name2) {
        File file = new File(getFilesDir().getParent() + File.separator + "shared_prefs" + File.separator + name + ".xml");
        File file2 = new File(getExternalFilesDir(null), name2);
        file2.delete();

        try {
            InputStream is = new FileInputStream(file);
            byte[] data = new byte[is.available()];

            if(data.length > 65) {
                OutputStream os = new FileOutputStream(file2);
                is.read(data);
                os.write(data);
                is.close();
                os.close();
            }
        } catch (IOException e) {}
    }
}
