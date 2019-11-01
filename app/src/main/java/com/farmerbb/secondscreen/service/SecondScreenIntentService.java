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
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
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
        if(intent.getBooleanExtra("start_foreground", false))
            startForeground();
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
}
