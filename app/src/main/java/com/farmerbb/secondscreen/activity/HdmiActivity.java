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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.service.DisplayConnectionService;
import com.farmerbb.secondscreen.util.U;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

// This activity is invoked by the DisplayConnectionService when "Show list of profiles" is set.
// It displays a pop-up window with the detected resolution of the external display, as well as
//  a list of profiles.  (This menu will only appear if the user is outside of the MainActivity)
//
// If the auto-start action is set to load a profile instead of displaying a menu, this activity
// will instead launch the ProfileLoadService and then immediately finish.
public final class HdmiActivity extends Activity {

    private final class FinishReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    }

    String filename;
    boolean menu = false;
    IntentFilter filter = new IntentFilter(U.SCREEN_DISCONNECT);
    FinishReceiver receiver = new FinishReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefMain = U.getPrefMain(this);
        filename = prefMain.getString("hdmi_load_profile", "show_list");

        if("show_list".equals(filename)) {
            if(prefMain.getBoolean("inactive", true))
                showMenu();
            else
                finish();
        } else {
            File file = new File(getFilesDir() + File.separator + filename);
            if(file.exists()) {
                U.loadProfile(this, filename);
                finish();
            } else {
                if(prefMain.getBoolean("inactive", true))
                    showMenu();
                else
                    finish();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(menu)
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if(menu) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);

            // Set checkbox preference
            CheckBox checkbox = findViewById(R.id.hdmiCheckBox);
            if(!checkbox.isChecked()) {
                SharedPreferences prefMain = U.getPrefMain(this);
                SharedPreferences.Editor editor = prefMain.edit();
                editor.putBoolean("hdmi", false);
                editor.apply();

                // Stop DisplayConnectionService
                Intent serviceIntent = new Intent(this, DisplayConnectionService.class);
                stopService(serviceIntent);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void showMenu() {
        setContentView(R.layout.activity_hdmi);
        setTitle(getResources().getString(R.string.hdmi_connected));
        menu = true;

        TextView header = findViewById(R.id.hdmi_header);
        header.setText(getString(R.string.hdmi_connected));

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            header.setTypeface(Typeface.DEFAULT);

        // Close notification drawer
        Intent closeDrawer = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(closeDrawer);

        DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(DISPLAY_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();

        Display[] displays = dm.getDisplays();
        displays[displays.length - 1].getRealMetrics(metrics);

        String extScreenRes = Integer.toString(displays[displays.length - 1].getWidth())
                + "x"
                + Integer.toString(displays[displays.length - 1].getHeight());

        switch(extScreenRes) {
            case "3840x2160":
                extScreenRes = getResources().getStringArray(R.array.pref_resolution_list)[1];
                break;
            case "1920x1080":
                extScreenRes = getResources().getStringArray(R.array.pref_resolution_list)[2];
                break;
            case "1280x720":
                extScreenRes = getResources().getStringArray(R.array.pref_resolution_list)[3];
                break;
            case "854x480":
                extScreenRes = getResources().getStringArray(R.array.pref_resolution_list)[4];
                break;
        }

        TextView textView = findViewById(R.id.hdmiTextView);
        textView.setText(extScreenRes);

        // Get array of profiles
        final String[][] profileList = U.listProfiles(this);

        // If there are no saved profiles, then show a toast message and exit
        if(profileList == null) {
            U.showToast(this, R.string.no_profiles_found);
            finish();
        } else {
            // Create ArrayList and populate with list of profiles
            ArrayList<String> arrayList = new ArrayList<>(profileList[1].length);
            arrayList.addAll(Arrays.asList(profileList[1]));

            // Create the custom adapter to bind the array to the ListView
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, arrayList);

            // Display the ListView
            final ListView listView = findViewById(R.id.listView3);
            listView.setAdapter(adapter);
            listView.setClickable(true);
            listView.setOnItemClickListener((arg0, arg1, position, arg3) -> {
                U.loadProfile(HdmiActivity.this, profileList[0][position]);
                finish();
            });
        }
    }
}
