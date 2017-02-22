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

package com.farmerbb.secondscreen.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.support.v7.app.NotificationCompat;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.receiver.KeyboardChangeReceiver;

import java.util.Random;

public class DisableKeyboardService extends InputMethodService {

    Integer notificationId;

    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        return false;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        boolean isEditingText = attribute.inputType != InputType.TYPE_NULL;
        boolean hasHardwareKeyboard = getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS;

        if(notificationId == null && isEditingText && !hasHardwareKeyboard) {
            Intent keyboardChangeIntent = new Intent(this, KeyboardChangeReceiver.class);
            PendingIntent keyboardChangePendingIntent = PendingIntent.getBroadcast(this, 0, keyboardChangeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentIntent(keyboardChangePendingIntent)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(getString(R.string.disabling_soft_keyboard))
                    .setContentText(getString(R.string.tap_to_change_keyboards))
                    .setPriority(Notification.PRIORITY_MIN)
                    .setOngoing(true)
                    .setShowWhen(false)
                    .build();

            notificationId = new Random().nextInt();

            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(notificationId, notification);
        } else if(notificationId != null && !isEditingText) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(notificationId);

            notificationId = null;
        }
    }

    @Override
    public void onDestroy() {
        if(notificationId != null) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(notificationId);

            notificationId = null;
        }

        super.onDestroy();
    }
}
