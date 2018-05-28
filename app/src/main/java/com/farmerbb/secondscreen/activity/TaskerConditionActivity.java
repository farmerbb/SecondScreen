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
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.util.PluginBundleManager;
import com.farmerbb.secondscreen.util.U;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

// This activity is responsible for displaying a list of profiles for the "SecondScreen - Profile
// Active" state plugin in Tasker.
// The profile list generation code behaves similarly to the ProfileListFragment.
public final class TaskerConditionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_launch);
        setTitle(getResources().getString(R.string.select_profile));

        // Get array of profiles
        final String[][] profileList = U.listProfiles(this, "any_profile", R.string.any_profile);

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
            listView.setAdapter(adapter);
            listView.setClickable(true);
            listView.setOnItemClickListener((arg0, arg1, position, arg3) -> {
                try {
                    loadProfile(profileList[0][position]);
                } catch (IOException e) {
                    U.showToast(this, R.string.error_loading_profile);
                }
            });
        }
    }

    private void loadProfile(String filename) throws IOException {
        final Intent resultIntent = new Intent();

        /*
         * This extra is the data to ourselves: either for the Activity or the BroadcastReceiver. Note
         * that anything placed in this Bundle must be available to Locale's class loader. So storing
         * String, int, and other standard objects will work just fine. Parcelable objects are not
         * acceptable, unless they also implement Serializable. Serializable objects must be standard
         * Android platform objects (A Serializable class private to this plug-in's APK cannot be
         * stored in the Bundle, as Locale's classloader will not recognize it).
         */
        final Bundle resultBundle = PluginBundleManager.generateBundle(this, filename);
        resultIntent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE, resultBundle);

        /*
         * The blurb is concise status text to be displayed in the host's UI.
         */

        if(filename.equals("any_profile"))
            resultIntent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_STRING_BLURB, getResources().getString(R.string.any_profile));
        else
            resultIntent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_STRING_BLURB, U.getProfileTitle(this, filename));

        setResult(RESULT_OK, resultIntent);

        finish();
    }
}
