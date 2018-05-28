/* Copyright 2016 Braden Farmer
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

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.farmerbb.secondscreen.BuildConfig;
import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.util.U;

// This activity is responsible for informing the user that SecondScreen is unable to start.
public final class UnableToStartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_turn_off);
        setTitle(R.string.permission_needed);
        setFinishOnTouchOutside(false);

        TextView textView = findViewById(R.id.turnOffTextView);
        textView.setText(R.string.permission_dialog_message);

        TextView adbShellCommand = findViewById(R.id.adb_shell_command);
        adbShellCommand.setVisibility(View.VISIBLE);
        adbShellCommand.setText(getString(R.string.adb_shell_command, BuildConfig.APPLICATION_ID, Manifest.permission.WRITE_SECURE_SETTINGS));

        Button button1 = findViewById(R.id.turnOffButtonPrimary);
        Button button2 = findViewById(R.id.turnOffButtonSecondary);

        button1.setText(R.string.action_continue);
        button1.setOnClickListener(v -> {
            if(getIntent().hasExtra("action")) {
                if(U.hasElevatedPermissions(this)) {
                    switch(getIntent().getStringExtra("action")) {
                        case "load-profile":
                            U.loadProfile(this, getIntent().getStringExtra("filename"));
                            break;
                        case "turn-off-profile":
                            U.turnOffProfile(this);
                            break;
                    }
                }
            } else
                LocalBroadcastManager.getInstance(UnableToStartActivity.this)
                        .sendBroadcast(new Intent("com.farmerbb.secondscreen.SHOW_DIALOGS"));

            finish();
        });

        button2.setVisibility(View.GONE);
    }

    // Disable the back button
    @Override
    public void onBackPressed() {}
}
