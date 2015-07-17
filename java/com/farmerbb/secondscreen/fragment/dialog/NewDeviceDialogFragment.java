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
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.farmerbb.secondscreen.R;

// SecondScreen keeps track of the user's Android device ID, in order to detect if a user has
// backed up and restored an instance of the app via Titanium Backup or similar means.
// For stability reasons, it is recommended to start the app in a fresh state if run on a different
// device.  This dialog is shown only once on application startup to inform the user of the best
// course of action in order to continue using the app reliably.
public final class NewDeviceDialogFragment extends DialogFragment {

/* The activity that creates an instance of this dialog fragment must
 * implement this interface in order to receive event call backs.
 * Each method passes the DialogFragment in case the host needs to query it. */
    public interface Listener {
        void onNewDeviceDialogPositiveClick(DialogFragment dialog);
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
        builder.setMessage(R.string.new_device_message)
        .setTitle(R.string.safeguard_title)
        .setPositiveButton(R.string.action_ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                listener.onNewDeviceDialogPositiveClick(NewDeviceDialogFragment.this);
            }
        });

        // Create the AlertDialog object and return it
        return builder.create();
    }
}