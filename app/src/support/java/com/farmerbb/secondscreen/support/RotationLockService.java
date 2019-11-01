/* Copyright 2018 Braden Farmer
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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public abstract class RotationLockService extends Service implements ServiceInterface {

    WindowManager windowManager;
    View view;

    BroadcastReceiver userForegroundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            drawSystemOverlay();
        }
    };

    BroadcastReceiver userBackgroundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            removeSystemOverlay();
        }
    };

    private final ServiceBinder binder = new ServiceBinder() {
        @Override
        public ServiceInterface getService() {
            return RotationLockService.this;
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    @CallSuper
    public void onCreate() {
        final IntentFilter filter1 = new IntentFilter();
        final IntentFilter filter2 = new IntentFilter();

        filter1.addAction(Intent.ACTION_USER_FOREGROUND);
        filter2.addAction(Intent.ACTION_USER_BACKGROUND);

        registerReceiver(userForegroundReceiver, filter1);
        registerReceiver(userBackgroundReceiver, filter2);
    }

    @Override
    public void startForeground() {
        startService();
        drawSystemOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getBooleanExtra("start_foreground", false))
            startForeground();

        return START_STICKY;
    }

    @Override
    @CallSuper
    public void onDestroy() {
        unregisterReceiver(userForegroundReceiver);
        unregisterReceiver(userBackgroundReceiver);

        removeSystemOverlay();
    }

    private void drawSystemOverlay() {
        int screenOrientation = getScreenOrientation();

        // Draw system overlay, if needed
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.canDrawOverlays(this)
                && screenOrientation != -1) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    0,
                    0,
                    SupportUtils.getOverlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);

            params.gravity = Gravity.TOP | Gravity.START;
            params.screenOrientation = screenOrientation;

            view = new View(this);
            windowManager.addView(view, params);
        }
    }

    private void removeSystemOverlay() {
        if(windowManager != null && view != null)
            windowManager.removeView(view);
    }

    protected abstract void startService();
    protected abstract int getScreenOrientation();
}
