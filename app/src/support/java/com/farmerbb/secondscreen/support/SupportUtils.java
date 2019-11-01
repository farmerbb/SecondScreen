/* Copyright 2019 Braden Farmer
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

package com.farmerbb.secondscreen.support;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;

public final class SupportUtils {

    private SupportUtils() {}

    public static void startService(Context oldContext, Intent intent) {
        Context context = oldContext.getApplicationContext();

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                // The binder of the service that returns the instance that is created.
                ServiceBinder binder = (ServiceBinder) service;

                // The getter method to acquire the service.
                ServiceInterface myService = binder.getService();

                // getServiceIntent(context) returns the relative service intent
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                    myService.startForeground();
                } else
                    context.startService(intent);

                // Release the connection to prevent leaks.
                context.unbindService(this);
            }

            @Override
            public void onBindingDied(ComponentName name) {}

            @Override
            public void onNullBinding(ComponentName name) {}

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };

        // Try to bind the service
        try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException ignored) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra("start_foreground", true);
                new Handler().post(() -> context.startForegroundService(intent));
            } else
                context.startService(intent);
        }
    }

    public static int getOverlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
    }

    public static boolean isDesktopModeActive(Context context) {
        boolean desktopModePrefEnabled;

        try {
            desktopModePrefEnabled = Settings.Global.getInt(context.getContentResolver(), "force_desktop_mode_on_external_displays") == 1;
        } catch (Settings.SettingNotFoundException e) {
            desktopModePrefEnabled = false;
        }

        return desktopModePrefEnabled && getExternalDisplayID(context) != Display.DEFAULT_DISPLAY;
    }

    public static int getExternalDisplayID(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();

        return displays[displays.length - 1].getDisplayId();
    }
}