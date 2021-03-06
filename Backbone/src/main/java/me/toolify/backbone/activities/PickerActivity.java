/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.toolify.backbone.activities;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.ListPopupWindow;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;
import me.toolify.backbone.R;
import me.toolify.backbone.adapters.CheckableListAdapter;
import me.toolify.backbone.adapters.CheckableListAdapter.CheckableItem;
import me.toolify.backbone.bus.events.FilesystemStatusUpdateEvent;
import me.toolify.backbone.console.ConsoleBuilder;
import me.toolify.backbone.fragments.NavigationFragment;
import me.toolify.backbone.fragments.NavigationFragment.OnDirectoryChangedListener;
import me.toolify.backbone.fragments.NavigationFragment.OnFilePickedListener;
import me.toolify.backbone.model.FileSystemObject;
import me.toolify.backbone.preferences.DisplayRestrictions;
import me.toolify.backbone.preferences.FileManagerSettings;
import me.toolify.backbone.preferences.Preferences;
import me.toolify.backbone.ui.ThemeManager;
import me.toolify.backbone.ui.ThemeManager.Theme;
import me.toolify.backbone.ui.widgets.Breadcrumb;
import me.toolify.backbone.ui.widgets.BreadcrumbItem;
import me.toolify.backbone.ui.widgets.ButtonItem;
import me.toolify.backbone.util.DialogHelper;
import me.toolify.backbone.util.ExceptionUtil;
import me.toolify.backbone.util.FileHelper;
import me.toolify.backbone.util.MimeTypeHelper;
import me.toolify.backbone.util.StorageHelper;

/**
 * The activity for allow to use a {@link FragmentActivity} like, to pick a file from other
 * application.
 */
public class PickerActivity extends AbstractNavigationActivity
        implements OnCancelListener, OnDismissListener,
        OnFilePickedListener, OnDirectoryChangedListener {

    private static final String TAG = "PickerActivity"; //$NON-NLS-1$

    private static boolean DEBUG = false;

    private final BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (intent.getAction().compareTo(FileManagerSettings.INTENT_THEME_CHANGED) == 0) {
                    applyTheme();
                }
            }
        }
    };

    // The result code
    private static final int RESULT_CROP_IMAGE = 1;

    // The component that holds the crop operation. We use Gallery3d because we are confidence
    // of his input parameters
    private static final ComponentName CROP_COMPONENT =
                                    new ComponentName(
                                            "com.android.gallery3d", //$NON-NLS-1$
                                            "com.android.gallery3d.app.CropImage"); //$NON-NLS-1$

    // Gallery crop editor action
    private static final String ACTION_CROP = "com.android.camera.action.CROP"; //$NON-NLS-1$

    // Extra data for Gallery CROP action
    private static final String EXTRA_CROP = "crop"; //$NON-NLS-1$

    // Scheme for file and directory picking
    private static final String FILE_URI_SCHEME = "file"; //$NON-NLS-1$
    private static final String FOLDER_URI_SCHEME = "folder"; //$NON-NLS-1$
    private static final String DIRECTORY_URI_SCHEME = "directory"; //$NON-NLS-1$

    FileSystemObject mFso;  // The picked item
    FileSystemObject mCurrentDirectory;
    private AlertDialog mDialog;
    /**
     * @hide
     */
    NavigationFragment mNavigationFragment;
    Breadcrumb mBreadcrumb;
    private View mRootView;
    private ButtonItem mFilesystemInfo;
    private ProgressBar mFilesystemInfoRefreshing;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle state) {
        //Save state
        super.onCreate(state);

        if (DEBUG) {
            Log.d(TAG, "PickerActivity.onCreate"); //$NON-NLS-1$
        }

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(FileManagerSettings.INTENT_THEME_CHANGED);
        registerReceiver(this.mNotificationReceiver, filter);

        // Initialize the activity
        init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "PickerActivity.onDestroy"); //$NON-NLS-1$
        }

        // Unregister the receiver
        try {
            unregisterReceiver(this.mNotificationReceiver);
        } catch (Throwable ex) {
            /**NON BLOCK**/
        }

        //All destroy. Continue
        super.onDestroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause() {
        super.onPause();
        // Always unregister when an object no longer should be on the bus.
        EventBus.getDefault().unregister(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        measureHeight();
    }

    /**
     * Method that displays a dialog with a {@link NavigationFragment} to select the
     * proposed file
     */
    private void init() {
        final boolean pickingDirectory;
        final Intent intent = getIntent();

        if (isFilePickIntent(intent)) {
            // ok
            Log.d(TAG, "PickerActivity: got file pick intent: " + String.valueOf(intent)); //$NON-NLS-1$
            pickingDirectory = false;
        } else if (isDirectoryPickIntent(getIntent())) {
            // ok
            Log.d(TAG, "PickerActivity: got folder pick intent: " + String.valueOf(intent)); //$NON-NLS-1$
            pickingDirectory = true;
        } else {
            Log.d(TAG, "PickerActivity got unrecognized intent: " + String.valueOf(intent)); //$NON-NLS-1$
            setResult(FragmentActivity.RESULT_CANCELED);
            finish();
            return;
        }

        // Display restrictions
        Map<DisplayRestrictions, Object> restrictions = new HashMap<DisplayRestrictions, Object>();
        //- Mime/Type restriction
        String mimeType = getIntent().getType();
        if (mimeType != null) {
            if (!MimeTypeHelper.isMimeTypeKnown(this, mimeType)) {
                Log.i(TAG,
                        String.format(
                                "Mime type %s unknown, falling back to wildcard.", //$NON-NLS-1$
                                mimeType));
                mimeType = MimeTypeHelper.ALL_MIME_TYPES;
            }
            restrictions.put(DisplayRestrictions.MIME_TYPE_RESTRICTION, mimeType);
        }
        // Other restrictions
        Bundle extras = getIntent().getExtras();
        Log.d(TAG, "PickerActivity. extras: " + String.valueOf(extras)); //$NON-NLS-1$
        if (extras != null) {
            //-- File size
            if (extras.containsKey(android.provider.MediaStore.Audio.Media.EXTRA_MAX_BYTES)) {
                long size =
                        extras.getLong(android.provider.MediaStore.Audio.Media.EXTRA_MAX_BYTES);
                restrictions.put(DisplayRestrictions.SIZE_RESTRICTION, Long.valueOf(size));
            }
            //-- Local filesystems only
            if (extras.containsKey(Intent.EXTRA_LOCAL_ONLY)) {
                boolean localOnly = extras.getBoolean(Intent.EXTRA_LOCAL_ONLY);
                restrictions.put(
                        DisplayRestrictions.LOCAL_FILESYSTEM_ONLY_RESTRICTION,
                        Boolean.valueOf(localOnly));
            }
        }
        if (pickingDirectory) {
            restrictions.put(DisplayRestrictions.DIRECTORY_ONLY_RESTRICTION, Boolean.TRUE);
        }

        // Create or use the console
        if (!initializeConsole()) {
            // Something when wrong. Display a message and exit
            DialogHelper.showToast(this, R.string.msgs_cant_create_console, Toast.LENGTH_SHORT);
            cancel();
            return;
        }

        // Create the root file
        this.mRootView = getLayoutInflater().inflate(R.layout.picker, null, false);
        this.mRootView.post(new Runnable() {
            @Override
            public void run() {
                measureHeight();
            }
        });

        // Initialize the (pseudo) action bar
        updateTitleActionBar();

        // Navigation view
        this.mNavigationFragment =
                (NavigationFragment)getFragmentManager().findFragmentById (R.id.navigation_fragment);
        this.mNavigationFragment.setRestrictions(restrictions);
        this.mNavigationFragment.setOnFilePickedListener(this);
        this.mNavigationFragment.setOnDirectoryChangedListener(this);

        // Apply the current theme
        applyTheme();

        // Create the dialog
        this.mDialog = DialogHelper.createDialog(
            this, R.drawable.ic_launcher,
            pickingDirectory ? R.string.directory_picker_title : R.string.picker_title,
            this.mRootView);

        this.mDialog.setButton(
                DialogInterface.BUTTON_NEGATIVE,
                getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dlg, int which) {
                dlg.cancel();
            }
        });
        if (pickingDirectory) {
            this.mDialog.setButton(
                    DialogInterface.BUTTON_POSITIVE,
                    getString(R.string.select),
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dlg, int which) {
                    PickerActivity.this.mFso = PickerActivity.this.mCurrentDirectory;
                    dlg.dismiss();
                }
            });
        }
        this.mDialog.setCancelable(true);
        this.mDialog.setOnCancelListener(this);
        this.mDialog.setOnDismissListener(this);
        DialogHelper.delegateDialogShow(this, this.mDialog);

        // Set content description of storage volume button
        mFilesystemInfo = (ButtonItem)this.mRootView.findViewById(R.id.button_filesystem_info);
        mFilesystemInfo.setContentDescription(getString(R.string.actionbar_button_storage_cd));
        mFilesystemInfoRefreshing = (ProgressBar)this.mRootView.findViewById(
                R.id.button_filesystem_info_refreshing);

    }

    /**
     * Method that measure the height needed to avoid resizing when
     * change to a new directory. This method fixed the height of the window
     * @hide
     */
    void measureHeight() {
        // Calculate the dialog size based on the window height
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        final int height = displaymetrics.heightPixels;

        Configuration config = getResources().getConfiguration();
        int percent = config.orientation == Configuration.ORIENTATION_LANDSCAPE ? 55 : 70;

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, (height * percent) / 100);
        this.mRootView.setLayoutParams(params);
    }

    /**
     * Method that initializes a console
     */
    private boolean initializeConsole() {
        try {
            // Create a ChRooted console
            ConsoleBuilder.createDefaultConsole(this, false, false);
            // There is a console allocated. Use it.
            return true;
        } catch (Throwable _throw) {
            // Capture the exception
            ExceptionUtil.translateException(this, _throw, true, false);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_CROP_IMAGE:
                // Return what the callee activity returns
                setResult(resultCode, data);
                finish();
                return;

            default:
                break;
        }

        // The response is not understood
        Log.w(TAG,
                String.format(
                        "Ignore response. requestCode: %s, resultCode: %s, data: %s", //$NON-NLS-1$
                        Integer.valueOf(requestCode),
                        Integer.valueOf(resultCode),
                        data));
        DialogHelper.showToast(this, R.string.msgs_operation_failure, Toast.LENGTH_SHORT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        if (this.mFso != null) {
            File src = new File(this.mFso.getFullPath());
            if (getIntent().getExtras() != null) {
                // Some AOSP applications use the gallery to edit and crop the selected image
                // with the Gallery crop editor. In this case pass the picked file to the
                // CropActivity with the requested parameters
                // Expected result is on onActivityResult
                Bundle extras = getIntent().getExtras();
                String crop = extras.getString(EXTRA_CROP);
                if (Boolean.parseBoolean(crop)) {
                    // We want to use the Gallery3d activity because we know about it, and his
                    // parameters. At least we have a compatible one.
                    Intent intent = new Intent(ACTION_CROP);
                    if (getIntent().getType() != null) {
                        intent.setType(getIntent().getType());
                    }
                    intent.setData(Uri.fromFile(src));
                    intent.putExtras(extras);
                    intent.setComponent(CROP_COMPONENT);
                    startActivityForResult(intent, RESULT_CROP_IMAGE);
                    return;
                }
            }

            // Return the picked file, as expected (this activity should fill the intent data
            // and return RESULT_OK result)
            Intent result = new Intent();
            result.setData(getResultUriForFileFromIntent(src, getIntent()));
            setResult(FragmentActivity.RESULT_OK, result);
            finish();

        } else {
            cancel();
        }
    }

    private static boolean isFilePickIntent(Intent intent) {
        final String action = intent.getAction();

        if (Intent.ACTION_GET_CONTENT.equals(action)) {
            return true;
        }
        if (Intent.ACTION_PICK.equals(action)) {
            final Uri data = intent.getData();
            if (data != null && FILE_URI_SCHEME.equals(data.getScheme())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isDirectoryPickIntent(Intent intent) {
        if (Intent.ACTION_PICK.equals(intent.getAction()) && intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            if (FOLDER_URI_SCHEME.equals(scheme) || DIRECTORY_URI_SCHEME.equals(scheme)) {
                return true;
            }
        }

        return false;
    }

    private static Uri getResultUriForFileFromIntent(File src, Intent intent) {
        Uri result = Uri.fromFile(src);

        if (Intent.ACTION_PICK.equals(intent.getAction()) && intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            if (scheme != null) {
                result = result.buildUpon().scheme(scheme).build();
            }
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCancel(DialogInterface dialog) {
        cancel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onFilePicked(FileSystemObject item) {
        this.mFso = item;
        this.mDialog.dismiss();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDirectoryChanged(FileSystemObject item) {
        this.mCurrentDirectory = item;
    }

    /**
     * This method is called by various pieces of code responsible for updating file listing or
     * breadcrumb data.  The method receives all {@link me.toolify.backbone.bus.events.FilesystemStatusUpdateEvent}
     * events generated by the various asyncTasks responsible for gathering and sending file info.
     *
     * @param event an event containing the filesystem status code
     */
    public void onEvent(FilesystemStatusUpdateEvent event) {
        setFilesystemStatusDrawable(event.status);
    }

    private void setFilesystemStatusDrawable(int fileSystemstatus){

        TypedArray a = getTheme().obtainStyledAttributes(R.styleable.FileManager);

        switch (fileSystemstatus) {

            case FilesystemStatusUpdateEvent.INDICATOR_UNLOCKED:
                if (mFilesystemInfo != null) {
                    mFilesystemInfo.setImageResource(
                            a.getResourceId(R.styleable.FileManager_actionIconLockOpen,
                                    R.drawable.ic_action_holo_dark_lock_open));
                    setFilesystemInfoProgressState(false);
                }
                break;

            case FilesystemStatusUpdateEvent.INDICATOR_LOCKED:
                if (mFilesystemInfo != null) {
                    mFilesystemInfo.setImageResource(
                            a.getResourceId(R.styleable.FileManager_actionIconLockClosed,
                                    R.drawable.ic_action_holo_dark_lock_closed));
                    setFilesystemInfoProgressState(false);
                }
                break;

            case FilesystemStatusUpdateEvent.INDICATOR_WARNING:
                if (mFilesystemInfo != null) {
                    mFilesystemInfo.setImageResource(
                            a.getResourceId(R.styleable.FileManager_actionIconWarning,
                                    R.drawable.ic_action_holo_dark_warning));
                    setFilesystemInfoProgressState(false);
                }
                break;

            case FilesystemStatusUpdateEvent.INDICATOR_REFRESHING:
                if (mFilesystemInfo != null) {
                    setFilesystemInfoProgressState(true);
                }
                break;

            case FilesystemStatusUpdateEvent.INDICATOR_STOP_REFRESHING:
                if (mFilesystemInfo != null) {
                    setFilesystemInfoProgressState(false);
                }
                break;
        }

        a.recycle();
    }

    /**
     * This function switches an action item between its normal icon and an indeterminate progress
     * circle
     * @param refreshing value is true if the action item should show the progress bar
     */
    public void setFilesystemInfoProgressState(final boolean refreshing) {
        if (mFilesystemInfo != null) {

            if (refreshing) {
                mFilesystemInfo.setVisibility(View.INVISIBLE);
                mFilesystemInfoRefreshing.setVisibility(View.VISIBLE);
            } else {
                mFilesystemInfo.setVisibility(View.VISIBLE);
                mFilesystemInfoRefreshing.setVisibility(View.INVISIBLE);
            }

        }
    }

    /**
     * Method invoked when an action item is clicked.
     *
     * @param view The button pushed
     */
    public void onActionBarItemClick(View view) {
        switch (view.getId()) {
            //######################
            //Breadcrumb Actions
            //######################
            case R.id.button_filesystem_info:
                //Show a popup with the storage volumes to select
                showStorageVolumesPopUp(view);
                break;

            default:
                break;
        }
    }

    /**
     * Method that cancels the activity
     */
    private void cancel() {
        setResult(FragmentActivity.RESULT_CANCELED);
        finish();
    }

    /**
     * Method that shows a popup with the storage volumes
     *
     * @param anchor The view on which anchor the popup
     */
    private void showStorageVolumesPopUp(View anchor) {
        // Create a list (but not checkable)
        final Object[] volumes = StorageHelper.getStorageVolumes(PickerActivity.this);
        List<CheckableItem> descriptions = new ArrayList<CheckableItem>();
        if (volumes != null) {
            int cc = volumes.length;
            for (int i = 0; i < cc; i++) {
                String desc = StorageHelper.getStorageVolumeDescription(this, volumes[i]);
                CheckableItem item = new CheckableItem(desc, false, false);
                descriptions.add(item);
            }
        }
        CheckableListAdapter adapter =
                new CheckableListAdapter(getApplicationContext(), descriptions);

        //Create a show the popup menu
        final ListPopupWindow popup = DialogHelper.createListPopupWindow(this, adapter, anchor);
        popup.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                popup.dismiss();
                if (volumes != null) {
                    PickerActivity.this.
                            mNavigationFragment.changeCurrentDir(
                                StorageHelper.getStoragePath(volumes[position]));
                }
            }
        });
        popup.show();
    }

    /**
     * {@inheritDoc}
     */
    public void updateTitleActionBar() {
        mBreadcrumb = (Breadcrumb)this.mRootView.findViewById(R.id.breadcrumb_view);
        // Set the free disk space warning level of the breadcrumb widget
        String fds = Preferences.getSharedPreferences().getString(
                FileManagerSettings.SETTINGS_DISK_USAGE_WARNING_LEVEL.getId(),
                (String)FileManagerSettings.SETTINGS_DISK_USAGE_WARNING_LEVEL.getDefaultValue());
        mBreadcrumb.setFreeDiskSpaceWarningLevel(Integer.parseInt(fds));
    }

    /**
     * {@inheritDoc}
     */
    public void pairBreadcrumb(int position, NavigationFragment fragment) {
        try {
            fragment.setBreadcrumb(mBreadcrumb);
        } catch (Throwable ex) {
            Log.e(TAG,
                    String.format("Failed to pair breadcrumb %d", //$NON-NLS-1$
                            position, ex));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBreadcrumbItemClick(File item) {
        this.mNavigationFragment.changeCurrentDir(item.getAbsolutePath());
    }

    /**
     * Method that applies the current theme to the activity
     * @hide
     */
    void applyTheme() {
        Theme theme = ThemeManager.getCurrentTheme(this);
        theme.setBaseTheme(this, true);
        this.mNavigationFragment.applyTheme();
    }
}
