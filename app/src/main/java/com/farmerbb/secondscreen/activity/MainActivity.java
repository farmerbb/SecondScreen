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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.farmerbb.secondscreen.BuildConfig;
import com.farmerbb.secondscreen.R;
import com.farmerbb.secondscreen.fragment.ProfileEditFragment;
import com.farmerbb.secondscreen.fragment.ProfileListFragment;
import com.farmerbb.secondscreen.fragment.ProfileViewFragment;
import com.farmerbb.secondscreen.fragment.WelcomeFragment;
import com.farmerbb.secondscreen.fragment.dialog.AboutDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.AndroidUpgradeDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.BusyboxDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.DeleteDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.ExpertModeSizeDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.FirstLoadDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.FirstRunDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.MultipleVersionsDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.NewDeviceDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.NewProfileDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.ReloadProfileDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.SystemAlertPermissionDialogFragment;
import com.farmerbb.secondscreen.fragment.dialog.UiRefreshDialogFragment;
import com.farmerbb.secondscreen.receiver.BootReceiver;
import com.farmerbb.secondscreen.receiver.PackageUpgradeReceiver;
import com.farmerbb.secondscreen.receiver.TaskerActionReceiver;
import com.farmerbb.secondscreen.receiver.TaskerConditionReceiver;
import com.farmerbb.secondscreen.service.DisplayConnectionService;
import com.farmerbb.secondscreen.service.NotificationService;
import com.farmerbb.secondscreen.util.U;
import com.jrummyapps.android.os.SystemProperties;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

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
public final class MainActivity extends AppCompatActivity implements
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
BusyboxDialogFragment.Listener,
ProfileListFragment.Listener,
ProfileEditFragment.Listener,
ProfileViewFragment.Listener,
AboutDialogFragment.Listener,
SystemAlertPermissionDialogFragment.Listener {
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
            final int freeformCommand = 6;
            final int hdmiRotationCommand = 7;

            // Initialize su array
            String[] su = new String[hdmiRotationCommand + 1];
            Arrays.fill(su, "");

            su[chromeCommand] = U.chromeCommandRemove;
            su[sizeCommand] = U.sizeCommand("reset");

            if(!(getPackageManager().hasSystemFeature("com.cyanogenmod.android")
                    && Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP_MR1)) {
                su[densityCommand] = U.densityCommand("reset");

                // We run the density command twice, for reliability
                su[densityCommand2] = su[densityCommand];
            }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                su[overscanCommand] = U.overscanCommand + "reset";

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                su[immersiveCommand] = U.immersiveCommand("do-nothing");

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && U.getTaskbarPackageName(MainActivity.this) == null)
                su[freeformCommand] = U.freeformCommand(false);
            
            su[hdmiRotationCommand] = U.hdmiRotationCommand + "landscape";

            // Run superuser commands
            U.runCommands(MainActivity.this, su, false);

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
            } catch (IllegalArgumentException e) { /* Gracefully fail */ }
        }
    }

    boolean showBusyboxDialog = true;
    boolean showUpgradeDialog = true;
    int clicks = 0;

    boolean returningFromGrantingSystemAlertPermission = false;
    String savedFilename;

    private BroadcastReceiver showDialogsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showMoreDialogs();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(savedInstanceState != null) {
            showBusyboxDialog = savedInstanceState.getBoolean("show-busybox-dialog");
            showUpgradeDialog = savedInstanceState.getBoolean("show-upgrade-dialog");
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Remove margins from layout on Lollipop devices
            LinearLayout layout = findViewById(R.id.profileViewEdit);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) layout.getLayoutParams();
            params.setMargins(0, 0, 0, 0);
            layout.setLayoutParams(params);

            // Set action bar elevation
            getSupportActionBar().setElevation(getResources().getDimensionPixelSize(R.dimen.action_bar_elevation));
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getWindow().setDecorCaptionShade(Window.DECOR_CAPTION_SHADE_DARK);
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(showDialogsReceiver, new IntentFilter("com.farmerbb.secondscreen.SHOW_DIALOGS"));

        // Handle cases where both the free and (formerly) paid versions may be installed at the same time
        PackageInfo paidPackage;
        PackageInfo freePackage;

        try {
            paidPackage = getPackageManager().getPackageInfo("com.farmerbb.secondscreen", 0);
            freePackage = getPackageManager().getPackageInfo("com.farmerbb.secondscreen.free", 0);

            Bundle bundle = new Bundle();
            if(paidPackage.firstInstallTime > freePackage.firstInstallTime)
                bundle.putString("package", paidPackage.packageName);
            else
                bundle.putString("package", freePackage.packageName);

            if(getFragmentManager().findFragmentByTag("multiple-versions-fragment") == null) {
                DialogFragment multiple = new MultipleVersionsDialogFragment();
                multiple.setArguments(bundle);
                multiple.show(getFragmentManager(), "multiple-versions-fragment");
            }
        } catch (PackageManager.NameNotFoundException e) {
            // Hooray!  Only one version of the app is installed, so proceed with app launch.

            SharedPreferences prefMain = U.getPrefMain(this);

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
                if("null".equals(prefMain.getString("notification_action", "null"))) {
                    SharedPreferences.Editor editor = prefMain.edit();
                    editor.putString("notification_action", "quick-actions");
                    editor.apply();
                }

                // Determine if we need to show any dialogs before we create the fragments
                if(savedInstanceState == null)
                    showDialogs();

                // Read debug mode preference
                if(isDebugModeEnabled(false))
                    clicks = 10;

                // Set launcher shortcuts on API 25+
                setLauncherShortcuts();

                // Finally, create fragments
                createFragments();
            } else if(getFragmentManager().findFragmentByTag("firstrunfragment") == null)
                // This is our first time running SecondScreen.
                // Run the safeguard commands before doing anything else
                (new RunSafeguard()).execute();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(returningFromGrantingSystemAlertPermission) {
            returningFromGrantingSystemAlertPermission = false;
            String filename = savedFilename;
            savedFilename = null;
            onFirstLoadPositiveClick(null, filename, false);
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
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(showDialogsReceiver);
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("show-busybox-dialog", showBusyboxDialog);
        savedInstanceState.putBoolean("show-upgrade-dialog", showUpgradeDialog);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void setLauncherShortcuts() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);

            if(shortcutManager.getDynamicShortcuts().size() == 0) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName(BuildConfig.APPLICATION_ID, TaskerQuickActionsActivity.class.getName());
                intent.putExtra("launched-from-app", true);

                ShortcutInfo shortcut = new ShortcutInfo.Builder(this, "quick_actions")
                        .setShortLabel(getString(R.string.label_quick_actions))
                        .setIcon(Icon.createWithResource(this, R.drawable.shortcut_icon))
                        .setIntent(intent)
                        .build();

                shortcutManager.setDynamicShortcuts(Collections.singletonList(shortcut));
            }
        }
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

    private void initializeComponents(String name) {
        ComponentName component = new ComponentName(getPackageName(), name);
        getPackageManager().setComponentEnabledSetting(component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
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
        SharedPreferences prefMain = U.getPrefMain(this);

        // Set checkbox preference
        if(isChecked) {
            SharedPreferences.Editor firstLoadEditor = prefMain.edit();
            firstLoadEditor.putBoolean("first-load", true);
            firstLoadEditor.apply();
        }

        // Dismiss dialog
        if(dialog != null) dialog.dismiss();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)
                && !"do-nothing".equals(U.getPrefSaved(this, filename).getString("rotation_lock_new", "fallback"))
                && !prefMain.getBoolean("dont_show_system_alert_dialog", false)) {
            returningFromGrantingSystemAlertPermission = true;
            savedFilename = filename;

            DialogFragment fragment = new SystemAlertPermissionDialogFragment();
            fragment.show(getFragmentManager(), "SystemAlertPermissionDialogFragment");
        } else {
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
        }
    }

    @Override
    public void onFirstRunDialogNegativeClick() {
        uninstallPackage(getPackageName());
    }

    @SuppressLint("HardwareIds")
    @Override
    public void onFirstRunDialogPositiveClick() {
        SharedPreferences prefMain = U.getPrefMain(this);

        // Check if "first-run" preference hasn't already been set
        if(!prefMain.getBoolean("first-run", false)) {

            // Initialize preferences
            U.initPrefs(this);

            // Restore DisplayConnectionService
            Intent serviceIntent = new Intent(this, DisplayConnectionService.class);
            startService(serviceIntent);

            // Determine if we need to show any dialogs before we create the fragments
            showDialogs();

            // Set launcher shortcuts on API 25+
            setLauncherShortcuts();

            // Finally, create fragments
            createFragments();
        }
    }

    public void onLoadProfileButtonClick(String filename) {
            SharedPreferences prefMain = U.getPrefMain(this);
            if(prefMain.getBoolean("first-load", false)) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(this)
                        && "landscape".equals(U.getPrefSaved(this, filename).getString("rotation_lock_new", "fallback"))
                        && !prefMain.getBoolean("dont_show_system_alert_dialog", false)) {
                    returningFromGrantingSystemAlertPermission = true;
                    savedFilename = filename;

                    DialogFragment fragment = new SystemAlertPermissionDialogFragment();
                    fragment.show(getFragmentManager(), "SystemAlertPermissionDialogFragment");
                } else {
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
                }
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
        } catch (NullPointerException e) { /* Gracefully fail */ }
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
            } catch (IOException e) { /* Gracefully fail */ }

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

        if("reset".equals(size)) {
            height = prefMain.getInt("height", 0);
            width = prefMain.getInt("width", 0);
        } else {
            Scanner scanner = new Scanner(size);
            scanner.useDelimiter("x");

            width = scanner.nextInt();
            height = scanner.nextInt();

            scanner.close();
        }

        Bundle bundle = new Bundle();
        if(prefMain.getBoolean("landscape", false)) {
            bundle.putString("height", Integer.toString(width));
            bundle.putString("width", Integer.toString(height));
        } else {
            bundle.putString("height", Integer.toString(height));
            bundle.putString("width", Integer.toString(width));
        }

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
        return findViewById(R.id.textView1);
    }

    @Override
    public void onNewProfilePositiveClick(String name, int pos) {
        U.createProfileFromTemplate(this, name, pos, U.getPrefNew(this));

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

        if(prefMain.getBoolean("landscape", false)) {
            if(height.isEmpty())
                height = Integer.toString(prefMain.getInt("width", 0));

            if(width.isEmpty())
                width = Integer.toString(prefMain.getInt("height", 0));

            editor.putString("size", height + "x" + width);
        } else {
            if(height.isEmpty())
                height = Integer.toString(prefMain.getInt("height", 0));

            if(width.isEmpty())
                width = Integer.toString(prefMain.getInt("width", 0));

            editor.putString("size", width + "x" + height);
        }

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
    public void onUpgradeDialogPositiveClick(DialogFragment dialog) {
        dialog.dismiss();

        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences.Editor editor = prefMain.edit();
        editor.putFloat("current_api_version_new", U.getCurrentApiVersion());
        editor.apply();
    }

    @Override
    public void onUpgradeDialogNegativeClick(DialogFragment dialog) {
        dialog.dismiss();
        U.checkForUpdates(this);

        SharedPreferences prefMain = U.getPrefMain(this);
        SharedPreferences.Editor editor = prefMain.edit();
        editor.putFloat("current_api_version_new", U.getCurrentApiVersion());
        editor.apply();
    }

    @Override
    public void onNewDeviceDialogPositiveClick(DialogFragment dialog) {
        dialog.dismiss();
        showMoreDialogs();
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
        Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));

        try {
            startActivity(uninstallIntent);
        } catch (ActivityNotFoundException e) { /* Gracefully fail */ }

        finish();
    }

    private void showDialogs() {
        if(U.hasElevatedPermissions(this))
            showMoreDialogs();
        else
            startActivity(new Intent(this, UnableToStartActivity.class));
    }

    private void showMoreDialogs() {
        new Handler().postDelayed(this::reallyShowMoreDialogs, 100);
    }

    @SuppressLint("HardwareIds")
    private void reallyShowMoreDialogs() {
        SharedPreferences prefMain = U.getPrefMain(this);

        // Save Android ID to preferences, and show dialog if ID does not match (new device)
        if("null".equals(prefMain.getString("android_id", "null"))) {
            SharedPreferences.Editor editor = prefMain.edit();
            editor.putString("android_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            editor.apply();
        } else if(!Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID).equals(prefMain.getString("android_id", "null"))) {
            SharedPreferences.Editor editor = prefMain.edit();
            editor.putString("android_id", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
            editor.apply();

            if(getFragmentManager().findFragmentByTag("new-device-fragment") == null) {
                DialogFragment newDeviceFragment = new NewDeviceDialogFragment();
                newDeviceFragment.show(getFragmentManager(), "new-device-fragment");
            }

        // Show dialog if on an untested Android version
        } else if(U.isUntestedAndroidVersion(this)
                && getFragmentManager().findFragmentByTag("upgrade-fragment") == null
                && showUpgradeDialog) {
            showUpgradeDialog = false;

            DialogFragment upgradeFragment = new AndroidUpgradeDialogFragment();
            upgradeFragment.show(getFragmentManager(), "upgrade-fragment");

        // Starting with 5.1.1 LMY48I, RunningAppProcessInfo no longer returns valid data,
        // which means we won't be able to use the "kill" command with the pid of SystemUI.
        // On Marshmallow, this isn't an issue, as we have the "pkill" command available as part
        // of toybox.  Users on recent builds of 5.1.1, however, will need to have busybox
        // installed to run "pkill".
        //
        // Thus, if the SystemUI pid gets returned as 0, we need to check for either busybox or
        // toybox on the device, and inform the user if either of these are missing.
        } else if(getFragmentManager().findFragmentByTag("busybox-fragment") == null
                && showBusyboxDialog) {
            showBusyboxDialog = false;

            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> pids = am.getRunningAppProcesses();
            int processid = 0;

            // Get SystemUI pid
            for(ActivityManager.RunningAppProcessInfo process : pids) {
                if(process.processName.equalsIgnoreCase("com.android.systemui"))
                    processid = process.pid;
            }

            if(processid == 0) {
                File toybox = new File("/system/bin", "toybox");
                File busybox = new File("/system/xbin", "busybox");
                File busybox2 = new File("/system/bin", "busybox");

                if(!(toybox.exists() || busybox.exists() || busybox2.exists())
                        && !prefMain.getBoolean("ignore_busybox_dialog", false)) {
                    DialogFragment busyboxFragment = new BusyboxDialogFragment();
                    busyboxFragment.show(getFragmentManager(), "busybox-fragment");
                }
            }
        }
    }

    @Override
    public void onBusyboxDialogNegativeClick() {
        U.getPrefMain(this).edit().putBoolean("ignore_busybox_dialog", true).apply();
    }

    // Enables and disables debug mode.  Debug mode can be enabled by tapping on the helper text
    // 10 times, and is used for simulating actions on non-rooted devices, among other things.
    @Override
    public boolean isDebugModeEnabled(boolean isClick) {
        SharedPreferences prefMain = U.getPrefMain(this);

        if(isClick && U.getPrefCurrent(this).getBoolean("not_active", true)) {
            clicks++;

            U.cancelToast();

            if(clicks > 5 && clicks < 10) {
                String message = String.format(getResources().getString(R.string.debug_mode_enabling), 10 - clicks);
                showDebugModeToast(message);
            } else if(clicks >= 10) {
                SharedPreferences.Editor editor = prefMain.edit();
                if(prefMain.getBoolean("debug_mode", false)) {
                    editor.putBoolean("debug_mode", false);
                    showDebugModeToast(getString(R.string.debug_mode_disabled));

                    // Clean up leftover notifications
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.cancelAll();

                    // Clean up leftover dump files
                    File file = new File(getExternalFilesDir(null), "prefCurrent.xml");
                    File file2 = new File(getExternalFilesDir(null), "prefSaved.xml");
                    File file3 = new File(getExternalFilesDir(null), "prefMain.xml");
                    File file4 = new File(getExternalFilesDir(null), "prefNew.xml");
                    file.delete();
                    file2.delete();
                    file3.delete();
                    file4.delete();
                } else {
                    editor.putBoolean("debug_mode", true);
                    showDebugModeToast(getString(R.string.debug_mode_enabled));
                }

                editor.apply();
            }
        }

        return prefMain.getBoolean("debug_mode", false);
    }

    private void showDebugModeToast(final String message) {
        new Handler().postDelayed(() -> U.showToast(this, message, Toast.LENGTH_SHORT), 100);
    }

    @Override
    public void onAboutDialogNegativeClick(DialogFragment dialog) {
        dialog.dismiss();
        U.checkForUpdates(this);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onSystemAlertPermissionDialogPositiveClick() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + BuildConfig.APPLICATION_ID)));
        } catch (ActivityNotFoundException e) {
            U.showErrorDialog(this, "SYSTEM_ALERT_WINDOW");
        }
    }

    @Override
    public void onSystemAlertPermissionDialogNegativeClick() {
        U.getPrefMain(this).edit().putBoolean("dont_show_system_alert_dialog", true).apply();

        if(returningFromGrantingSystemAlertPermission) {
            returningFromGrantingSystemAlertPermission = false;
            String filename = savedFilename;
            savedFilename = null;
            onFirstLoadPositiveClick(null, filename, false);
        }
    }
}