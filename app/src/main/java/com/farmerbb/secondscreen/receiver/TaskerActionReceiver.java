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

package com.farmerbb.secondscreen.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.farmerbb.secondscreen.activity.QuickLaunchActivity;
import com.farmerbb.secondscreen.activity.TaskerQuickActionsActivity;
import com.farmerbb.secondscreen.util.BundleScrubber;
import com.farmerbb.secondscreen.util.PluginBundleManager;
import com.farmerbb.secondscreen.util.PluginBundleManagerQuickActions;
import com.farmerbb.secondscreen.util.U;

// Receiver run by Tasker whenever a SecondScreen action is performed inside of a Tasker task.
// It launches either QuickLaunchActivity or TaskerQuickActionsActivity, depending on the type
// of action to be performed.
public final class TaskerActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        BundleScrubber.scrub(intent);

        final Bundle bundle = intent.getBundleExtra(com.twofortyfouram.locale.api.Intent.EXTRA_BUNDLE);
        BundleScrubber.scrub(bundle);

        if(bundle.containsKey(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE)) {
            if(PluginBundleManager.isBundleValid(bundle)) {
                String filename = bundle.getString(PluginBundleManager.BUNDLE_EXTRA_STRING_MESSAGE);

                Intent shortcutIntent = new Intent(context, QuickLaunchActivity.class);
                shortcutIntent.setAction(Intent.ACTION_MAIN);
                shortcutIntent.putExtra(U.NAME, filename);
                shortcutIntent.putExtra("tasker", true);
                shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                context.startActivity(shortcutIntent);
            }
        } else if(bundle.containsKey(PluginBundleManagerQuickActions.BUNDLE_EXTRA_STRING_KEY)
                && bundle.containsKey(PluginBundleManagerQuickActions.BUNDLE_EXTRA_STRING_VALUE)) {
            if(PluginBundleManagerQuickActions.isBundleValid(bundle)) {
                String key = bundle.getString(PluginBundleManagerQuickActions.BUNDLE_EXTRA_STRING_KEY);
                String value = bundle.getString(PluginBundleManagerQuickActions.BUNDLE_EXTRA_STRING_VALUE);

                Intent shortcutIntent = new Intent(context, TaskerQuickActionsActivity.class);
                shortcutIntent.setAction(Intent.ACTION_MAIN);
                shortcutIntent.putExtra(U.KEY, key);
                shortcutIntent.putExtra(U.VALUE, value);
                shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                context.startActivity(shortcutIntent);
            }
        }
    }
}