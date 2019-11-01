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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.Display;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.HdmiActivity;
import com.farmerbb.secondscreen.activity.MainActivity;
import com.farmerbb.secondscreen.activity.TurnOffActivity;
import com.farmerbb.secondscreen.support.ServiceBinder;
import com.farmerbb.secondscreen.support.ServiceInterface;
import com.farmerbb.secondscreen.util.U;

// This is a long-running service started if the "Enable auto-start" preference is set.
// It normally takes up around 2.5MB of memory and only actively runs whenever a display is
// connected or removed.
// When a display is connected, it will launch the HdmiActivity.  When a display is removed, and a
// profile is active, it will either launch the TurnOffService directly if the auto-start action is
// set to load the currently active profile, or it will launch the TurnOffActivity otherwise.
public final class DisplayConnectionService extends Service implements ServiceInterface {

    DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
            SharedPreferences prefCurrent = U.getPrefCurrent(DisplayConnectionService.this);
            DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            Display[] displays = dm.getDisplays();

            try {
                if(displays[displays.length - 2].getDisplayId() == Display.DEFAULT_DISPLAY
                        && prefCurrent.getBoolean("not_active", true)) {
                    Intent hdmiIntent = new Intent(DisplayConnectionService.this, HdmiActivity.class);
                    hdmiIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(hdmiIntent);
                }
            } catch (ArrayIndexOutOfBoundsException e) { /* Gracefully fail */ }
        }

        @Override
        public void onDisplayChanged(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {
            Intent intent = new Intent();
            intent.setAction(U.SCREEN_DISCONNECT);
            LocalBroadcastManager.getInstance(DisplayConnectionService.this).sendBroadcast(intent);

            SharedPreferences prefCurrent = U.getPrefCurrent(DisplayConnectionService.this);
            DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            Display[] displays = dm.getDisplays();

            try {
                if(displays[displays.length - 1].getDisplayId() == Display.DEFAULT_DISPLAY
                        && !prefCurrent.getBoolean("not_active", true)) {
                    SharedPreferences prefMain = U.getPrefMain(DisplayConnectionService.this);
                    if("quick_actions".equals(prefCurrent.getString("filename", "0"))) {
                        SharedPreferences prefSaved = U.getPrefQuickActions(DisplayConnectionService.this);
                        if(prefMain.getString("hdmi_load_profile", "show_list").equals(prefSaved.getString("original_filename", "0")))
                            U.turnOffProfile(DisplayConnectionService.this);
                        else if(prefMain.getBoolean("inactive", true)) {
                            Intent turnOffIntent = new Intent(DisplayConnectionService.this, TurnOffActivity.class);
                            turnOffIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(turnOffIntent);
                        }
                    } else if(prefMain.getString("hdmi_load_profile", "show_list").equals(prefCurrent.getString("filename", "0")))
                        U.turnOffProfile(DisplayConnectionService.this);
                    else if(prefMain.getBoolean("inactive", true)) {
                        Intent turnOffIntent = new Intent(DisplayConnectionService.this, TurnOffActivity.class);
                        turnOffIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(turnOffIntent);
                    }
                }
            } catch (ArrayIndexOutOfBoundsException e) { /* Gracefully fail */ }
        }
    };

    private final ServiceBinder binder = new ServiceBinder() {
        @Override
        public ServiceInterface getService() {
            return DisplayConnectionService.this;
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        manager.registerDisplayListener(listener, null);
    }

    @Override
    public void startForeground() {
        // Intent to launch MainActivity when notification is clicked
        Intent mainActivityIntent = new Intent(this, MainActivity.class);
        PendingIntent mainActivityPendingIntent = PendingIntent.getActivity(this, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        String id = "DisplayConnectionService";

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        CharSequence name = getString(R.string.auto_start);
        int importance = NotificationManager.IMPORTANCE_MIN;

        mNotificationManager.createNotificationChannel(new NotificationChannel(id, name, importance));

        // Build the notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, id)
                .setContentIntent(mainActivityPendingIntent)
                .setSmallIcon(R.drawable.ic_action_dock)
                .setContentTitle(getString(R.string.auto_start_active))
                .setOngoing(true)
                .setShowWhen(false);

        // Set notification color on Lollipop
        mBuilder.setColor(ContextCompat.getColor(this, R.color.primary_dark))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        startForeground(2, mBuilder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getBooleanExtra("start_foreground", false))
            startForeground();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        manager.unregisterDisplayListener(listener);
    }
}
