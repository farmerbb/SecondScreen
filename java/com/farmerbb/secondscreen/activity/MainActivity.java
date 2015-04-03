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

package com.farmerbb.secondscreen.activity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.fragment.ProfileEditFragment;
import com.farmerbb.secondscreen.fragment.ProfileListFragment;
import com.farmerbb.secondscreen.fragment.ProfileViewFragment;
import com.farmerbb.secondscreen.fragment.WelcomeFragment;
import com.farmerbb.secondscreen.fragment.dialog.AndroidUpgradeDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.DeleteDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.ExpertModeSizeDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.FirstLoadDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.FirstRunDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.MultipleVersionsDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.NewDeviceDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.NewProfileDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.ReloadProfileDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.SwiftkeyDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.UiRefreshDialogFragment;
import com.farmerbb.secondscreen.receiver.BootReceiver;
import com.farmerbb.secondscreen.receiver.PackageUpgradeReceiver;
import com.farmerbb.secondscreen.receiver.TaskerActionReceiver;
import com.farmerbb.secondscreen.receiver.TaskerConditionReceiver;
import com.farmerbb.secondscreen.service.DisplayConnectionService;
import com.farmerbb.secondscreen.service.NotificationService;
import com.farmerbb.secondscreen.util.U;
import com.jrummyapps.android.os.SystemProperties;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import eu.chainfire.libsuperuser.Shell;

// This is the main activity of the application.  This is the activity that is launched when
// SecondScreen is selected in the user's application launcher.
// It shows fragments in a single-pane layout on phones and a dual-pane layout on tablets and larger
// screens.  The left pane will always show the ProfileListFragment.  The right pane will show,
// depending on where the user is in the application flow, either the WelcomeFragment,
// ProfileViewFragment, or ProfileEditFragment.
// The single-pane layout follows the flow of the dual-pane layout's right pane, with
// ProfileListFragment shown in place of WelcomeFragment (except initially on the first run).
//
// The MainActivity also takes care of some basic application initialization, such as running a
// safeguard check, testing for superuser access, setting some initial preferences, gathering basic
// info about the user's device, launching services, and displaying the warning dialog on first run.
public final class MainActivity extends Activity implements
FirstRunDialogFragment.Listener,
FirstLoadDialogFragment.Listener,
NewProfileDialogFragment.Listener,
DeleteDialogFragment.Listener,
ReloadProfileDialogFragment.Listener,
ExpertModeSizeDialogFragment.Listener,
AndroidUpgradeDialogFragment.Listener,
NewDeviceDialogFragment.Listener,
UiRefreshDialogFragment.Listener,
MultipleVersionsDialogFragment.Listener,
SwiftkeyDialogFragment.Listener,
ProfileListFragment.Listener,
ProfileEditFragment.Listener,
ProfileViewFragment.Listener {
    private final class RunSafeguard extends AsyncTask<Void, Void, Void> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(MainActivity.this);
            dialog.setMessage(getResources().getString(R.string.checking_for_superuser));
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Build commands to pass to su
            final int chromeCommand = 0;
            final int densityCommand = 1;
            final int densityCommand2 = 2;
            final int sizeCommand = 3;
            final int overscanCommand = 4;
            final int immersiveCommand = 5;

            // Initialize su array
            String[] su = new String[immersiveCommand + 1];
            Arrays.fill(su, "");

            su[chromeCommand] = U.chromeCommandRemove;
            su[densityCommand] = U.densityCommand("reset");
            su[sizeCommand] = U.sizeCommand("reset");

            // We run the density command twice, for reliability
            su[densityCommand2] = su[densityCommand];

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                su[overscanCommand] = U.overscanCommand + "reset";

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                su[immersiveCommand] = U.immersiveCommand(false);

            // Run superuser commands
            Shell.SU.run(su);

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            try {
                dialog.dismiss();

                // Show welcome dialog if this is the user's first time running SecondScreen
                if(getFragmentManager().findFragmentByTag("firstrunfragment") == null) {
                    DialogFragment firstRun = new FirstRunDialogFragment();
                    firstRun.show(getFragmentManager(), "firstrunfragment");
                }
            } catch (IllegalStateException e) {
                finish();
            } catch (IllegalArgumentException e) {}
        }
    }

    boolean showUpgradeDialog = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(savedInstanceState != null)
            showUpgradeDialog = savedInstanceState.getBoolean("show-upgrade-dialog");

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Remove margins from layout on Lollipop devices
            LinearLayout layout = (LinearLayout) findViewById(R.id.profileViewEdit);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) layout.getLayoutParams();
            params.setMargins(0, 0, 0, 0);
            layout.setLayoutParams(params);

            // Set action bar elevation
            getActionBar().setElevation(15f);
        } else
            // Change window title on pre-Lollipop devices
            setTitle(" " + getResources().getString(R.string.app_name));

        // Handle cases where both the free and (formerly) paid versions may be installed at the same time
        SharedPreferences prefMain = U.getPrefMain(this);
        boolean stopAppLaunch = false;

        if((getPackageName().equals("com.farmerbb.secondscreen.free")
               && isActivityAvailable("com.farmerbb.secondscreen", MainActivity.class.getName()))
            || (getPackageName().equals("com.farmerbb.secondscreen")
            && isActivityAvailable("com.farmerbb.secondscreen.free", MainActivity.class.getName()))) {
            stopAppLaunch = true;

            Bundle bundle = new Bundle();
            // If the first-run preference is false, then assume that this is the duplicate package.
            // If true, then this package has already been run, so determine the duplicate package name.
            if(!prefMain.getBoolean("first-run", false))
                bundle.putString("package", getPackageName());
            else if(getPackageName().equals("com.farmerbb.secondscreen"))
                bundle.putString("package", "com.farmerbb.secondscreen.free");
            else
                bundle.putString("package", "com.farmerbb.secondscreen");

            if(getFragmentManager().findFragmentByTag("multiple-versions-fragment") == null) {
                DialogFragment multiple = new MultipleVersionsDialogFragment();
                multiple.setArguments(bundle);
                multiple.show(getFragmentManager(), "multiple-versions-fragment");
            }
        }

        if(!stopAppLaunch) {
            // Ensure that all receivers are enabled and stay enabled, so that critical functionality
            // associated with these receivers can always run when needed
            initializeComponents(BootReceiver.class.getName());
            initializeComponents(PackageUpgradeReceiver.class.getName());
            initializeComponents(TaskerActionReceiver.class.getName());
            initializeComponents(TaskerConditionReceiver.class.getName());

            // Previous free versions of SecondScreen disabled certain activities containing
            // functionality that, at the time, was restricted to the paid version of SecondScreen.
            // Ensure that these activities are always enabled for users on 2.0+
            initializeComponents(HdmiActivity.class.getName());
            initializeComponents(HdmiProfileSelectActivity.class.getName());
            initializeComponents(QuickLaunchActivity.class.getName());
            initializeComponents(TaskerConditionActivity.class.getName());
            initializeComponents(TaskerQuickActionsActivity.class.getName());

            // Restore DisplayConnectionService
            if(prefMain.getBoolean("hdmi", true) && prefMain.getBoolean("first-run", false)) {
                Intent serviceIntent = new Intent(this, DisplayConnectionService.class);
                startService(serviceIntent);
            }

            // Restore NotificationService
            SharedPreferences prefCurrent = U.getPrefCurrent(this);
            if(!prefCurrent.getBoolean("not_active", true)) {
                Intent serviceIntent = new Intent(this, NotificationService.class);
                startService(serviceIntent);
            }

            if(prefMain.getBoolean("first-run", false)) {
                // Clear preferences from ProfileEditFragment, just in case
                if(!(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment)) {
                    SharedPreferences prefNew = U.getPrefNew(this);
                    SharedPreferences.Editor prefNewEditor = prefNew.edit();
                    prefNewEditor.clear();
                    prefNewEditor.apply();
                }

                // Save preferences when upgrading to 2.0+ from a previous free version
                if(prefMain.getString("notification_action", "null").equals("null")) {
                    SharedPreferences.Editor editor = prefMain.edit();
                    editor.putString("notification_action", "quick-actions");
                    editor.apply();
                }

                // Determine if we need to show any dialogs before we create the fragments
                showDialogs();

                // Finally, create fragments
                createFragments();
            } else if(getFragmentManager().findFragmentByTag("firstrunfragment") == null)
                // This is our first time running SecondScreen.
                // Run the safeguard commands before doing anything else
                (new RunSafeguard()).execute();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Set MainActivity active state
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences.Editor editor = prefMain.edit();
        editor.putBoolean("inactive", false);
        editor.apply();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Set MainActivity active state
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences.Editor editor = prefMain.edit();
        editor.remove("inactive");
        editor.apply();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("show-upgrade-dialog", showUpgradeDialog);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void createFragments() {
        // Begin a new FragmentTransaction
        FragmentTransaction transaction = getFragmentManager().beginTransaction();

        // This fragment shows ProfileListFragment as a sidebar (only seen in tablet mode landscape)
        if(!(getFragmentManager().findFragmentById(R.id.profileList) instanceof ProfileListFragment))
            transaction.replace(R.id.profileList, new ProfileListFragment(), "ProfileListFragment");

        // This fragment shows ProfileListFragment in the main screen area (only seen on phones and tablet mode portrait),
        // but only if it doesn't already contain ProfileViewFragment or ProfileEditFragment.
        // If ProfileListFragment is already showing in the sidebar, use WelcomeFragment instead
        if(!((getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment)
                || (getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileViewFragment))) {
            SharedPreferences prefMain = U.getPrefMain(this);
            if(prefMain.getBoolean("show-welcome-message", false)
                    || (getFragmentManager().findFragmentById(R.id.profileViewEdit) == null
                    && findViewById(R.id.layoutMain).getTag().equals("main-layout-large"))
                    || ((getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileListFragment)
                    && findViewById(R.id.layoutMain).getTag().equals("main-layout-large"))) {
                // Show welcome message
                Bundle bundle = new Bundle();
                bundle.putBoolean("show-welcome-message", prefMain.getBoolean("show-welcome-message", false));

                Fragment fragment = new WelcomeFragment();
                fragment.setArguments(bundle);

                transaction.replace(R.id.profileViewEdit, fragment, "NoteListFragment");
            } else if(findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
                transaction.replace(R.id.profileViewEdit, new ProfileListFragment(), "NoteListFragment");
        }

        // Commit fragment transaction
        transaction.commit();
    }

    private void getDisplayMetrics(SharedPreferences prefMain) {
        SharedPreferences.Editor editor = prefMain.edit();

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        disp.getRealMetrics(metrics);

        editor.putInt("density", SystemProperties.getInt("ro.sf.lcd_density", metrics.densityDpi));

        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
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
        }
        else if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
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

    private void initializeComponents(String name) {
        ComponentName component = new ComponentName(getPackageName(), name);
        getPackageManager().setComponentEnabledSetting(component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    private boolean isActivityAvailable(String packageName, String className) {
        final PackageManager packageManager = getPackageManager();
        final Intent intent = new Intent();
        intent.setClassName(packageName, className);

        @SuppressWarnings("rawtypes")
        List list = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

        return list.size() > 0;
    }

    @Override
    public void onBackPressed() {
        if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment) {
            ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
            fragment.onBackPressed(fragment.getFilename(), false, true);
        } else if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileViewFragment) {
            ProfileViewFragment fragment = (ProfileViewFragment) getFragmentManager().findFragmentByTag("ProfileViewFragment");
            fragment.onBackPressed();
        } else
            finish();
    }

    @Override
    public void onFirstLoadPositiveClick(DialogFragment dialog, String filename, boolean isChecked) {
            // Set checkbox preference
            if(isChecked) {
                SharedPreferences prefMain = U.getPrefMain(this);
                SharedPreferences.Editor firstLoadEditor = prefMain.edit();
                firstLoadEditor.putBoolean("first-load", true);
                firstLoadEditor.apply();
            }

            // Dismiss dialog
            dialog.dismiss();

            U.loadProfile(this, filename);

            // Add ProfileListFragment or WelcomeFragment
            Fragment fragment;
            if(findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
                fragment = new ProfileListFragment();
            else {
                SharedPreferences prefMain = U.getPrefMain(this);
                Bundle bundle = new Bundle();
                bundle.putBoolean("show-welcome-message", prefMain.getBoolean("show-welcome-message", false));

                fragment = new WelcomeFragment();
                fragment.setArguments(bundle);
            }

            getFragmentManager()
                .beginTransaction()
                .replace(R.id.profileViewEdit, fragment, "ProfileListFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                .commit();
    }

    @Override
    public void onFirstRunDialogNegativeClick() {
        uninstallPackage(getPackageName());
    }

    @Override
    public void onFirstRunDialogPositiveClick() {
        SharedPreferences prefMain = U.getPrefMain(this);

        // Check if "first-run" preference hasn't already been set
        if(!prefMain.getBoolean("first-run", false)) {

            // Gather display metrics
            getDisplayMetrics(prefMain);

            // Set some default preferences
            SharedPreferences.Editor editor = prefMain.edit();
            editor.putBoolean("first-run", true);
            editor.putBoolean("safe_mode", true);
            editor.putString("android_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            editor.putBoolean("hdmi", true);
            editor.putString("notification_action", "quick-actions");
            editor.putBoolean("show-welcome-message", true);

                // Restore DisplayConnectionService
                boolean displayConnectionServiceRunning = false;

                ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                for(ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                    if(DisplayConnectionService.class.getName().equals(service.service.getClassName()))
                        displayConnectionServiceRunning = true;
                }

                if(!displayConnectionServiceRunning) {
                    // Start DisplayConnectionService
                    Intent serviceIntent = new Intent(this, DisplayConnectionService.class);
                    startService(serviceIntent);
                }

            editor.apply();

            // Determine if we need to show any dialogs before we create the fragments
            showDialogs();

            // Finally, create fragments
            createFragments();
        }
    }

    public void onLoadProfileButtonClick(String filename) {
        // User touched the dialog's positive button
            SharedPreferences prefMain = U.getPrefMain(this);
            if(prefMain.getBoolean("first-load", false)) {
                U.loadProfile(this, filename);

                // Add ProfileListFragment or WelcomeFragment
                Fragment fragment;
                if(findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
                    fragment = new ProfileListFragment();
                else {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("show-welcome-message", prefMain.getBoolean("show-welcome-message", false));

                    fragment = new WelcomeFragment();
                    fragment.setArguments(bundle);
                }

                getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.profileViewEdit, fragment, "ProfileListFragment")
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                        .commit();
            } else {
                Bundle bundle = new Bundle();
                bundle.putString("filename", filename);

                DialogFragment firstLoadFragment = new FirstLoadDialogFragment();
                firstLoadFragment.setArguments(bundle);
                firstLoadFragment.show(getFragmentManager(), "first-load");
            }
    }

    @Override
    public void onTurnOffProfileButtonClick() {
        // User touched the dialog's positive button
        try {
            SharedPreferences prefCurrent = U.getPrefCurrent(this);
            if(!prefCurrent.getBoolean("not_active", true))
                U.turnOffProfile(this);

            // Add ProfileListFragment or WelcomeFragment
            Fragment fragment;
            if(findViewById(R.id.layoutMain).getTag().equals("main-layout-normal"))
                fragment = new ProfileListFragment();
            else {
                SharedPreferences prefMain = U.getPrefMain(this);
                Bundle bundle = new Bundle();
                bundle.putBoolean("show-welcome-message", prefMain.getBoolean("show-welcome-message", false));

                fragment = new WelcomeFragment();
                fragment.setArguments(bundle);
            }

            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.profileViewEdit, fragment, "ProfileListFragment")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commit();
        } catch (NullPointerException e) {}
    }

    @Override
    public void onDeleteDialogPositiveClick(DialogFragment dialog) {
        // User touched the dialog's positive button

        // Dismiss dialog
        dialog.dismiss();

        if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment) {
            ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
            fragment.deleteProfile();
        } else if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileViewFragment) {
            ProfileViewFragment fragment = (ProfileViewFragment) getFragmentManager().findFragmentByTag("ProfileViewFragment");
            fragment.deleteProfile();
        }
    }

    @Override
    public void editProfile(String filename) {
        String currentFilename;
        boolean isEditing = false;

        if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment) {
            ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
            currentFilename = fragment.getFilename();
            isEditing = true;
        } else if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileViewFragment) {
            ProfileViewFragment fragment = (ProfileViewFragment) getFragmentManager().findFragmentByTag("ProfileViewFragment");
            currentFilename = fragment.getFilename();
        } else
            currentFilename = " ";

        if(currentFilename.equals(filename)) {
            if(!isEditing)
                switchFragments(filename, true);
        } else
            switchFragments(filename, true);
    }

    @Override
    public void viewProfile(String filename) {
        String currentFilename;
        boolean isViewing = false;

        if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment) {
            ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
            currentFilename = fragment.getFilename();
        } else if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileViewFragment) {
            ProfileViewFragment fragment = (ProfileViewFragment) getFragmentManager().findFragmentByTag("ProfileViewFragment");
            currentFilename = fragment.getFilename();
            isViewing = true;
        } else
            currentFilename = " ";

        if(currentFilename.equals(filename)) {
            if(!isViewing)
                switchFragments(filename, false);
        } else
            switchFragments(filename, false);
    }

    private void switchFragments(String filename, boolean type) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileListFragment)
                U.hideFab(this, R.id.button_floating_action);
            else if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof WelcomeFragment)
                U.hideFab(this, R.id.button_floating_action_welcome);
        }

        if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment) {
            ProfileEditFragment editFragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
            editFragment.onBackPressed(filename, type, false);
        } else {
            Fragment fragment;
            String tag;

            if(type) {
                fragment = new ProfileEditFragment();
                tag = "ProfileEditFragment";
            } else {
                fragment = new ProfileViewFragment();
                tag = "ProfileViewFragment";
            }

            Bundle bundle = new Bundle();
            bundle.putString("filename", filename);

            try {
                bundle.putString("title", U.getProfileTitle(this, filename));
            } catch (IOException e) {}

            fragment.setArguments(bundle);

            // Add fragment
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.profileViewEdit, fragment, tag)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
        }
    }

    @Override
    public String getProfileTitle(String filename) throws IOException {
        return U.getProfileTitle(this, filename);
    }

    @Override
    public void showDeleteDialog() {
        DialogFragment deleteFragment = new DeleteDialogFragment();
        deleteFragment.show(getFragmentManager(), "delete");
    }

    @Override
    public void showExpertModeDialog() {
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences prefNew = U.getPrefNew(this);
        String size = prefNew.getString("size", "reset");
        int height;
        int width;

        if(size.equals("reset")) {
            height = prefMain.getInt("height", 0);
            width = prefMain.getInt("width", 0);
        } else {
            Scanner scanner = new Scanner(size);
            scanner.useDelimiter("x");

            if(prefMain.getBoolean("landscape", false)) {
                height = scanner.nextInt();
                width = scanner.nextInt();
            } else {
                width = scanner.nextInt();
                height = scanner.nextInt();
            }

            scanner.close();
        }

        Bundle bundle = new Bundle();
        bundle.putString("height", Integer.toString(height));
        bundle.putString("width", Integer.toString(width));

        DialogFragment sizeFragment = new ExpertModeSizeDialogFragment();
        sizeFragment.setArguments(bundle);
        sizeFragment.show(getFragmentManager(), "expert-mode-size");
    }

    @Override
    public void showReloadDialog(String filename, boolean isEdit, boolean returnToList) {
        Bundle bundle = new Bundle();
        bundle.putString("filename", filename);
        bundle.putBoolean("is-edit", isEdit);
        bundle.putBoolean("return-to-list", returnToList);

        DialogFragment reloadProfileFragment = new ReloadProfileDialogFragment();
        reloadProfileFragment.setArguments(bundle);
        reloadProfileFragment.show(getFragmentManager(), "reload");
    }

    @Override
    public void onReloadDialogPositiveClick(String filename, boolean isEdit, boolean returnToList) {
        if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment) {
            ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
            fragment.onReloadDialogPositiveClick(filename, isEdit, returnToList);
        }
    }

    @Override
    public void onReloadDialogNegativeClick(String filename, boolean isEdit, boolean returnToList) {
        if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment) {
            ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
            fragment.onReloadDialogNegativeClick(filename, isEdit, returnToList);
        }
    }

    @Override
    public SharedPreferences getPrefCurrent() {
        return U.getPrefCurrent(this);
    }

    @Override
    public SharedPreferences getPrefQuickActions() {
        return U.getPrefQuickActions(this);
    }

    @Override
    public TextView getHelperText() {
        return (TextView) findViewById(R.id.textView1);
    }

    @Override
    public void onNewProfilePositiveClick(String name, int pos) {
        SharedPreferences prefNew = U.getPrefNew(this);
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences.Editor editor = prefNew.edit();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileListFragment)
                U.hideFab(this, R.id.button_floating_action);
            else if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof WelcomeFragment)
                U.hideFab(this, R.id.button_floating_action_welcome);
        }

        if(name.isEmpty())
            if(pos == 4)
                editor.putString("profile_name", getResources().getString(R.string.action_new));
            else
                editor.putString("profile_name", getResources().getStringArray(R.array.new_profile_templates)[pos]);
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
                editor.putString("ui_refresh", "system-ui");

                try {
                    getPackageManager().getPackageInfo("com.chrome.beta", 0);
                    editor.putBoolean("chrome", true);
                } catch (PackageManager.NameNotFoundException e) {
                    try {
                        getPackageManager().getPackageInfo("com.android.chrome", 0);
                        editor.putBoolean("chrome", true);
                    } catch (PackageManager.NameNotFoundException e1) {
                        editor.putBoolean("chrome", false);
                    }
                }
                break;

            // TV (720p)
            case 1:
                if(prefMain.getBoolean("landscape", false))
                    editor.putString("size", "1280x720");
                else
                    editor.putString("size", "720x1280");

                editor.putString("rotation_lock_new", "landscape");
                editor.putString("density", "160");
                editor.putString("ui_refresh", "system-ui");

                try {
                    getPackageManager().getPackageInfo("com.chrome.beta", 0);
                    editor.putBoolean("chrome", true);
                } catch (PackageManager.NameNotFoundException e) {
                    try {
                        getPackageManager().getPackageInfo("com.android.chrome", 0);
                        editor.putBoolean("chrome", true);
                    } catch (PackageManager.NameNotFoundException e1) {
                        editor.putBoolean("chrome", false);
                    }
                }
                break;

            // Monitor (1080p)
            case 2:
                if(prefMain.getBoolean("landscape", false))
                    editor.putString("size", "1920x1080");
                else
                    editor.putString("size", "1080x1920");

                editor.putString("rotation_lock_new", "landscape");
                editor.putString("density", "160");
                editor.putString("ui_refresh", "system-ui");

                try {
                    getPackageManager().getPackageInfo("com.chrome.beta", 0);
                    editor.putBoolean("chrome", true);
                } catch (PackageManager.NameNotFoundException e) {
                    try {
                        getPackageManager().getPackageInfo("com.android.chrome", 0);
                        editor.putBoolean("chrome", true);
                    } catch (PackageManager.NameNotFoundException e1) {
                        editor.putBoolean("chrome", false);
                    }
                }
                break;

            // AppRadio
            case 3:
                if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
                    editor.putBoolean("bluetooth_on", true);

                if(U.filesExist(U.backlightOff)) {
                    editor.putBoolean("backlight_off", true);

                    if(Build.MANUFACTURER.equalsIgnoreCase("Samsung"))
                        U.showToastLong(this, R.string.backlight_off_message_samsung);
                    else
                        U.showToastLong(this, R.string.backlight_off_message);
                }

                if(U.filesExist(U.vibrationOff))
                    editor.putBoolean("vibration_off", true);

                if(prefMain.getBoolean("expert_mode", false)) {
                    if(prefMain.getBoolean("landscape", false))
                        editor.putString("size", Integer.toString(prefMain.getInt("height", 0))
                                + "x"
                                + Integer.toString(prefMain.getInt("width", 0)));
                    else
                        editor.putString("size", Integer.toString(prefMain.getInt("width", 0))
                                + "x"
                                + Integer.toString(prefMain.getInt("height", 0)));

                    editor.putString("density", Integer.toString(SystemProperties.getInt("ro.sf.lcd_density", prefMain.getInt("density", 0))));

                    editor.putBoolean("size-reset", true);
                    editor.putBoolean("density-reset", true);
                }

                break;

            // Other / None
            case 4:
                if(prefMain.getBoolean("expert_mode", false)) {
                    if(prefMain.getBoolean("landscape", false))
                        editor.putString("size", Integer.toString(prefMain.getInt("height", 0))
                                + "x"
                                + Integer.toString(prefMain.getInt("width", 0)));
                    else
                        editor.putString("size", Integer.toString(prefMain.getInt("width", 0))
                                + "x"
                                + Integer.toString(prefMain.getInt("height", 0)));

                    editor.putString("density", Integer.toString(SystemProperties.getInt("ro.sf.lcd_density", prefMain.getInt("density", 0))));

                    editor.putBoolean("size-reset", true);
                    editor.putBoolean("density-reset", true);
                }

                break;
        }

        editor.apply();

        // Add ProfileEditFragment
        getFragmentManager()
                .beginTransaction()
                .replace(R.id.profileViewEdit, new ProfileEditFragment(), "ProfileEditFragment")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
    }

    @Override
    public void onExpertModeSizePositiveClick(String height, String width) {
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences prefNew = U.getPrefNew(this);
        SharedPreferences.Editor editor = prefNew.edit();

        if(height.isEmpty())
            height = Integer.toString(prefMain.getInt("height", 0));

        if(width.isEmpty())
            width = Integer.toString(prefMain.getInt("width", 0));

        if(prefMain.getBoolean("landscape", false))
            editor.putString("size", height + "x" + width);
        else
            editor.putString("size", width + "x" + height);

        editor.apply();

        ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
        U.bindPreferenceSummaryToValue(fragment.findPreference("size"), ProfileEditFragment.opcl);
    }

    @Override
    public void setDefaultDensity() {
        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences prefNew = U.getPrefNew(this);
        SharedPreferences.Editor editor = prefNew.edit();
        String density = Integer.toString(SystemProperties.getInt("ro.sf.lcd_density", prefMain.getInt("density", 0)));

        editor.putString("density", density);
        editor.apply();

        ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
        U.bindPreferenceSummaryToValue(fragment.findPreference("density"), ProfileEditFragment.opcl);
    }

    @Override
    public String generateBlurb(String key, String value) {
        return U.generateBlurb(this, key, value, true);
    }

    @Override
    public void setEmptyTitle(String title) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            setTitle(title);
        else
            setTitle(" " + title);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 42
                && resultCode == RESULT_OK
                && (getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment)) {
                ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
                fragment.setPrefChange(data.getBooleanExtra("pref-change", false));
        }
    }

    @Override
    public void onUpgradeDialogNegativeClick(DialogFragment dialog) {
        dialog.dismiss();
        U.checkForUpdates(this);
    }

    @Override
    public void onNewDeviceDialogPositiveClick(DialogFragment dialog) {
        dialog.dismiss();
        showDialogs();
    }

    @Override
    public void showUiRefreshDialog(String filename, boolean isEdit, boolean returnToList) {
        Bundle bundle = new Bundle();
        bundle.putString("filename", filename);
        bundle.putBoolean("is-edit", isEdit);
        bundle.putBoolean("return-to-list", returnToList);

        DialogFragment uiRefreshFragment = new UiRefreshDialogFragment();
        uiRefreshFragment.setArguments(bundle);
        uiRefreshFragment.show(getFragmentManager(), "ui-refresh");
    }

    @Override
    public void onUiRefreshDialogPositiveClick(String filename, boolean isEdit, boolean returnToList) {
        if(getFragmentManager().findFragmentById(R.id.profileViewEdit) instanceof ProfileEditFragment) {
            ProfileEditFragment fragment = (ProfileEditFragment) getFragmentManager().findFragmentByTag("ProfileEditFragment");
            fragment.preSave(filename, isEdit, returnToList);
        }
    }

    @Override
    public void uninstallPackage(String packageName) {
        Uri packageURI = Uri.parse("package:" + packageName);
        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
        startActivity(uninstallIntent);
        finish();
    }

    private void showDialogs() {
        SharedPreferences prefMain = U.getPrefMain(this);
        boolean swiftKey = true;

        try {
            getPackageManager().getPackageInfo("com.touchtype.swiftkey", 0);
        } catch (PackageManager.NameNotFoundException e1) {
            swiftKey = false;

            if(prefMain.getBoolean("swiftkey", false)) {
                SharedPreferences.Editor editor = prefMain.edit();
                editor.remove("swiftkey");
                editor.apply();
            }
        }

        // Save Android ID to preferences, and show dialog if ID does not match (new device)
        if(prefMain.getString("android_id", "null").equals("null")) {
            SharedPreferences.Editor editor = prefMain.edit();
            editor.putString("android_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            editor.apply();
        } else if(!prefMain.getString("android_id", "null").equals(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID))) {
            SharedPreferences.Editor editor = prefMain.edit();
            editor.putString("android_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            editor.apply();

            if(getFragmentManager().findFragmentByTag("new-device-fragment") == null) {
                DialogFragment newDeviceFragment = new NewDeviceDialogFragment();
                newDeviceFragment.show(getFragmentManager(), "new-device-fragment");
            }

            // Show dialog if SwiftKey is installed
        } else if(swiftKey
                && !prefMain.getBoolean("swiftkey", false)
                && getFragmentManager().findFragmentByTag("swiftkey-fragment") == null) {
            SharedPreferences.Editor editor = prefMain.edit();
            editor.putBoolean("swiftkey", true);
            editor.apply();

            DialogFragment swiftkeyFragment = new SwiftkeyDialogFragment();
            swiftkeyFragment.show(getFragmentManager(), "swiftkey-fragment");

        // Show dialog if device is newer than API 22 (Lollipop MR1)
        } else if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1
                && getFragmentManager().findFragmentByTag("upgrade-fragment") == null
                && showUpgradeDialog) {
            DialogFragment upgradeFragment = new AndroidUpgradeDialogFragment();
            upgradeFragment.show(getFragmentManager(), "upgrade-fragment");
            showUpgradeDialog = false;
        }
    }

    @Override
    public void onSwiftkeyDialogPositiveClick(DialogFragment dialog) {
        dialog.dismiss();
        showDialogs();
    }
}