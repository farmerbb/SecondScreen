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
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.util.PluginBundleManager;
import com.farmerbb.secondscreen.util.U;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

// The QuickLaunchActivity is responsible for handling the "SecondScreen - Load Profile"
// homescreen widget and also the corresponding Tasker plugin.
// This activity is launched whenever the user places a SecondScreen widget on their home screen.
// It shows a list of profiles (similar to the ProfileListFragment), and when one is selected,
// a homescreen widget is placed with an Intent to launch the specified profile.  When the widget
// is clicked, the QuickLaunchActivity is launched again, however, instead of showing the profile
// selection UI, it will start the ProfileLoadService for the specified profile, and then
// immediately finish.  (This behavior is due to poor design choices in early versions of
// SecondScreen, and the need to retain backwards compatibility).
//
// For the Tasker plugin, the behavior is the same, however, instead of creating a homescreen
// widget, it creates an action for a Tasker task.  When the Tasker action is executed, the
// QuickLaunchActivity is again started and proceeds to load the specified profile, in the same
// fashion as clicking the homescreen widget.
public final class QuickLaunchActivity extends Activity {

    String filename;
    boolean tasker = false;
    boolean launchShortcut = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Handle intents
        Intent quickLaunchIntent = getIntent();
        if(quickLaunchIntent.getStringExtra(U.NAME) != null) {
            filename = quickLaunchIntent.getStringExtra(U.NAME);
            tasker = quickLaunchIntent.getBooleanExtra("tasker", false);
            launchShortcut = true;
        }

        SharedPreferences prefMain = U.getPrefMain(this);
        if(!prefMain.getBoolean("first-run", false))
            finish();
        else if(launchShortcut) {
            SharedPreferences prefCurrent = U.getPrefCurrent(this);
            String currentFilename = "null";
            if(!tasker)
                currentFilename = prefCurrent.getString("filename", "null");

            File fileToLaunch = new File(getFilesDir() + File.separator + filename);
            if(fileToLaunch.exists() && !filename.equals(currentFilename))
                U.loadProfile(this, filename);
            else if(filename.equals("turn_off") || filename.equals(currentFilename)) {
                if(!prefCurrent.getBoolean("not_active", true))
                    U.turnOffProfile(this);
            } else
                U.showToastLong(this, R.string.recreate_shortcut);

            finish();
        } else {
            setContentView(R.layout.activity_quick_launch);
            setTitle(getResources().getString(R.string.select_profile));

            // Get array of profiles
            final String[][] profileList = U.listProfiles(this, "turn_off", R.string.dialog_turn_off_title);

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
                    PackageManager pm;
                    pm = getPackageManager();

                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_HOME);
                    i.addCategory(Intent.CATEGORY_DEFAULT);

                    final ResolveInfo mInfo = pm.resolveActivity(i, 0);

                    try {
                        if(mInfo.activityInfo.applicationInfo.packageName.equals(getCallingPackage()))
                            quickLaunchProfile(profileList[0][position]);
                        else {
                            try {
                                loadProfile(profileList[0][position]);
                            } catch (IOException e) {
                                U.showToast(this, R.string.error_loading_profile);
                            }
                        }
                    } catch (NullPointerException e) {
                        finish();
                    }
                });
            }
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

        if(filename.equals("turn_off"))
            resultIntent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_STRING_BLURB, getResources().getString(R.string.dialog_turn_off_title));
        else
            resultIntent.putExtra(com.twofortyfouram.locale.api.Intent.EXTRA_STRING_BLURB, U.getProfileTitle(this, filename));

        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void quickLaunchProfile(String filename) {
        // The meat of our shortcut
        Intent shortcutIntent = new Intent (this, QuickLaunchActivity.class);
        shortcutIntent.setAction(Intent.ACTION_MAIN);
        shortcutIntent.putExtra(U.NAME, filename);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ShortcutIconResource iconResource = Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher);

        // The result we are passing back from this activity
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);

        if(filename.equals("turn_off"))
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getResources().getStringArray(R.array.pref_notification_action_list)[0]);
        else {
            try {
                intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, U.getProfileTitle(this, filename));
            } catch (IOException e) {
                U.showToast(this, R.string.error_loading_list);
            }
        }

        setResult(RESULT_OK, intent);

        finish(); // Must call finish for result to be returned immediately
    }
}
