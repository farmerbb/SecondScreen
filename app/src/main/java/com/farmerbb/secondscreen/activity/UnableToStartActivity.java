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
import android.os.Bundle;
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

        // UnableToStartActivity requires a bundle extra "action" to be passed to it,
        // so that the activity knows what to do after permission is granted.
        if(!getIntent().hasExtra("action"))
            finish();
        else {
            setContentView(R.layout.activity_turn_off);
            setTitle(R.string.permission_needed);
            setFinishOnTouchOutside(false);

            TextView textView = (TextView) findViewById(R.id.turnOffTextView);
            textView.setText(getString(R.string.permission_dialog_message, BuildConfig.APPLICATION_ID, Manifest.permission.WRITE_SECURE_SETTINGS));

            Button button1 = (Button) findViewById(R.id.turnOffButtonPrimary);
            Button button2 = (Button) findViewById(R.id.turnOffButtonSecondary);

            button1.setText(R.string.action_ok);
            button1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(U.hasRoot(UnableToStartActivity.this)) {
                        switch(getIntent().getStringExtra("action")) {
                            case "load-profile":
                                U.loadProfile(UnableToStartActivity.this, getIntent().getStringExtra("filename"));
                                break;
                            case "turn-off-profile":
                                U.turnOffProfile(UnableToStartActivity.this);
                                break;
                        }
                    }

                    finish();
                }
            });

            button2.setVisibility(View.GONE);
        }
    }

    // Disable the back button
    @Override
    public void onBackPressed() {}
}
