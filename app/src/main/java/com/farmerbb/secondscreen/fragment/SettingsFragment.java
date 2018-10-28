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

package com.farmerbb.secondscreen.fragment;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.HdmiProfileSelectActivity;
import com.farmerbb.secondscreen.activity.NotificationSettingsActivity;
import com.farmerbb.secondscreen.service.DisplayConnectionService;
import com.farmerbb.secondscreen.service.SafeModeToggleService;
import com.farmerbb.secondscreen.util.U;

import java.io.File;
import java.io.IOException;

// Fragment launched as part of FragmentContainerActivity that shows a list of application settings.
// Settings are saved as soon as onPause is called or the back button is pressed.
// The DisplayConnectionService is also started or stopped at that time, based on if the "Enable
// auto-start" preference is checked.
public final class SettingsFragment extends PreferenceFragment implements OnPreferenceClickListener {

    boolean addPrefs = true;
    boolean safeMode = false;
    boolean saveSettings;

    /* The activity that creates an instance of this fragment must
     * implement this interface in order to receive event call backs. */
    public interface Listener {
        void showExpertModeDialog(CheckBoxPreference checkBoxPreference);
    }

    // Use this instance of the interface to deliver action events
    Listener listener;

    // Override the Fragment.onAttach() method to instantiate the Listener
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the Listener so we can send events to the host
            listener = (Listener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement Listener");
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set values
        setRetainInstance(true);
        setHasOptionsMenu(true);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Change window title
            getActivity().setTitle(getResources().getString(R.string.action_settings));

            // Remove dividers
            View rootView = getView();
            if(rootView != null) {
                ListView list = rootView.findViewById(android.R.id.list);
                if(list != null) list.setDivider(null);
            }
        } else
            getActivity().setTitle(" " + getResources().getString(R.string.action_settings));

        // Show the Up button in the action bar.
        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStart() {
        super.onStart();

        SharedPreferences prefMain = U.getPrefMain(getActivity());
        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences.Editor editor = prefNew.edit();

        editor.putBoolean("safe_mode", prefMain.getBoolean("safe_mode", false));
        editor.putBoolean("hdmi", prefMain.getBoolean("hdmi", true));
        editor.putBoolean("expert_mode", prefMain.getBoolean("expert_mode", false));
        editor.putBoolean("force_backlight_off", prefMain.getBoolean("force_backlight_off", false));
        editor.putBoolean("tasker_enabled", prefMain.getBoolean("tasker_enabled", true));
        editor.putBoolean("notch_compat_mode", prefMain.getBoolean("notch_compat_mode", false));
        editor.apply();

        if(addPrefs) {
            // Add preferences
            addPreferencesFromResource(R.xml.settings_preferences);

            // Set OnClickListeners for certain preferences
            findPreference("safe_mode").setOnPreferenceClickListener(this);
            findPreference("expert_mode").setOnPreferenceClickListener(this);
            findPreference("hdmi_select_profile").setOnPreferenceClickListener(this);
            findPreference("notification_settings").setOnPreferenceClickListener(this);
            findPreference("notch_compat_mode").setOnPreferenceClickListener(this);

            addPrefs = false;
        }

        saveSettings = true;
    }

    @Override
    public void onStop() {
        super.onStop();

        if(saveSettings)
            saveSettings();
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences prefMain = U.getPrefMain(getActivity());
        if(prefMain.getString("hdmi_load_profile", "show_list").equals("show_list"))
            findPreference("hdmi_select_profile").setSummary(getResources().getString(R.string.show_list));
        else {
            File file = new File(getActivity().getFilesDir() + File.separator + prefMain.getString("hdmi_load_profile", "show_list"));
            if(file.exists()) {
                try {
                    findPreference("hdmi_select_profile").setSummary(getResources().getString(R.string.action_load, U.getProfileTitle(getActivity(), prefMain.getString("hdmi_load_profile", "show_list"))));
                } catch (IOException e) { /* Gracefully fail */ }
            } else
                findPreference("hdmi_select_profile").setSummary(getResources().getString(R.string.show_list));
        }

        findPreference("notch_compat_mode").setEnabled(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !prefMain.getBoolean("landscape", false)
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Override default Android "up" behavior to instead mimic the back button
                this.onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onBackPressed() {
        saveSettings();
        getActivity().finish();
    }

    private void saveSettings() {
        saveSettings = false;

        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences prefMain = U.getPrefMain(getActivity());

        // Handle turning on/off safe mode
        if(safeMode) {
            safeMode = false;

            Intent serviceIntent = new Intent(getActivity(), SafeModeToggleService.class);
            if(prefNew.getBoolean("safe_mode", false) && !prefMain.getBoolean("safe_mode", false)) {
                serviceIntent.putExtra("safe_mode", true);
                getActivity().startService(serviceIntent);
            } else if(!prefNew.getBoolean("safe_mode", false) && prefMain.getBoolean("safe_mode", false)) {
                serviceIntent.putExtra("safe_mode", false);
                getActivity().startService(serviceIntent);
            }
        }

        // Handle starting/stopping DisplayConnectionService
        Intent serviceIntent = new Intent(getActivity(), DisplayConnectionService.class);
        if(prefNew.getBoolean("hdmi", true))
            getActivity().startService(serviceIntent);
        else
            getActivity().stopService(serviceIntent);

        // Save settings
        SharedPreferences.Editor editor = prefMain.edit();
        editor.putBoolean("safe_mode", prefNew.getBoolean("safe_mode", false));
        editor.putBoolean("hdmi", prefNew.getBoolean("hdmi", true));
        editor.putBoolean("expert_mode", prefNew.getBoolean("expert_mode", false));
        editor.putBoolean("force_backlight_off", prefNew.getBoolean("force_backlight_off", false));
        editor.putBoolean("tasker_enabled", prefNew.getBoolean("tasker_enabled", true));
        editor.putBoolean("notch_compat_mode", prefNew.getBoolean("notch_compat_mode", false));
        editor.apply();

        // Cleanup
        SharedPreferences.Editor prefNewEditor = prefNew.edit();
        prefNewEditor.remove("safe_mode");
        prefNewEditor.remove("hdmi");
        prefNewEditor.remove("expert_mode");
        prefNewEditor.remove("force_backlight_off");
        prefNewEditor.remove("tasker_enabled");
        prefNewEditor.remove("notch_compat_mode");
        prefNewEditor.apply();
    }

    @Override
    public boolean onPreferenceClick(Preference p) {
        SharedPreferences prefCurrent = U.getPrefCurrent(getActivity());

        switch(p.getKey()) {
            case "safe_mode":
                if(!prefCurrent.getBoolean("not_active", true) && !"quick_actions".equals(prefCurrent.getString("filename", "0")))
                    safeMode = true;
                break;
            case "hdmi_select_profile":
                // Get number of files
                int numOfFiles = U.getNumOfFiles(getActivity().getFilesDir());

                if(numOfFiles == 0)
                    U.showToast(getActivity(), R.string.no_profiles_found);
                else {
                    Intent intent = new Intent(getActivity(), HdmiProfileSelectActivity.class);
                    startActivity(intent);
                }
                break;
            case "expert_mode":
                SharedPreferences prefNew = U.getPrefNew(getActivity());
                if(prefNew.getBoolean("expert_mode", false)) {
                    SharedPreferences.Editor editor = prefNew.edit();
                    editor.putBoolean("expert_mode", false);
                    editor.apply();

                    CheckBoxPreference checkBoxPreference = (CheckBoxPreference) p;
                    checkBoxPreference.setChecked(false);

                    listener.showExpertModeDialog(checkBoxPreference);
                }
                break;
            case "notification_settings":
                Intent intent = new Intent(getActivity(), NotificationSettingsActivity.class);
                startActivity(intent);
                break;
            case "notch_compat_mode":
                if(!prefCurrent.getBoolean("not_active", true))
                    U.showToast(getActivity(), R.string.notch_compat_mode_toast);
                break;
        }

        return true;
    }

    public void onExpertModeDialogPositiveClick(CheckBoxPreference checkBoxPreference) {
        try {
            checkBoxPreference.setChecked(true);

            SharedPreferences prefNew = U.getPrefNew(getActivity());
            SharedPreferences.Editor editor = prefNew.edit();
            editor.putBoolean("expert_mode", true);
            editor.apply();
        } catch (NullPointerException e) { /* Gracefully fail */ }
    }
}
