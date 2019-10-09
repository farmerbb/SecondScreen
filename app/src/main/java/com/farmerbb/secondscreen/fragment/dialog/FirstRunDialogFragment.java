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
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.farmerbb.secondscreen.R;

// This is the warning message shown when SecondScreen is run for the first time.  The user must
// accept the terms in this dialog before they can proceed any further in the app.  If the user
// declines the terms, they are offered to uninstall the app via the standard system dialog.
public final class FirstRunDialogFragment extends DialogFragment {

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event call backs.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface Listener {
        void onFirstRunDialogPositiveClick();
        void onFirstRunDialogNegativeClick();
    }

    // Use this instance of the interface to deliver action events
    Listener listener;

    private int secondsLeft = 5;
    private boolean isStarted = false;

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
        builder.setMessage(R.string.first_run)
                .setTitle(R.string.welcome)
                .setPositiveButton(R.string.accept, (dialog, id) -> listener.onFirstRunDialogPositiveClick())
                .setNegativeButton(R.string.decline, (dialog, id) -> listener.onFirstRunDialogNegativeClick());

        // Prevent the user from cancelling this particular dialog
        setCancelable(false);

        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        isStarted = true;

        startCountdown((AlertDialog) getDialog());
    }

    @Override
    public void onStop() {
        isStarted = false;
        super.onStop();
    }

    private void startCountdown(AlertDialog dialog) {
        if(!isStarted) return;

        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if(secondsLeft == 0) {
            button.setEnabled(true);
            button.setText(getString(R.string.accept));
            return;
        }

        button.setEnabled(false);
        button.setText(getString(R.string.accept_alt, secondsLeft));
        secondsLeft--;

        new Handler().postDelayed(() -> startCountdown(dialog), 1000);
    }
}
