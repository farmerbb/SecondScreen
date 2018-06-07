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

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.util.U;

// This activity is responsible for informing the user that they need to reboot,
// if they have non-root mode enabled.
public final class RebootRequiredActivity extends AppCompatActivity {

    boolean rebootLater = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_turn_off);
        setTitle(R.string.reboot_required_title);
        setFinishOnTouchOutside(false);

        TextView textView = findViewById(R.id.turnOffTextView);
        textView.setText(R.string.reboot_required_message);

        Button button1 = findViewById(R.id.turnOffButtonPrimary);
        Button button2 = findViewById(R.id.turnOffButtonSecondary);

        button1.setText(R.string.action_later);
        button1.setOnClickListener(v -> {
            if(rebootLater)
                finish();
            else {
                rebootLater = true;
                U.showToast(this, R.string.confirm_reboot_later);
            }
        });

        button2.setVisibility(View.GONE);
    }

    // Disable the back button
    @Override
    public void onBackPressed() {}
}
