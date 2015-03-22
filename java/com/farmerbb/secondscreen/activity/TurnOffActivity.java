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

import android.app.Activity;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.fragment.dialog.TurnOffDialogFragment;
import com.farmerbb.secondscreen.util.U;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

// This activity is responsible for invoking the TurnOffService via either the notification
// action button, or when a display is disconnected.  (When a profile is turned off via
// ProfileViewFragment, this activity is not invoked and TurnOffService is run directly.)
//
// The activity invokes the TurnOffService immediately if run via the notification action button;
// it will show the TurnOffDialogFragment first if run when a display is disconnected.
public final class TurnOffActivity extends Activity implements TurnOffDialogFragment.Listener {

    public final class FinishReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    }

    boolean dialog = false;
    IntentFilter filter = new IntentFilter(U.SCREEN_CONNECT);
    FinishReceiver receiver = new FinishReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load preferences
        SharedPreferences prefCurrent = U.getPrefCurrent(this);
        String filename = prefCurrent.getString("filename", "0");

        // Close notification drawer
        Intent closeDrawer = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        sendBroadcast(closeDrawer);

        // Handle intents
        Intent quickLaunchIntent = getIntent();
        if(quickLaunchIntent.getBooleanExtra("notification", false)) {
            U.turnOffProfile(this);
            finish();
        } else {
            dialog = true;
            setTitle(getResources().getString(R.string.display_disconnected));

            if(prefCurrent.getString("filename", "0").equals("quick_actions")) {
                SharedPreferences prefSaved = U.getPrefQuickActions(this);
                filename = prefSaved.getString("original_filename", "0");

                Bundle bundle = new Bundle();
                if(filename.equals("0"))
                    bundle.putString("name", getResources().getStringArray(R.array.pref_notification_action_list)[1]);
                else {
                    try {
                        bundle.putString("name", getProfileTitle(filename));
                    } catch (IOException e) {
                        bundle.putString("name", getResources().getString(R.string.this_profile));
                    }
                }

                bundle.putString("title", getResources().getString(R.string.display_disconnected));

                try {
                    if(getFragmentManager().findFragmentByTag("turn-off") == null) {
                        DialogFragment turnOffFragment = new TurnOffDialogFragment();
                        turnOffFragment.setArguments(bundle);
                        turnOffFragment.show(getFragmentManager(), "turn-off");
                    }
                } catch (IllegalStateException e) {}
            } else {
                Bundle bundle = new Bundle();
                try {
                    bundle.putString("name", getProfileTitle(filename));
                } catch (IOException e) {
                    bundle.putString("name", getResources().getString(R.string.this_profile));
                }

                bundle.putString("title", getResources().getString(R.string.display_disconnected));

                try {
                    if(getFragmentManager().findFragmentByTag("turn-off") == null) {
                        DialogFragment turnOffFragment = new TurnOffDialogFragment();
                        turnOffFragment.setArguments(bundle);
                        turnOffFragment.show(getFragmentManager(), "turn-off");
                    }
                } catch (IllegalStateException e) {}
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(dialog)
            registerReceiver(receiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if(dialog)
            unregisterReceiver(receiver);
    }

    // Loads first line of a profile for display in the ListView
    private String getProfileTitle(String filename) throws IOException {

        // Open the file on disk
        FileInputStream input = openFileInput(filename);
        InputStreamReader reader = new InputStreamReader(input);
        BufferedReader buffer = new BufferedReader(reader);

        // Load the file
        String line = buffer.readLine();

        // Close file on disk
        reader.close();

        return(line);
    }

    @Override
    public void onTurnOffDialogPositiveClick(DialogFragment dialog) {
        // User touched the dialog's positive button
        try {
            // Dismiss dialog
            dialog.dismiss();

            SharedPreferences prefCurrent = U.getPrefCurrent(this);
            if(!prefCurrent.getBoolean("not_active", true))
                U.turnOffProfile(this);
        } catch (NullPointerException e) {}
    }

    @Override
    public void finishActivity() {
        finish();
    }
}
