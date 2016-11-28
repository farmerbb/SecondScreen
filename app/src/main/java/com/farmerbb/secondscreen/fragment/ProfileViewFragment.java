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
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.util.U;

import java.io.File;

// Fragment launched as part of MainActivity that shows a brief summary of a profile selected from
// ProfileListActivity. It also contains a button that will either: launch the ProfileLoadService
// if the profile is inactive, or launch the TurnOffService if the profile is active.
public final class ProfileViewFragment extends Fragment {

    String filename = "";
    String left;
    String right;
    int n;

    /* The activity that creates an instance of this fragment must
     * implement this interface in order to receive event call backs. */
    public interface Listener {
        void showDeleteDialog();
        void onLoadProfileButtonClick(String filename);
        void onTurnOffProfileButtonClick();
        String generateBlurb(String key, String value);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile_view, container, false);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set values
        setRetainInstance(true);
        setHasOptionsMenu(true);

        // Change window title
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            getActivity().setTitle(getArguments().getString("title"));
        else
            getActivity().setTitle(" " + getArguments().getString("title"));

        // Show the Up button in the action bar.
        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Animate elevation change
        if(getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-large")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            LinearLayout profileViewEdit = (LinearLayout) getActivity().findViewById(R.id.profileViewEdit);
            LinearLayout profileList = (LinearLayout) getActivity().findViewById(R.id.profileList);
            profileList.animate().z(0f);
            profileViewEdit.animate().z(getResources().getDimensionPixelSize(R.dimen.profile_view_edit_elevation));
        }

        // Get filename of saved note
        filename = getArguments().getString("filename");

        SharedPreferences prefSaved = U.getPrefSaved(getActivity(), filename);
        SharedPreferences prefCurrent = U.getPrefCurrent(getActivity());
        Button button = (Button) getActivity().findViewById(R.id.pvButton);
        TextView resolution = (TextView) getActivity().findViewById(R.id.pvResolution);
        TextView density = (TextView) getActivity().findViewById(R.id.pvDensity);
        TextView profileSettingsLeft = (TextView) getActivity().findViewById(R.id.pvProfileSettingsLeft);
        TextView profileSettingsRight = (TextView) getActivity().findViewById(R.id.pvProfileSettingsRight);

        // Change color and/or background of the Load/Turn Off button
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            button.setBackground(getResources().getDrawable(R.drawable.pl_button));
        else
            button.getBackground().setColorFilter(ContextCompat.getColor(getActivity(), R.color.pl_selected), PorterDuff.Mode.SRC);

        // Set listeners for Load/Turn Off button
        if("quick_actions".equals(prefCurrent.getString("filename", "0"))) {
            SharedPreferences prefQuick = U.getPrefQuickActions(getActivity());
            if(filename.equals(prefQuick.getString("original_filename", "0"))) {
                button.setText(getResources().getStringArray(R.array.pref_notification_action_list)[0] + " " + getArguments().getString("title"));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onTurnOffProfileButtonClick();
                    }
                });
            } else {
                button.setText(getResources().getString(R.string.action_load) + " " + getArguments().getString("title"));
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        listener.onLoadProfileButtonClick(filename);
                    }
                });
            }
        } else if(filename.equals(prefCurrent.getString("filename", "0"))) {
            button.setText(getResources().getStringArray(R.array.pref_notification_action_list)[0] + " " + getArguments().getString("title"));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onTurnOffProfileButtonClick();
                }
            });
        } else {
            button.setText(getResources().getString(R.string.action_load) + " " + getArguments().getString("title"));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    listener.onLoadProfileButtonClick(filename);
                }
            });
        }

        // Generate a brief overview of this profile's settings to display within the fragment
        // NOTE:  these statements must remain in order
        resolution.setText(listener.generateBlurb("size", prefSaved.getString("size", "reset")));
        density.setText(listener.generateBlurb("density", prefSaved.getString("density", "reset")));

        left = "";
        right = "";
        n = 0;

        generateProfileSettings(prefSaved.getBoolean("backlight_off", false), R.string.pref_title_backlight_off);
        generateProfileSettings(prefSaved.getBoolean("bluetooth_on", false), R.string.profile_view_bluetooth_on);
        generateProfileSettings(prefSaved.getBoolean("chrome", false), R.string.quick_chrome);
        generateProfileSettings(prefSaved.getBoolean("clear_home", false), R.string.quick_clear_home);
        generateProfileSettings(prefSaved.getBoolean("daydreams_on", false), R.string.profile_view_daydreams_on);
        generateProfileSettings(prefSaved.getBoolean("navbar", false), R.string.profile_view_navbar);
        generateProfileSettings(prefSaved.getBoolean("freeform", false), R.string.profile_view_freeform);

        switch(prefSaved.getString("hdmi_rotation", "landscape")) {
            case "portrait":
                generateProfileSettings(true, R.string.profile_view_hdmi_output);
                break;
        }

        generateProfileSettings(prefSaved.getBoolean("overscan", false), R.string.quick_overscan);

        switch(prefSaved.getString("rotation_lock_new", "fallback")) {
            case "fallback":
                if(prefSaved.getBoolean("rotation_lock", false))
                    generateProfileSettings(true, R.string.profile_view_rotation_landscape);
                break;
            case "landscape":
                generateProfileSettings(true, R.string.profile_view_rotation_landscape);
                break;
            case "auto-rotate":
                generateProfileSettings(true, R.string.profile_view_rotation_autorotate);
                break;
        }

        switch(prefSaved.getString("screen_timeout", "do-nothing")) {
            case "always-on":
                generateProfileSettings(true, R.string.profile_view_screen_timeout_always_on);
                break;
            case "always-on-charging":
                generateProfileSettings(true, R.string.profile_view_screen_timeout_always_on_charging);
                break;
        }

        generateProfileSettings(prefSaved.getBoolean("show_touches", false), R.string.pref_title_show_touches);

        switch(prefSaved.getString("immersive_new", "fallback")) {
            case "fallback":
                if(prefSaved.getBoolean("immersive", false))
                    generateProfileSettings(true, R.string.pref_title_immersive);
                break;
            case "status-only":
                generateProfileSettings(true, R.string.pref_title_immersive);
                break;
            case "immersive-mode":
                generateProfileSettings(true, R.string.pref_title_immersive);
                break;
        }

        generateProfileSettings(prefSaved.getBoolean("taskbar", false), R.string.quick_taskbar);

        switch(prefSaved.getString("ui_refresh", "do-nothing")) {
            case "system-ui":
                if(getActivity().getPackageManager().hasSystemFeature("com.cyanogenmod.android")
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                    generateProfileSettings(true, R.string.profile_view_ui_refresh_soft_reboot);
                else
                    generateProfileSettings(true, R.string.profile_view_ui_refresh_systemui);
                break;
            case "activity-manager":
                generateProfileSettings(true, R.string.profile_view_ui_refresh_soft_reboot);
                break;
        }

        generateProfileSettings(prefSaved.getBoolean("vibration_off", false), R.string.pref_title_vibration_off);
        generateProfileSettings(prefSaved.getBoolean("wifi_on", false), R.string.profile_view_wifi_on);

        if(!left.isEmpty())
            profileSettingsLeft.setText(left);

        if(!right.isEmpty())
            profileSettingsRight.setText(right);

        if(n == 0) {
            TextView header = (TextView) getActivity().findViewById(R.id.pvHeaderProfileSettings);
            header.setText(" ");
            header.setBackgroundColor(Color.WHITE);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.profile_view, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Override default Android "up" behavior to instead mimic the back button
                onBackPressed();
                return true;

            // Edit button
            case R.id.action_edit:
                Bundle bundle = new Bundle();
                bundle.putString("filename", filename);

                Fragment fragment = new ProfileEditFragment();
                fragment.setArguments(bundle);

                getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.profileViewEdit, fragment, "ProfileEditFragment")
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .commit();

                return true;

            // Delete button
            case R.id.action_delete_2:
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

        // Refresh list of profiles
        U.listProfilesBroadcast(getActivity());

        onBackPressed();
    }

    public void onBackPressed() {
        // Add ProfileListFragment or WelcomeFragment
        Fragment fragment;
        if(getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
            fragment = new ProfileListFragment();
        else {
            SharedPreferences prefMain = U.getPrefMain(getActivity());

            Bundle bundle = new Bundle();
            bundle.putBoolean("show-welcome-message", prefMain.getBoolean("show-welcome-message", false));

            fragment = new WelcomeFragment();
            fragment.setArguments(bundle);
        }

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.profileViewEdit, fragment, "ProfileListFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                .commit();
    }

    public String getFilename() {
        return getArguments().getString("filename");
    }

    private void generateProfileSettings(boolean optionIsSet, int text) {
        if(optionIsSet) {
            n++;
            if(n % 2 == 0)
                right = right + getResources().getString(R.string.bullet) + " " + getResources().getString(text) + "\n";
            else
                left = left + getResources().getString(R.string.bullet) + " " + getResources().getString(text) + "\n";
        }
    }
}
