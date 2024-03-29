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

package com.farmerbb.secondscreen.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.farmerbb.secondscreen.BuildConfig;
import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.activity.DummyLauncherActivity;
import com.farmerbb.secondscreen.activity.LockDeviceActivity;
import com.farmerbb.secondscreen.activity.MainActivity;
import com.farmerbb.secondscreen.activity.RebootRequiredActivity;
import com.farmerbb.secondscreen.activity.TaskerQuickActionsActivity;
import com.farmerbb.secondscreen.activity.WriteSettingsPermissionActivity;
import com.farmerbb.secondscreen.receiver.LockDeviceReceiver;
import com.farmerbb.secondscreen.service.ProfileLoadService;
import com.farmerbb.secondscreen.service.TurnOffService;
import com.farmerbb.secondscreen.support.NonRootUtils;
import com.farmerbb.secondscreen.support.SupportUtils;

import org.apache.commons.lang3.math.NumberUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

// Utility class to store common methods and objects shared between multiple classes
public final class U {

    private U() {}

    // Intents and extras
    // NOTE: these intents are only sent in certain scenarios and should not be relied upon by third-party apps

    // Re-lists the profiles in ProfileListActivity
    public static final String LIST_PROFILES = "com.farmerbb.secondscreen.LIST_PROFILES";

    // Sent when SecondScreen detects a screen connection
    public static final String SCREEN_CONNECT = "com.farmerbb.secondscreen.SCREEN_CONNECT";

    // Sent when SecondScreen detects a screen disconnection
    public static final String SCREEN_DISCONNECT = "com.farmerbb.secondscreen.SCREEN_DISCONNECT";

    // Used by debug mode
    public static final String SIMULATE_REBOOT = "com.farmerbb.secondscreen.SIMULATE_REBOOT";
    public static final String SIMULATE_APP_UPGRADE = "com.farmerbb.secondscreen.SIMULATE_APP_UPGRADE";

    // Extras for intents sent via homescreen shortcuts or Tasker
    public static final String NAME = "com.farmerbb.secondscreen.NAME";
    public static final String KEY = "com.farmerbb.secondscreen.KEY";
    public static final String VALUE = "com.farmerbb.secondscreen.VALUE";

    // Path to the Chrome command line file
    private static final String CHROME_COMMAND_LINE = "eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE)
            ? "/data/local/tmp/chrome-command-line"
            : "/data/local/chrome-command-line";

    // Arrays of sysfs files that turn the backlight or vibration off.
    // Add new files to the end of the respective array to add backlight/vibration off support to a device

    public static final File[] backlightOff = {
            new File("/sys/class/leds/lcd-backlight", "brightness"),
            new File("/sys/class/backlight/panel0-backlight", "brightness"),
            new File("/sys/class/backlight/pwm-backlight", "brightness"),
            new File("/sys/class/backlight/intel_backlight", "brightness"),
            new File("/sys/class/backlight/tegra-dsi-backlight.0", "brightness"),
            new File("/sys/devices/platform/i2c-gpio.24/i2c-24/24-002c/backlight/panel", "brightness")};

    public static final File[] vibrationOff = {
            new File("/sys/class/timed_output/vibrator", "amp"),
            new File("/sys/drv2605", "rtp_strength")};

    // Tests if backlight/vibration off files exist
    public static boolean filesExist(File[] array) {
        boolean exists = false;

        for(File file : array) {
            if(file.exists())
                exists = true;
        }

        return exists;
    }

    // Superuser commands

    // Commands for features with boolean values.
    // "true" to turn a feature on, "false" to turn it off
    private static final String navbarCommand = "settings put secure dev_force_show_navbar ";
    public static String navbarCommand(boolean checked) {
        if(checked)
            return navbarCommand + "1";
        else
            return navbarCommand + "0";
    }

    private static final String showTouchesCommand = "settings put system show_touches ";
    public static String showTouchesCommand(boolean checked) {
        if(checked)
            return showTouchesCommand + "1";
        else
            return showTouchesCommand + "0";
    }

    private static final String daydreamsCommand = "settings put secure screensaver_enabled ";
    public static String daydreamsCommand(boolean checked) {
        if(checked)
            return daydreamsCommand + "1";
        else
            return daydreamsCommand + "0";
    }

    private static final String daydreamsChargingCommand = "settings put secure screensaver_activate_on_sleep ";
    public static String daydreamsChargingCommand(boolean checked) {
        if(checked)
            return daydreamsChargingCommand + "1";
        else
            return daydreamsChargingCommand + "0";
    }

    private static final String freeformCommand = "settings put global enable_freeform_support ";
    public static String freeformCommand(boolean checked) {
        if(checked)
            return freeformCommand + "1";
        else
            return freeformCommand + "0";
    }

    // Non-boolean commands.  Most of these take a variable value either as an argument to the method,
    // or by tacking the argument onto the end of the string.
    public static final String chromeCommandRemove = "rm " + CHROME_COMMAND_LINE;
    public static final String rotationCommand = "am broadcast -a android.intent.action.DOCK_EVENT --ei android.intent.extra.DOCK_STATE ";
    public static final String rotationPrePostCommands = "settings put secure screensaver_activate_on_dock ";
    public static final String stayOnCommand = "settings put global stay_on_while_plugged_in ";
    public static final String timeoutCommand = "settings put secure lock_screen_lock_after_timeout ";
    public static final String hdmiRotationCommand = "setprop persist.demo.hdmirotation ";
    public static final String setHomeActivityCommand = "cmd package set-home-activity ";

    public static String wifiCommand(boolean enabled) {
        return "svc wifi " + (enabled ? "enable" : "disable");
    }

    public static String safeModeSizeCommand(String args) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return "settings put global display_size_forced " + args;
        else
            return "settings put secure display_size_forced " + args;
    }

    public static String safeModeDensityCommand(String args) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return "settings put global display_density_forced " + args;
        else
            return "settings put secure display_density_forced " + args;
    }

    public static String sizeCommand(Context context, String args) {
        if(isDesktopModeActive(context))
            return "wm size " + args + " -d " + getExternalDisplayID(context);
        else if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1)
            return "wm size " + args;
        else
            return "am display-size " + args;
    }

    public static String densityCommand(Context context, String args) {
        if(isDesktopModeActive(context))
            return "wm density " + args + " -d " + getExternalDisplayID(context);
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1)
            return "wm density " + args;
        else
            return "am display-density " + args;
    }

    public static String overscanCommand(Context context, String args) {
        if(isDesktopModeActive(context))
            return "wm overscan " + args + " -d " + getExternalDisplayID(context);
        else
            return "wm overscan " + args;
    }

    public static String chromeCommand(Context context) {
        return "echo 'chrome --user-agent=\"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + getChromeVersion(context) + " Safari/537.36\"' > " + CHROME_COMMAND_LINE + " && chmod 644 " + CHROME_COMMAND_LINE;
    }

    public static String chromeCommand2(Context context) {
        return "am force-stop " + getChromePackageName(context);
    }

    public static String immersiveCommand(String pref) {
        String returnCommand = "settings put global policy_control ";

        switch(pref) {
            case "status-only":
                returnCommand = returnCommand + "immersive.navigation=*";
                break;
            case "immersive-mode":
                returnCommand = returnCommand + "immersive.full=*";
                break;
            case "do-nothing":
                returnCommand = returnCommand + "null";
                break;
        }

        return returnCommand;
    }

    public static String uiRefreshCommand(Context context, boolean restartActivityManager) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> pids = am.getRunningAppProcesses();
        int processid = 0;

        if(restartActivityManager) {
            // Kill surfaceflinger if on a Jelly Bean device; run "am restart" if on KitKat or later
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                for(ActivityManager.RunningAppProcessInfo process : pids) {
                    if(process.processName.equalsIgnoreCase("/system/bin/surfaceflinger"))
                        processid = process.pid;
                }

                return "sleep 1 && kill " + processid;
            } else
                return "sleep 1 && am restart";
        } else {
            // Get SystemUI pid
            for(ActivityManager.RunningAppProcessInfo process : pids) {
                if(process.processName.equalsIgnoreCase("com.android.systemui"))
                    processid = process.pid;
            }

            // Starting with 5.1.1 LMY48I, RunningAppProcessInfo no longer returns valid data,
            // which means we won't be able to use the "kill" command with the pid of SystemUI.
            // Thus, if the SystemUI pid gets returned as 0, we need to use the "pkill" command
            // instead, and hope that the user has that command available on their device.
            // Starting with 7.0, pkill doesn't work, so use "kill" and "pidof" instead.
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                return "sleep 2 && kill `pidof com.android.systemui`";
            else if(processid == 0)
                return "sleep 2 && pkill com.android.systemui";
            else
                return "sleep 2 && kill " + processid;
        }
    }

    public static String uiRefreshCommand2(Context context, boolean shouldClearHome) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        // For better reliability, we execute the UI refresh while on the home screen
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if(!shouldClearHome) {
            try {
                context.startActivity(homeIntent);
            } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
        }

        // Kill all background processes, in order to fully refresh UI
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(0);
        for(ApplicationInfo packageInfo : packages) {
            if(!packageInfo.packageName.equalsIgnoreCase(context.getPackageName()))
                am.killBackgroundProcesses(packageInfo.packageName);
        }

        // Get launcher package name
        final ResolveInfo mInfo = pm.resolveActivity(homeIntent, 0);
        final String launcherPackageName = mInfo.activityInfo.applicationInfo.packageName;

        if(launcherPackageName.equals(getTaskbarPackageName(context))
                || launcherPackageName.equals("android"))
            return "sleep 1";
        else
            return "sleep 1 && am force-stop " + launcherPackageName;
    }

    // Runs checks to determine if size or density commands need to be run.
    // Don't run these commands if we don't need to.
    public static boolean runSizeCommand(Context context, String requestedRes) {
        if(isDesktopModeActive(context))
            return true;

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        disp.getRealMetrics(metrics);

        SharedPreferences prefMain = getPrefMain(context);
        String currentRes = " ";
        String nativeRes = prefMain.getInt("width", 0)
                + "x"
                + prefMain.getInt("height", 0);

        if(prefMain.getBoolean("debug_mode", false)) {
            SharedPreferences prefCurrent = getPrefCurrent(context);
            currentRes = prefCurrent.getString("size", "reset");

            if("reset".equals(currentRes))
                currentRes = nativeRes;
        } else {
            if((context.getApplicationContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && !prefMain.getBoolean("landscape", false))
                    || (context.getApplicationContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && prefMain.getBoolean("landscape", false))) {
                currentRes = metrics.widthPixels
                        + "x"
                        + metrics.heightPixels;
            } else if((context.getApplicationContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && !prefMain.getBoolean("landscape", false))
                    || (context.getApplicationContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && prefMain.getBoolean("landscape", false))) {
                currentRes = metrics.heightPixels
                        + "x"
                        + metrics.widthPixels;
            }
        }

        if(requestedRes.equals("reset"))
            requestedRes = nativeRes;

        return !requestedRes.equals(currentRes);
    }

    public static boolean runDensityCommand(Context context, String requestedDpi) {
        if(isDesktopModeActive(context))
            return true;

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        disp.getRealMetrics(metrics);

        SharedPreferences prefMain = getPrefMain(context);
        String currentDpi;
        String nativeDpi = Integer.toString(getSystemProperty("ro.sf.lcd_density", prefMain.getInt("density", 0)));

        if(prefMain.getBoolean("debug_mode", false)) {
            SharedPreferences prefCurrent = getPrefCurrent(context);
            currentDpi = prefCurrent.getString("density", "reset");

            if("reset".equals(currentDpi))
                currentDpi = nativeDpi;
        } else
            currentDpi = Integer.toString(metrics.densityDpi);

        if(requestedDpi.equals("reset"))
            requestedDpi = nativeDpi;

        return !requestedDpi.equals(currentDpi);
    }

    // Methods used to retrieve SharedPreferences objects

    // XML file containing the main application preferences; normally set to the MainActivity preferences file.
    public static SharedPreferences getPrefMain(Context context) {
        SharedPreferences prefMain;
        if(context.getPackageName().equals("com.farmerbb.secondscreen"))
            prefMain = context.getSharedPreferences(MainActivity.class.getName().replace("com.farmerbb.secondscreen.", ""), Context.MODE_PRIVATE);
        else
            prefMain = context.getSharedPreferences(MainActivity.class.getName(), Context.MODE_PRIVATE);

        return prefMain;
    }

    // XML file containing the current state of any running profiles
    public static SharedPreferences getPrefCurrent(Context context) {
        return getPrefSaved(context, "current");
    }

    // XML file containing the current Quick Actions temporary profile
    public static SharedPreferences getPrefQuickActions(Context context) {
        return getPrefSaved(context, "quick_actions");
    }

    // XML file containing any changes to profiles that have not yet been saved;
    // normally set to the global application preferences file due to the way PreferenceActivity works.
    public static SharedPreferences getPrefNew(Context context) {
        return getPrefSaved(context, context.getPackageName() + "_preferences");
    }

    // Gets any XML file corresponding to a saved profile filename.
    public static SharedPreferences getPrefSaved(Context context, String filename) {
        return context.getSharedPreferences(filename, Context.MODE_PRIVATE);
    }

    // Methods used for generating the list of saved profiles

    // Returns list of filenames in /data/data/com.farmerbb.secondscreen/files/, plus a fake entry for "Turn Off"
    private static String[] getListOfProfiles(File file) {
        return file.list();
    }

    private static String[] getListOfProfiles(File file, String fakeEntry) {
        String[] list = file.list();
        String[] realList = new String[list.length + 1];

        System.arraycopy(list, 0, realList, 0, list.length);

        realList[list.length] = fakeEntry;

        return realList;
    }

    // Returns an integer with number of files in /data/data/com.farmerbb.secondscreen/files/
    public static int getNumOfFiles(File file) {
        return new File(file.getPath()).list().length;
    }

    // Loads first line of a profile for display in the ListView
    public static String getProfileTitle(Context context, String filename) throws IOException {
        // Open the file on disk
        FileInputStream input = context.openFileInput(filename);
        InputStreamReader reader = new InputStreamReader(input);
        BufferedReader buffer = new BufferedReader(reader);

        // Load the file
        String line = buffer.readLine();

        // Close file on disk
        reader.close();

        return(line);
    }

    // Gathers list of profile names and their corresponding filenames, optionally with fake entries
    public static String[][] listProfiles(Context context) {
        return listProfiles(context, false, null, 0);
    }

    public static String[][] listProfiles(Context context, String fakeEntryValue, int fakeEntryTitle) {
        return listProfiles(context, true, fakeEntryValue, fakeEntryTitle);
    }

    private static String[][] listProfiles(Context context, boolean fakeEntry, String fakeEntryValue, int fakeEntryTitle) {
        // Get number of files
        int numOfFiles = getNumOfFiles(context.getFilesDir());
        int numOfProfiles = numOfFiles;

        // Get array of filenames
        String[] listOfFiles;
        ArrayList<String> listOfProfiles = new ArrayList<>();

        if(fakeEntry)
            listOfFiles = getListOfProfiles(context.getFilesDir(), fakeEntryValue);
        else
            listOfFiles = getListOfProfiles(context.getFilesDir());

        // Remove any files from the list that aren't profiles
        for(int i = 0; i < numOfFiles; i++) {
            if(isValidFilename(listOfFiles[i]) && !listOfFiles[i].equals(fakeEntryValue))
                listOfProfiles.add(listOfFiles[i]);
            else
                numOfProfiles--;
        }

        if(numOfProfiles == 0)
            return null;
        else {
            // Get "fake" number of files, if applicable
            int fakeNumOfProfiles;
            if(fakeEntry)
                fakeNumOfProfiles = numOfProfiles + 1;
            else
                fakeNumOfProfiles = numOfProfiles;

            // Create arrays of profile lists
            String[] listOfProfilesByDate = new String[fakeNumOfProfiles];
            String[] listOfProfilesByName = new String[fakeNumOfProfiles];

            String[] listOfTitlesByDate = new String[fakeNumOfProfiles];
            String[] listOfTitlesByName = new String[fakeNumOfProfiles];

            for(int i = 0; i < numOfProfiles; i++) {
                listOfProfilesByDate[i] = listOfProfiles.get(i);
            }

            // Get array of first lines of each profile
            for(int i = 0; i < numOfProfiles; i++) {
                try {
                    listOfTitlesByDate[i] = getProfileTitle(context, listOfProfilesByDate[i]);
                } catch (IOException e) {
                    showToast(context, R.string.error_loading_list);
                }
            }

            // Add fake entry, if applicable
            if(fakeEntry)
                listOfTitlesByDate[numOfProfiles] = " " + context.getResources().getString(R.string.bullet) + " " + context.getResources().getString(fakeEntryTitle) + " " + context.getResources().getString(R.string.bullet);

            // Sort alphabetically
            // Copy titles array
            System.arraycopy(listOfTitlesByDate, 0, listOfTitlesByName, 0, fakeNumOfProfiles);

            // Sort titles
            Arrays.sort(listOfTitlesByName, (s, t1) -> Collator.getInstance().compare(s, t1));

            // Initialize profiles array
            for(int i = 0; i < fakeNumOfProfiles; i++)
                listOfProfilesByName[i] = "new";

            // Copy filenames array with new sort order of titles and nullify date arrays
            for(int i = 0; i < fakeNumOfProfiles; i++) {
                for(int j = 0; j < fakeNumOfProfiles; j++) {
                    if(listOfTitlesByName[i].equals(listOfTitlesByDate[j]) && listOfProfilesByName[i].equals("new")) {
                        listOfProfilesByName[i] = listOfProfilesByDate[j];
                        listOfProfilesByDate[j] = "";
                        listOfTitlesByDate[j] = "";
                    }
                }
            }

            if(fakeEntry) listOfProfilesByName[0] = fakeEntryValue;

            return new String[][] {listOfProfilesByName, listOfTitlesByName};
        }
    }

    // Sends broadcast to refresh list of profiles
    public static void listProfilesBroadcast(Context context) {
        Intent listProfilesIntent = new Intent();
        listProfilesIntent.setAction(LIST_PROFILES);
        LocalBroadcastManager.getInstance(context).sendBroadcast(listProfilesIntent);
    }

    // Miscellaneous utility methods

    // Checks if elevated permissions are available.
    // If debug mode is enabled, the app acts as if elevated permissions are always available.
    public static boolean hasElevatedPermissions(Context context) {
        return hasElevatedPermissions(context, false);
    }

    public static boolean hasElevatedPermissions(Context context, boolean forceRecheck) {
        return Superuser.getInstance().available(forceRecheck)
                || NonRootUtils.hasWriteSecureSettingsPermission(context)
                || getPrefMain(context).getBoolean("debug_mode", false);
    }

    // Checks if SecondScreen is running in non-root mode.
    public static boolean isInNonRootMode(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !(Superuser.getInstance().available()
                || getPrefMain(context).getBoolean("debug_mode", false));
    }

    // Executes multiple commands, either by calling superuser
    // or by writing to the settings database directly.
    public static void runCommands(Context context, String[] commands, boolean rebootRequired) {
        boolean arrayIsEmpty = true;
        for(String command : commands) {
            if(command != null && !command.equals("")) {
                arrayIsEmpty = false;
                break;
            }
        }

        boolean runAsRoot = Superuser.getInstance().available()
                || getPrefMain(context).getBoolean("debug_mode", false);

        if(!arrayIsEmpty) {
            if(runAsRoot) {
                for(String command : commands) {
                    if(!command.isEmpty()) {
                        runSuCommands(context, commands);
                        break;
                    }
                }
            } else
                NonRootUtils.runCommands(context, commands);
        }

        CommandDispatcher.getInstance().dispatch(context);

        if(!runAsRoot && rebootRequired) {
            SharedPreferences prefCurrent = getPrefCurrent(context);
            if(!prefCurrent.getBoolean("not_active", true))
                prefCurrent.edit().putBoolean("reboot_required", true).apply();

            Intent intent = new Intent(context, RebootRequiredActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    // Executes a single command.
    public static void runCommand(Context context, String command) {
        runCommands(context, new String[]{command}, false);
    }

    // Executes multiple superuser commands.
    // If debug mode is enabled, the command is not actually run; instead, this will show a
    // notification containing the command that would have been run instead.
    private static void runSuCommands(Context context, String[] commands) {
        if(getPrefMain(context).getBoolean("debug_mode", false)) {
            StringBuilder dump = new StringBuilder();

            for(String command : commands) {
                if(!command.equals(""))
                    dump.append(context.getResources().getString(R.string.bullet))
                            .append(" ").append(command).append("\n");
            }

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            String id = "debug_mode";

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CharSequence name = context.getString(R.string.debug_mode);
                int importance = NotificationManager.IMPORTANCE_LOW;

                nm.createNotificationChannel(new NotificationChannel(id, name, importance));
            }

            Notification notification = new NotificationCompat.Builder(context, id)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle(context.getResources().getString(R.string.debug_mode_enabled))
                    .setContentText(dump.toString())
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(dump.toString()))
                    .build();

            nm.notify(new Random().nextInt(), notification);

            // Some devices (Android TV) don't show notifications, so let's also print the commands
            // to the log just in case.
            System.out.println(dump);
        } else
            Superuser.getInstance().run(commands);
    }

    // Loads a profile with the given filename
    public static void loadProfile(Context context, String filename) {
        SharedPreferences prefSaved = getPrefSaved(context, filename);
        SharedPreferences prefMain = getPrefMain(context);

        String requestedRes = prefSaved.getString("size", "reset");
        String requestedDpi = prefSaved.getString("density", "reset");

        // Check to see if the user is trying to load a profile with a blacklisted resolution/DPI combo
        boolean blacklisted = isBlacklisted(context, requestedRes, requestedDpi);

        if(blacklisted && !prefMain.getBoolean("expert_mode", false)) {
            showToastLong(context, R.string.blacklisted);
            return;
        }
        
        // Initialize the support library
        if(hasSupportLibrary(context)) {
            try {
                Intent intent = new Intent();
                intent.setComponent(ComponentName.unflattenFromString(BuildConfig.SUPPORT_APPLICATION_ID + "/.InitActivity"));
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
        }

        if(NonRootUtils.hasWriteSettingsPermission(context)) {
            // Set filename in current.xml
            SharedPreferences prefCurrent = getPrefCurrent(context);
            SharedPreferences.Editor editor = prefCurrent.edit();
            editor.putString("filename", filename);
            editor.apply();

            // Start ProfileLoadService
            Intent intent = new Intent(context, ProfileLoadService.class);
            // Get filename of selected profile
            intent.putExtra(NAME, filename);
            startService(context, intent);
        } else {
            Intent intent = new Intent(context, WriteSettingsPermissionActivity.class);
            intent.putExtra("action", "load-profile");
            intent.putExtra("filename", filename);
            context.startActivity(intent);
        }
    }

    // Turns off the currently active profile
    public static void turnOffProfile(Context context) {
        if(NonRootUtils.hasWriteSettingsPermission(context)) {
            // Set filename in current.xml
            SharedPreferences prefCurrent = getPrefCurrent(context);
            SharedPreferences.Editor editor = prefCurrent.edit();
            editor.putString("filename_backup", prefCurrent.getString("filename", "0"));
            editor.putString("filename", "0");
            editor.apply();

            // Start TurnOffService
            Intent intent = new Intent(context, TurnOffService.class);
            startService(context, intent);
        } else {
            Intent intent = new Intent(context, WriteSettingsPermissionActivity.class);
            intent.putExtra("action", "turn-off-profile");
            context.startActivity(intent);
        }
    }

    // Shows toast notifications
    public static void showToast(Context context, int message) {
        showToast(context, context.getString(message), Toast.LENGTH_SHORT);
    }

    public static void showToastLong(Context context, int message) {
        showToast(context, context.getString(message), Toast.LENGTH_LONG);
    }

    public static void showToast(Context context, String message, int length) {
        cancelToast();

        ToastInterface toast = createToast(context.getApplicationContext(), message, length);
        toast.show();

        ToastHelper.getInstance().setLastToast(toast);
    }

    public static void cancelToast() {
        ToastInterface toast = ToastHelper.getInstance().getLastToast();
        if(toast != null) toast.cancel();
    }

    private static ToastInterface createToast(Context context, String message, int length) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
            return new ToastFrameworkImpl(context, message, length);
        else
            return new ToastCompatImpl(context, message, length);
    }

    // Generates blurb text for profile options, used in various places in the UI
    public static String generateBlurb(Activity a, String key, String value, boolean isNotification) {
        String blurb = " ";

        if(a instanceof TaskerQuickActionsActivity) {
            blurb = a.getResources().getStringArray(R.array.pref_notification_action_list)[1];

            // If this blurb is being generated for the notification, and the value is "Toggle",
            // set value to the actual "On" or "Off" state
            if(isNotification && value.equals("Toggle")) {
                SharedPreferences prefCurrent = getPrefCurrent(a);
                switch(key) {
                    case "temp_backlight_off":
                        if(prefCurrent.getBoolean("backlight_off", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        break;
                    case "temp_chrome":
                        if(prefCurrent.getBoolean("chrome", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        break;
                    case "temp_immersive":
                    case "temp_immersive_new":
                        if(key.equals("temp_immersive_new"))
                            key = "temp_immersive";

                        if ("immersive-mode".equals(prefCurrent.getString("immersive_new", "fallback"))) {
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        } else {
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        }
                        break;
                    case "temp_overscan":
                        if(prefCurrent.getBoolean("overscan", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        break;
                    case "temp_vibration_off":
                        if(prefCurrent.getBoolean("vibration_off", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        break;
                    case "temp_freeform":
                        if(prefCurrent.getBoolean("freeform", false))
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
                        else
                            value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
                        break;
                    case "temp_hdmi_rotation":
                        switch(prefCurrent.getString("hdmi_rotation", "landscape")) {
                            case "portrait":
                                value = a.getResources().getStringArray(R.array.pref_hdmi_rotation_list)[1];
                                break;
                            case "landscape":
                                value = a.getResources().getStringArray(R.array.pref_hdmi_rotation_list)[0];
                                break;
                        }
                        break;
                }
            }

            // Modifications for non-English locales
            if(value.equals(a.getResources().getStringArray(R.array.pref_quick_actions_values)[0]))
                value = a.getResources().getStringArray(R.array.pref_quick_actions)[0];
            else if(value.equals(a.getResources().getStringArray(R.array.pref_quick_actions_values)[1])) {
                if(key.equals("temp_overscan"))
                    value = a.getResources().getStringArray(R.array.pref_quick_actions_overscan)[0];
                else
                    value = a.getResources().getStringArray(R.array.pref_quick_actions)[1];
            }
        }

        switch(key) {
            case "turn_off":
                blurb = a.getResources().getString(R.string.quick_turn_off);
                break;
            case "lock_device":
                blurb = a.getResources().getStringArray(R.array.pref_notification_action_list)[2];
                break;
            case "temp_backlight_off":
                blurb = a.getResources().getString(R.string.quick_backlight) + " " + value;
                break;
            case "temp_chrome":
                blurb = a.getResources().getString(R.string.quick_chrome) + " " + value;
                break;
            case "temp_immersive":
                blurb = a.getResources().getString(R.string.quick_immersive) + " " + value;
                break;
            case "temp_immersive_new":
                switch(value) {
                    case "do-nothing":
                        blurb = a.getResources().getStringArray(R.array.pref_immersive_list_alt)[0];
                        break;
                    case "status-only":
                        blurb = a.getResources().getStringArray(R.array.pref_immersive_list_alt)[1];
                        break;
                    case "immersive-mode":
                        blurb = a.getResources().getStringArray(R.array.pref_immersive_list_alt)[2];
                        break;
                    case "Toggle":
                        blurb = a.getResources().getStringArray(R.array.pref_immersive_list_alt)[3];
                        break;
                }
                break;
            case "density":
            case "temp_density":
                switch(value) {
                    case "reset":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[0];
                        break;
                    case "120":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[1];
                        break;
                    case "160":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[2];
                        break;
                    case "213":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[3];
                        break;
                    case "240":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[4];
                        break;
                    case "320":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[5];
                        break;
                    case "480":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[6];
                        break;
                    case "640":
                        blurb = a.getResources().getStringArray(R.array.pref_dpi_list)[7];
                        break;
                    default:
                        blurb = value + a.getResources().getString(R.string.dpi);
                        break;
                }
                break;
            case "temp_overscan":
                blurb = a.getResources().getString(R.string.quick_overscan) + " " + value;
                break;
            case "size":
            case "temp_size":
                SharedPreferences prefMain = getPrefMain(a);
                if(value.equals("reset"))
                    blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[0];
                else if(prefMain.getBoolean("landscape", false)) {
                    switch(value) {
                        case "3840x2160":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[1];
                            break;
                        case "1920x1080":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[2];
                            break;
                        case "1280x720":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[3];
                            break;
                        case "854x480":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[4];
                            break;
                        default:
                            blurb = value;
                            break;
                    }
                } else {
                    switch(value) {
                        case "2160x3840":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[1];
                            break;
                        case "1080x1920":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[2];
                            break;
                        case "720x1280":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[3];
                            break;
                        case "480x854":
                            blurb = a.getResources().getStringArray(R.array.pref_resolution_list)[4];
                            break;
                        default:
                            Scanner scanner = new Scanner(value);
                            scanner.useDelimiter("x");

                            int height = scanner.nextInt();
                            int width = scanner.nextInt();
                            scanner.close();

                            blurb = width + "x" + height;
                            break;
                    }
                }
                break;
            case "temp_rotation_lock_new":
                switch(value) {
                    case "do-nothing":
                        blurb = a.getResources().getStringArray(R.array.pref_rotation_list)[0];
                        break;
                    case "auto-rotate":
                        blurb = a.getResources().getStringArray(R.array.pref_rotation_list)[1];
                        break;
                    case "landscape":
                        blurb = a.getResources().getStringArray(R.array.pref_rotation_list)[2];
                        break;
                    case "portrait":
                        blurb = a.getResources().getStringArray(R.array.pref_rotation_list)[3];
                        break;
                }
                break;
            case "temp_vibration_off":
                blurb = a.getResources().getString(R.string.quick_vibration) + " " + value;
                break;
            case "temp_freeform":
                blurb = a.getResources().getString(R.string.quick_freeform) + " " + value;
                break;
            case "temp_hdmi_rotation":
                blurb = a.getResources().getString(R.string.quick_hdmi_rotation) + " " + value;
                break;
        }

        return blurb;
    }

    // Updates a preference summary with the currently set value
    public static void bindPreferenceSummaryToValue(Preference preference, Preference.OnPreferenceChangeListener listener) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(listener);

        // Trigger the listener immediately with the preference's current value.
        listener.onPreferenceChange(preference, PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
    }

    // Determine if a resolution/density combo is blacklisted (unsafe to use under normal circumstances)
    public static boolean isBlacklisted(Context context, String requestedRes, String requestedDpi) {
        boolean blacklisted = false;

        SharedPreferences prefMain = getPrefMain(context);
        int defaultHeight = prefMain.getInt("height", 0);
        int defaultWidth = prefMain.getInt("width", 0);
        int defaultDpi = getSystemProperty("ro.sf.lcd_density", prefMain.getInt("density", 0));

        int height, width, density;
        if("reset".equals(requestedRes)) {
            height = defaultHeight;
            width = defaultWidth;
        } else {
            Scanner scanner = new Scanner(requestedRes);
            scanner.useDelimiter("x");

            width = scanner.nextInt();
            height = scanner.nextInt();

            scanner.close();
        }

        if("reset".equals(requestedDpi))
            density = defaultDpi;
        else
            density = Integer.parseInt(requestedDpi);

        // Blacklist DPI values that result in a smallest width that's too low or too high
        int smallestWidth = (DisplayMetrics.DENSITY_DEFAULT * Math.min(height, width)) / density;
        if(smallestWidth < 320 || smallestWidth > 1280)
            blacklisted = true;

        // On Android 10, blacklist resolutions that are larger than the device's native resolution
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (height > defaultHeight || width > defaultWidth))
            blacklisted = true;

        return blacklisted;
    }

    // Detects if we are currently casting the screen using Chromecast
    public static boolean castScreenActive(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();

        for(Display display : displays) {
            try {
                if(Class.forName("android.view.Display")
                        .getMethod("getOwnerPackageName")
                        .invoke(display)
                        .equals("com.google.android.gms"))
                    return true;
            } catch (Exception e) { /* Gracefully fail */ }
        }

        return false;
    }

    // Directs the user to check for updates
    public static void checkForUpdates(Context context) {
        String url;
        if(isPlayStoreRelease(context)) {
            if(BuildConfig.APPLICATION_ID.equals("com.farmerbb.secondscreen.free")
                    && !isPlayStoreInstalled(context))
                url = "https://github.com/farmerbb/SecondScreen/releases";
            else
                url = "https://play.google.com/store/apps/details?id=" + BuildConfig.APPLICATION_ID;
        } else
            url = "https://f-droid.org/repository/browse/?fdid=" + BuildConfig.APPLICATION_ID;

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) { /* Gracefully fail */ }
    }

    // Shows an error dialog when permissions cannot be granted
    public static void showErrorDialog(final Context context, String appopCmd) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.error_dialog_title)
                .setMessage(context.getString(R.string.error_dialog_message, BuildConfig.APPLICATION_ID, appopCmd))
                .setPositiveButton(R.string.action_ok, null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Clears the default home screen
    public static void clearDefaultHome(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ComponentName componentName = new ComponentName(context, DummyLauncherActivity.class.getName());
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        goHome(context);

        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    public static void goHome(Context context) {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(homeIntent);
    }

    // Locks the screen
    public static void lockDevice(Context context) {
        if(isInNonRootMode(context)) {
            ComponentName component = new ComponentName(context, LockDeviceReceiver.class);
            context.getPackageManager().setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);

            DevicePolicyManager mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if(mDevicePolicyManager.isAdminActive(component))
                mDevicePolicyManager.lockNow();
            else {
                Intent intent = new Intent(context, LockDeviceActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

                if(context instanceof Activity)
                    ((Activity) context).overridePendingTransition(0, 0);
            }
        } else
            runCommand(context, "input keyevent 26");
    }

    private static boolean isValidFilename(String filename) {
        // Handle legacy default profiles from version 1.x.x
        switch(filename) {
            case "monitor_1080p":
            case "monitor_720p":
            case "tv_1080p":
            case "tv_720p":
                return true;
        }

        return NumberUtils.isCreatable(filename);
    }

    private static boolean isSystemApp(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0);
            int mask = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
            return (info.flags & mask) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isBlissOs(Context context) {
        String blissVersion = getSystemProperty("ro.bliss.version");
        return blissVersion != null && !blissVersion.isEmpty()
                && BuildConfig.APPLICATION_ID.equals("com.farmerbb.secondscreen.free")
                && isSystemApp(context);
    }

    private static void getDisplayMetrics(Context context) {
        SharedPreferences prefMain = getPrefMain(context);
        SharedPreferences.Editor editor = prefMain.edit();

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        disp.getRealMetrics(metrics);

        editor.putInt("density", getSystemProperty("ro.sf.lcd_density", metrics.densityDpi));

        if(context.getApplicationContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            if(wm.getDefaultDisplay().getRotation() == Surface.ROTATION_90
                    || wm.getDefaultDisplay().getRotation() == Surface.ROTATION_270) {
                editor.putBoolean("landscape", true);
                editor.putInt("height", metrics.widthPixels);
                editor.putInt("width", metrics.heightPixels);
            } else {
                editor.putBoolean("landscape", false);
                editor.putInt("height", metrics.heightPixels);
                editor.putInt("width", metrics.widthPixels);
            }
        } else if(context.getApplicationContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if(wm.getDefaultDisplay().getRotation() == Surface.ROTATION_0
                    || wm.getDefaultDisplay().getRotation() == Surface.ROTATION_180) {
                editor.putBoolean("landscape", true);
                editor.putInt("height", metrics.heightPixels);
                editor.putInt("width", metrics.widthPixels);
            } else {
                editor.putBoolean("landscape", false);
                editor.putInt("height", metrics.widthPixels);
                editor.putInt("width", metrics.heightPixels);
            }
        }

        editor.apply();
    }

    @SuppressWarnings("HardwareIds")
    public static void initPrefs(Context context) {
        // Gather display metrics
        getDisplayMetrics(context);

        // Set some default preferences
        SharedPreferences prefMain = getPrefMain(context);
        SharedPreferences.Editor editor = prefMain.edit();
        editor.putBoolean("first-run", true);
        editor.putBoolean("safe_mode", true);
        editor.putString("android_id", Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID));
        editor.putBoolean("hdmi", true);
        editor.putString("notification_action", "quick-actions");
        editor.putBoolean("show-welcome-message", !isBlissOs(context));
        editor.apply();

        // Create default profile for BlissOS
        if(isBlissOs(context)) {
            String filename = String.valueOf(System.currentTimeMillis());
            String profileName = context.getResources().getString(R.string.blissos_default);

            createProfileFromTemplate(context, profileName, 3, getPrefSaved(context, filename));

            // Write the String to a new file with filename of current milliseconds of Unix time
            try {
                FileOutputStream output = context.openFileOutput(filename, Context.MODE_PRIVATE);
                output.write(profileName.getBytes());
                output.close();
            } catch (IOException e) { /* Gracefully fail */ }
        }
    }

    public static String getTaskbarPackageName(Context context) {
        return getInstalledPackage(context, Arrays.asList(
                "com.farmerbb.taskbar.paid",
                "com.farmerbb.taskbar"));
    }

    public static String getChromePackageName(Context context) {
        return getInstalledPackage(context, Arrays.asList(
                "com.chrome.canary",
                "com.chrome.dev",
                "com.chrome.beta",
                "com.android.chrome"));
    }

    // Returns the name of an installed package from a list of package names, in order of preference
    private static String getInstalledPackage(Context context, List<String> packageNames) {
        if(packageNames == null || packageNames.isEmpty())
            return null;

        List<String> packages = packageNames instanceof ArrayList ? packageNames : new ArrayList<>(packageNames);
        String packageName = packages.get(0);

        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return packageName;
        } catch (PackageManager.NameNotFoundException e) {
            packages.remove(0);
            return getInstalledPackage(context, packages);
        }
    }

    private static String getChromeVersion(Context context) {
        String packageName = getChromePackageName(context);
        if(packageName != null) {
            try {
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(packageName, 0);
                return pInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) { /* Gracefully fail */ }
        }

        return null;
    }
    
    public static void createProfileFromTemplate(Context context, String name, int pos, SharedPreferences profileToCreate) {
        SharedPreferences.Editor editor = profileToCreate.edit();
        SharedPreferences prefMain = getPrefMain(context);

        if(name.isEmpty())
            if(pos == 5)
                editor.putString("profile_name", context.getResources().getString(R.string.action_new));
            else
                editor.putString("profile_name", context.getResources().getStringArray(R.array.new_profile_templates)[pos]);
        else
            editor.putString("profile_name", name);

        switch(pos) {
            // TV (1080p)
            case 0:
                if(prefMain.getBoolean("landscape", false))
                    editor.putString("size", "1920x1080");
                else
                    editor.putString("size", "1080x1920");

                editor.putString("rotation_lock_new", "landscape");
                editor.putString("density", "240");

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    editor.putString("ui_refresh",
                            isInNonRootMode(context) ? "activity-manager" : "system-ui");
                }

                editor.putBoolean("chrome", getChromePackageName(context) != null);
                break;

            // TV (4K)
            case 1:
                if(prefMain.getBoolean("landscape", false))
                    editor.putString("size", "3840x2160");
                else
                    editor.putString("size", "2160x3840");

                editor.putString("rotation_lock_new", "landscape");
                editor.putString("density", "480");

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    editor.putString("ui_refresh",
                            isInNonRootMode(context) ? "activity-manager" : "system-ui");
                }

                editor.putBoolean("chrome", getChromePackageName(context) != null);
                break;

            // TV (720p)
            case 2:
                if(prefMain.getBoolean("landscape", false))
                    editor.putString("size", "1280x720");
                else
                    editor.putString("size", "720x1280");

                editor.putString("rotation_lock_new", "landscape");
                editor.putString("density", "160");

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    editor.putString("ui_refresh",
                            isInNonRootMode(context) ? "activity-manager" : "system-ui");
                }

                editor.putBoolean("chrome", getChromePackageName(context) != null);
                break;

            // Monitor (1080p)
            case 3:
                if(prefMain.getBoolean("landscape", false))
                    editor.putString("size", "1920x1080");
                else
                    editor.putString("size", "1080x1920");

                editor.putString("rotation_lock_new", "landscape");
                editor.putString("density", "160");

                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    editor.putString("ui_refresh",
                            isInNonRootMode(context) ? "activity-manager" : "system-ui");
                }

                editor.putBoolean("chrome", getChromePackageName(context) != null);

                if(canEnableFreeform(context)
                        && getTaskbarPackageName(context) != null
                        && isPlayStoreRelease(context)) {
                    editor.putBoolean("taskbar", true);
                    editor.putBoolean("freeform", true);
                    editor.putBoolean("clear_home", true);
                }

                break;

            // AppRadio
            case 4:
                if(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
                    editor.putBoolean("bluetooth_on", true);

                editor.putBoolean("backlight_off", true);

                if(filesExist(vibrationOff))
                    editor.putBoolean("vibration_off", true);

                if(prefMain.getBoolean("expert_mode", false)) {
                    editor.putString("size", prefMain.getInt("width", 0)
                            + "x"
                            + prefMain.getInt("height", 0));

                    editor.putString("density", Integer.toString(getSystemProperty("ro.sf.lcd_density", prefMain.getInt("density", 0))));

                    editor.putBoolean("size-reset", true);
                    editor.putBoolean("density-reset", true);
                }

                break;

            // Other / None
            case 5:
                if(prefMain.getBoolean("expert_mode", false)) {
                    editor.putString("size", prefMain.getInt("width", 0)
                            + "x"
                            + prefMain.getInt("height", 0));

                    editor.putString("density", Integer.toString(getSystemProperty("ro.sf.lcd_density", prefMain.getInt("density", 0))));

                    editor.putBoolean("size-reset", true);
                    editor.putBoolean("density-reset", true);
                }

                break;
        }

        editor.apply();
    }

    public static boolean canEnableFreeform(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                || Build.VERSION.SDK_INT != Build.VERSION_CODES.P
                || (getTaskbarPackageName(context) != null && isPlayStoreRelease(context)));
    }

    @TargetApi(Build.VERSION_CODES.N)
    public static boolean hasFreeformSupport(Context context) {
        return canEnableFreeform(context)
                && (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                || Settings.Global.getInt(context.getContentResolver(), "enable_freeform_support", 0) != 0
                || (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1
                && Settings.Global.getInt(context.getContentResolver(), "force_resizable_activities", 0) != 0
                && (getTaskbarPackageName(context) != null && isPlayStoreRelease(context))));
    }

    public static boolean isUntestedAndroidVersion(Context context) {
        SharedPreferences prefMain = getPrefMain(context);

        return getCurrentApiVersion() > Math.max(
                BuildConfig.TESTED_API_VERSION,
                prefMain.getFloat("current_api_version_new", BuildConfig.TESTED_API_VERSION)
        );
    }

    public static float getCurrentApiVersion() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            return Float.valueOf(Build.VERSION.SDK_INT + "." + Build.VERSION.PREVIEW_SDK_INT);
        else
            return (float) Build.VERSION.SDK_INT;
    }

    @SuppressLint("PackageManagerGetSignatures")
    public static boolean isPlayStoreRelease(Context context) {
        Signature playStoreSignature = new Signature(context.getString(R.string.signature));
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            for(Signature signature : info.signatures) {
                if(signature.equals(playStoreSignature))
                    return true;
            }
        } catch (Exception e) { /* Gracefully fail */ }

        return false;
    }

    public static boolean isExternalAccessDisabled(Context context) {
        SharedPreferences prefMain = getPrefMain(context);
        return !prefMain.getBoolean("tasker_enabled", true);
    }

    public static int getScreenOrientation(Context context) {
        SharedPreferences prefCurrent = getPrefCurrent(context);
        String rotationLockPref = prefCurrent.getString("rotation_lock_new", "fallback");

        switch(rotationLockPref) {
            case "landscape":
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case "auto-rotate":
                return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
            case "portrait":
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            default:
                return -1;
        }
    }

    public static boolean hasSupportLibrary(Context context) {
        return hasSupportLibrary(context, 0);
    }

    private static boolean hasSupportLibrary(Context context, int minVersion) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pInfo = pm.getPackageInfo(BuildConfig.SUPPORT_APPLICATION_ID, 0);
            return pm.checkSignatures(BuildConfig.SUPPORT_APPLICATION_ID, BuildConfig.APPLICATION_ID)
                    == PackageManager.SIGNATURE_MATCH
                    && pInfo.versionCode >= minVersion;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isPlayStoreInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.android.vending", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void startService(Context context, Intent intent) {
        SupportUtils.startService(context, intent);
    }

    @SuppressLint("PrivateApi")
    public static String getSystemProperty(String key) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            return cls.getMethod("get", String.class).invoke(null, key).toString();
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressLint("PrivateApi")
    public static int getSystemProperty(String key, int def) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            return (int) cls.getMethod("getInt", String.class, int.class).invoke(null, key, def);
        } catch (Exception e) {
            return def;
        }
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
        SharedPreferences prefCurrent = getPrefCurrent(context);
        int savedID = prefCurrent.getInt("external_display_id", -1);

        if(savedID != -1)
            return savedID;

        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();

        return displays[displays.length - 1].getDisplayId();
    }

    public static boolean canEnableOverscan() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && U.getCurrentApiVersion() <= 29.0f;
    }

    public static boolean canEnableImmersiveMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && U.getCurrentApiVersion() <= 29.0f;
    }

    public static boolean canEnableWifi(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true;
        }

        if (hasElevatedPermissions(context) && !isInNonRootMode(context)) {
            return true;
        }

        return hasSupportLibrary(context, 3);
    }

    public static boolean setWifiEnabled(Context context, boolean enabled) {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            wifi.setWifiEnabled(enabled);
            return true;
        }

        return false;
    }

    @SuppressLint("MissingPermission")
    public static void closeNotificationDrawer(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Intent closeDrawer = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcast(closeDrawer);
        }
    }
}