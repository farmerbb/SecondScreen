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
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.farmerbb.secondscreen.R;

// DialogFragment shown when the user is attempting to save an edited profile, and the profile they
// are editing is the currently active profile.
// It will offer to reload the profile in order to apply any changed settings.
public final class ReloadProfileDialogFragment extends DialogFragment {

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event call backs.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface Listener {
        void onReloadDialogPositiveClick(String filename, boolean isEdit, boolean returnToList);
        void onReloadDialogNegativeClick(String filename, boolean isEdit, boolean returnToList);
    }

    // Use this instance of the interface to deliver action events
    Listener listener;

    // Override the Fragment.onAttach() method to instantiate the Listener
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
        builder.setTitle(R.string.dialog_reload_profile_title)
        .setMessage(R.string.editing_current_profile)
        .setPositiveButton(R.string.action_save_reload, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if(getArguments() != null)
                    listener.onReloadDialogPositiveClick(getArguments().getString("filename"),
                        getArguments().getBoolean("is-edit"),
                        getArguments().getBoolean("return-to-list"));
                else
                    listener.onReloadDialogPositiveClick(null, false, false);
            }
        })
        .setNegativeButton(R.string.action_save_only, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                if(getArguments() != null)
                    listener.onReloadDialogNegativeClick(getArguments().getString("filename"),
                            getArguments().getBoolean("is-edit"),
                            getArguments().getBoolean("return-to-list"));
                else
                    listener.onReloadDialogNegativeClick(null, false, false);
            }
        });

        // Create the AlertDialog object and return it
        return builder.create();
    }
}