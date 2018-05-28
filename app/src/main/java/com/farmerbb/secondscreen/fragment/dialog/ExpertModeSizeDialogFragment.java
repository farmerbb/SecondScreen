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
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.farmerbb.secondscreen.R;

// DialogFragment shown when the Resolution option is selected in ProfileEditFragment while in
// expert mode.
public final class ExpertModeSizeDialogFragment extends DialogFragment {

        EditText editText;
        EditText editText2;
        boolean showKeyboard = true;

        /* The activity that creates an instance of this dialog fragment must
         * implement this interface in order to receive event call backs.
         * Each method passes the DialogFragment in case the host needs to query it. */
        public interface Listener {
            void onExpertModeSizePositiveClick(String height, String width);
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

            // Get the layout inflater
            final LayoutInflater inflater = getActivity().getLayoutInflater();

            // Inflate and set the layout for the dialog
            // Pass null as the parent view because its going in the dialog layout
            final View view = inflater.inflate(R.layout.fragment_expert_mode_size, null);
            builder.setView(view)
                .setTitle(R.string.pref_title_resolution)
                .setPositiveButton(R.string.action_ok, (dialog, id) -> {
                    editText = view.findViewById(R.id.fragmentEditText1);
                    editText2 = view.findViewById(R.id.fragmentEditText2);

                    String height = editText.getText().toString();
                    String width = editText2.getText().toString();

                    listener.onExpertModeSizePositiveClick(height, width);
                })
                .setNegativeButton(R.string.action_cancel, (dialog, id) -> {}
                );

            editText = view.findViewById(R.id.fragmentEditText1);
            editText2 = view.findViewById(R.id.fragmentEditText2);

            editText.setText(getArguments().getString("height"));
            editText2.setText(getArguments().getString("width"));

            editText.selectAll();
            editText.setOnFocusChangeListener((v, hasFocus) -> {
                if(showKeyboard) {
                    showKeyboard = false;
                    editText.post(() -> {
                        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                    });
                }
            });
            editText.requestFocus();

            // Remove padding from layout on pre-Lollipop devices
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                view.setPadding(0, 0, 0, 0);

            // Create the AlertDialog object and return it
            return builder.create();
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            showKeyboard = true;
            super.onDismiss(dialog);
        }
    }
