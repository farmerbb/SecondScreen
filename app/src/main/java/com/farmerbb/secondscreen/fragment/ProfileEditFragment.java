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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.FragmentContainerActivity;
import com.farmerbb.secondscreen.fragment.dialog.SystemAlertPermissionDialogFragment;
import com.farmerbb.secondscreen.util.U;
import com.jrummyapps.android.os.SystemProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// Fragment launched as part of MainActivity either by: pressing the Edit button in
// ProfileViewFragment, pressing the OK button in NewProfileDialogFragment, or by long-pressing a
// profile entry in ProfileListFragment (legacy behavior).
// It is a PreferenceFragment that generates an xml file with saved profile settings, for later use
// by ProfileLoadService.  It also generates short text files containing the profile's title for
// use by ProfileListFragment (this is due to that code having been based off of corresponding code
// in the Notepad application).
public final class ProfileEditFragment extends PreferenceFragment implements
OnPreferenceClickListener, 
SharedPreferences.OnSharedPreferenceChangeListener {

    String filename = String.valueOf(System.currentTimeMillis());
    static String name;
    boolean isSavedProfile = false;
    boolean prefChange = true;
    boolean uiRefreshWarning = false;
    boolean taskbarSettingsPrefEnabled = false;

    /* The activity that creates an instance of this fragment must
     * implement this interface in order to receive event call backs. */
    public interface Listener {
        void showDeleteDialog();
        void showExpertModeDialog();
        void showReloadDialog(String filename, boolean isEdit, boolean returnToList);
        String getProfileTitle(String filename) throws IOException;
        void setDefaultDensity();
        String generateBlurb(String key, String value);
        void setEmptyTitle(String title);
        void showUiRefreshDialog(String filename, boolean isEdit, boolean returnToList);
    }

    // Use this instance of the interface to deliver action events
    static Listener listener;

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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set values
        setRetainInstance(true);
        setHasOptionsMenu(true);

        // Show the Up button in the action bar.
        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Animate elevation change
            if(getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-large")) {
                LinearLayout profileViewEdit = getActivity().findViewById(R.id.profileViewEdit);
                LinearLayout profileList = getActivity().findViewById(R.id.profileList);
                profileList.animate().z(0f);
                profileViewEdit.animate().z(getResources().getDimensionPixelSize(R.dimen.profile_view_edit_elevation));
            }

            // Remove dividers
            View rootView = getView();
            if(rootView != null) {
                ListView list = rootView.findViewById(android.R.id.list);
                if(list != null) list.setDivider(null);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get filename
        if(getArguments() != null)
            if(getArguments().getString("filename") != null) {
                filename = getArguments().getString("filename");
                isSavedProfile = true;
            }

        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences prefMain = U.getPrefMain(getActivity());

        // If editing a previously saved profile, begin loading saved settings
        if(isSavedProfile) {
            SharedPreferences prefSaved = U.getPrefSaved(getActivity(), filename);
            SharedPreferences.Editor editor = prefNew.edit();

            if("fallback".equals(prefSaved.getString("rotation_lock_new", "fallback")) && prefSaved.getBoolean("rotation_lock", false))
                editor.putString("rotation_lock_new", "landscape");
            else
                editor.putString("rotation_lock_new", prefSaved.getString("rotation_lock_new", "do-nothing"));

            if("fallback".equals(prefSaved.getString("immersive_new", "fallback")) && prefSaved.getBoolean("immersive", false))
                editor.putString("immersive_new", "immersive-mode");
            else
                editor.putString("immersive_new", prefSaved.getString("immersive_new", "do-nothing"));

            if(prefSaved.getBoolean("overscan", false)) {
                editor.putBoolean("overscan", true);
                editor.putInt("overscan_left", prefSaved.getInt("overscan_left", 0));
                editor.putInt("overscan_right", prefSaved.getInt("overscan_right", 0));
                editor.putInt("overscan_top", prefSaved.getInt("overscan_top", 0));
                editor.putInt("overscan_bottom", prefSaved.getInt("overscan_bottom", 0));
            } else {
                editor.putBoolean("overscan", false);
                editor.putInt("overscan_left", 0);
                editor.putInt("overscan_right", 0);
                editor.putInt("overscan_top", 0);
                editor.putInt("overscan_bottom", 0);
            }

            if(prefMain.getBoolean("expert_mode", false)) {
                if("reset".equals(prefSaved.getString("size", "reset"))) {
                    editor.putString("size", Integer.toString(prefMain.getInt("width", 0))
                            + "x"
                            + Integer.toString(prefMain.getInt("height", 0)));

                    editor.putBoolean("size-reset", true);
                } else
                    editor.putString("size", prefSaved.getString("size", "reset"));

                if("reset".equals(prefSaved.getString("density", "reset"))) {
                    editor.putString("density", Integer.toString(SystemProperties.getInt("ro.sf.lcd_density", prefMain.getInt("density", 0))));
                    editor.putBoolean("density-reset", true);
                } else
                    editor.putString("density", prefSaved.getString("density", "reset"));
            } else {
                if(prefSaved.getBoolean("size-reset", false)) {
                    editor.remove("size-reset");

                    String nativeRes = Integer.toString(prefMain.getInt("width", 0))
                            + "x"
                            + Integer.toString(prefMain.getInt("height", 0));

                    if(nativeRes.equals(prefSaved.getString("size", "reset")))
                        editor.putString("size", "reset");
                    else
                        editor.putString("size", prefSaved.getString("size", "reset"));
                } else
                    editor.putString("size", prefSaved.getString("size", "reset"));

                if(prefSaved.getBoolean("density-reset", false)) {
                    editor.remove("density-reset");

                    if(Integer.toString(SystemProperties.getInt("ro.sf.lcd_density", prefMain.getInt("density", 0))).equals(prefSaved.getString("density", "reset")))
                        editor.putString("density", "reset");
                    else
                        editor.putString("density", prefSaved.getString("density", "reset"));
                } else
                    editor.putString("density", prefSaved.getString("density", "reset"));
            }

            if(getActivity().getPackageManager().hasSystemFeature("com.cyanogenmod.android")
                    && Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1
                    && prefSaved.getString("ui_refresh", "do-nothing").equals("system-ui")) {
                editor.putString("ui_refresh", "activity-manager");
            } else
                editor.putString("ui_refresh", prefSaved.getString("ui_refresh", "do-nothing"));

            editor.putString("profile_name", prefSaved.getString("profile_name", getResources().getString(R.string.action_new)));
            editor.putBoolean("bluetooth_on", prefSaved.getBoolean("bluetooth_on", false));
            editor.putBoolean("wifi_on", prefSaved.getBoolean("wifi_on", false));
            editor.putBoolean("daydreams_on", prefSaved.getBoolean("daydreams_on", false));
            editor.putBoolean("show_touches", prefSaved.getBoolean("show_touches", false));
            editor.putBoolean("backlight_off", prefSaved.getBoolean("backlight_off", false));
            editor.putBoolean("vibration_off", prefSaved.getBoolean("vibration_off", false));
            editor.putBoolean("chrome", prefSaved.getBoolean("chrome", false));
            editor.putBoolean("navbar", prefSaved.getBoolean("navbar", false));
            editor.putBoolean("freeform", prefSaved.getBoolean("freeform", false));
            editor.putString("screen_timeout", prefSaved.getString("screen_timeout", "do-nothing"));
            editor.putString("hdmi_rotation", prefSaved.getString("hdmi_rotation", "landscape"));
            editor.putBoolean("taskbar", prefSaved.getBoolean("taskbar", false));
            editor.putBoolean("clear_home", prefSaved.getBoolean("clear_home", false));
            editor.apply();

            name = prefSaved.getString("profile_name", getResources().getString(R.string.action_new));
            prefChange = false;
        } else
            name = prefNew.getString("profile_name", getResources().getString(R.string.action_new));

        // Add preferences
        addPreferencesFromResource(R.xml.profile_name_setting);

        if(prefMain.getBoolean("expert_mode", false))
            addPreferencesFromResource(R.xml.display_settings_expert);
        else
            addPreferencesFromResource(R.xml.display_settings);

        if(isPlayStoreInstalled(getActivity())
                && U.isPlayStoreRelease(getActivity())
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addPreferencesFromResource(R.xml.desktop_optimization);
            findPreference("taskbar_settings").setOnPreferenceClickListener(this);
            taskbarSettingsPrefEnabled = true;
        } else
            addPreferencesFromResource(R.xml.desktop_optimization_alt);

        addPreferencesFromResource(R.xml.additional_settings);

        // Modifications for certain scenarios
        if(!prefMain.getBoolean("expert_mode", false) && prefMain.getBoolean("landscape", false)) {
            ListPreference size = (ListPreference) findPreference("size");
            size.setEntryValues(R.array.pref_resolution_list_values_landscape);
        }

        if(getActivity().getPackageManager().hasSystemFeature("com.cyanogenmod.android")
                && Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1) {
            ListPreference uiRefresh = (ListPreference) findPreference("ui_refresh");
            uiRefresh.setEntries(R.array.pref_ui_refresh_list_alt);
            uiRefresh.setEntryValues(R.array.pref_ui_refresh_list_values_alt);
        }

        if(U.isInNonRootMode(getActivity())) {
            ListPreference uiRefresh = (ListPreference) findPreference("ui_refresh");
            uiRefresh.setEntries(R.array.pref_ui_refresh_list_non_root);
        }

        // Set OnClickListeners for certain preferences
        findPreference("daydreams_on").setOnPreferenceClickListener(this);
        findPreference("overscan_settings").setOnPreferenceClickListener(this);
        findPreference("freeform").setOnPreferenceClickListener(this);

        if(prefMain.getBoolean("expert_mode", false))
            findPreference("size").setOnPreferenceClickListener(this);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        U.bindPreferenceSummaryToValue(findPreference("profile_name"), opcl);
        U.bindPreferenceSummaryToValue(findPreference("size"), opcl);
        U.bindPreferenceSummaryToValue(findPreference("density"), opcl);
        U.bindPreferenceSummaryToValue(findPreference("rotation_lock_new"), opcl);
        U.bindPreferenceSummaryToValue(findPreference("ui_refresh"), opcl);
        U.bindPreferenceSummaryToValue(findPreference("screen_timeout"), opcl);
        U.bindPreferenceSummaryToValue(findPreference("immersive_new"), opcl);
        U.bindPreferenceSummaryToValue(findPreference("hdmi_rotation"), opcl);

        // Disable unsupported preferences
        if(!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            disablePreference(prefNew, "bluetooth_on", true);

        if(!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI))
            disablePreference(prefNew, "wifi_on", true);

        if(!getActivity().getPackageManager().hasSystemFeature("com.cyanogenmod.android"))
            disablePreference(prefNew, "navbar", true);

        if(!U.filesExist(U.vibrationOff))
            disablePreference(prefNew, "vibration_off", true);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
            disablePreference(prefNew, "overscan_settings", false);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            disablePreference(prefNew, "immersive_new", false);

        if(!U.canEnableFreeform(getActivity()))
            disablePreference(prefNew, "freeform", true);

        if(U.getChromePackageName(getActivity()) == null)
            disablePreference(prefNew, "chrome", true);

        if(U.isInNonRootMode(getActivity())) {
            disablePreference(prefNew, "hdmi_rotation", false);
            disablePreference(prefNew, "chrome", false);
            disablePreference(prefNew, "vibration_off", false);
            disablePreference(prefNew, "show_touches", false);
        }

        uiRefreshWarning = true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefNew = U.getPrefNew(getActivity());

        // Change window title
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            getActivity().setTitle(prefNew.getString("profile_name", getResources().getString(R.string.action_new)));
        else
            getActivity().setTitle(" " + prefNew.getString("profile_name", getResources().getString(R.string.action_new)));

        if(prefNew.getBoolean("overscan", false))
            findPreference("overscan_settings").setSummary(getResources().getString(R.string.enabled));
        else
            findPreference("overscan_settings").setSummary(getResources().getString(R.string.disabled));

        String taskbarPackageName = U.getTaskbarPackageName(getActivity());
        if(taskbarPackageName == null || !U.isPlayStoreRelease(getActivity()))
            disablePreference(prefNew, "taskbar", true);
        else
            findPreference("taskbar").setEnabled(true);

        if(taskbarSettingsPrefEnabled) {
            findPreference("taskbar_settings").setTitle(
                    taskbarPackageName == null
                            ? R.string.pref_taskbar_settings_title_install
                            : R.string.pref_taskbar_settings_title_open);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        // Register listener to check for changed preferences
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        // Unregister listener
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.profile_edit, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                // Override default Android "up" behavior to instead mimic the back button
                onBackPressed(filename, false, true);
                return true;

                // Save button
            case R.id.action_save:
                onBackPressed(filename, false, false);
                return true;

                // Delete button
            case R.id.action_delete:
                // Show toast if this is the currently active profile
                SharedPreferences prefCurrent = U.getPrefCurrent(getActivity());
                if("quick_actions".equals(prefCurrent.getString("filename", "0"))) {
                    SharedPreferences prefSaved = U.getPrefQuickActions(getActivity());
                    if(filename.equals(prefSaved.getString("original_filename", "0")))
                        U.showToast(getActivity(), R.string.deleting_current_profile);
                    else
                        listener.showDeleteDialog();
                } else if(filename.equals(prefCurrent.getString("filename", "0")))
                    U.showToast(getActivity(), R.string.deleting_current_profile);
                else
                    listener.showDeleteDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        prefChange = true;

        switch(key) {
            case "ui_refresh":
                if(uiRefreshWarning && "activity-manager".equals(sharedPreferences.getString(key, "do-nothing")))
                    U.showToastLong(getActivity(), R.string.am_restart_warning);
                break;
            case "density":
                if(sharedPreferences.getString(key, "reset").isEmpty())
                    listener.setDefaultDensity();
                break;
            case "profile_name":
                if(!sharedPreferences.getString(key, getResources().getString(R.string.action_new)).isEmpty()) {
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        getActivity().setTitle(sharedPreferences.getString(key, getResources().getString(R.string.action_new)));
                    else
                        getActivity().setTitle(" " + sharedPreferences.getString(key, getResources().getString(R.string.action_new)));
                }
                break;
            case "rotation_lock_new":
                if(!"do-nothing".equals(sharedPreferences.getString(key, "fallback"))
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(getActivity())
                        && !U.getPrefMain(getActivity()).getBoolean("dont_show_system_alert_dialog", false)) {
                    DialogFragment fragment = new SystemAlertPermissionDialogFragment();
                    fragment.show(getFragmentManager(), "SystemAlertPermissionDialogFragment");
                }
                break;
            case "hdmi_rotation":
                U.showToastLong(getActivity(), R.string.hdmi_output_toast);
                break;
        }
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    public static Preference.OnPreferenceChangeListener opcl = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
        String stringValue = value.toString();

        // Damage control if user inputs an empty profile name
        if(stringValue.isEmpty() && preference.getKey().equals("profile_name")) {
            stringValue = name;
            listener.setEmptyTitle(name);
        }

        if(preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);
            CharSequence summary = index >= 0 ? listPreference.getEntries()[index] : null;

            // Set the summary to reflect the new value.
            if(summary == null) {
                stringValue = listener.generateBlurb(preference.getKey(), stringValue);
                preference.setSummary(stringValue);
            } else
                preference.setSummary(summary);
        } else {
            if((preference.getKey().equals("density") && !stringValue.isEmpty())
                    || preference.getKey().equals("size"))
                stringValue = listener.generateBlurb(preference.getKey(), stringValue);

            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }

        return true;
        }
    };

    private void saveProfile() throws IOException {
        // Save preferences to XML file
        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences prefSaved = U.getPrefSaved(getActivity(), filename);
        SharedPreferences prefMain = U.getPrefMain(getActivity());
        SharedPreferences.Editor editor = prefSaved.edit();

        if(prefNew.getString("profile_name", getResources().getString(R.string.action_new)).isEmpty())
            editor.putString("profile_name", name);
        else
            editor.putString("profile_name", prefNew.getString("profile_name", getResources().getString(R.string.action_new)));

        editor.putBoolean("overscan", prefNew.getBoolean("overscan", false));
        editor.putInt("overscan_left", prefNew.getInt("overscan_left", 0));
        editor.putInt("overscan_right", prefNew.getInt("overscan_right", 0));
        editor.putInt("overscan_top", prefNew.getInt("overscan_top", 0));
        editor.putInt("overscan_bottom", prefNew.getInt("overscan_bottom", 0));
        editor.putBoolean("bluetooth_on", prefNew.getBoolean("bluetooth_on", false));
        editor.putBoolean("wifi_on", prefNew.getBoolean("wifi_on", false));
        editor.putBoolean("daydreams_on", prefNew.getBoolean("daydreams_on", false));
        editor.putBoolean("show_touches", prefNew.getBoolean("show_touches", false));
        editor.putString("rotation_lock_new", prefNew.getString("rotation_lock_new", "do-nothing"));
        editor.putBoolean("backlight_off", prefNew.getBoolean("backlight_off", false));
        editor.putBoolean("vibration_off", prefNew.getBoolean("vibration_off", false));
        editor.putString("size", prefNew.getString("size", "reset"));
        editor.putString("density", prefNew.getString("density", "reset"));
        editor.putBoolean("chrome", prefNew.getBoolean("chrome", false));
        editor.putString("ui_refresh", prefNew.getString("ui_refresh", "do-nothing"));
        editor.putBoolean("navbar", prefNew.getBoolean("navbar", false));
        editor.putString("screen_timeout", prefNew.getString("screen_timeout", "do-nothing"));
        editor.putString("immersive_new", prefNew.getString("immersive_new", "do-nothing"));
        editor.putBoolean("freeform", prefNew.getBoolean("freeform", false));
        editor.putString("hdmi_rotation", prefNew.getString("hdmi_rotation", "landscape"));
        editor.putBoolean("taskbar", prefNew.getBoolean("taskbar", false));
        editor.putBoolean("clear_home", prefNew.getBoolean("clear_home", false));

        if(prefMain.getBoolean("expert_mode", false)) {
            if(prefNew.getBoolean("size-reset", false))
                editor.putBoolean("size-reset", true);

            if(prefNew.getBoolean("density-reset", false))
                editor.putBoolean("density-reset", true);
        }

        editor.apply();

        // Save profile name to file, for use with MainActivity
        FileOutputStream output = getActivity().openFileOutput(filename, Context.MODE_PRIVATE);
        output.write(prefSaved.getString("profile_name", getResources().getString(R.string.action_new)).getBytes());
        output.close();

        // Refresh list of profiles
        U.listProfilesBroadcast(getActivity());

        U.showToast(getActivity(), R.string.profile_saved);

        if(prefMain.getBoolean("show-welcome-message", false)) {
            SharedPreferences.Editor prefMainEditor = prefMain.edit();
            prefMainEditor.remove("show-welcome-message");
            prefMainEditor.apply();
        }
    }

    public void onBackPressed(String filename, boolean isEdit, boolean returnToList) {
        if(prefChange) {
            SharedPreferences prefNew = U.getPrefNew(getActivity());
            SharedPreferences prefMain = U.getPrefMain(getActivity());

            String requestedRes = prefNew.getString("size", "reset");
            String requestedDpi = prefNew.getString("density", "reset");
            int currentHeight = prefMain.getInt("height", 0);
            int currentWidth = prefMain.getInt("width", 0);
            int currentDpi = SystemProperties.getInt("ro.sf.lcd_density", prefMain.getInt("density", 0));

            // Check to see if the user is trying to set a blacklisted resolution/DPI combo
            boolean blacklisted = U.isBlacklisted(requestedRes, requestedDpi, currentHeight, currentWidth, currentDpi);

            if(blacklisted && !prefMain.getBoolean("expert_mode", false))
                U.showToastLong(getActivity(), R.string.blacklisted);
            else if(!("reset".equals(requestedRes)
                    && "reset".equals(requestedDpi))
                    && "do-nothing".equals(prefNew.getString("ui_refresh", "do-nothing"))
                    && !prefMain.getBoolean("expert_mode", false)
                    && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                listener.showUiRefreshDialog(filename, isEdit, returnToList);
            } else
                preSave(filename, isEdit, returnToList);
        } else {
            // Cleanup
            SharedPreferences prefNew = U.getPrefNew(getActivity());
            SharedPreferences.Editor prefNewEditor = prefNew.edit();
            prefNewEditor.clear();
            prefNewEditor.apply();

            finish(filename, isEdit, returnToList);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference p) {
        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences prefMain = U.getPrefMain(getActivity());

        switch(p.getKey()) {
            case "daydreams_on":
                if(prefNew.getBoolean("daydreams_on", true))
                    U.showToastLong(getActivity(), R.string.configure_daydreams);
                break;
            case "overscan_settings":
                Intent intent = new Intent(getActivity(), FragmentContainerActivity.class);
                intent.putExtra("tag", "OverscanFragment");
                intent.putExtra("filename", filename);
                getActivity().startActivityForResult(intent, 42);
                break;
            case "size":
                if(prefMain.getBoolean("expert_mode", false))
                    listener.showExpertModeDialog();
                break;
            case "freeform":
                if(prefNew.getBoolean("freeform", true) && !U.hasFreeformSupport(getActivity())) {
                    if(!U.isInNonRootMode(getActivity())
                            && "do-nothing".equals(prefNew.getString("ui_refresh", "do-nothing")))
                        U.showToastLong(getActivity(), R.string.freeform_message);
                    else if(U.isInNonRootMode(getActivity())
                            && !"activity-manager".equals(prefNew.getString("ui_refresh", "do-nothing")))
                        U.showToastLong(getActivity(), R.string.freeform_message_non_root);
                }
                break;
            case "taskbar_settings":
                PackageManager packageManager = getActivity().getPackageManager();
                String packageName = U.getTaskbarPackageName(getActivity());
                Intent intent2;

                if(packageName == null) {
                    intent2 = new Intent(Intent.ACTION_VIEW);
                    intent2.setData(Uri.parse("https://play.google.com/store/apps/details?id=com.farmerbb.taskbar"));
                } else
                    intent2 = packageManager.getLaunchIntentForPackage(packageName);

                if(intent2 != null) {
                    intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    try {
                        startActivity(intent2);
                    } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
                }

                break;
        }

        return true;
    }

    public void onReloadDialogPositiveClick(String filename, boolean isEdit, boolean returnToList) {
        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences prefCurrent = U.getPrefCurrent(getActivity());
        SharedPreferences prefSaved = U.getPrefSaved(getActivity(), prefCurrent.getString("filename", "0"));
        SharedPreferences.Editor editor = prefCurrent.edit();

        if(!prefNew.getString("ui_refresh", "do-nothing").equals(prefSaved.getString("ui_refresh", "do-nothing"))) {
            editor.putBoolean("force_ui_refresh", true);
            editor.apply();
        }

        try {
            saveProfile();
        } catch(IOException e) {
            // Show error message as toast if file fails to save
            U.showToast(getActivity(), R.string.failed_to_save);
        }

        U.loadProfile(getActivity(), filename);

        // Cleanup
        SharedPreferences.Editor prefNewEditor = prefNew.edit();
        prefNewEditor.clear();
        prefNewEditor.apply();

        finish(filename, isEdit, returnToList);
    }

    public void onReloadDialogNegativeClick(String filename, boolean isEdit, boolean returnToList) {
        try {
            saveProfile();
        } catch(IOException e) {
            // Show error message as toast if file fails to save
            U.showToast(getActivity(), R.string.failed_to_save);
        }

        // Cleanup
        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences.Editor prefNewEditor = prefNew.edit();
        prefNewEditor.clear();
        prefNewEditor.apply();

        finish(filename, isEdit, returnToList);
    }

    public void deleteProfile() {
        // Build the pathname to delete file, then perform delete operation
        File fileToDelete = new File(getActivity().getFilesDir() + File.separator + filename);
        fileToDelete.delete();

        File xmlFileToDelete = new File(getActivity().getFilesDir().getParent() + File.separator + "shared_prefs" + File.separator + filename + ".xml");
        xmlFileToDelete.delete();

        U.showToast(getActivity(), R.string.profile_deleted);

        // Cleanup
        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences.Editor prefNewEditor = prefNew.edit();
        prefNewEditor.clear();
        prefNewEditor.apply();

        SharedPreferences prefMain = U.getPrefMain(getActivity());

        Fragment fragment;

        if(prefMain.getBoolean("show-welcome-message", false)) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("show-welcome-message", prefMain.getBoolean("show-welcome-message", false));

            fragment = new WelcomeFragment();
            fragment.setArguments(bundle);
        } else {
            // Refresh list of profiles
            U.listProfilesBroadcast(getActivity());

            // Add ProfileListFragment or WelcomeFragment
            if(getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
                fragment = new ProfileListFragment();
            else {
                Bundle bundle = new Bundle();
                bundle.putBoolean("show-welcome-message", prefMain.getBoolean("show-welcome-message", false));

                fragment = new WelcomeFragment();
                fragment.setArguments(bundle);
            }
        }

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.profileViewEdit, fragment, "ProfileListFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                .commit();
    }

    private void finish(String filename, boolean isEdit, boolean returnToList) {
        Fragment fragment;
        String id;
        int transition;

        // returnToList is true if we are editing a profile by long-pressing an entry in ProfileListFragment
        // if we were not currently viewing or editing a profile, or if we are creating a new profile.
        // This method should then return the user to ProfileListFragment.
        if(returnToList) {
            if(getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
                fragment = new ProfileListFragment();
            else {
                SharedPreferences prefMain = U.getPrefMain(getActivity());

                Bundle bundle = new Bundle();
                bundle.putBoolean("show-welcome-message", prefMain.getBoolean("show-welcome-message", false));

                fragment = new WelcomeFragment();
                fragment.setArguments(bundle);
            }

            id = "ProfileListFragment";
            transition = FragmentTransaction.TRANSIT_FRAGMENT_CLOSE;
        }

        // returnToList is false if we are editing a profile by using the Edit button in ProfileViewFragment,
        // or if we are selecting / long-pressing a new entry in ProfileListFragment while viewing or editing
        // an exiting profile. This method should then return the user to either ProfileEditFragment or
        // ProfileViewFragment, depending on if the isEdit value is true or false respectively.

        else {
            Bundle bundle = new Bundle();
            bundle.putString("filename", filename);

            if(isEdit) {
                fragment = new ProfileEditFragment();
                fragment.setArguments(bundle);
                id = "ProfileEditFragment";
                transition = FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
            } else {
                try {
                    bundle.putString("title", listener.getProfileTitle(filename));
                } catch (IOException e) { /* Gracefully fail */ }

                fragment = new ProfileViewFragment();
                fragment.setArguments(bundle);
                id = "ProfileViewFragment";
                transition = FragmentTransaction.TRANSIT_FRAGMENT_FADE;
            }
        }

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.profileViewEdit, fragment, id)
                .setTransition(transition)
                .commit();
    }

    public String getFilename() {
        return filename;
    }

    public void setPrefChange(boolean prefChange) {
        if(!this.prefChange)
            this.prefChange = prefChange;
    }

    public void preSave(String filename, boolean isEdit, boolean returnToList) {
        SharedPreferences prefCurrent = U.getPrefCurrent(getActivity());
        SharedPreferences prefNew = U.getPrefNew(getActivity());

        // Show dialog if this is the currently active profile
        if("quick_actions".equals(prefCurrent.getString("filename", "0"))) {
            SharedPreferences prefSaved = U.getPrefQuickActions(getActivity());
            if(this.filename.equals(prefSaved.getString("original_filename", "0")))
                listener.showReloadDialog(filename, isEdit, returnToList);
            else {
                try {
                    saveProfile();
                } catch (IOException e) {
                    // Show error message as toast if file fails to save
                    U.showToast(getActivity(), R.string.failed_to_save);
                }

                // Cleanup
                SharedPreferences.Editor prefNewEditor = prefNew.edit();
                prefNewEditor.clear();
                prefNewEditor.apply();

                finish(filename, isEdit, returnToList);
            }
        } else if(this.filename.equals(prefCurrent.getString("filename", "0")))
            listener.showReloadDialog(filename, isEdit, returnToList);
        else {
            try {
                saveProfile();
            } catch(IOException e) {
                // Show error message as toast if file fails to save
                U.showToast(getActivity(), R.string.failed_to_save);
            }

            // Cleanup
            SharedPreferences.Editor prefNewEditor = prefNew.edit();
            prefNewEditor.clear();
            prefNewEditor.apply();

            finish(filename, isEdit, returnToList);
        }
    }

    private void disablePreference(SharedPreferences prefNew, String preferenceName, boolean shouldClear) {
        if(shouldClear && prefNew.getBoolean(preferenceName, false)) {
            SharedPreferences.Editor editor = prefNew.edit();
            editor.putBoolean(preferenceName, false);
            editor.apply();
        }

        Preference preference = getPreferenceScreen().findPreference(preferenceName);
        preference.setEnabled(false);

        if(preference instanceof CheckBoxPreference)
            ((CheckBoxPreference) preference).setChecked(false);
    }

    public static boolean isPlayStoreInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.android.vending", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
