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
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;

import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.farmerbb.secondscreen.BuildConfig;
import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.util.U;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.ShizukuProvider;
import rikka.shizuku.SystemServiceHelper;

// This activity is responsible for informing the user that SecondScreen is unable to start.
public final class UnableToStartActivity extends AppCompatActivity implements Shizuku.OnRequestPermissionResultListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Shizuku.pingBinder()) {
            proceedWithOnCreateShizuku();

            boolean isGranted;
            if(Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                isGranted = checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED;
            } else {
                isGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            }

            if(isGranted) {
                grantWriteSecureSettingsPermission();
            } else {
                int SHIZUKU_CODE = 123;
                if(Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                    requestPermissions(new String[] { ShizukuProvider.PERMISSION }, SHIZUKU_CODE);
                } else {
                    Shizuku.requestPermission(SHIZUKU_CODE);
                }
            }
        } else {
            proceedWithOnCreate();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for(int i = 0; i < permissions.length; i++) {
            String permission = permissions[i];
            int result = grantResults[i];

            if(permission.equals(ShizukuProvider.PERMISSION)) {
                onRequestPermissionResult(requestCode, result);
            }
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, int grantResult) {
        boolean isGranted = grantResult == PackageManager.PERMISSION_GRANTED;
        if(isGranted) {
            grantWriteSecureSettingsPermission();
        } else {
            proceedWithOnCreate();
        }
    }

    @SuppressLint("PrivateApi")
    private void grantWriteSecureSettingsPermission() {
        try {
            Class<?> iPmClass = Class.forName("android.content.pm.IPackageManager");
            Class<?> iPmStub = Class.forName("android.content.pm.IPackageManager$Stub");
            Method asInterfaceMethod = iPmStub.getMethod("asInterface", IBinder.class);
            Method grantRuntimePermissionMethod = iPmClass.getMethod("grantRuntimePermission", String.class, String.class, int.class);

            Object iPmInstance = asInterfaceMethod.invoke(null, new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")));
            grantRuntimePermissionMethod.invoke(iPmInstance, BuildConfig.APPLICATION_ID, android.Manifest.permission.WRITE_SECURE_SETTINGS, 0);
        } catch (Exception ignored) {}

        proceedWithProfileLoad();
    }

    private void proceedWithOnCreate() {
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
        button1.setOnClickListener(v -> proceedWithProfileLoad());

        button2.setVisibility(View.GONE);
    }

    private void proceedWithOnCreateShizuku() {
        setContentView(R.layout.activity_turn_off);
        setTitle(R.string.permission_needed);

        TextView textView = findViewById(R.id.turnOffTextView);
        textView.setText(R.string.shizuku_dialog);

        Button button1 = findViewById(R.id.turnOffButtonPrimary);
        Button button2 = findViewById(R.id.turnOffButtonSecondary);

        button1.setText(R.string.action_close);
        button1.setOnClickListener(v -> finish());

        button2.setVisibility(View.GONE);
    }

    private void proceedWithProfileLoad() {
        if(getIntent().hasExtra("action")) {
            if(U.hasElevatedPermissions(this, true)) {
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
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent("com.farmerbb.secondscreen.SHOW_DIALOGS"));

        finish();
    }

    // Disable the back button
    @Override
    public void onBackPressed() {}
}
