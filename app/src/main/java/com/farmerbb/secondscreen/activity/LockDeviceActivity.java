/* Copyright 2017 Braden Farmer
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
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.view.ContextThemeWrapper;
import android.view.View;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.receiver.LockDeviceReceiver;
import com.farmerbb.secondscreen.util.U;

public class LockDeviceActivity extends Activity {

    boolean shouldFinish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new View(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(shouldFinish)
            finish();
        else {
            shouldFinish = true;

            AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.Theme_Secondscreen_2));
            builder.setTitle(R.string.permission_needed)
                    .setMessage(R.string.device_admin_disclosure)
                    .setNegativeButton(R.string.action_cancel, (dialog, which) -> new Handler().post(this::finish))
                    .setPositiveButton(R.string.action_activate, (dialog, which) -> {
                        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, LockDeviceReceiver.class));
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description));

                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            U.showToast(this, R.string.not_compatible);

                            finish();
                        }
                    });

            AlertDialog dialog = builder.create();
            dialog.show();
            dialog.setCancelable(false);
        }
    }
}