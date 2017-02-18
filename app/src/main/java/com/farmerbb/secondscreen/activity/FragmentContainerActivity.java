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

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.fragment.OverscanFragment;
import com.farmerbb.secondscreen.fragment.SettingsFragment;
import com.farmerbb.secondscreen.fragment.dialog.ExpertModeDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.KeepOverscanDialogFragment;

// This activity serves as a container for any fragments that are not part of the main app flow.
// Currently, this activity will load either the SettingsFragment, or the OverscanFragment
// as part of the profile edit flow.
public final class FragmentContainerActivity extends AppCompatActivity implements
        ExpertModeDialogFragment.Listener,
        KeepOverscanDialogFragment.Listener,
        SettingsFragment.Listener,
        OverscanFragment.Listener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(getIntent() == null)
            finish();
        else {
            setContentView(R.layout.activity_fragment_container);

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Set action bar elevation
                getSupportActionBar().setElevation(getResources().getDimensionPixelSize(R.dimen.action_bar_elevation));
            }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                getWindow().setDecorCaptionShade(Window.DECOR_CAPTION_SHADE_DARK);
            }

            // Begin a new FragmentTransaction
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            Fragment fragment = null;

            // Determine if we need to launch SettingsFragment or OverscanFragment
            String tag = getIntent().getStringExtra("tag");
            if(!((getFragmentManager().findFragmentById(R.id.fragmentContainer) instanceof SettingsFragment)
                    || (getFragmentManager().findFragmentById(R.id.fragmentContainer) instanceof OverscanFragment))) {
                switch(tag) {
                    case "SettingsFragment":
                        fragment = new SettingsFragment();
                        break;
                    case "OverscanFragment":
                        fragment = new OverscanFragment();
                        break;
                }

                if(fragment != null)
                    transaction.replace(R.id.fragmentContainer, fragment, tag);
            }

            // Commit fragment transaction
            transaction.commit();
        }
    }

    @Override
    public void onBackPressed() {
        if(getFragmentManager().findFragmentById(R.id.fragmentContainer) instanceof SettingsFragment) {
            SettingsFragment fragment = (SettingsFragment) getFragmentManager().findFragmentByTag("SettingsFragment");
            fragment.onBackPressed();
        } else if(getFragmentManager().findFragmentById(R.id.fragmentContainer) instanceof OverscanFragment) {
            OverscanFragment fragment = (OverscanFragment) getFragmentManager().findFragmentByTag("OverscanFragment");
            fragment.onBackPressed();
        }
    }

    @Override
    public void onExpertModeDialogPositiveClick(CheckBoxPreference checkBoxPreference) {
        if(getFragmentManager().findFragmentById(R.id.fragmentContainer) instanceof SettingsFragment) {
            SettingsFragment fragment = (SettingsFragment) getFragmentManager().findFragmentByTag("SettingsFragment");
            fragment.onExpertModeDialogPositiveClick(checkBoxPreference);
        }
    }

    @Override
    public void showExpertModeDialog(CheckBoxPreference checkBoxPreference) {
        ExpertModeDialogFragment expertModeFragment = new ExpertModeDialogFragment();
        expertModeFragment.setPreference(checkBoxPreference);
        expertModeFragment.show(getFragmentManager(), "expert-mode");
    }

    @Override
    public void showKeepOverscanDialog() {
        DialogFragment keepOverscanFragment = new KeepOverscanDialogFragment();
        keepOverscanFragment.show(getFragmentManager(), "keep-overscan");
    }

    @Override
    public void onKeepOverscanDialogPositiveClick() {
        if(getFragmentManager().findFragmentById(R.id.fragmentContainer) instanceof OverscanFragment) {
            OverscanFragment fragment = (OverscanFragment) getFragmentManager().findFragmentByTag("OverscanFragment");
            fragment.finish(false);
        }
    }

    @Override
    public void onKeepOverscanDialogNegativeClick() {
        if(getFragmentManager().findFragmentById(R.id.fragmentContainer) instanceof OverscanFragment) {
            OverscanFragment fragment = (OverscanFragment) getFragmentManager().findFragmentByTag("OverscanFragment");
            fragment.finish(true);
        }
    }

    @Override
    public void dismissKeepOverscanDialog() {
        if(getFragmentManager().findFragmentByTag("keep-overscan") != null) {
            DialogFragment keepOverscanFragment = (DialogFragment) getFragmentManager().findFragmentByTag("keep-overscan");
            keepOverscanFragment.dismiss();
        }
    }

    @Override
    public String getFilename() {
        return getIntent().getStringExtra("filename");
    }
}