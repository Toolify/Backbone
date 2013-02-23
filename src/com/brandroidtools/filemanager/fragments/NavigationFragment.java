package com.brandroidtools.filemanager.fragments;

/**
 * Created with IntelliJ IDEA.
 * User: Brent
 * Date: 1/16/13
 * Time: 9:43 PM
 * To change this template use File | Settings | File Templates.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.Fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.*;
import com.brandroidtools.filemanager.FileManagerApplication;
import com.brandroidtools.filemanager.R;
import com.brandroidtools.filemanager.activities.NavigationActivity;
import com.brandroidtools.filemanager.adapters.FileSystemObjectAdapter;
import com.brandroidtools.filemanager.adapters.FileSystemObjectAdapter.OnSelectionChangedListener;
import com.brandroidtools.filemanager.console.Console;
import com.brandroidtools.filemanager.console.ConsoleAllocException;
import com.brandroidtools.filemanager.console.ConsoleBuilder;
import com.brandroidtools.filemanager.listeners.OnHistoryListener;
import com.brandroidtools.filemanager.listeners.OnRequestRefreshListener;
import com.brandroidtools.filemanager.listeners.OnSelectionListener;
import com.brandroidtools.filemanager.model.*;
import com.brandroidtools.filemanager.parcelables.NavigationViewInfoParcelable;
import com.brandroidtools.filemanager.parcelables.SearchInfoParcelable;
import com.brandroidtools.filemanager.preferences.*;
import com.brandroidtools.filemanager.ui.ThemeManager;
import com.brandroidtools.filemanager.ui.actionmode.SelectionModeCallback;
import com.brandroidtools.filemanager.ui.policy.*;
import com.brandroidtools.filemanager.ui.widgets.*;
import com.brandroidtools.filemanager.ui.widgets.FlingerListView.OnItemFlingerListener;
import com.brandroidtools.filemanager.ui.widgets.FlingerListView.OnItemFlingerResponder;
import com.brandroidtools.filemanager.util.*;

public class NavigationFragment extends Fragment implements
        AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener,
        OnSelectionChangedListener, OnSelectionListener, OnRequestRefreshListener {

    private static final String TAG = "NavigationFragment"; //$NON-NLS-1$

    /**
     * An interface to communicate a request for show the menu associated
     * with an item.
     */
    public interface OnNavigationRequestMenuListener {
        /**
         * Method invoked when a request to show the menu associated
         * with an item is started.
         *
         * @param navFragment The navigation fragment that generates the event
         * @param item The item for which the request was started
         */
        void onRequestMenu(NavigationFragment navFragment, FileSystemObject item);
    }

    /**
     * An interface to communicate a request when the user choose a file.
     */
    public interface OnFilePickedListener {
        /**
         * Method invoked when a request when the user choose a file.
         *
         * @param item The item choose
         */
        void onFilePicked(FileSystemObject item);
    }

    /**
     * An interface to communicate a change of the current directory
     */
    public interface OnDirectoryChangedListener {
        /**
         * Method invoked when the current directory changes
         *
         * @param item The newly active directory
         */
        void onDirectoryChanged(FileSystemObject item);
    }

    /**
     * The navigation view mode
     * @hide
     */
    public enum NAVIGATION_MODE {
        /**
         * The navigation view acts as a browser, and allow open files itself.
         */
        BROWSABLE,
        /**
         * The navigation view acts as a picker of files
         */
        PICKABLE,
    }

    /**
     * A listener for flinging events from {@link FlingerListView}
     */
    private final OnItemFlingerListener mOnItemFlingerListener = new OnItemFlingerListener() {

        @Override
        public boolean onItemFlingerStart(
                AdapterView<?> parent, View view, int position, long id) {
            try {
                // Response if the item can be removed
                FileSystemObjectAdapter adapter = (FileSystemObjectAdapter)parent.getAdapter();
                FileSystemObject fso = adapter.getItem(position);
                if (fso != null) {
                    if (fso instanceof ParentDirectory) {
                        return false;
                    }
                    return true;
                }
            } catch (Exception e) {
                ExceptionUtil.translateException(mActivity, e, true, false);
            }
            return false;
        }

        @Override
        public void onItemFlingerEnd(OnItemFlingerResponder responder,
                                     AdapterView<?> parent, View view, int position, long id) {

            try {
                // Response if the item can be removed
                FileSystemObjectAdapter adapter = (FileSystemObjectAdapter)parent.getAdapter();
                FileSystemObject fso = adapter.getItem(position);
                if (fso != null) {
                    DeleteActionPolicy.removeFileSystemObject(
                            mActivity,
                            fso,
                            NavigationFragment.this,
                            NavigationFragment.this,
                            responder);
                    return;
                }

                // Cancels the flinger operation
                responder.cancel();

            } catch (Exception e) {
                ExceptionUtil.translateException(mActivity, e, true, false);
                responder.cancel();
            }
        }
    };

    private int mId;
    private String mCurrentDir;
    private NavigationLayoutMode mCurrentMode;
    /**
     * @hide
     */
    List<FileSystemObject> mFiles;
    private FileSystemObjectAdapter mAdapter;

    private final Object mSync = new Object();

    private OnHistoryListener mOnHistoryListener;
    private OnNavigationRequestMenuListener mOnNavigationRequestMenuListener;
    private OnFilePickedListener mOnFilePickedListener;
    private OnDirectoryChangedListener mOnDirectoryChangedListener;

    private boolean mChRooted;

    private NAVIGATION_MODE mNavigationMode;

    // Restrictions
    private Map<DisplayRestrictions, Object> mRestrictions;

    /**
     * @hide
     */
    RelativeLayout mNavigationViewHolder;
    /**
     * @hide
     */
    Breadcrumb mBreadcrumb;
    /**
     * @hide
     */
    NavigationCustomTitleView mTitle;
    /**
     * @hide
     */
    AdapterView<?> mAdapterView;
    /**
     * @hide
     */
    private SelectionModeCallback mSelectionModeCallback;

    //The layout for icons mode
    private static final int RESOURCE_MODE_ICONS_LAYOUT = R.layout.navigation_view_icons;
    private static final int RESOURCE_MODE_ICONS_ITEM = R.layout.navigation_view_icons_item;
    //The layout for simple mode
    private static final int RESOURCE_MODE_SIMPLE_LAYOUT = R.layout.navigation_view_simple;
    private static final int RESOURCE_MODE_SIMPLE_ITEM = R.layout.navigation_view_simple_item;
    //The layout for details mode
    private static final int RESOURCE_MODE_DETAILS_LAYOUT = R.layout.navigation_view_details;
    private static final int RESOURCE_MODE_DETAILS_ITEM = R.layout.navigation_view_details_item;

    //The current layout identifier (is shared for all the mode layout)
    private static final int RESOURCE_CURRENT_LAYOUT = R.id.navigation_view_layout;

    private NavigationActivity mActivity;

    /**
     * @hide
     */
    Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = (NavigationActivity)getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.navigation, container, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(Bundle state) {
        super.onActivityCreated(state);

        //Navigation views
        initNavigationViewContainer();

        // Apply the theme
        applyTheme();

        this.mHandler = new Handler();
        this.mHandler.post(new Runnable() {

            @Override
            public void run() {
                //Initialize navigation
                initNavigation(false);
                if (mActivity.isFirstRun() == true)
                    mActivity.updateTitleActionBar();
                mActivity.setFirstRun(false);
            };
        });
    }

    /**
     * Create a new instance of FileListFragment, providing "num" as an
     * argument.
     */
    public static NavigationFragment newInstance(int num) {
        NavigationFragment f = new NavigationFragment();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt("num", num);
        f.setArguments(args);
        return f;
    }

    /**
     * Method that initializes the navigation views of the activity
     */
    private void initNavigationViewContainer() {
        mNavigationViewHolder = (RelativeLayout)getView().findViewById(R.id.navigation_view_container);
        TypedArray a = mActivity.obtainStyledAttributes(R.styleable.Navigable);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Method that initializes the navigation.
     *
     * @param restore Initialize from a restore info
     * @hide
     */
    void initNavigation(final boolean restore) {
        this.mHandler.post(new Runnable() {
            @Override
            public void run() {
                //Create the default console (from the preferences)
                try {
                    Console console = ConsoleBuilder.getConsole(mActivity);
                    if (console == null) {
                        throw new ConsoleAllocException("console == null"); //$NON-NLS-1$
                    }
                } catch (Throwable ex) {
                    if (!mActivity.isChRooted()) {
                        //Show exception and exists
                        Log.e(TAG, getString(R.string.msgs_cant_create_console), ex);
                        // We don't have any console
                        // Show exception and exists
                        DialogHelper.showToast(
                                mActivity,
                                R.string.msgs_cant_create_console, Toast.LENGTH_LONG);
                        mActivity.exit();
                        return;
                    }

                    // We are in a trouble (something is not allowing creating the console)
                    // Ask the user to return to prompt or root access mode mode with a
                    // non-privileged console, prior to make crash the application
                    mActivity.askOrExit();
                    return;
                }

                //Is necessary navigate?
                if (!restore) {
                    //Load the preference initial directory
                    String initialDir =
                            Preferences.getSharedPreferences().getString(
                                    FileManagerSettings.SETTINGS_INITIAL_DIR.getId(),
                                    (String)FileManagerSettings.
                                            SETTINGS_INITIAL_DIR.getDefaultValue());
                    if (mActivity.isChRooted()) {
                        // Initial directory is the first external sdcard (sdcard, emmc, usb, ...)
                        FileSystemStorageVolume[] volumes =
                                StorageHelper.getStorageVolumes(mActivity);
                        if (volumes != null && volumes.length > 0) {
                            initialDir = volumes[0].getPath();
                        }
                    }

                    //Ensure initial is an absolute directory
                    try {
                        initialDir = new File(initialDir).getAbsolutePath();
                    } catch (Throwable e) {
                        Log.e(TAG, "Resolve of initital directory fails", e); //$NON-NLS-1$
                        String msg =
                                getString(
                                        R.string.msgs_settings_invalid_initial_directory,
                                        initialDir);
                        DialogHelper.showToast(mActivity, msg, Toast.LENGTH_SHORT);
                        initialDir = FileHelper.ROOT_DIRECTORY;
                    }

                    // Change the current directory to the preference initial directory or the
                    // request if exists
                    String navigateTo = mActivity.getIntent().getStringExtra(mActivity.EXTRA_NAVIGATE_TO);
                    if (navigateTo != null && navigateTo.length() > 0) {
                        NavigationFragment.this.changeCurrentDir(navigateTo);
                    } else {
                        NavigationFragment.this.changeCurrentDir(initialDir);
                    }
                }
            }
        });
    }

    /**
     * Invoked when the instance need to be saved.
     *
     * @return NavigationViewInfoParcelable The serialized info
     */
    public NavigationViewInfoParcelable onSaveState() {
        //Return the persistent the data
        NavigationViewInfoParcelable parcel = new NavigationViewInfoParcelable();
        parcel.setId(this.mId);
        parcel.setCurrentDir(this.mCurrentDir);
        parcel.setChRooted(this.mChRooted);
        parcel.setSelectedFiles(this.mAdapter.getSelectedItems());
        parcel.setFiles(this.mFiles);
        return parcel;
    }

    /**
     * Invoked when the instance need to be restored.
     *
     * @param info The serialized info
     */
    public void onRestoreState(NavigationViewInfoParcelable info) {
        //Restore the data
        this.mId = info.getId();
        this.mCurrentDir = info.getCurrentDir();
        this.mChRooted = info.getChRooted();
        this.mFiles = info.getFiles();
        this.mAdapter.setSelectedItems(info.getSelectedFiles());

        //Update the views
        refresh();
    }

    /**
     * Method that initializes the view. This method loads all the necessary
     * information and create an appropriate layout for the view.
     *
     * @param tarray The type array
     */
    private void init(TypedArray tarray) {
        // Retrieve the mode
        this.mNavigationMode = NAVIGATION_MODE.BROWSABLE;
        int mode = tarray.getInteger(
                R.styleable.Navigable_navigation,
                NAVIGATION_MODE.BROWSABLE.ordinal());
        if (mode >= 0 && mode < NAVIGATION_MODE.values().length) {
            this.mNavigationMode = NAVIGATION_MODE.values()[mode];
        }

        // Initialize default restrictions (no restrictions)
        this.mRestrictions = new HashMap<DisplayRestrictions, Object>();

        //Initialize variables
        this.mFiles = new ArrayList<FileSystemObject>();

        // Is ChRooted environment?
        if (this.mNavigationMode.compareTo(NAVIGATION_MODE.PICKABLE) == 0) {
            // Pick mode is always ChRooted
            this.mChRooted = true;
        } else {
            this.mChRooted =
                    FileManagerApplication.getAccessMode().compareTo(AccessMode.SAFE) == 0;
        }

        //Retrieve the default configuration
        if (this.mNavigationMode.compareTo(NAVIGATION_MODE.BROWSABLE) == 0) {
            SharedPreferences preferences = Preferences.getSharedPreferences();
            int viewMode = preferences.getInt(
                    FileManagerSettings.SETTINGS_LAYOUT_MODE.getId(),
                    ((ObjectIdentifier)FileManagerSettings.
                            SETTINGS_LAYOUT_MODE.getDefaultValue()).getId());
            changeViewMode(NavigationLayoutMode.fromId(viewMode));
        } else {
            // Pick mode has always a details layout
            changeViewMode(NavigationLayoutMode.DETAILS);
        }
    }

    /**
     * Method that returns the display restrictions to apply to this view.
     *
     * @return Map<DisplayRestrictions, Object> The restrictions to apply
     */
    public Map<DisplayRestrictions, Object> getRestrictions() {
        return this.mRestrictions;
    }

    /**
     * Method that sets the display restrictions to apply to this view.
     *
     * @param mRestrictions The restrictions to apply
     */
    public void setRestrictions(Map<DisplayRestrictions, Object> mRestrictions) {
        this.mRestrictions = mRestrictions;
    }

    /**
     * Method that returns the current file list of the navigation view.
     *
     * @return List<FileSystemObject> The current file list of the navigation view
     */
    public List<FileSystemObject> getFiles() {
        if (this.mFiles == null) {
            return null;
        }
        return new ArrayList<FileSystemObject>(this.mFiles);
    }

    /**
     * Method that returns the current file list of the navigation fragment.
     *
     * @return List<FileSystemObject> The current file list of the navigation view
     */
    public List<FileSystemObject> getSelectedFiles() {
        if (this.mAdapter != null && this.mAdapter.getSelectedItems() != null) {
            return new ArrayList<FileSystemObject>(this.mAdapter.getSelectedItems());
        }
        return null;
    }

    /**
     * Method that returns the custom title view associated with this navigation view.
     *
     * @return NavigationCustomTitleView The custom title view fragment
     */
    public NavigationCustomTitleView getCustomTitle() {
        return this.mTitle;
    }

    /**
     * Method that associates the custom title fragment with this navigation view.
     *
     * @param title The custom title view fragment
     */
    public void setCustomTitle(NavigationCustomTitleView title) {
        this.mTitle = title;
    }

    /**
     * Method that returns the breadcrumb associated with this navigation view.
     *
     * @return Breadcrumb The breadcrumb view fragment
     */
    public Breadcrumb getBreadcrumb() {
        return this.mBreadcrumb;
    }

    /**
     * Method that associates the breadcrumb with this navigation view.
     *
     * @param breadcrumb The breadcrumb view fragment
     */
    public void setBreadcrumb(Breadcrumb breadcrumb) {
        this.mBreadcrumb = breadcrumb;
        this.mBreadcrumb.addBreadcrumbListener(mActivity);
    }

    /**
     * Method that sets the listener for communicate history changes.
     *
     * @param onHistoryListener The listener for communicate history changes
     */
    public void setOnHistoryListener(OnHistoryListener onHistoryListener) {
        this.mOnHistoryListener = onHistoryListener;
    }

    /**
     * Method that sets the listener for menu item requests.
     *
     * @param onNavigationRequestMenuListener The listener reference
     */
    public void setOnNavigationOnRequestMenuListener(
            OnNavigationRequestMenuListener onNavigationRequestMenuListener) {
        this.mOnNavigationRequestMenuListener = onNavigationRequestMenuListener;
    }

    /**
     * @return the mOnFilePickedListener
     */
    public OnFilePickedListener getOnFilePickedListener() {
        return this.mOnFilePickedListener;
    }

    /**
     * Method that sets the listener for picked items
     *
     * @param onFilePickedListener The listener reference
     */
    public void setOnFilePickedListener(OnFilePickedListener onFilePickedListener) {
        this.mOnFilePickedListener = onFilePickedListener;
    }

    /**
     * Method that sets the listener for directory changes
     *
     * @param onDirectoryChangedListener The listener reference
     */
    public void setOnDirectoryChangedListener(
            OnDirectoryChangedListener onDirectoryChangedListener) {
        this.mOnDirectoryChangedListener = onDirectoryChangedListener;
    }

    /**
     * Method that sets if the view should use flinger gesture detection.
     *
     * @param useFlinger If the view should use flinger gesture detection
     */
    public void setUseFlinger(boolean useFlinger) {
        if (this.mCurrentMode.compareTo(NavigationLayoutMode.ICONS) == 0) {
            // Not supported
            return;
        }
        // Set the flinger listener (only when navigate)
        if (this.mNavigationMode.compareTo(NAVIGATION_MODE.BROWSABLE) == 0) {
            if (this.mAdapterView instanceof FlingerListView) {
                if (useFlinger) {
                    ((FlingerListView)this.mAdapterView).
                            setOnItemFlingerListener(this.mOnItemFlingerListener);
                } else {
                    ((FlingerListView)this.mAdapterView).setOnItemFlingerListener(null);
                }
            }
        }
    }

    /**
     * Method that forces the view to scroll to the file system object passed.
     *
     * @param fso The file system object
     */
    public void scrollTo(FileSystemObject fso) {
        if (fso != null) {
            try {
                int position = this.mAdapter.getPosition(fso);
                this.mAdapterView.setSelection(position);
            } catch (Exception e) {
                this.mAdapterView.setSelection(0);
            }
        } else {
            this.mAdapterView.setSelection(0);
        }
    }

    /**
     * Method that refresh the view data.
     */
    public void refresh() {
        refresh(false);
    }

    /**
     * Method that refresh the view data.
     *
     * @param restore Restore previous position
     */
    public void refresh(boolean restore) {
        FileSystemObject fso = null;
        // Try to restore the previous scroll position
        if (restore) {
            try {
                if (this.mAdapterView != null && this.mAdapter != null) {
                    int position = this.mAdapterView.getFirstVisiblePosition();
                    fso = this.mAdapter.getItem(position);
                }
            } catch (Throwable _throw) {/**NON BLOCK**/}
        }
        refresh(fso);
    }

    /**
     * Method that refresh the view data.
     *
     * @param scrollTo Scroll to object
     */
    public void refresh(FileSystemObject scrollTo) {
        //Check that current directory was set
        if (this.mCurrentDir == null || this.mFiles == null) {
            return;
        }

        //Reload data
        changeCurrentDir(this.mCurrentDir, false, true, false, null, scrollTo);
    }

    /**
     * Method that change the view mode.
     *
     * @param newMode The new mode
     */
    @SuppressWarnings({ "unchecked", "null" })
    public void changeViewMode(final NavigationLayoutMode newMode) {
        synchronized (this.mSync) {
            //Check that it is really necessary change the mode
            if (this.mCurrentMode != null && this.mCurrentMode.compareTo(newMode) == 0) {
                return;
            }

            // If we should set the listview to response to flinger gesture detection
            boolean useFlinger =
                    Preferences.getSharedPreferences().getBoolean(
                            FileManagerSettings.SETTINGS_USE_FLINGER.getId(),
                            ((Boolean)FileManagerSettings.
                                    SETTINGS_USE_FLINGER.
                                    getDefaultValue()).booleanValue());

            //Creates the new layout
            AdapterView<ListAdapter> newView = null;
            int itemResourceId = -1;
            if (newMode.compareTo(NavigationLayoutMode.ICONS) == 0) {
                newView = (AdapterView<ListAdapter>)mNavigationViewHolder.inflate(
                        mActivity, RESOURCE_MODE_ICONS_LAYOUT, null);
                itemResourceId = RESOURCE_MODE_ICONS_ITEM;

            } else if (newMode.compareTo(NavigationLayoutMode.SIMPLE) == 0) {
                newView =  (AdapterView<ListAdapter>)mNavigationViewHolder.inflate(
                        mActivity, RESOURCE_MODE_SIMPLE_LAYOUT, null);
                itemResourceId = RESOURCE_MODE_SIMPLE_ITEM;

                // Set the flinger listener (only when navigate)
                if (this.mNavigationMode.compareTo(NAVIGATION_MODE.BROWSABLE) == 0) {
                    if (useFlinger && newView instanceof FlingerListView) {
                        ((FlingerListView)newView).
                                setOnItemFlingerListener(this.mOnItemFlingerListener);
                    }
                }

            } else if (newMode.compareTo(NavigationLayoutMode.DETAILS) == 0) {
                newView =  (AdapterView<ListAdapter>)mNavigationViewHolder.inflate(
                        mActivity, RESOURCE_MODE_DETAILS_LAYOUT, null);
                itemResourceId = RESOURCE_MODE_DETAILS_ITEM;

                // Set the flinger listener (only when navigate)
                if (this.mNavigationMode.compareTo(NAVIGATION_MODE.BROWSABLE) == 0) {
                    if (useFlinger && newView instanceof FlingerListView) {
                        ((FlingerListView)newView).
                                setOnItemFlingerListener(this.mOnItemFlingerListener);
                    }
                }
            }

            //Get the current adapter and its adapter list
            List<FileSystemObject> files = new ArrayList<FileSystemObject>(this.mFiles);
            final AdapterView<ListAdapter> current =
                    (AdapterView<ListAdapter>)getView().findViewById(RESOURCE_CURRENT_LAYOUT);
            FileSystemObjectAdapter adapter =
                    new FileSystemObjectAdapter(
                            mActivity,
                            new ArrayList<FileSystemObject>(),
                            itemResourceId,
                            this.mNavigationMode.compareTo(NAVIGATION_MODE.PICKABLE) == 0);
            adapter.setOnSelectionChangedListener(this);

            //Remove current layout
            if (current != null) {
                if (current.getAdapter() != null) {
                    //Save selected items before dispose adapter
                    FileSystemObjectAdapter currentAdapter =
                            ((FileSystemObjectAdapter)current.getAdapter());
                    adapter.setSelectedItems(currentAdapter.getSelectedItems());
                    currentAdapter.dispose();
                }
                mNavigationViewHolder.removeView(current);
            }
            this.mFiles = files;
            adapter.addAll(files);
            adapter.notifyDataSetChanged();

            //Set the adapter
            this.mAdapter = adapter;
            newView.setAdapter(this.mAdapter);
            newView.setOnItemClickListener(NavigationFragment.this);

            //Add the new layout
            this.mAdapterView = newView;
            mNavigationViewHolder.addView(newView, 0);
            this.mCurrentMode = newMode;

            // Pick mode doesn't implements the onlongclick
            if (this.mNavigationMode.compareTo(NAVIGATION_MODE.BROWSABLE) == 0) {
                this.mAdapterView.setOnItemLongClickListener(this);
            } else {
                this.mAdapterView.setOnItemLongClickListener(null);
            }

            //Save the preference (only in navigation browse mode)
            if (this.mNavigationMode.compareTo(NAVIGATION_MODE.BROWSABLE) == 0) {
                try {
                    Preferences.savePreference(
                            FileManagerSettings.SETTINGS_LAYOUT_MODE, newMode, true);
                } catch (Exception ex) {
                    Log.e(TAG, "Save of view mode preference fails", ex); //$NON-NLS-1$
                }
            }
        }
    }

    /**
     * Method that removes a {@link FileSystemObject} from the view
     *
     * @param fso The file system object
     */
    public void removeItem(FileSystemObject fso) {
        this.mAdapter.remove(fso);
        // Delete also from internal list
        if (fso != null) {
            int cc = this.mFiles.size()-1;
            for (int i = cc; i >= 0; i--) {
                FileSystemObject f = this.mFiles.get(i);
                if (f != null && f.compareTo(fso) == 0) {
                    this.mFiles.remove(i);
                    break;
                }
            }
        }
        this.mAdapter.notifyDataSetChanged();
    }

    /**
     * Method that removes a file system object from his path from the view
     *
     * @param path The file system object path
     */
    public void removeItem(String path) {
        FileSystemObject fso = this.mAdapter.getItem(path);
        if (fso != null) {
            this.mAdapter.remove(fso);
            this.mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Method that returns the current directory.
     *
     * @return String The current directory
     */
    public String getCurrentDir() {
        return this.mCurrentDir;
    }

    /**
     * Method that changes the current directory of the view.
     *
     * @param newDir The new directory location
     */
    public void changeCurrentDir(final String newDir) {
        changeCurrentDir(newDir, true, false, false, null, null);
    }

    /**
     * Method that changes the current directory of the view.
     *
     * @param newDir The new directory location
     * @param searchInfo The search information (if calling activity is {@link "SearchActivity"})
     */
    public void changeCurrentDir(final String newDir, SearchInfoParcelable searchInfo) {
        changeCurrentDir(newDir, true, false, false, searchInfo, null);
    }

    /**
     * Method that changes the current directory of the view.
     *
     * @param newDir The new directory location
     * @param addToHistory Add the directory to history
     * @param reload Force the reload of the data
     * @param useCurrent If this method must use the actual data (for back actions)
     * @param searchInfo The search information (if calling activity is {@link "SearchActivity"})
     * @param scrollTo If not null, then listview must scroll to this item
     */
    private void changeCurrentDir(
            final String newDir, final boolean addToHistory,
            final boolean reload, final boolean useCurrent,
            final SearchInfoParcelable searchInfo, final FileSystemObject scrollTo) {

        // Check navigation security (don't allow to go outside the ChRooted environment if one
        // is created)
        final String fNewDir = checkChRootedNavigation(newDir);

        synchronized (this.mSync) {
            //Check that it is really necessary change the directory
            if (!reload && this.mCurrentDir != null && this.mCurrentDir.compareTo(fNewDir) == 0) {
                return;
            }

            final boolean hasChanged =
                    !(this.mCurrentDir != null && this.mCurrentDir.compareTo(fNewDir) == 0);
            final boolean isNewHistory = (this.mCurrentDir != null);

            //Execute the listing in a background process
            AsyncTask<String, Integer, List<FileSystemObject>> task =
                    new AsyncTask<String, Integer, List<FileSystemObject>>() {
                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        protected List<FileSystemObject> doInBackground(String... params) {
                            try {
                                //Reset the custom title view and returns to breadcrumb
                                if (NavigationFragment.this.mTitle != null) {
                                    NavigationFragment.this.mTitle.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                NavigationFragment.this.mTitle.restoreView();
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }


                                //Start of loading data
                                if (NavigationFragment.this.mBreadcrumb != null) {
                                    try {
                                        NavigationFragment.this.mBreadcrumb.startLoading();
                                    } catch (Throwable ex) {
                                        /**NON BLOCK**/
                                    }
                                }

                                //Get the files, resolve links and apply configuration
                                //(sort, hidden, ...)
                                List<FileSystemObject> files = NavigationFragment.this.mFiles;
                                if (!useCurrent) {
                                    files = CommandHelper.listFiles(mActivity, fNewDir, null);
                                }
                                return files;
                            } catch (final ConsoleAllocException e) {
                                //Show exception and exists
                                mNavigationViewHolder.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.e(TAG, mActivity.getString(
                                                R.string.msgs_cant_create_console), e);
                                        DialogHelper.showToast(mActivity,
                                                R.string.msgs_cant_create_console,
                                                Toast.LENGTH_LONG);
                                        mActivity.finish();
                                    }
                                });
                                return null;

                            } catch (Exception ex) {
                                //End of loading data
                                if (NavigationFragment.this.mBreadcrumb != null) {
                                    try {
                                        NavigationFragment.this.mBreadcrumb.endLoading();
                                    } catch (Throwable ex2) {
                                        /**NON BLOCK**/
                                    }
                                }

                                //Capture exception (attach task, and use listener to do the anim)
                                ExceptionUtil.attachAsyncTask(
                                        ex,
                                        new AsyncTask<Object, Integer, Boolean>() {
                                            private List<FileSystemObject> mTaskFiles = null;
                                            @Override
                                            @SuppressWarnings({
                                                    "unchecked", "unqualified-field-access"
                                            })
                                            protected Boolean doInBackground(Object... taskParams) {
                                                mTaskFiles = (List<FileSystemObject>)taskParams[0];
                                                return Boolean.TRUE;
                                            }

                                            @Override
                                            @SuppressWarnings("unqualified-field-access")
                                            protected void onPostExecute(Boolean result) {
                                                if (!result.booleanValue()){
                                                    return;
                                                }
                                                onPostExecuteTask(
                                                        mTaskFiles, addToHistory,
                                                        isNewHistory, hasChanged,
                                                        searchInfo, fNewDir, scrollTo);
                                            }
                                        });
                                final ExceptionUtil.OnRelaunchCommandResult exListener =
                                        new ExceptionUtil.OnRelaunchCommandResult() {
                                            @Override
                                            public void onSuccess() {
                                                // Do animation
                                                fadeEfect(false);
                                            }
                                            @Override
                                            public void onFailed(Throwable cause) {
                                                // Do animation
                                                fadeEfect(false);
                                            }
                                            @Override
                                            public void onCancelled() {
                                                // Do animation
                                                fadeEfect(false);
                                            }
                                        };
                                ExceptionUtil.translateException(
                                        mActivity, ex, false, true, exListener);
                            }
                            return null;
                        }

                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        protected void onPreExecute() {
                            // Do animation
                            fadeEfect(true);
                        }



                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        protected void onPostExecute(List<FileSystemObject> files) {
                            if (files != null) {
                                onPostExecuteTask(
                                        files, addToHistory, isNewHistory,
                                        hasChanged, searchInfo, fNewDir, scrollTo);

                                // Do animation
                                fadeEfect(false);
                            }
                        }

                        /**
                         * Method that performs a fade animation.
                         *
                         * @param out Fade out (true); Fade in (false)
                         */
                        void fadeEfect(final boolean out) {
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Animation fadeAnim = out ?
                                            new AlphaAnimation(1, 0) :
                                            new AlphaAnimation(0, 1);
                                    fadeAnim.setDuration(50L);
                                    fadeAnim.setFillAfter(true);
                                    fadeAnim.setInterpolator(new AccelerateInterpolator());
                                    mNavigationViewHolder.startAnimation(fadeAnim);
                                }
                            });
                        }
                    };
            task.execute(fNewDir);
        }
    }


    /**
     * Method invoked when a execution ends.
     *
     * @param files The files obtains from the list
     * @param addToHistory If add path to history
     * @param isNewHistory If is new history
     * @param hasChanged If current directory was changed
     * @param searchInfo The search information (if calling activity is {@link "SearchActivity"})
     * @param newDir The new directory
     * @param scrollTo If not null, then listview must scroll to this item
     * @hide
     */
    void onPostExecuteTask(
            List<FileSystemObject> files, boolean addToHistory, boolean isNewHistory,
            boolean hasChanged, SearchInfoParcelable searchInfo,
            String newDir, final FileSystemObject scrollTo) {
        try {
            //Check that there is not errors and have some data
            if (files == null) {
                return;
            }

            //Apply user preferences
            List<FileSystemObject> sortedFiles =
                    FileHelper.applyUserPreferences(files, this.mRestrictions, this.mChRooted);

            //Remove parent directory if we are in the root of a chrooted environment
            if (this.mChRooted && StorageHelper.isStorageVolume(newDir)) {
                if (files.size() > 0 && files.get(0) instanceof ParentDirectory) {
                    files.remove(0);
                }
            }

            //Load the data
            loadData(sortedFiles);
            this.mFiles = sortedFiles;
            if (searchInfo != null) {
                searchInfo.setSuccessNavigation(true);
            }

            //Add to history?
            if (addToHistory && hasChanged && isNewHistory) {
                if (this.mOnHistoryListener != null) {
                    //Communicate the need of a history change
                    this.mOnHistoryListener.onNewHistory(onSaveState());
                }
            }

            //Change the breadcrumb
            if (this.mBreadcrumb != null) {
                this.mBreadcrumb.changeBreadcrumbPath(newDir, this.mChRooted);
            }

            //Scroll to object?
            if (scrollTo != null) {
                scrollTo(scrollTo);
            }

            //The current directory is now the "newDir"
            this.mCurrentDir = newDir;
            if (this.mOnDirectoryChangedListener != null) {
                FileSystemObject dir = FileHelper.createFileSystemObject(new File(newDir));
                this.mOnDirectoryChangedListener.onDirectoryChanged(dir);
            }
        } finally {
            //If calling activity is search, then save the search history
            if (searchInfo != null) {
                this.mOnHistoryListener.onNewHistory(searchInfo);
            }

            //End of loading data
            try {
                NavigationFragment.this.mBreadcrumb.endLoading();
            } catch (Throwable ex) {
                /**NON BLOCK**/
            }
        }
    }

    /**
     * Method that loads the files in the adapter.
     *
     * @param files The files to load in the adapter
     * @hide
     */
    @SuppressWarnings("unchecked")
    private void loadData(final List<FileSystemObject> files) {
        //Notify data to adapter view
        final AdapterView<ListAdapter> view =
                (AdapterView<ListAdapter>)getView().findViewById(RESOURCE_CURRENT_LAYOUT);
        FileSystemObjectAdapter adapter = (FileSystemObjectAdapter)view.getAdapter();
        adapter.clear();
        adapter.addAll(files);
        adapter.notifyDataSetChanged();
        view.setSelection(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // Different actions depending on user preference

        // Get the adapter and the fso
        FileSystemObjectAdapter adapter = ((FileSystemObjectAdapter)parent.getAdapter());
        FileSystemObject fso = adapter.getItem(position);

        // Parent directory hasn't actions
        if (fso instanceof ParentDirectory) {
            return false;
        }

        // Pick mode doesn't implements the onlongclick
        if (this.mNavigationMode.compareTo(NAVIGATION_MODE.PICKABLE) == 0) {
            return false;
        }

        updateSelectionMode();
        return true; //Always consume the event
    }

    /**
     * Method that opens or navigates to the {@link FileSystemObject}
     *
     * @param fso The file system object
     */
    public void open(FileSystemObject fso) {
        open(fso, null);
    }

    /**
     * Method that opens or navigates to the {@link FileSystemObject}
     *
     * @param fso The file system object
     * @param searchInfo The search info
     */
    public void open(FileSystemObject fso, SearchInfoParcelable searchInfo) {
        // If is a folder, then navigate to
        if (FileHelper.isDirectory(fso)) {
            changeCurrentDir(fso.getFullPath(), searchInfo);
        } else {
            // Open the file with the preferred registered app
            IntentsActionPolicy.openFileSystemObject(mActivity, fso, false, null, null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        try {
            FileSystemObject fso = ((FileSystemObjectAdapter)parent.getAdapter()).getItem(position);
            if (fso instanceof ParentDirectory) {
                changeCurrentDir(fso.getParent(), true, false, false, null, null);
                return;
            } else if (fso instanceof Directory) {
                changeCurrentDir(fso.getFullPath(), true, false, false, null, null);
                return;
            } else if (fso instanceof Symlink) {
                Symlink symlink = (Symlink)fso;
                if (symlink.getLinkRef() != null && symlink.getLinkRef() instanceof Directory) {
                    changeCurrentDir(
                            symlink.getLinkRef().getFullPath(), true, false, false, null, null);
                    return;
                }

                // Open the link ref
                fso = symlink.getLinkRef();
            }

            // Open the file (edit or pick)
            if (this.mNavigationMode.compareTo(NAVIGATION_MODE.BROWSABLE) == 0) {
                // Open the file with the preferred registered app
                IntentsActionPolicy.openFileSystemObject(mActivity, fso, false, null, null);
            } else {
                // Request a file pick selection
                if (this.mOnFilePickedListener != null) {
                    this.mOnFilePickedListener.onFilePicked(fso);
                }
            }
        } catch (Throwable ex) {
            ExceptionUtil.translateException(mActivity, ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestRefresh(Object o, boolean clearSelection) {
        if (o instanceof FileSystemObject) {
            refresh((FileSystemObject)o);
        } else if (o == null) {
            refresh();
        }
        if (clearSelection) {
            onDeselectAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestRemove(Object o, boolean clearSelection) {
        if (o != null && o instanceof FileSystemObject) {
            removeItem((FileSystemObject)o);
        } else {
            onRequestRefresh(null, clearSelection);
        }
        if (clearSelection) {
            onDeselectAll();
        }
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
    public void onSelectionChanged(final List<FileSystemObject> selectedItems) {
        updateSelectionMode();
    }

    /**
     * Method invoked when a request to show the menu associated
     * with an item is started.
     *
     * @param item The item for which the request was started
     */
    public void onRequestMenu(final FileSystemObject item) {
        if (this.mOnNavigationRequestMenuListener != null) {
            this.mOnNavigationRequestMenuListener.onRequestMenu(this, item);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onToggleSelection(FileSystemObject fso) {
        if (this.mAdapter != null) {
            this.mAdapter.toggleSelection(fso);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDeselectAll() {
        if (this.mAdapter != null) {
            this.mAdapter.deselectedAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSelectAllVisibleItems() {
        if (this.mAdapter != null) {
            this.mAdapter.selectedAllVisibleItems();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDeselectAllVisibleItems() {
        if (this.mAdapter != null) {
            this.mAdapter.deselectedAllVisibleItems();
        }
    }

    public int onRequestSelectionCount() {
        return mAdapter.getSelectedItemsCount();
    }

    /**
     * Show/hide the "selection" action mode, according to the number of
     * selected messages and the visibility of the fragment. Also update the
     * content (title and menus) if necessary.
     */
    public void updateSelectionMode() {
        final int numSelected = onRequestSelectionCount();
        if (numSelected == 0) {
            finishSelectionMode();
            return;
        }
        if (isInSelectionMode()) {
            updateSelectionModeView();
        } else {
            mSelectionModeCallback = new SelectionModeCallback(mActivity, false);
            mSelectionModeCallback.setOnRequestRefreshListener(mActivity);
            mSelectionModeCallback.setOnSelectionListener(this);
            mActivity.startActionMode(mSelectionModeCallback);
        }
    }

    /**
     * Finish the "selection" action mode.
     *
     * Note this method finishes the contextual mode, but does *not* clear the
     * selection. If you want to do so use {@link #onDeselectAll()} instead.
     */
    private void finishSelectionMode() {
        if (isInSelectionMode()) {
            mSelectionModeCallback.setClosedByUser(false);
            mSelectionModeCallback.finish();
        }
    }

    /**
     * @return true if the list is in the "selection" mode.
     */
    private boolean isInSelectionMode() {
        if (mSelectionModeCallback == null) {
            return false;
        } else if (mSelectionModeCallback.inSelectionMode()) {
            return true;
        } else {
            return false;
        }
    }

    /** Update the "selection" action mode bar */
    private void updateSelectionModeView() {
        mSelectionModeCallback.refresh();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FileSystemObject> onRequestSelectedFiles() {
        return this.getSelectedFiles();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<FileSystemObject> onRequestCurrentItems() {
        return this.getFiles();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String onRequestCurrentDir() {
        return this.mCurrentDir;
    }

    /**
     * Method that creates a ChRooted environment, protecting the user to break anything
     * in the device
     * @hide
     */
    public void createChRooted() {
        // If we are in a ChRooted environment, then do nothing
        if (this.mChRooted) return;
        this.mChRooted = true;

        //Change to first storage volume
        FileSystemStorageVolume[] volumes =
                StorageHelper.getStorageVolumes(mActivity);
        if (volumes != null && volumes.length > 0) {
            changeCurrentDir(volumes[0].getPath(), false, true, false, null, null);
        }
    }

    /**
     * Method that exits from a ChRooted environment
     * @hide
     */
    public void exitChRooted() {
        // If we aren't in a ChRooted environment, then do nothing
        if (!this.mChRooted) return;
        this.mChRooted = false;

        // Refresh
        refresh();
    }

    /**
     * Method that ensures that the user don't go outside the ChRooted environment
     *
     * @param newDir The new directory to navigate to
     * @return String
     */
    private String checkChRootedNavigation(String newDir) {
        // If we aren't in ChRooted environment, then there is nothing to check
        if (!this.mChRooted) return newDir;

        // Check if the path is owned by one of the storage volumes
        if (!StorageHelper.isPathInStorageVolume(newDir)) {
            FileSystemStorageVolume[] volumes = StorageHelper.getStorageVolumes(mActivity);
            if (volumes != null && volumes.length > 0) {
                return volumes[0].getPath();
            }
        }
        return newDir;
    }

    /**
     * Method that applies the current theme to the activity
     */
    public void applyTheme() {
        //- Breadcrumb
        if (getBreadcrumb() != null) {
            getBreadcrumb().applyTheme();
        }

        //- Redraw the adapter view
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme(mActivity);
        theme.setBackgroundDrawable(mActivity, mNavigationViewHolder, "background_drawable"); //$NON-NLS-1$
        if (this.mAdapter != null) {
            this.mAdapter.notifyThemeChanged();
        }
        if (this.mAdapterView instanceof ListView) {
            ((ListView)this.mAdapterView).setDivider(
                    theme.getDrawable(mActivity, "horizontal_divider_drawable")); //$NON-NLS-1$
        }
        refresh();
    }
}
