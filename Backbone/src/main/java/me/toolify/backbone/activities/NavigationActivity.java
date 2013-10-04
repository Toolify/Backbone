/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2013 BrandroidTools
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

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.InflateException;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.otto.Subscribe;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import me.toolify.backbone.BuildConfig;
import me.toolify.backbone.FileManagerApplication;
import me.toolify.backbone.R;
import me.toolify.backbone.actionmode.PropertiesModeCallback;
import me.toolify.backbone.activities.preferences.SettingsPreferences;
import me.toolify.backbone.adapters.NavigationFragmentPagerAdapter;
import me.toolify.backbone.bus.BusProvider;
import me.toolify.backbone.bus.events.BookmarkDeleteEvent;
import me.toolify.backbone.bus.events.BookmarkOpenEvent;
import me.toolify.backbone.bus.events.BookmarkRefreshEvent;
import me.toolify.backbone.bus.events.ClosePropertiesDrawerEvent;
import me.toolify.backbone.bus.events.FilesystemStatusUpdateEvent;
import me.toolify.backbone.bus.events.OpenPropertiesDrawerEvent;
import me.toolify.backbone.console.Console;
import me.toolify.backbone.console.ConsoleAllocException;
import me.toolify.backbone.console.ConsoleBuilder;
import me.toolify.backbone.console.NoSuchFileOrDirectory;
import me.toolify.backbone.fragments.HistoryFragment;
import me.toolify.backbone.fragments.NavigationFragment;
import me.toolify.backbone.fragments.NavigationFragment.OnNavigationRequestMenuListener;
import me.toolify.backbone.listeners.OnCopyMoveListener;
import me.toolify.backbone.listeners.OnRequestRefreshListener;
import me.toolify.backbone.model.Bookmark;
import me.toolify.backbone.model.DiskUsage;
import me.toolify.backbone.model.FileSystemObject;
import me.toolify.backbone.model.History;
import me.toolify.backbone.model.MountPoint;
import me.toolify.backbone.parcelables.NavigationViewInfoParcelable;
import me.toolify.backbone.parcelables.SearchInfoParcelable;
import me.toolify.backbone.preferences.AccessMode;
import me.toolify.backbone.preferences.Bookmarks;
import me.toolify.backbone.preferences.FileManagerSettings;
import me.toolify.backbone.preferences.NavigationLayoutMode;
import me.toolify.backbone.preferences.NavigationSortMode;
import me.toolify.backbone.preferences.ObjectIdentifier;
import me.toolify.backbone.preferences.Preferences;
import me.toolify.backbone.ui.ThemeManager;
import me.toolify.backbone.ui.ThemeManager.Theme;
import me.toolify.backbone.ui.dialogs.FilesystemInfoDialog;
import me.toolify.backbone.ui.dialogs.FilesystemInfoDialog.OnMountListener;
import me.toolify.backbone.ui.dialogs.InputNameDialog;
import me.toolify.backbone.ui.policy.BookmarksActionPolicy;
import me.toolify.backbone.ui.policy.CopyMoveActionPolicy;
import me.toolify.backbone.ui.policy.CopyMoveActionPolicy.COPY_MOVE_OPERATION;
import me.toolify.backbone.ui.policy.NewActionPolicy;
import me.toolify.backbone.ui.widgets.BookmarksListView;
import me.toolify.backbone.ui.widgets.Breadcrumb;
import me.toolify.backbone.ui.widgets.BreadcrumbItem;
import me.toolify.backbone.ui.widgets.FsoPropertiesView;
import me.toolify.backbone.ui.widgets.NavigationCustomTitleView;
import me.toolify.backbone.util.CommandHelper;
import me.toolify.backbone.util.DialogHelper;
import me.toolify.backbone.util.ExceptionUtil;
import me.toolify.backbone.util.FileHelper;
import me.toolify.backbone.util.StorageHelper;

/**
 * The main navigation activity. This activity is the center of the application.
 * From this the user can navigate, search, make actions.<br/>
 * This activity is singleTop, so when it is displayed no other activities exists in
 * the stack.<br/>
 * This cause an issue with the saved instance of this class, because if another activity
 * is displayed, and the process is killed, NavigationActivity is started and the saved
 * instance gets corrupted.<br/>
 * For this reason the methods {link {@link android.app.Activity#onSaveInstanceState(android.os.Bundle)} and
 * {@link android.app.Activity#onRestoreInstanceState(android.os.Bundle)} are not implemented, and every time
 * the app is killed, is restarted from his initial state.
 */
public class NavigationActivity extends AbstractNavigationActivity
    implements OnRequestRefreshListener, OnCopyMoveListener,
        OnNavigationRequestMenuListener, OnPageChangeListener {

    private static final String TAG = "NavigationActivity"; //$NON-NLS-1$

    private static boolean DEBUG = false;

    /**
     * Intent code for request a bookmark selection.
     */
    public static final int INTENT_REQUEST_BOOKMARK = 10001;

    /**
     * Intent code for request a history selection.
     */
    public static final int INTENT_REQUEST_HISTORY = 20001;

    /**
     * Intent code for request a search.
     */
    public static final int INTENT_REQUEST_SEARCH = 30001;


    /**
     * Constant for extra information about selected bookmark.
     */
    public static final String EXTRA_BOOKMARK_SELECTION =
            "extra_bookmark_selection"; //$NON-NLS-1$

    /**
     * Constant for extra information about selected history entry.
     */
    public static final String EXTRA_HISTORY_ENTRY_SELECTION =
            "extra_history_entry_selection"; //$NON-NLS-1$

    /**
     * Constant for extra information about clear selection action.
     */
    public static final String EXTRA_HISTORY_CLEAR =
            "extra_history_clear_history"; //$NON-NLS-1$

    /**
     * Constant for extra information about selected search entry.
     */
    public static final String EXTRA_SEARCH_ENTRY_SELECTION =
            "extra_search_entry_selection"; //$NON-NLS-1$

    /**
     * Constant for extra information about last search data.
     */
    public static final String EXTRA_SEARCH_LAST_SEARCH_DATA =
            "extra_search_last_search_data"; //$NON-NLS-1$

    /**
     * Constant for extra information for request a navigation to the passed path.
     */
    public static final String EXTRA_NAVIGATE_TO =
            "extra_navigate_to"; //$NON-NLS-1$

    // The timeout needed to reset the exit status for back button
    // After this time user need to tap 2 times the back button to
    // exit, and the toast is shown again after the first tap.
    private static final int RELEASE_EXIT_CHECK_TIMEOUT = 3500;

    private final BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (intent.getAction().compareTo(FileManagerSettings.INTENT_SETTING_CHANGED) == 0) {
                    // The settings has changed
                    String key = intent.getStringExtra(FileManagerSettings.EXTRA_SETTING_CHANGED_KEY);
                    if (key != null) {
                        // Disk usage warning level
                        if (key.compareTo(FileManagerSettings.
                                SETTINGS_DISK_USAGE_WARNING_LEVEL.getId()) == 0) {

                            // Set the free disk space warning level of the breadcrumb widget
                            Breadcrumb breadcrumb = getCurrentNavigationFragment().getBreadcrumb();
                            String fds = Preferences.getSharedPreferences().getString(
                                    FileManagerSettings.SETTINGS_DISK_USAGE_WARNING_LEVEL.getId(),
                                    (String)FileManagerSettings.
                                        SETTINGS_DISK_USAGE_WARNING_LEVEL.getDefaultValue());
                            breadcrumb.setFreeDiskSpaceWarningLevel(Integer.parseInt(fds));
                            breadcrumb.updateMountPointInfo();
                            return;
                        }

                        // Case sensitive sort
                        if (key.compareTo(FileManagerSettings.
                                SETTINGS_CASE_SENSITIVE_SORT.getId()) == 0) {
                            getCurrentNavigationFragment().refresh();
                            return;
                        }

                        // Display thumbs
                        if (key.compareTo(FileManagerSettings.
                                SETTINGS_DISPLAY_THUMBS.getId()) == 0) {
                            // Clean the icon cache applying the current theme
                            applyTheme();
                            return;
                        }

                        // Use flinger
                        if (key.compareTo(FileManagerSettings.
                                SETTINGS_USE_FLINGER.getId()) == 0) {
                            boolean useFlinger =
                                    Preferences.getSharedPreferences().getBoolean(
                                            FileManagerSettings.SETTINGS_USE_FLINGER.getId(),
                                                ((Boolean)FileManagerSettings.
                                                        SETTINGS_USE_FLINGER.
                                                            getDefaultValue()).booleanValue());
                            getCurrentNavigationFragment().setUseFlinger(useFlinger);
                            return;
                        }

                        // Access mode
                        if (key.compareTo(FileManagerSettings.
                                SETTINGS_ACCESS_MODE.getId()) == 0) {
                            // Is it necessary to create or exit of the ChRooted?
                            boolean chRooted =
                                    FileManagerApplication.
                                        getAccessMode().compareTo(AccessMode.SAFE) == 0;
                            if (chRooted != NavigationActivity.this.mChRooted) {
                                if (chRooted) {
                                    createChRooted();
                                } else {
                                    exitChRooted();
                                }
                            }
                            // Update bookmarks to reflect access mode change
                            BusProvider.postEvent(new BookmarkRefreshEvent());
                        }

                        // Filetime format mode
                        if (key.compareTo(FileManagerSettings.
                                SETTINGS_FILETIME_FORMAT_MODE.getId()) == 0) {
                            // Refresh the data
                            synchronized (FileHelper.DATETIME_SYNC) {
                                FileHelper.sReloadDateTimeFormats = true;
                                NavigationActivity.this.getCurrentNavigationFragment().refresh();
                            }
                        }
                    }

                } else if (intent.getAction().compareTo(
                        FileManagerSettings.INTENT_FILE_CHANGED) == 0) {
                    // Retrieve the file that was changed
                    String file =
                            intent.getStringExtra(FileManagerSettings.EXTRA_FILE_CHANGED_KEY);
                    try {
                        FileSystemObject fso = CommandHelper.getFileInfo(context, file, null);
                        if (fso != null) {
                            getCurrentNavigationFragment().refresh(fso);
                        }
                    } catch (Exception e) {
                        ExceptionUtil.translateException(context, e, true, false);
                    }

                } else if (intent.getAction().compareTo(
                        FileManagerSettings.INTENT_THEME_CHANGED) == 0) {
                    applyTheme();

                } else if (intent.getAction().compareTo(Intent.ACTION_TIME_CHANGED) == 0 ||
                           intent.getAction().compareTo(Intent.ACTION_DATE_CHANGED) == 0 ||
                           intent.getAction().compareTo(Intent.ACTION_TIMEZONE_CHANGED) == 0) {
                    // Refresh the data
                    synchronized (FileHelper.DATETIME_SYNC) {
                        FileHelper.sReloadDateTimeFormats = true;
                        NavigationActivity.this.getCurrentNavigationFragment().refresh();
                    }
                }
            }
        }
    };

    /**
     * @hide
     */
    private ActionBar mActionBar;
    private Menu mOptionsMenu;
    private MenuItem mFilesystemInfo;
    private int mFilesystemStatus;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private BookmarksListView mBookmarkDrawer;
    private FsoPropertiesView mInfoDrawer;
    private PropertiesModeCallback mPropertiesModeCallback;
    private View mTitleLayout;
    private NavigationCustomTitleView mTitle;
    private Breadcrumb mBreadcrumb;

    public NavigationFragmentPagerAdapter mPagerAdapter;
    public ViewPager mViewPager;

    private boolean mExitFlag = false;
    private long mExitBackTimeout = -1;

    /**
     * @hide
     */
    boolean mChRooted;

    /**
     * @hide
     */
    Handler mHandler;

    /**
     * @hide
     */
    private List<FileSystemObject> mFilesForPaste;
    /**
     * @hide
     */
    private COPY_MOVE_OPERATION mPasteOperationType;

    /**
     * {@inheritDoc}
     */
    @TargetApi(16)
	@Override
    protected void onCreate(Bundle state) {

        if (DEBUG) {
            Log.d(TAG, "NavigationActivity.onCreate"); //$NON-NLS-1$
        }

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(FileManagerSettings.INTENT_SETTING_CHANGED);
        filter.addAction(FileManagerSettings.INTENT_FILE_CHANGED);
        filter.addAction(FileManagerSettings.INTENT_THEME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(this.mNotificationReceiver, filter);

        // Initialize NFC adapter
        NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter != null && Build.VERSION.SDK_INT > 16) {
            mNfcAdapter.setBeamPushUrisCallback(new NfcAdapter.CreateBeamUrisCallback() {
                @Override
                public Uri[] createBeamUris(NfcEvent event) {
                    List<FileSystemObject> selectedFiles =
                            getCurrentNavigationFragment().getSelectedFiles();
                    if (selectedFiles.size() > 0) {
                        List<Uri> fileUri = new ArrayList<Uri>();
                        for (FileSystemObject f : selectedFiles) {
                            //Beam ignores folders and system files
                            if (!FileHelper.isDirectory(f) && !FileHelper.isSystemFile(f)) {
                                fileUri.add(Uri.fromFile(new File(f.getFullPath())));
                            }
                        }
                        if (fileUri.size() > 0) {
                            return fileUri.toArray(new Uri[fileUri.size()]);
                        }
                    }
                    return null;
                }
            }, this);
        }

        setContentView(R.layout.navigation);
        
        //Initialize activity console
        init();
        
        //Initialize viewPager
        initViewPager();

        //Initialize action bar
        mActionBar = getActionBar();
        initTitleActionBar();

        // Create ActionBar drawer toggle drawable
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                // Reload the fragments current dir into the breadcrumb
                if (mBreadcrumb != null) {
                    mBreadcrumb.changeBreadcrumbPath(getCurrentNavigationFragment().getCurrentDir(), mChRooted);
                }
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()

                // If the properties drawer was just closed, we should lock it closed and make sure
                // that the properties action mode is closed too.
                if (view instanceof FsoPropertiesView) {
                    BusProvider.postEvent(new ClosePropertiesDrawerEvent());
                }
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                getActionBar().setTitle(R.string.bookmarks);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        mBookmarkDrawer = (BookmarksListView) findViewById(R.id.left_drawer);
        mInfoDrawer = (FsoPropertiesView) findViewById(R.id.right_drawer);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, mInfoDrawer);

        // Apply the theme
        applyTheme();

        // Show welcome message
        showWelcomeMsg();

        //Save state
        super.onCreate(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onNewIntent(Intent intent) {
        //Initialize navigation
        NavigationActivity.this.getCurrentNavigationFragment().initNavigation(true, intent);

        //Check the intent action
        checkIntent(intent);
    }

    /**
     * {@inheritDoc}
     */
    @Override 
    protected void onPause() {
        super.onPause();

        // Always unregister when an object no longer should be on the bus.
        BusProvider.unregister(this);
        BusProvider.unregister(mBookmarkDrawer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        mViewPager.setOnPageChangeListener(this);

        // Register ourselves so that we can provide the initial value.
        BusProvider.register(this);
        BusProvider.register(mBookmarkDrawer);

        // Tell the bookmarks fragment to refresh itself
        BusProvider.postEvent(new BookmarkRefreshEvent());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "NavigationActivity.onDestroy"); //$NON-NLS-1$
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    /**
     * Method that returns the current navigation view.
     *
     * @return NavigationFragment The current navigation view
     */
    public NavigationFragment getCurrentNavigationFragment() {
        return mPagerAdapter.getFragment(mViewPager, mViewPager.getCurrentItem());
    }

    /**
     * Method that returns the requested navigation view.
     *
     * @param fragmentNum The fragment to return
     * @return NavigationFragment The requested navigation view
     */
    public NavigationFragment getNavigationFragment(int fragmentNum) {
        return mPagerAdapter.getFragment(mViewPager, fragmentNum);
    }

    /**
     * Method that initializes the activity.
     */
    private void init() {
        this.mChRooted = FileManagerApplication.getAccessMode().compareTo(AccessMode.SAFE) == 0;

        this.mHandler = new Handler();
        this.mHandler.post(new Runnable() {
            @Override
            public void run() {
                //Create the default console (from the preferences)
                try {
                    Console console = ConsoleBuilder.getConsole(NavigationActivity.this);
                    if (console == null) {
                        throw new ConsoleAllocException("console == null"); //$NON-NLS-1$
                    }
                } catch (Throwable ex) {
                    if (!NavigationActivity.this.isChRooted()) {
                        //Show exception and exists
                        Log.e(TAG, getString(R.string.msgs_cant_create_console), ex);
                        // We don't have any console
                        // Show exception and exists
                        DialogHelper.showToast(
                                NavigationActivity.this,
                                R.string.msgs_cant_create_console, Toast.LENGTH_LONG);
                        NavigationActivity.this.exit();
                        return;
                    }

                    // We are in a trouble (something is not allowing creating the console)
                    // Ask the user to return to prompt or root access mode mode with a
                    // non-privileged console, prior to make crash the application
                    NavigationActivity.this.askOrExit();
                    return;
                }
            }
        });
    }

    /**
     * Method that displays a welcome message the first time the user
     * access the application
     */
    private void showWelcomeMsg() {
        boolean firstUse = Preferences.getSharedPreferences().getBoolean(
                FileManagerSettings.SETTINGS_FIRST_USE.getId(),
                ((Boolean)FileManagerSettings.SETTINGS_FIRST_USE.getDefaultValue()).booleanValue());

        //Display the welcome message?
        if (firstUse) {
            AlertDialog dialog = DialogHelper.createAlertDialog(
                this, R.drawable.ic_launcher,
                R.string.welcome_title, getString(R.string.welcome_msg), false);
            DialogHelper.delegateDialogShow(this, dialog);

            // Don't display again this dialog
            try {
                Preferences.savePreference(
                        FileManagerSettings.SETTINGS_FIRST_USE, Boolean.FALSE, true);
            } catch (Exception e) {/**NON BLOCK**/}
        }
    }

    /**
     * Method that initializes the ViewPager and NavigationPagerAdapter
     */
    private void initViewPager(){
        mViewPager = (ViewPager)findViewById(R.id.navigation_pager);

        // Plug the ViewPager into the Pager Adapter and set the number of pages
        mPagerAdapter = new NavigationFragmentPagerAdapter(this, getFragmentManager(), 2);
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setHorizontalScrollBarEnabled(true);

        // TODO this is a pretty much a hack job for now, and proper state
        // retaining needs to be implemented for tablets
        mViewPager.setOffscreenPageLimit(mPagerAdapter.getCount());
    }

    /**
     * Method that initializes the titlebar of the activity.
     */
    private void initTitleActionBar() {
        //Inflate the view and associate breadcrumb
        mTitleLayout = getLayoutInflater().inflate(
                R.layout.navigation_view_customtitle, null, false);
        mTitle = (NavigationCustomTitleView)mTitleLayout.findViewById(R.id.navigation_title_flipper);
        mBreadcrumb = (Breadcrumb)mTitle.findViewById(R.id.breadcrumb_view);

        // Set the free disk space warning level of the breadcrumb widget
        String fds = Preferences.getSharedPreferences().getString(
                FileManagerSettings.SETTINGS_DISK_USAGE_WARNING_LEVEL.getId(),
                (String)FileManagerSettings.SETTINGS_DISK_USAGE_WARNING_LEVEL.getDefaultValue());
        mBreadcrumb.setFreeDiskSpaceWarningLevel(Integer.parseInt(fds));
        //Configure the action bar options
        mActionBar.setDisplayOptions(
                ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME);
        mActionBar.setCustomView(mTitleLayout);
    }

    /**
     * {@inheritDoc}
     */
    public void updateTitleActionBar() {

        NavigationFragment navigationFragment = getCurrentNavigationFragment();
        mTitle.setOnHistoryListener(navigationFragment);
        navigationFragment.setBreadcrumb(mBreadcrumb);
        navigationFragment.setOnHistoryListener(navigationFragment);
        navigationFragment.setOnNavigationOnRequestMenuListener(this);
        navigationFragment.setCustomTitle(mTitle);
    }

    /**
     * Method that verifies the intent passed to the activity, and checks
     * if a request is made like Search.
     *
     * @param intent The intent to check
     * @hide
     */
    void checkIntent(Intent intent) {
        //Search action
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            Intent searchIntent = new Intent(this, SearchActivity.class);
            searchIntent.setAction(Intent.ACTION_SEARCH);
            //- SearchActivity.EXTRA_SEARCH_DIRECTORY
            searchIntent.putExtra(
                    SearchActivity.EXTRA_SEARCH_DIRECTORY,
                    getCurrentNavigationFragment().getCurrentDir());
            //- SearchManager.APP_DATA
            if (intent.getBundleExtra(SearchManager.APP_DATA) != null) {
                Bundle bundle = new Bundle();
                bundle.putAll(intent.getBundleExtra(SearchManager.APP_DATA));
                searchIntent.putExtra(SearchManager.APP_DATA, bundle);
            }
            //-- SearchManager.QUERY
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (query != null) {
                searchIntent.putExtra(SearchManager.QUERY, query);
            }
            //- android.speech.RecognizerIntent.EXTRA_RESULTS
            ArrayList<String> extraResults =
                    intent.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (extraResults != null) {
                searchIntent.putStringArrayListExtra(
                        android.speech.RecognizerIntent.EXTRA_RESULTS, extraResults);
            }
            startActivityForResult(searchIntent, INTENT_REQUEST_SEARCH);
            return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (checkBackAction()) {
                return true;
            }

            // An exit event has occurred, force the destroy the consoles
            exit();
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.mOptionsMenu = menu;
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.navigation, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        // Ensure that the appropriate sort mode menuItem radio is selected
        NavigationSortMode sortMode = NavigationSortMode.fromId(Preferences.getSharedPreferences()
                .getInt(FileManagerSettings.SETTINGS_SORT_MODE.getId(),
                        ((ObjectIdentifier)FileManagerSettings.SETTINGS_SORT_MODE
                                .getDefaultValue()).getId()));
        switch(sortMode) {
            case DATE_ASC:
                menu.findItem(R.id.mnu_actions_sort_by_date_asc).setChecked(true);
                break;
            case DATE_DESC:
                menu.findItem(R.id.mnu_actions_sort_by_date_desc).setChecked(true);
                break;
            case NAME_ASC:
                menu.findItem(R.id.mnu_actions_sort_by_name_asc).setChecked(true);
                break;
            case NAME_DESC:
                menu.findItem(R.id.mnu_actions_sort_by_name_desc).setChecked(true);
                break;
        }

        // Ensure that the appropriate layout mode menuItem radio is selected
        NavigationLayoutMode layoutMode = NavigationLayoutMode.fromId(Preferences.
                getSharedPreferences().getInt(FileManagerSettings.SETTINGS_LAYOUT_MODE.getId(),
                        ((ObjectIdentifier)FileManagerSettings.SETTINGS_LAYOUT_MODE
                                .getDefaultValue()).getId()));
        switch(layoutMode) {
            case ICONS:
                menu.findItem(R.id.mnu_actions_layout_icons).setChecked(true);
                break;
            case DETAILS:
                menu.findItem(R.id.mnu_actions_layout_details).setChecked(true);
                break;
            case SIMPLE:
                menu.findItem(R.id.mnu_actions_layout_simple).setChecked(true);
                break;
        }

        // Ensure that the other view mode options are correctly selected
        int[] menuSettingIDs = new int[] {
                R.id.mnu_actions_show_dirs_first,
                R.id.mnu_actions_show_hidden,
                R.id.mnu_actions_show_system,
                R.id.mnu_actions_show_symlinks
        };
        FileManagerSettings[] settingShows = new FileManagerSettings[] {
                FileManagerSettings.SETTINGS_SHOW_DIRS_FIRST,
                FileManagerSettings.SETTINGS_SHOW_HIDDEN,
                FileManagerSettings.SETTINGS_SHOW_SYSTEM,
                FileManagerSettings.SETTINGS_SHOW_SYMLINKS
        };
        for(int i = 0; i < menuSettingIDs.length; i++) {
            MenuItem item = menu.findItem(menuSettingIDs[i]);
            if(item != null) {
                item.setChecked(Preferences.getSharedPreferences().getBoolean(
                        settingShows[i].getId(),
                        Boolean.parseBoolean(settingShows[i].getDefaultValue().toString())));
            }
        }

        // Make paste action visible if there are files available for pasting
        menu.findItem(R.id.mnu_actions_paste_selection).setVisible(this.onAreFilesMarkedForPaste());
        // Toggle layout mode visibility so that it is only visible in development mode
        menu.findItem(R.id.mnu_actions_layout_mode).setVisible(BuildConfig.DEBUG);
        mFilesystemInfo = menu.findItem(R.id.mnu_actions_show_filesystem_info);
        setFilesystemStatusDrawable(mFilesystemStatus);
        return super.onPrepareOptionsMenu(menu);
    }


    /**
     * Called by various pieces of code responsible for updating file listing or breadcrumb data.
     * This is an Otto event designed to de-couple this activity and various asyncTasks responsible
     * for gathering and sending file info.
     */
    @Subscribe
    public void onFilesystemStatusUpdate(FilesystemStatusUpdateEvent event) {
        mFilesystemStatus = event.status;
        setFilesystemStatusDrawable(event.status);
    }

    private void setFilesystemStatusDrawable(int fileSystemstatus){

        TypedArray a = getTheme().obtainStyledAttributes(R.styleable.FileManager);

        switch (fileSystemstatus) {

            case FilesystemStatusUpdateEvent.INDICATOR_UNLOCKED:
                if (mFilesystemInfo != null) {
                    mFilesystemInfo.setIcon(getResources().
                            getDrawable(a.getResourceId(R.styleable.FileManager_actionIconLockOpen,
                                    R.drawable.ic_action_holo_dark_lock_open)));
                    setFilesystemInfoProgressState(false);
                }
                break;

            case FilesystemStatusUpdateEvent.INDICATOR_LOCKED:
                if (mFilesystemInfo != null) {
                    mFilesystemInfo.setIcon(getResources().
                            getDrawable(a.getResourceId(R.styleable.FileManager_actionIconLockClosed,
                                    R.drawable.ic_action_holo_dark_lock_closed)));
                    setFilesystemInfoProgressState(false);
                }
                break;

            case FilesystemStatusUpdateEvent.INDICATOR_WARNING:
                if (mFilesystemInfo != null) {
                    mFilesystemInfo.setIcon(getResources().
                            getDrawable(a.getResourceId(R.styleable.FileManager_actionIconWarning,
                                    R.drawable.ic_action_holo_dark_warning)));
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
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Drawer Toggle pass-off
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        // Action Items
        switch (item.getItemId()) {
            case R.id.mnu_actions_sort_by_date_asc:
                FileManagerSettings.setSorting(NavigationSortMode.DATE_ASC);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_sort_by_date_desc:
                FileManagerSettings.setSorting(NavigationSortMode.DATE_DESC);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_sort_by_name_asc:
                FileManagerSettings.setSorting(NavigationSortMode.NAME_ASC);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_sort_by_name_desc:
                FileManagerSettings.setSorting(NavigationSortMode.NAME_DESC);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_show_dirs_first:
                FileManagerSettings.toggleViewPreference(FileManagerSettings.SETTINGS_SHOW_DIRS_FIRST);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_show_hidden:
                FileManagerSettings.toggleViewPreference(FileManagerSettings.SETTINGS_SHOW_HIDDEN);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_show_system:
                FileManagerSettings.toggleViewPreference(FileManagerSettings.SETTINGS_SHOW_SYSTEM);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_show_symlinks:
                FileManagerSettings.toggleViewPreference(FileManagerSettings.SETTINGS_SHOW_SYMLINKS);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_layout_simple:
                FileManagerSettings.setLayout(NavigationLayoutMode.SIMPLE);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_layout_details:
                FileManagerSettings.setLayout(NavigationLayoutMode.DETAILS);
                getCurrentNavigationFragment().refresh();
                break;

            case R.id.mnu_actions_layout_icons:
                FileManagerSettings.setLayout(NavigationLayoutMode.ICONS);
                getCurrentNavigationFragment().refresh();
                break;

            // Show information of the filesystem
            case R.id.mnu_actions_show_filesystem_info:
                MountPoint mp = getCurrentNavigationFragment().getBreadcrumb().getMountPointInfo();
                DiskUsage du = getCurrentNavigationFragment().getBreadcrumb().getDiskUsageInfo();
                showMountPointInfo(mp, du);
                break;

            case R.id.mnu_actions_history:
                openHistory();
                break;

            case R.id.mnu_actions_search:
                openSearch();
                break;

            // Refresh the current navigation fragment
            case R.id.mnu_actions_refresh:
                getCurrentNavigationFragment().refresh();
                break;

            // Create new object
            case R.id.mnu_actions_new_file:
                showFileTypeDialog(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0)
                            showInputNameDialog(which);
                        else dialog.dismiss();
                    }
                });
                break;

            // Paste selection
            case R.id.mnu_actions_paste_selection:
                if (true) {
                    CopyMoveActionPolicy.triggerCopyMoveFileSystemObjects(
                            NavigationActivity.this,
                            this.onRequestFilesMarkedForPaste(),
                            this.onRequestPasteOperationType(),
                            getCurrentNavigationFragment(),
                            getCurrentNavigationFragment(),
                            NavigationActivity.this);
                    /* Clear files as soon as the user decides to paste, thus permitting more copy
                       selections while this paste task finishes. */
                    onClearFilesMarkedForPaste();
                }
                break;

            // Open a properties drawer on the current directory
            case R.id.mnu_actions_properties_current_folder:
                BusProvider.postEvent(new OpenPropertiesDrawerEvent(
                        getCurrentNavigationFragment().getCurrentDir()));
                break;

            // Add current directory to bookmarks
            case R.id.mnu_actions_add_to_bookmarks:
                try {
                    FileSystemObject bookmarkFso = CommandHelper.getFileInfo(this,
                            getCurrentNavigationFragment().getCurrentDir(),
                            null);
                    BookmarksActionPolicy.addToBookmarks(this, bookmarkFso);
                    BusProvider.postEvent(new BookmarkRefreshEvent());
                } catch (Exception e) {
                    ExceptionUtil.translateException(this, e, true, false);
                }
                break;
            // Open settings
            case R.id.mnu_settings:
                Intent settings = new Intent(
                NavigationActivity.this, SettingsPreferences.class);
                startActivity(settings);
                break;

            default:
               return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This function switches an action item between its normal icon and an indeterminate progress
     * circle
     * @param refreshing value is true if the action item should show the progress bar
     */
    public void setFilesystemInfoProgressState(final boolean refreshing) {
        if (mFilesystemInfo != null) {
            final MenuItem refreshItem = mOptionsMenu
                    .findItem(R.id.mnu_actions_show_filesystem_info);
            if (refreshItem != null) {
                if (refreshing) {
                    refreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
                } else {
                    refreshItem.setActionView(null);
                }
            }
        }
    }
    
    @Subscribe
    public void onBookmarkOpen(BookmarkOpenEvent event) {
    	String path = event.path;
    	// Check that the bookmark exists
        try {
            FileSystemObject fso = CommandHelper.getFileInfo(this, path, null);
            if (fso != null) {
            	getCurrentNavigationFragment().open(fso);
                mDrawerLayout.closeDrawer(mBookmarkDrawer);
            } else {
                // The bookmark not exists, delete the user-defined bookmark
                try {
                	BusProvider.postEvent(new BookmarkDeleteEvent(path));
                	Bookmark b = Bookmarks.getBookmark(getContentResolver(), path);
                    Bookmarks.removeBookmark(this, b);
                    BusProvider.postEvent(new BookmarkRefreshEvent());
                } catch (Exception ex) {/**NON BLOCK**/}
            }
        } catch (Exception e) {
            // Capture the exception
            ExceptionUtil.translateException(this, e);
            if (e instanceof NoSuchFileOrDirectory || e instanceof FileNotFoundException) {
                // The bookmark not exists, delete the user-defined bookmark
                try {
                	BusProvider.postEvent(new BookmarkDeleteEvent(path));
                } catch (Exception ex) {/**NON BLOCK**/}
            }
            return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            switch (requestCode) {
                case INTENT_REQUEST_HISTORY:
                    if (resultCode == RESULT_OK) {
                        //Change current directory
                        History history =
                                (History)data.getSerializableExtra(EXTRA_HISTORY_ENTRY_SELECTION);
                        navigateToHistory(history);
                    } else if (resultCode == RESULT_CANCELED) {
                        boolean clear = data.getBooleanExtra(EXTRA_HISTORY_CLEAR, false);
                        if (clear) {
                            clearHistory();
                        }
                    }
                    break;

                case INTENT_REQUEST_SEARCH:
                    if (resultCode == RESULT_OK) {
                        //Change directory?
                        FileSystemObject fso =
                                (FileSystemObject)data.
                                    getSerializableExtra(EXTRA_SEARCH_ENTRY_SELECTION);
                        SearchInfoParcelable searchInfo =
                                data.getParcelableExtra(EXTRA_SEARCH_LAST_SEARCH_DATA);
                        if (fso != null) {
                            //Goto to new directory
                            getCurrentNavigationFragment().open(fso, searchInfo);
                        }
                    } else if (resultCode == RESULT_CANCELED) {
                        SearchInfoParcelable searchInfo =
                                data.getParcelableExtra(EXTRA_SEARCH_LAST_SEARCH_DATA);
                        if (searchInfo != null && searchInfo.isSuccessNavigation()) {
                            //Navigate to previous history
                            back();
                        } else {
                            // I don't know is the search view was changed, so try to do a refresh
                            // of the navigation view
                            getCurrentNavigationFragment().refresh(true);
                        }
                    }
                    break;

                default:
                    break;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestRefresh(Object o, boolean clearSelection) {
        if (o instanceof FileSystemObject) {
            // Refresh only the item
            this.getCurrentNavigationFragment().refresh((FileSystemObject) o);
        } else if (o == null) {
            // Refresh all
            getCurrentNavigationFragment().refresh();
        }
        if (clearSelection) {
            this.getCurrentNavigationFragment().onDeselectAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestRemove(Object o, boolean clearSelection) {
        if (o instanceof FileSystemObject) {
            // Remove from view
            this.getCurrentNavigationFragment().removeItem((FileSystemObject) o);

            //Remove from history
            getCurrentNavigationFragment().removeFromHistory((FileSystemObject) o);
        } else {
            onRequestRefresh(null, clearSelection);
        }
        if (clearSelection) {
            this.getCurrentNavigationFragment().onDeselectAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBreadcrumbItemClick(BreadcrumbItem item) {
        getCurrentNavigationFragment().changeCurrentDir(item.getItemPath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNavigateTo(Object o) {
        // Ignored
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestMenu(NavigationFragment navFragment, FileSystemObject item) {

    }

    /**
     * Show/hide the "selection" action mode, according to the number of
     * selected messages and the visibility of the fragment. Also update the
     * content (title and menus) if necessary.
     */
    public void startPropertiesActionMode(FileSystemObject fso) {
        mPropertiesModeCallback = new PropertiesModeCallback(this, fso);
        mPropertiesModeCallback.setOnRequestRefreshListener(this);
        mPropertiesModeCallback.setOnCopyMoveListener(this);
        startActionMode(mPropertiesModeCallback);
    }

    /**
     * Finish the "properties" action mode.
     *
     */
    private void finishPropertiesActionMode() {
        // Close the action mode
        if (isInPropertiesActionMode()) {
            mPropertiesModeCallback.setClosedByUser(false);
            mPropertiesModeCallback.finish();
        }
        // Close and lock the info drawer
        if (mDrawerLayout.isDrawerOpen(mInfoDrawer)) {
            mDrawerLayout.closeDrawer(mInfoDrawer);
        }
        // The properties drawer has been closed, so lock it shut and unlock the bookmarks bar.
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, mInfoDrawer);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, mBookmarkDrawer);
    }

    /**
     * @return true if the list is in the "selection" mode.
     */
    private boolean isInPropertiesActionMode() {
        if (mPropertiesModeCallback == null) {
            return false;
        } else if (mPropertiesModeCallback.inPropertiesActionMode()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Method that show the information of a filesystem mount point.
     *
     * @param mp The mount point info
     * @param du The disk usage of the mount point
     */
    private void showMountPointInfo(MountPoint mp, DiskUsage du) {
        //Has mount point info?
        if (mp == null) {
            //There is no information
            AlertDialog alert =
                    DialogHelper.createWarningDialog(
                            this,
                            R.string.filesystem_info_warning_title,
                            R.string.filesystem_info_warning_msg);
            DialogHelper.delegateDialogShow(this, alert);
            return;
        }

        //Show a the filesystem info dialog
        FilesystemInfoDialog dialog = new FilesystemInfoDialog(this, mp, du);
        dialog.setOnMountListener(new OnMountListener() {
            @Override
            public void onRemount(MountPoint mountPoint) {
                //Update the statistics of breadcrumb, only if mount point is the same
                Breadcrumb breadcrumb = getCurrentNavigationFragment().getBreadcrumb();
                if (breadcrumb.getMountPointInfo().compareTo(mountPoint) == 0) {
                    breadcrumb.updateMountPointInfo();
                }
            }
        });
        dialog.show();
    }

    /**
     * Method that checks the action that must be realized when the
     * back button is pushed.
     *
     * @return boolean Indicates if the action must be intercepted
     */
    private boolean checkBackAction() {
        // We need a basic structure to check this
        if (getCurrentNavigationFragment() == null) return false;

        //Check if the configuration view is showing. In this case back
        //action must be "close configuration"
        if (getCurrentNavigationFragment().getCustomTitle().isConfigurationViewShowing()) {
            getCurrentNavigationFragment().getCustomTitle().restoreView();
            return true;
        }

        //Do back operation over the navigation history
        boolean flag = this.mExitFlag;

        this.mExitFlag = !back();

        // Retrieve if the exit status timeout has expired
        long now = System.currentTimeMillis();
        boolean timeout = (this.mExitBackTimeout == -1 ||
                            (now - this.mExitBackTimeout) > RELEASE_EXIT_CHECK_TIMEOUT);

        //Check if there no history and if the user was advised in the last back action
        if (this.mExitFlag && (this.mExitFlag != flag || timeout)) {
            //Communicate the user that the next time the application will be closed
            this.mExitBackTimeout = System.currentTimeMillis();
            DialogHelper.showToast(this, R.string.msgs_push_again_to_exit, Toast.LENGTH_SHORT);
            return true;
        }

        //Back action not applied
        return !this.mExitFlag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onSearchRequested() {
        Bundle bundle = new Bundle();
        bundle.putString(
                SearchActivity.EXTRA_SEARCH_DIRECTORY,
                getCurrentNavigationFragment().getCurrentDir());
        startSearch(Preferences.getLastSearch(), true, bundle, false);
        return true;
    }

    /**
     * Method that clears the fragment history.
     */
    private void clearHistory() {
        getCurrentNavigationFragment().mHistory.clear();
        getCurrentNavigationFragment().onCheckHistory();
    }

    /**
     * Method that navigates to the passed history reference.
     *
     * @param history The history reference
     * @return boolean A problem occurs while navigate
     */
    public synchronized boolean navigateToHistory(History history) {
        try {
            NavigationFragment currentNavFragment = getCurrentNavigationFragment();
            //Gets the history
            History realHistory = currentNavFragment.mHistory.get(history.getPosition());

            //Navigate to item. Check what kind of history is
            if (realHistory.getItem() instanceof NavigationViewInfoParcelable) {
                //Navigation
                NavigationViewInfoParcelable info =
                        (NavigationViewInfoParcelable)realHistory.getItem();
                // Selected items must not be restored from on history navigation
                info.setSelectedFiles(currentNavFragment.getSelectedFiles());
                if (!currentNavFragment.onRestoreState(info)) {
                    return true;
                }

            } else if (realHistory.getItem() instanceof SearchInfoParcelable) {
                //Search (open search with the search results)
                SearchInfoParcelable info = (SearchInfoParcelable)realHistory.getItem();
                Intent searchIntent = new Intent(this, SearchActivity.class);
                searchIntent.setAction(SearchActivity.ACTION_RESTORE);
                searchIntent.putExtra(SearchActivity.EXTRA_SEARCH_RESTORE, (Parcelable)info);
                startActivityForResult(searchIntent, INTENT_REQUEST_SEARCH);
            } else {
                //The type is unknown
                throw new IllegalArgumentException("Unknown history type"); //$NON-NLS-1$
            }

            //Remove the old history
            int cc = realHistory.getPosition();
            for (int i = currentNavFragment.mHistory.size() - 1; i >= cc; i--) {
                currentNavFragment.mHistory.remove(i);
            }

            //Navigate
            return true;

        } catch (Throwable ex) {
            if (history != null) {
                Log.e(TAG,
                        String.format("Failed to navigate to history %d: %s", //$NON-NLS-1$
                                Integer.valueOf(history.getPosition()),
                                history.getItem().getTitle()), ex);
            } else {
                Log.e(TAG,
                        String.format("Failed to navigate to history: null", ex)); //$NON-NLS-1$
            }
            this.mHandler.post(new Runnable() {
                @Override
                public void run() {
                    DialogHelper.showToast(
                            NavigationActivity.this,
                            R.string.msgs_history_unknown, Toast.LENGTH_LONG);
                }
            });

            //Not change directory
            return false;
        }
    }

    /**
     * Method that request a back action over the navigation history.
     *
     * @return boolean If a back action was applied
     */
    public boolean back() {
        NavigationFragment currentNavFragment = getCurrentNavigationFragment();
        // Check that has valid history
        while (currentNavFragment.mHistory.size() > 0) {
            History h = currentNavFragment.mHistory.get(currentNavFragment.mHistory.size() - 1);
            if (h.getItem() instanceof NavigationViewInfoParcelable) {
                // Verify that the path exists
                String path = ((NavigationViewInfoParcelable)h.getItem()).getCurrentDir();

                try {
                    FileSystemObject info = CommandHelper.getFileInfo(this, path, null);
                    if (info != null) {
                        break;
                    }
                    currentNavFragment.mHistory.remove(currentNavFragment.mHistory.size() - 1);
                } catch (Exception e) {
                    ExceptionUtil.translateException(this, e, true, false);
                    currentNavFragment.mHistory.remove(currentNavFragment.mHistory.size() - 1);
                }
            } else {
                break;
            }
        }

        //Extract a history from the
        if (currentNavFragment.mHistory.size() > 0) {
            //Navigate to history
            return navigateToHistory(currentNavFragment.mHistory.get(currentNavFragment.mHistory.size() - 1));
        }

        //Nothing to apply
        return false;
    }

    /**
     * Method that show a new dialog for input a name.
     *
     * @param fileTypeIndex The file_type array index associated
     */
    private void showInputNameDialog(final int fileTypeIndex) {
        String[] fileTypes = getResources().getStringArray(R.array.file_types);
        String title = fileTypes[0];
        if(fileTypeIndex < fileTypes.length)
            title = fileTypes[fileTypeIndex];

        //Show the input name dialog
        final InputNameDialog inputNameDialog =
                new InputNameDialog(
                        this,
                        getCurrentNavigationFragment().onRequestCurrentItems(),
                        title);
        inputNameDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                //Retrieve the name an execute the action
                try {
                    String name = inputNameDialog.getName();
                    createNewFileSystemObject(fileTypeIndex, name);
                } catch (InflateException e) {
                    Log.e(TAG, "Could not create the input name dialog");
                }
            }
        });
        inputNameDialog.show();
    }

    private static int getFileTypeIcon(Context context, int position)
    {
        TypedArray ar = context.getResources().obtainTypedArray(R.array.file_type_icons);
        if(position < 0 || position > ar.length()) return 0;
        return ar.getResourceId(position, 0);
    }

    private void showFileTypeDialog(final DialogInterface.OnClickListener onclick)
    {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1,
                getResources().getStringArray(R.array.file_types)){
            public View getView(int position, View convertView, ViewGroup parent) {
                View ret = super.getView(position, convertView, parent);
                if(ret instanceof TextView)
                {
                    int res = getFileTypeIcon(getContext(), position);
                    if(res > 0)
                    {
                        Drawable d = getResources().getDrawable(res);
                        ((TextView)ret).setCompoundDrawablePadding(8);
                        ((TextView)ret).setCompoundDrawablesRelativeWithIntrinsicBounds(
                                d, null, null, null);
                    }
                    return ret;
                }
                return ret;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_file_type)
                .setAdapter(adapter, onclick)
                .setNegativeButton(R.string.cancel, onclick)
                .create()
                .show();
    }

    /**
     * Method that create the a new file system object.
     *
     * @param fileTypeIndex The file_type array index
     * @param name The name of the file system object
     * @hide
     */
    void createNewFileSystemObject(final int fileTypeIndex, final String name) {
        switch (fileTypeIndex) {
            case 0:
                NewActionPolicy.createNewDirectory(this, name, getCurrentNavigationFragment(), this);
                break;
            case 1:
                NewActionPolicy.createNewFile(this, name, getCurrentNavigationFragment(), this);
                break;
            default:
                break;
        }
    }

    /**
     * Method that opens a properties drawer and starts a properties action mode on a file
     *
     * @param event The event referencing the file
     */
    @Subscribe
    public void openPropertiesDrawer(OpenPropertiesDrawerEvent event) {


        // Resolve the full path
        String path = String.valueOf(event.item);
        if (event.item instanceof FileSystemObject) {
            path = ((FileSystemObject)event.item).getFullPath();
        }

        // Prior to show the dialog, refresh the item reference
        FileSystemObject fso = null;
        try {
            fso = CommandHelper.getFileInfo(this, path, false, null);
            if (fso == null) {
                throw new NoSuchFileOrDirectory(path);
            }

        } catch (Exception e) {
            // Notify the user
            ExceptionUtil.translateException(this, e);

            // Remove the object
            if (e instanceof FileNotFoundException || e instanceof NoSuchFileOrDirectory) {
                // If have a FileSystemObject reference then there is no need to search
                // the path (less resources used)
                if (event.item instanceof FileSystemObject) {
                    getCurrentNavigationFragment().removeItem((FileSystemObject) event.item);
                } else {
                    getCurrentNavigationFragment().removeItem((String) event.item);
                }
            }
            return;
        }

        if (mDrawerLayout.isDrawerOpen(mInfoDrawer)) {
            mDrawerLayout.closeDrawer(mInfoDrawer);
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, mInfoDrawer);
            finishPropertiesActionMode();
        } else {
            startPropertiesActionMode(fso);
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, mInfoDrawer);
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, mBookmarkDrawer);
            mDrawerLayout.openDrawer(mInfoDrawer);
            mInfoDrawer.loadFso(fso);
        }
    }

    /**
     * Method that closes an open properties drawer and finishes the paired properties action mode
     *
     * @param event The event referencing the file
     */
    @Subscribe
    public void onClosePropertiesDrawer(ClosePropertiesDrawerEvent event) {
        finishPropertiesActionMode();
    }

    /**
     * Method that opens the history activity.
     * @hide
     */
    void openHistory() {
        Intent historyIntent = new Intent(this, HistoryActivity.class);
        historyIntent.putExtra(HistoryFragment.EXTRA_HISTORY_LIST, (Serializable)this.getCurrentNavigationFragment().mHistory);
        historyIntent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        startActivityForResult(historyIntent, INTENT_REQUEST_HISTORY);
    }

    /**
     * Method that opens the search activity.
     * @hide
     */
    void openSearch() {
        onSearchRequested();
    }

    /**
     * Method that ask the user to change the access mode prior to crash.
     * @hide
     */
    public void askOrExit() {
        //Show a dialog asking the user
        AlertDialog dialog =
            DialogHelper.createYesNoDialog(
                this,
                R.string.msgs_change_to_prompt_access_mode_title,
                R.string.msgs_change_to_prompt_access_mode_msg,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface alertDialog, int which) {
                        if (which == DialogInterface.BUTTON_NEGATIVE) {
                            // We don't have any console
                            // Show exception and exit
                            DialogHelper.showToast(
                                    NavigationActivity.this,
                                    R.string.msgs_cant_create_console, Toast.LENGTH_LONG);
                            exit();
                            return;
                        }

                        // Ok. Now try to change to prompt mode. Any crash
                        // here is a fatal error. We won't have any console to operate.
                        try {
                            // Change console
                            ConsoleBuilder.changeToNonPrivilegedConsole(NavigationActivity.this);

                            // Save preferences
                            Preferences.savePreference(
                                    FileManagerSettings.SETTINGS_ACCESS_MODE,
                                    AccessMode.PROMPT, true);

                        } catch (Exception e) {
                            // Displays an exception and exit
                            Log.e(TAG, getString(R.string.msgs_cant_create_console), e);
                            DialogHelper.showToast(
                                    NavigationActivity.this,
                                    R.string.msgs_cant_create_console, Toast.LENGTH_LONG);
                            exit();
                        }
                    }
               });
        DialogHelper.delegateDialogShow(this, dialog);
    }

    /**
     * Method that creates a ChRooted environment, protecting the user to break anything in
     * the device
     * @hide
     */
    void createChRooted() {
        // If we are in a ChRooted mode, then do nothing
        if (this.mChRooted) return;
        this.mChRooted = true;

        //Change to first storage volume
        Object[] volumes =
                StorageHelper.getStorageVolumes(this);
        if (volumes != null && volumes.length > 0) {
            for (int x = 0; x < mPagerAdapter.getCount();x++) {
                mPagerAdapter.getFragment(mViewPager, x).enterChRooted(
                        StorageHelper.getStoragePath(volumes[0]));
            }
        }

        // Remove the history (don't allow to access to previous data)
        clearHistory();
    }

    /**
     * Method that exits from a ChRooted
     * @hide
     */
    void exitChRooted() {
        // If we aren't in a ChRooted mode, then do nothing
        if (!this.mChRooted) return;
        this.mChRooted = false;

        for (int x = 0; x < mPagerAdapter.getCount();x++) {
            NavigationFragment navigationFragment = (NavigationFragment) mPagerAdapter.getItem(x);
            navigationFragment.exitChRooted();

        }
    }

    /**
     * Method that returns whether the activity is ChRooted
     */
    public boolean isChRooted(){
        return this.mChRooted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * A part of the PageChangeListener interface. Since there are numerous UI
     * elements that are contextually based on the currently selected page, we
     * need to trigger UI updates after the user swipes to a new page. The pager
     * is contained within the main activity, but the relevant data must be
     * pushed from the currently selected list fragment. Therefore, the changeBreadcrumbPath
     * method is called so that the fragment's current dir can be reflected in the breadcrumbs.
     *
     * @param position the integer index of the currently selected page
     * @return nothing
     */
    @Override
    public void onPageSelected(int position) {

    	// Load the new fragments current dir into the breadcrumb
        if (this.mBreadcrumb != null) {
            this.mBreadcrumb.changeBreadcrumbPath(getCurrentNavigationFragment().getCurrentDir(), this.mChRooted);
        }

        // Tell the breadcrumb that the new fragment will now be the one sending dir changes
        NavigationFragment navigationFragment = getCurrentNavigationFragment();
        navigationFragment.setBreadcrumb(mBreadcrumb);
        navigationFragment.setOnHistoryListener(navigationFragment);
        navigationFragment.setOnNavigationOnRequestMenuListener(this);
        navigationFragment.setCustomTitle(mTitle);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPageScrollStateChanged(int state) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onMarkFilesForPaste(List<FileSystemObject> filesForPaste, COPY_MOVE_OPERATION pasteOperationType) {
        this.mFilesForPaste = filesForPaste;
        this.mPasteOperationType = pasteOperationType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onAreFilesMarkedForPaste() {
        return this.mFilesForPaste != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClearFilesMarkedForPaste() {
        this.mFilesForPaste = null;
        this.mPasteOperationType = null;
        // Make sure that the paste action item is removed
        invalidateOptionsMenu();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FileSystemObject> onRequestFilesMarkedForPaste() {
        return this.mFilesForPaste;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public COPY_MOVE_OPERATION onRequestPasteOperationType() {
        return this.mPasteOperationType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String onRequestDestinationDir() {
        return getCurrentNavigationFragment().getCurrentDir();
    }
    
    /**
     * Method that applies the current theme to the activity
     * @hide
     */
    void applyTheme() {
        Theme theme = ThemeManager.getCurrentTheme(this);
        theme.setBaseTheme(this, false);
    }
}
