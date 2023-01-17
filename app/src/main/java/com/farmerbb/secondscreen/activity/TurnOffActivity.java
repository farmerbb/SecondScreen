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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.util.U;

import java.io.IOException;

// This activity is responsible for invoking the TurnOffService via either the notification
// action button, or when a display is disconnected.  (When a profile is turned off via
// ProfileViewFragment, this activity is not invoked and TurnOffService is run directly.)
//
// The activity invokes the TurnOffService immediately if run via the notification action button;
// it will show a confirmation dialog first if run when a display is disconnected.
public final class TurnOffActivity extends AppCompatActivity {

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
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences prefCurrent = U.getPrefCurrent(this);
        String filename = prefCurrent.getString("filename", "0");

        // Close notification drawer
        U.closeNotificationDrawer(this);

        // Handle intents
        Intent quickLaunchIntent = getIntent();
        if(quickLaunchIntent.getBooleanExtra("notification", false)) {
            U.turnOffProfile(this);
            finish();
        } else if(prefMain.getBoolean("show_turn_off_dialog", true)) {
            dialog = true;
            String name;
            setTitle(getResources().getString(R.string.display_disconnected));
            setContentView(R.layout.activity_turn_off);

            Button buttonPrimary = findViewById(R.id.turnOffButtonPrimary);
            Button buttonSecondary = findViewById(R.id.turnOffButtonSecondary);
            buttonPrimary.setText(getResources().getString(R.string.action_turn_off).toUpperCase());
            buttonSecondary.setText(getResources().getString(R.string.action_close).toUpperCase());

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                buttonPrimary.setTextSize(14);
                buttonSecondary.setTextSize(14);
            }

            if("quick_actions".equals(prefCurrent.getString("filename", "0"))) {
                SharedPreferences prefSaved = U.getPrefQuickActions(this);
                filename = prefSaved.getString("original_filename", "0");

                if("0".equals(filename))
                    name = getResources().getStringArray(R.array.pref_notification_action_list)[1];
                else {
                    try {
                        name = U.getProfileTitle(this, filename);
                    } catch (IOException e) {
                        name = getResources().getString(R.string.this_profile);
                    }
                }
            } else {
                try {
                    name = U.getProfileTitle(this, filename);
                } catch (IOException e) {
                    name = getResources().getString(R.string.this_profile);
                }
            }

            // Set TextView contents
            TextView textView = findViewById(R.id.turnOffTextView);
            textView.setText(getResources().getString(R.string.dialog_turn_off_message, name));

            // Set OnClickListeners for the buttons
            buttonSecondary.setOnClickListener(view -> finish());
            buttonPrimary.setOnClickListener(view -> {
                try {
                    SharedPreferences prefCurrent1 = U.getPrefCurrent(this);
                    if(!prefCurrent1.getBoolean("not_active", true))
                        U.turnOffProfile(this);
                } catch (NullPointerException e) { /* Gracefully fail */ }

                finish();
            });

            // "Don't show again" checkbox
            CheckBox checkBox = findViewById(R.id.turnOffCheckbox);
            checkBox.setVisibility(View.VISIBLE);
        } else
            finish();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(dialog)
            LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if(dialog) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);

            // Set checkbox preference
            CheckBox checkbox = findViewById(R.id.turnOffCheckbox);
            if(checkbox.isChecked()) {
                SharedPreferences prefMain = U.getPrefMain(this);
                SharedPreferences.Editor editor = prefMain.edit();
                editor.putBoolean("show_turn_off_dialog", false);
                editor.apply();
            }
        }
    }
}
