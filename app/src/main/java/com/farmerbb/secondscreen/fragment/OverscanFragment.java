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
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.service.TestOverscanService;
import com.farmerbb.secondscreen.util.U;

import java.util.Scanner;

// Fragment launched as part of FragmentContainerActivity whenever the user selects the "Overscan
// settings" option when editing a profile.
public final class OverscanFragment extends PreferenceFragment implements
        OnPreferenceClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    boolean prefChange = false;
    boolean currentProfile = false;
    boolean testOverscan = false;

    /* The activity that creates an instance of this fragment must
     * implement this interface in order to receive event call backs. */
    public interface Listener {
        void showKeepOverscanDialog();
        void dismissKeepOverscanDialog();
        String getFilename();
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

        // Change window title
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getActivity().setTitle(getResources().getString(R.string.pref_title_overscan_settings));
        } else
            getActivity().setTitle(" " + getResources().getString(R.string.pref_title_overscan_settings));

        // Show the Up button in the action bar
        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Determine if we are editing the currently active profile
        String filename = listener.getFilename();
        SharedPreferences prefCurrent = U.getPrefCurrent(getActivity());
        if("quick_actions".equals(prefCurrent.getString("filename", "0"))) {
            SharedPreferences prefSaved = U.getPrefQuickActions(getActivity());
            if(filename.equals(prefSaved.getString("original_filename", "0")))
                currentProfile = true;
        } else if(filename.equals(prefCurrent.getString("filename", "0")))
            currentProfile = true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefMain = U.getPrefMain(getActivity());

        // Add preferences
        if(prefMain.getBoolean("expert_mode", false)) {
            SharedPreferences prefNew = U.getPrefNew(getActivity());
            SharedPreferences.Editor editor = prefNew.edit();

            editor.putString("overscan_top_expert", Integer.toString(prefNew.getInt("overscan_top", 0)));
            editor.putString("overscan_bottom_expert", Integer.toString(prefNew.getInt("overscan_bottom", 0)));
            editor.putString("overscan_left_expert", Integer.toString(prefNew.getInt("overscan_left", 0)));
            editor.putString("overscan_right_expert", Integer.toString(prefNew.getInt("overscan_right", 0)));
            editor.apply();

            addPreferencesFromResource(R.xml.overscan_preferences_expert);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
            // their values. When their values change, their summaries are updated
            // to reflect the new value, per the Android Design guidelines.
            U.bindPreferenceSummaryToValue(findPreference("overscan_top_expert"), opcl);
            U.bindPreferenceSummaryToValue(findPreference("overscan_bottom_expert"), opcl);
            U.bindPreferenceSummaryToValue(findPreference("overscan_left_expert"), opcl);
            U.bindPreferenceSummaryToValue(findPreference("overscan_right_expert"), opcl);
        } else
            addPreferencesFromResource(R.xml.overscan_preferences);

        // Set OnClickListeners for certain preferences
        findPreference("test_overscan").setOnPreferenceClickListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Register listener to check for changed preferences
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

        // Dismiss the KeepOverscanDialogFragment if it is showing, as it is no longer relevant
        listener.dismissKeepOverscanDialog();
    }

    @Override
    public void onStop() {
        super.onStop();

        // Unregister listener
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);

        // Reset tested overscan values; otherwise, the values will permanently stick
        if(!getActivity().isFinishing() && testOverscan)
            resetOverscan();
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
        if(testOverscan && currentProfile)
            listener.showKeepOverscanDialog();
        else
            finish(true);
    }

    public void resetOverscan() {
        // If user used the "Test overscan" option, reset any tested overscan values
        testOverscan = false;

        Intent serviceIntent = new Intent(getActivity(), TestOverscanService.class);
        serviceIntent.putExtra("test_overscan", testOverscan);
        getActivity().startService(serviceIntent);
    }

    public void finish(boolean resetOverscan) {
        Intent outData = new Intent();
        outData.putExtra("pref-change", prefChange);

        SharedPreferences prefNew = U.getPrefNew(getActivity());
        SharedPreferences.Editor prefNewEditor = prefNew.edit();

        if(!prefNew.getBoolean("overscan", false)) {
            prefNewEditor.putInt("overscan_left", 0);
            prefNewEditor.putInt("overscan_right", 0);
            prefNewEditor.putInt("overscan_top", 0);
            prefNewEditor.putInt("overscan_bottom", 0);
            prefNewEditor.apply();
        }

        if(resetOverscan)
            resetOverscan();

        getActivity().setResult(Activity.RESULT_OK, outData);
        getActivity().finish();
    }

    @Override
    public boolean onPreferenceClick(Preference p) {
        if(p.getKey().equals("test_overscan")) {
            testOverscan = true;

            Intent serviceIntent = new Intent(getActivity(), TestOverscanService.class);
            serviceIntent.putExtra("test_overscan", testOverscan);
            getActivity().startService(serviceIntent);
        }

        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals("overscan_left_expert")
                || key.equals("overscan_right_expert")
                || key.equals("overscan_top_expert")
                || key.equals("overscan_bottom_expert")) {
            SharedPreferences.Editor prefNewEditor = sharedPreferences.edit();

            if(sharedPreferences.getString(key, "0").isEmpty())
                prefNewEditor.putString(key, "0");

            Scanner scanner = new Scanner(key);
            scanner.useDelimiter("_expert");

            prefNewEditor.putInt(scanner.next(), Integer.parseInt(sharedPreferences.getString(key, "0")));
            prefNewEditor.apply();

            scanner.close();
        } else
            prefChange = true;
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener opcl = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            // Damage control if user inputs an empty value
            if(stringValue.isEmpty())
                stringValue = "0";

            // Set the summary to the value's simple string representation
            preference.setSummary(stringValue);

            return true;
        }
    };
}
