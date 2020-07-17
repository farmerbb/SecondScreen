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

package com.farmerbb.secondscreen.service;

import android.annotation.TargetApi;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.support.ServiceBinder;
import com.farmerbb.secondscreen.support.ServiceInterface;

import java.util.Random;

public abstract class SecondScreenIntentService extends IntentService implements ServiceInterface {

    public SecondScreenIntentService(String name) {
        super(name);
    }

    private final ServiceBinder binder = new ServiceBinder() {
        @Override
        public ServiceInterface getService() {
            return SecondScreenIntentService.this;
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    @CallSuper
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (checkNetwork()) {
            if(intent.getBooleanExtra("start_foreground", false))
                startForeground();
        }
        else {
            NetworkStateReceiver.enable(getApplicationContext());
        }
    }

    @Override
    public void startForeground() {
        String id = "SecondScreenIntentService";

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        CharSequence name = getString(R.string.background_operations);
        int importance = NotificationManager.IMPORTANCE_MIN;

        mNotificationManager.createNotificationChannel(new NotificationChannel(id, name, importance));

        // Build the notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, id)
                .setSmallIcon(R.drawable.ic_action_dock)
                .setContentTitle(getString(R.string.background_operations_active))
                .setOngoing(true)
                .setShowWhen(false);

        // Set notification color on Lollipop
        mBuilder.setColor(ContextCompat.getColor(this, R.color.primary_dark))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        startForeground(new Random().nextInt(), mBuilder.build());
    }

    @TargetApi(Build.VERSION_CODES.O)
    public Notification getNotification() {
        String id = "SecondScreenIntentService";

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        CharSequence name = getString(R.string.background_operations);
        int importance = NotificationManager.IMPORTANCE_MIN;

        mNotificationManager.createNotificationChannel(new NotificationChannel(id, name, importance));

        // Build the notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, id)
                .setSmallIcon(R.drawable.ic_action_dock)
                .setContentTitle(getString(R.string.background_operations_active))
                .setOngoing(true)
                .setShowWhen(false);

        // Set notification color on Lollipop
        mBuilder.setColor(ContextCompat.getColor(this, R.color.primary_dark))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return mBuilder.build();
    }

    boolean checkNetwork() {
        final ConnectivityManager connManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = connManager.getActiveNetwork();
        if (activeNetwork != null) {
            return true;
        }
        return false;
    }

    public static class NetworkStateReceiver extends BroadcastReceiver {
        private static final String TAG = NetworkStateReceiver.class.getName();

        private static SecondScreenIntentService service;

        public static void setService(SecondScreenIntentService newService) {
            service = newService;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            if (service.checkNetwork()) {
                NetworkStateReceiver.disable(context);

                final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

                final Intent innerIntent = new Intent(context, SecondScreenIntentService.class);
                final PendingIntent pendingIntent = PendingIntent.getService(context, 0, innerIntent, 0);

                SharedPreferences preferences = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
                preferences.edit();
                boolean autoRefreshEnabled = preferences.getBoolean("pref_auto_refresh_enabled", false);

                final String hours = preferences.getString("pref_auto_refresh_enabled", "0");
                long hoursLong = Long.parseLong(hours) * 60 * 60 * 1000;

                if (autoRefreshEnabled && hoursLong != 0) {

                    final long alarmTime = preferences.getLong("last_auto_refresh_time", 0) + hoursLong;
                    alarmManager.set(AlarmManager.RTC, alarmTime, pendingIntent);

                } else {

                    alarmManager.cancel(pendingIntent);
                }
            }
        }

        public static void enable(Context context) {
            final PackageManager packageManager = context.getPackageManager();
            final ComponentName receiver = new ComponentName(context, NetworkStateReceiver.class);
            packageManager.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }

        public static void disable(Context context) {
            final PackageManager packageManager = context.getPackageManager();
            final ComponentName receiver = new ComponentName(context, NetworkStateReceiver.class);
            packageManager.setComponentEnabledSetting(receiver, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }
}
