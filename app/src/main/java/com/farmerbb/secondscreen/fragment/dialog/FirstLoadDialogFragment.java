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
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.farmerbb.secondscreen.R;

// DialogFragment shown when the user attempts to load a profile for the first time, and any
// subsequent times as long as the "Don't show again" option isn't ticked.
public final class FirstLoadDialogFragment extends DialogFragment {

    CheckBox checkbox;
    TextView textView;

    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event call backs.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface Listener {
        void onFirstLoadPositiveClick(DialogFragment dialog, String filename, boolean isChecked);
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
            throw new ClassCastException(activity
                    + " must implement Listener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        // Get the layout inflater
        final LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        final View view = inflater.inflate(R.layout.fragment_dialogs, null);

        builder.setView(view)
        .setTitle(R.string.dialog_load_profile)
        .setPositiveButton(R.string.action_ok, (dialog, id) -> {
            checkbox = view.findViewById(R.id.firstLoadCheckBox);
            listener.onFirstLoadPositiveClick(this,
                    getArguments().getString("filename"),
                    checkbox.isChecked());
        })
        .setNegativeButton(R.string.action_cancel, (dialog, id) -> {}
        );

        textView = view.findViewById(R.id.dialogMessage);
        textView.setText(R.string.dialog_first_load);

        // Create the AlertDialog object and return it
        return builder.create();
    }
}