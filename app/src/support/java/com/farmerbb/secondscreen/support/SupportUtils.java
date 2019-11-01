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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.WindowManager;

public final class SupportUtils {

    private SupportUtils() {}

    public static void startService(Context oldContext, Intent intent) {
        Context context = oldContext.getApplicationContext();

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ServiceBinder binder = (ServiceBinder) service;
                ServiceInterface myService = binder.getService();

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                    myService.startForeground();
                } else
                    context.startService(intent);

                context.unbindService(this);
            }

            @Override
            public void onBindingDied(ComponentName name) {}

            @Override
            public void onNullBinding(ComponentName name) {}

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };

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
}