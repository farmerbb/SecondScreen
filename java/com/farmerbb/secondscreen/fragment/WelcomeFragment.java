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
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.FragmentContainerActivity;
import com.farmerbb.secondscreen.activity.TaskerQuickActionsActivity;
import com.farmerbb.secondscreen.fragment.dialog.NewProfileDialogFragment;
import com.melnykov.fab.FloatingActionButton;

// Fragment launched as part of MainActivity.  The WelcomeFragment is shown on all devices when the
// application is first started.  It guides the user to create a new profile.  Once a profile is
// created, this fragment is never shown again in the single-pane layout.  However, in the dual-pane
// layout, WelcomeFragment is shown in the right pane whenever ProfileViewFragment or
// ProfileEditFragment is not shown.
public final class WelcomeFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(getArguments().getBoolean("show-welcome-message", true))
            return inflater.inflate(R.layout.fragment_welcome, container, false);
        else
            return inflater.inflate(R.layout.fragment_welcome_alt, container, false);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set values
        setRetainInstance(true);
        setHasOptionsMenu(true);

        // Animate elevation change
        if(getActivity().findViewById(R.id.layoutMain).getTag().equals("main-layout-large")
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            LinearLayout profileViewEdit = (LinearLayout) getActivity().findViewById(R.id.profileViewEdit);
            LinearLayout profileList = (LinearLayout) getActivity().findViewById(R.id.profileList);

            if(getArguments().getBoolean("show-welcome-message", true)) {
                profileList.animate().z(0f);
                profileViewEdit.animate().z(getResources().getDimensionPixelSize(R.dimen.profile_view_edit_elevation));
            } else {
                profileViewEdit.animate().z(0f);
                profileList.animate().z(getResources().getDimensionPixelSize(R.dimen.profile_list_elevation));
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        // Change window title
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            getActivity().setTitle(getResources().getString(R.string.app_name));
        else
            getActivity().setTitle(" " + getResources().getString(R.string.app_name));

        // Don't show the Up button in the action bar, and disable the button
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
        getActivity().getActionBar().setHomeButtonEnabled(false);

        // Floating action button
        FloatingActionButton floatingActionButton = (FloatingActionButton) getActivity().findViewById(R.id.button_floating_action_welcome);
        floatingActionButton.hide(false);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            floatingActionButton.show();
            floatingActionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    DialogFragment newProfileFragment = new NewProfileDialogFragment();
                    newProfileFragment.show(getFragmentManager(), "new");
                }
            });
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch(item.getItemId()) {
                // New button
            case R.id.action_new:
                    DialogFragment newProfileFragment = new NewProfileDialogFragment();
                    newProfileFragment.show(getFragmentManager(), "new");
                return true;

                // Quick Actions button
            case R.id.action_quick:
                    Intent intentQuick = new Intent(getActivity(), TaskerQuickActionsActivity.class);
                    intentQuick.putExtra("launched-from-app", true);
                    startActivity(intentQuick);
                return true;

                // About button
            case R.id.action_settings:
                    Intent intentSettings = new Intent(getActivity(), FragmentContainerActivity.class);
                    intentSettings.putExtra("tag", "SettingsFragment");
                    startActivity(intentSettings);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
