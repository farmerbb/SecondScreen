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

// This dialog is shown when both the formerly paid (com.farmerbb.secondscreen) and current free
// (com.farmerbb.secondscreen.free) versions of the apk are installed on the user's device.
// For stability reasons, only one version of SecondScreen may be active on a device.  If a curious
// user has one version installed and decides to install the other version, this dialog will appear
// on startup of either version of the app and block the user from proceeding any further.
// It will offer to uninstall the duplicate version of the app.
public final class MultipleVersionsDialogFragment extends DialogFragment {

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event call backs.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface Listener {
        void uninstallPackage(String packageName);
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
        builder.setMessage(R.string.both_versions_installed)
            .setTitle(R.string.safeguard_title)
            .setPositiveButton(R.string.action_uninstall, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    listener.uninstallPackage(getArguments().getString("package"));
                }
            });

        // Prevent the user from cancelling this particular dialog
        setCancelable(false);

        // Create the AlertDialog object and return it
        return builder.create();
    }
}
