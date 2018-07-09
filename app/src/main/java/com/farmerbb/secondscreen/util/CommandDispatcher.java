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

package com.farmerbb.secondscreen.util;

import android.content.Context;
import android.content.Intent;

import com.farmerbb.secondscreen.BuildConfig;

import java.util.ArrayList;
import java.util.List;

public class CommandDispatcher {

    private List<String> commands = new ArrayList<>();

    private static CommandDispatcher theInstance;

    private CommandDispatcher() {}

    public static CommandDispatcher getInstance() {
        if(theInstance == null) theInstance = new CommandDispatcher();

        return theInstance;
    }

    public boolean addCommand(Context context, String command) {
        if(U.isInNonRootMode(context) && U.hasSupportLibrary(context) && !command.isEmpty()) {
            commands.add(command);
            return true;
        }

        return false;
    }

    @SuppressWarnings("deprecation")
    void dispatch(Context context) {
        if(U.hasSupportLibrary(context)) {
            Intent intent = new Intent(BuildConfig.SUPPORT_APPLICATION_ID + ".DISPATCH_COMMANDS");
            intent.setPackage(BuildConfig.SUPPORT_APPLICATION_ID);

            if(!commands.isEmpty())
                intent.putExtra("commands", commands.toArray(new String[commands.size()]));

            int screenOrientation = U.getScreenOrientation(context);
            if(screenOrientation != -1)
                intent.putExtra("screen_orientation", screenOrientation);

            context.sendBroadcast(intent);
        }

        theInstance = null;
    }
}