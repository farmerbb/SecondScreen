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

package com.farmerbb.secondscreen.fragment.dialog;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.preference.CheckBoxPreference;

import com.farmerbb.secondscreen.R;

// DialogFragment shown when the expert mode option is pressed in SettingsFragment.  The user is
// blocked from checking that option until the user acknowledges the information shown in this
// dialog.
public final class ExpertModeDialogFragment extends DialogFragment {

    CheckBoxPreference checkBoxPreference;

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event call backs.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface Listener {
        void onExpertModeDialogPositiveClick(CheckBoxPreference checkBoxPreference);
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
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_are_you_sure)
        .setMessage(R.string.expert_mode_dialog)
        .setPositiveButton(R.string.action_ok, (dialog, id) -> listener.onExpertModeDialogPositiveClick(checkBoxPreference))
        .setNegativeButton(R.string.action_cancel, (dialog, id) -> {});

        // Create the AlertDialog object and return it
        return builder.create();
    }

    public void setPreference(CheckBoxPreference checkBoxPreference) {
        this.checkBoxPreference = checkBoxPreference;
    }
}