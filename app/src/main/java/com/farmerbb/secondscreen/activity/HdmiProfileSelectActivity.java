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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.util.U;

import java.util.ArrayList;
import java.util.Arrays;

// This pops up a window of profiles to select for the "Auto-start action" in the Settings.
// The profile list generation code behaves similarly to the ProfileListFragment.
public final class HdmiProfileSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_launch);
        setTitle(getResources().getString(R.string.select_profile));

        // Get array of profiles
        final String[][] profileList = U.listProfiles(this, "show_list", R.string.show_list);

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
            final ListView listView = findViewById(R.id.listView2);
            listView.setPadding(getResources().getDimensionPixelSize(R.dimen.list_view_padding), 0, 0, 0);
            listView.setAdapter(adapter);
            listView.setClickable(true);
            listView.setOnItemClickListener((arg0, arg1, position, arg3) -> {
                SharedPreferences prefMain = U.getPrefMain(HdmiProfileSelectActivity.this);
                SharedPreferences.Editor editor = prefMain.edit();
                editor.putString("hdmi_load_profile", profileList[0][position]);
                editor.apply();

                finish();
            });
        }
    }
}
