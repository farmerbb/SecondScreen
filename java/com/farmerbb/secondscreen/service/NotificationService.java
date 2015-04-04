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

package com.farmerbb.secondscreen.service;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.view.Display;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.MainActivity;
import com.farmerbb.secondscreen.activity.TaskerQuickActionsActivity;
import com.farmerbb.secondscreen.activity.TurnOffActivity;
import com.farmerbb.secondscreen.util.U;

// The NotificationService is started whenever a profile is active, whether it be a user-created
// profile or a temporary one created through Quick Actions.  In addition to generating and showing
// a notification, the NotificationService is responsible for detecting when the screen is turned
// off and back on,launching either the TempBacklightOnService or ScreenOnService to control the
// backlight.  It will also temporarily restore the backlight if a display is connected or
// disconnected while a profile is active.  Lastly, it is responsible for showing the
// TurnOffActivity when the DisplayConnectionService is not running.
public final class NotificationService extends Service {

    Notification.Builder mBuilder;

    BroadcastReceiver screenOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefMain = U.getPrefMain(NotificationService.this);
            SharedPreferences.Editor editor = prefMain.edit();
            editor.putLong("screen_on_time", System.currentTimeMillis());
            editor.apply();

            if(U.castScreenActive(NotificationService.this)) {
                Intent serviceIntent = new Intent(context, TempBacklightOnService.class);
                context.startService(serviceIntent);
            } else {
                Intent serviceIntent = new Intent(context, ScreenOnService.class);
                context.startService(serviceIntent);
            }
        }
    };

    BroadcastReceiver userPresentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SharedPreferences prefMain = U.getPrefMain(NotificationService.this);
            long screenOnTime = prefMain.getLong("screen_on_time", 0);

            SharedPreferences.Editor editor = prefMain.edit();
            editor.remove("screen_on_time");
            editor.apply();

            if(U.castScreenActive(NotificationService.this)
                    || screenOnTime < (System.currentTimeMillis() - 5000)) {
                Intent serviceIntent = new Intent(context, ScreenOnService.class);
                context.startService(serviceIntent);
            }
        }
    };

    DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            Intent intent = new Intent();
            intent.setAction(U.SCREEN_CONNECT);
            sendBroadcast(intent);

            DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            Display[] displays = dm.getDisplays();

            if(displays[displays.length - 2].getDisplayId() == Display.DEFAULT_DISPLAY) {
                Intent serviceIntent = new Intent(NotificationService.this, ScreenOnService.class);
                startService(serviceIntent);
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {
            DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            Display[] displays = dm.getDisplays();

            if(displays[displays.length - 1].getDisplayId() == Display.DEFAULT_DISPLAY) {
                Intent serviceIntent = new Intent(NotificationService.this, TempBacklightOnService.class);
                startService(serviceIntent);

                SharedPreferences prefMain = U.getPrefMain(NotificationService.this);
                ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                boolean displayConnectionServiceRunning = false;

                for(ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                    if(DisplayConnectionService.class.getName().equals(service.service.getClassName()))
                        displayConnectionServiceRunning = true;
                }

                if(prefMain.getBoolean("inactive", true) && !displayConnectionServiceRunning) {
                    Intent turnOffIntent = new Intent(NotificationService.this, TurnOffActivity.class);
                    turnOffIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(turnOffIntent);
                }
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate() {
        // Load preferences
        SharedPreferences prefCurrent = U.getPrefCurrent(this);
        SharedPreferences prefMain = U.getPrefMain(this);

        // Register broadcast receivers for screen on and user present
        final IntentFilter filter1 = new IntentFilter();
        final IntentFilter filter2 = new IntentFilter();

        filter1.addAction(Intent.ACTION_SCREEN_ON);
        filter1.addAction(Intent.ACTION_DREAMING_STARTED);
        filter2.addAction(Intent.ACTION_USER_PRESENT);

        registerReceiver(screenOnReceiver, filter1);
        registerReceiver(userPresentReceiver, filter2);

        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        manager.registerDisplayListener(listener, null);

        // Intent to launch MainActivity when notification is clicked
        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        PendingIntent mainActivityPendingIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Build the notification
        mBuilder = new Notification.Builder(this)
                .setContentIntent(mainActivityPendingIntent)
                .setSmallIcon(R.drawable.ic_action_dock)
                .setContentTitle(getResources().getString(R.string.notification))
                .setContentText(prefCurrent.getString("profile_name", getResources().getString(R.string.action_new)))
                .setOngoing(true);

        // Set action buttons
        setActionButton(prefMain.getString("notification_action_2", "turn-off"), prefCurrent, 0);
        setActionButton(prefMain.getString("notification_action", "lock-device"), prefCurrent, 1);

        // Respect setting to hide notification
        if(prefMain.getBoolean("hide_notification", false))
            mBuilder.setPriority(Notification.PRIORITY_MIN);

        // Set notification color on Lollipop
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setColor(getResources().getColor(R.color.primary_dark))
                    .setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        // Start NotificationService
        startForeground(1, mBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenOnReceiver);
        unregisterReceiver(userPresentReceiver);

        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        manager.unregisterDisplayListener(listener);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    private void setActionButton(String key, SharedPreferences prefCurrent, int code) {
        Intent customIntent;
        PendingIntent customPendingIntent = null;
        int customIcon = 0;
        String customString = null;

        if(key.equals("turn-off")) {
            // Turn Off
            customIntent = new Intent(this, TurnOffActivity.class);
            customIntent.putExtra("notification", true);
            customPendingIntent = PendingIntent.getActivity(this, code, customIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            customIcon = R.drawable.ic_action_remove;
            customString = getResources().getStringArray(R.array.pref_notification_action_list)[0];
        } else if(key.equals("lock-device")) {
            // Lock Device
            customIntent = new Intent(this, LockDeviceService.class);
            customPendingIntent = PendingIntent.getService(this, code, customIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            customIcon = R.drawable.ic_action_secure;
            customString = getResources().getStringArray(R.array.pref_notification_action_list)[2];
        } else if(key.equals("quick-actions")) {
            // Quick Actions
            customIntent = new Intent(this, TaskerQuickActionsActivity.class);
            customIntent.putExtra("launched-from-app", true);
            customPendingIntent = PendingIntent.getActivity(this, code, customIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                customIcon = R.drawable.ic_action_forward_light;
            else
                customIcon = R.drawable.ic_action_forward;

            customString = getResources().getStringArray(R.array.pref_notification_action_list)[1];
        } else if(key.startsWith("temp_")) {
            // Toggle
            customIntent = new Intent(this, TaskerQuickActionsActivity.class);
            customIntent.putExtra(U.KEY, key);
            customIntent.putExtra(U.VALUE, "Toggle");
            customPendingIntent = PendingIntent.getActivity(this, code, customIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            String onOffString;

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                customIcon = R.drawable.shadow;

            switch(key) {
                case "temp_backlight_off":
                    if(prefCurrent.getBoolean("backlight_off", false))
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[0];
                    else
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[1];

                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        customIcon = R.drawable.ic_action_brightness_7;

                    customString = getResources().getString(R.string.quick_backlight) + onOffString;
                    break;
                case "temp_chrome":
                    if(prefCurrent.getBoolean("chrome", false))
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[1];
                    else
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[0];

                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        customIcon = R.drawable.ic_action_web;

                    customString = getResources().getString(R.string.desktop) + onOffString;
                    break;
                case "temp_immersive":
                    if(prefCurrent.getBoolean("immersive", false))
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[1];
                    else
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[0];

                    customIcon = R.drawable.ic_action_immersive;
                    customString = getResources().getString(R.string.immersive) + onOffString;
                    break;
                case "temp_overscan":
                    if(prefCurrent.getBoolean("overscan", false))
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[1];
                    else
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[0];

                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        customIcon = R.drawable.ic_action_settings_overscan;

                    customString = getResources().getString(R.string.quick_overscan) + onOffString;
                    break;
                case "temp_vibration_off":
                    if(prefCurrent.getBoolean("vibration_off", false))
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[0];
                    else
                        onOffString = " " + getResources().getStringArray(R.array.pref_quick_actions)[1];

                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        customIcon = R.drawable.ic_action_vibration;

                    customString = getResources().getString(R.string.quick_vibration) + onOffString;
                    break;
            }
        }

        // Add action to notification builder
        mBuilder.addAction(customIcon, customString, customPendingIntent);
    }
}
