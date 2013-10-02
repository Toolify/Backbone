/*
 * Copyright (C) 2012-2013 The CyanogenMod Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceActivity;
import android.provider.SearchRecentSuggestions;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import me.toolify.backbone.FileManagerApplication;
import me.toolify.backbone.R;
import me.toolify.backbone.activities.preferences.SettingsPreferences;
import me.toolify.backbone.activities.preferences.SearchPreferenceFragment;
import me.toolify.backbone.adapters.SearchResultAdapter;
import me.toolify.backbone.commands.AsyncResultExecutable;
import me.toolify.backbone.commands.AsyncResultListener;
import me.toolify.backbone.console.NoSuchFileOrDirectory;
import me.toolify.backbone.console.RelaunchableException;
import me.toolify.backbone.listeners.OnRequestRefreshListener;
import me.toolify.backbone.model.Directory;
import me.toolify.backbone.model.FileSystemObject;
import me.toolify.backbone.model.ParentDirectory;
import me.toolify.backbone.model.Query;
import me.toolify.backbone.model.SearchResult;
import me.toolify.backbone.model.Symlink;
import me.toolify.backbone.parcelables.SearchInfoParcelable;
import me.toolify.backbone.preferences.AccessMode;
import me.toolify.backbone.preferences.FileManagerSettings;
import me.toolify.backbone.preferences.Preferences;
import me.toolify.backbone.providers.RecentSearchesContentProvider;
import me.toolify.backbone.tasks.SearchResultDrawingAsyncTask;
import me.toolify.backbone.ui.ThemeManager;
import me.toolify.backbone.ui.ThemeManager.Theme;
import me.toolify.backbone.ui.dialogs.MessageProgressDialog;
import me.toolify.backbone.ui.policy.DeleteActionPolicy;
import me.toolify.backbone.ui.policy.IntentsActionPolicy;
import me.toolify.backbone.ui.widgets.FlingerListView;
import me.toolify.backbone.ui.widgets.FlingerListView.OnItemFlingerListener;
import me.toolify.backbone.ui.widgets.FlingerListView.OnItemFlingerResponder;
import me.toolify.backbone.util.CommandHelper;
import me.toolify.backbone.util.DialogHelper;
import me.toolify.backbone.util.ExceptionUtil;
import me.toolify.backbone.util.ExceptionUtil.OnRelaunchCommandResult;
import me.toolify.backbone.util.FileHelper;
import me.toolify.backbone.util.StorageHelper;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * An activity for search files and folders.
 */
public class SearchActivity extends Activity
    implements AsyncResultListener, OnItemClickListener,
               OnItemLongClickListener, OnRequestRefreshListener {

    private static final String TAG = "SearchActivity"; //$NON-NLS-1$

    private static boolean DEBUG = false;

    /**
     * An {@link Intent} action for restore view information.
     */
    public static final String ACTION_RESTORE =
            "me.toolify.backbone.activities.SearchActivity#Restore"; //$NON-NLS-1$

    /**
     * Intent extra parameter for search in the selected directory on enter.
     */
    public static final String EXTRA_SEARCH_DIRECTORY = "extra_search_directory";  //$NON-NLS-1$

    /**
     * Intent extra parameter for pass the restore information.
     */
    public static final String EXTRA_SEARCH_RESTORE = "extra_search_restore";  //$NON-NLS-1$


    //Minimum characters to allow query
    private static final int MIN_CHARS_SEARCH = 3;

    private final BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (intent.getAction().compareTo(
                        FileManagerSettings.INTENT_SETTING_CHANGED) == 0) {

                    // The settings has changed
                    String key = intent.getStringExtra(
                            FileManagerSettings.EXTRA_SETTING_CHANGED_KEY);
                    if (key != null) {
                        if (SearchActivity.this.mSearchListView.getAdapter() != null &&
                           (key.compareTo(
                                   FileManagerSettings.
                                       SETTINGS_HIGHLIGHT_TERMS.getId()) == 0 ||
                            key.compareTo(
                                    FileManagerSettings.
                                        SETTINGS_SHOW_RELEVANCE_WIDGET.getId()) == 0 ||
                            key.compareTo(
                                    FileManagerSettings.
                                        SETTINGS_SORT_SEARCH_RESULTS_MODE.getId()) == 0)) {

                            // Recreate the adapter
                            int pos = SearchActivity.
                                        this.mSearchListView.getFirstVisiblePosition();
                            drawResults();
                            SearchActivity.this.mSearchListView.setSelection(pos);
                            return;
                        }
                    }
                } else if (intent.getAction().compareTo(
                        FileManagerSettings.INTENT_THEME_CHANGED) == 0) {
                    applyTheme();
                }
            }
        }
    };

    /**
     * A listener for flinging events from {@link FlingerListView}
     */
    private final OnItemFlingerListener mOnItemFlingerListener = new OnItemFlingerListener() {

        @Override
        public boolean onItemFlingerStart(
                AdapterView<?> parent, View view, int position, long id) {
            try {
                // Response if the item can be removed
                SearchResult result = mSearchResultAdaper.getItem(position);
                if (result != null && result.getFso() != null) {
                    if (result.getFso() instanceof ParentDirectory) {
                        // This is not possible ...
                        return false;
                    }
                    return true;
                }
            } catch (Exception e) {
                ExceptionUtil.translateException(SearchActivity.this, e, true, false);
            }
            return false;
        }

        @Override
        public void onItemFlingerEnd(OnItemFlingerResponder responder,
                AdapterView<?> parent, View view, int position, long id) {

            try {
                // Response if the item can be removed
                SearchResult result = mSearchResultAdaper.getItem(position);
                if (result != null && result.getFso() != null) {
                    DeleteActionPolicy.removeFileSystemObject(
                            SearchActivity.this,
                            result.getFso(),
                            null,
                            SearchActivity.this,
                            responder);
                    return;
                }

                // Cancels the flinger operation
                responder.cancel();

            } catch (Exception e) {
                ExceptionUtil.translateException(SearchActivity.this, e, true, false);
                responder.cancel();
            }
        }
    };

    /**
     * @hide
     */
    MessageProgressDialog mDialog = null;
    /**
     * @hide
     */
    AsyncResultExecutable mExecutable = null;

    /**
     * @hide
     */
    ListView mSearchListView;
    /**
     * @hide
     */
    SearchResultAdapter mSearchResultAdaper;
    /**
     * @hide
     */
    ProgressBar mSearchWaiting;
    /**
     * @hide
     */
    MenuItem mSearchFoundItems;

    private View mEmptyListMsg;

    private String mSearchDirectory;
    /**
     * @hide
     */
    List<FileSystemObject> mResultList;
    /**
     * @hide
     */
    Query mQuery;

    /**
     * @hide
     */
    SearchInfoParcelable mRestoreState;

    private SearchResultDrawingAsyncTask mDrawingSearchResultTask;

    /**
     * @hide
     */
    boolean mChRooted;


    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle state) {
        if (DEBUG) {
            Log.d(TAG, "SearchActivity.onCreate"); //$NON-NLS-1$
        }

        // Check if app is running in chrooted mode
        this.mChRooted = FileManagerApplication.getAccessMode().compareTo(AccessMode.SAFE) == 0;

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(FileManagerSettings.INTENT_SETTING_CHANGED);
        filter.addAction(FileManagerSettings.INTENT_THEME_CHANGED);
        registerReceiver(this.mNotificationReceiver, filter);

        //Set in transition
        overridePendingTransition(R.anim.translate_to_right_in, R.anim.hold_out);

        //Set the main layout of the activity
        setContentView(R.layout.search);

        //Restore state
        if (state != null) {
            restoreState(state);
        }

        //Initialize action bars and search
        initTitleActionBar();
        initComponents();

        // Apply current theme
        applyTheme();

        if (this.mRestoreState != null) {
            //Restore activity from cached data
            loadFromCacheData();
        } else {
            //New query
            if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
                initSearch();
            } else if (ACTION_RESTORE.equals(getIntent().getAction())) {
                restoreState(getIntent().getExtras());
                loadFromCacheData();
            }
        }

        //Save state
        super.onCreate(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "SearchActivity.onDestroy"); //$NON-NLS-1$
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
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onNewIntent(Intent intent) {
        //New query
        if (Intent.ACTION_SEARCH.equals(getIntent().getAction())) {
            initSearch();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause() {
        //Set out transition
        overridePendingTransition(R.anim.hold_in, R.anim.translate_to_left_out);
        super.onPause();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (DEBUG) {
            Log.d(TAG, "SearchActivity.onSaveInstanceState"); //$NON-NLS-1$
        }
        saveState(outState);
        super.onSaveInstanceState(outState);
    }

    /**
     * Method that save the instance of the activity.
     *
     * @param state The current state of the activity
     */
    private void saveState(Bundle state) {
        try {
            if (this.mSearchListView.getAdapter() != null) {
                state.putParcelable(EXTRA_SEARCH_RESTORE, createSearchInfo());
            }
        } catch (Throwable ex) {
            Log.w(TAG, "The state can't be saved", ex); //$NON-NLS-1$
        }
    }

    /**
     * Method that restore the instance of the activity.
     *
     * @param state The previous state of the activity
     */
    private void restoreState(Bundle state) {
        try {
            if (state.containsKey(EXTRA_SEARCH_RESTORE)) {
                this.mRestoreState = state.getParcelable(EXTRA_SEARCH_RESTORE);
            }
        } catch (Throwable ex) {
            Log.w(TAG, "The state can't be restored", ex); //$NON-NLS-1$
        }
    }

    /**
     * Method that initializes the titlebar of the activity.
     */
    private void initTitleActionBar() {
        //Configure the action bar options
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setTitle(R.string.search);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.search, menu);
        mSearchFoundItems = menu.findItem(R.id.mnu_search_result_number);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                back(true, null, false);
                break;
            case R.id.mnu_search_settings:
                //Settings
                Intent settings = new Intent(this, SettingsPreferences.class);
                settings.putExtra(
                        PreferenceActivity.EXTRA_SHOW_FRAGMENT,
                        SearchPreferenceFragment.class.getName());
                startActivity(settings);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Method that initializes the component of the activity.
     */
    private void initComponents() {
        //Empty list view
        this.mEmptyListMsg = findViewById(R.id.search_empty_msg);
        //The list view
        this.mSearchListView = (ListView)findViewById(R.id.search_listview);
        this.mSearchListView.setOnItemClickListener(this);
        this.mSearchListView.setOnItemLongClickListener(this);

        // If we should set the listview to response to flinger gesture detection
        boolean useFlinger =
                Preferences.getSharedPreferences().getBoolean(
                        FileManagerSettings.SETTINGS_USE_FLINGER.getId(),
                            ((Boolean)FileManagerSettings.
                                    SETTINGS_USE_FLINGER.
                                        getDefaultValue()).booleanValue());
        if (useFlinger) {
            ((FlingerListView)this.mSearchListView).
                    setOnItemFlingerListener(this.mOnItemFlingerListener);
        }

        //Other components
        this.mSearchWaiting = (ProgressBar)findViewById(R.id.search_waiting);
        /*TODO: Set this info to the status action icon when "in progress" searching is focused on the list view and
                not the dialog * /
/*        this.mSearchFoundItems = (TextView)findViewById(R.id.search_status_found_items);
        setFoundItems(0, ""); //$NON-NLS-1$
        this.mSearchTerms = (TextView)findViewById(R.id.search_status_query_terms);
        this.mSearchTerms.setText(
                Html.fromHtml(getString(R.string.search_terms, ""))); //$NON-NLS-1$*/
    }

    /**
     * Method that initializes the titlebar of the activity.
     */
    private void initSearch() {
        //Stop any pending action
        try {
            if (SearchActivity.this.mDrawingSearchResultTask != null
                    && SearchActivity.this.mDrawingSearchResultTask.isRunning()) {
                SearchActivity.this.mDrawingSearchResultTask.cancel(true);
            }
        } catch (Throwable ex2) {
            /**NON BLOCK**/
        }
        try {
            if (SearchActivity.this.mDialog != null) {
                SearchActivity.this.mDialog.dismiss();
            }
        } catch (Throwable ex2) {
            /**NON BLOCK**/
        }

        //Recovery the search directory
        Bundle bundle = getIntent().getBundleExtra(SearchManager.APP_DATA);
        //If data is not present, use root directory to do the search
        this.mSearchDirectory = FileHelper.ROOT_DIRECTORY;
        if (bundle != null) {
            this.mSearchDirectory =
                    bundle.getString(EXTRA_SEARCH_DIRECTORY, FileHelper.ROOT_DIRECTORY);
        }

        //Retrieve the query ¿from voice recognizer?
        boolean voiceQuery = true;
        List<String> userQueries =
                getIntent().getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        if (userQueries == null || userQueries.size() == 0) {
            //From input text
            userQueries = new ArrayList<String>();
            //Recovers and save the last term search in the memory
            Preferences.setLastSearch(getIntent().getStringExtra(SearchManager.QUERY));
            userQueries.add(Preferences.getLastSearch());
            voiceQuery = false;
        }

        //Filter the queries? Needed if queries come from voice recognition
        final List<String> filteredUserQueries =
                (voiceQuery) ? filterQuery(userQueries) : userQueries;

        //Create the queries
        this.mQuery = new Query().fillSlots(filteredUserQueries);
        List<String> queries = this.mQuery.getQueries();

        //Check if some queries has lower than allowed, in this case
        //request the user for stop the search
        boolean ask = false;
        int cc = queries.size();
        for (int i = 0; i < cc; i++) {
            if (queries.get(i).trim().length() < MIN_CHARS_SEARCH) {
                ask = true;
                break;
            }
        }
        if (ask) {
            askUserBeforeSearch(voiceQuery, this.mQuery, this.mSearchDirectory);
        } else {
            doSearch(voiceQuery, this.mQuery, this.mSearchDirectory);
        }

    }

    /**
     * Method that ask the user before do the search.
     *
     * @param voiceQuery Indicates if the query is from voice recognition
     * @param query The terms of the search
     * @param searchDirectory The directory of the search
     */
    private void askUserBeforeSearch(
            final boolean voiceQuery, final Query query, final String searchDirectory) {
        //Show a dialog asking the user
        AlertDialog dialog =
                DialogHelper.createYesNoDialog(
                        this,
                        R.string.search_few_characters_title,
                        R.string.search_few_characters_msg,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface alertDialog, int which) {
                                if (which == DialogInterface.BUTTON_POSITIVE) {
                                    doSearch(voiceQuery, query, searchDirectory);
                                    return;
                                }

                                //Close search activity
                                back(true, null, false);
                            }
                        });
        DialogHelper.delegateDialogShow(this, dialog);
    }

    /**
     * Method that do the search.
     *
     * @param voiceQuery Indicates if the query is from voice recognition
     * @param query The terms of the search
     * @param searchDirectory The directory of the search
     * @hide
     */
    void doSearch(
            final boolean voiceQuery, final Query query, final String searchDirectory) {

        // Recovers the user preferences about save suggestions
        boolean saveSuggestions = Preferences.getSharedPreferences().getBoolean(
                FileManagerSettings.SETTINGS_SAVE_SEARCH_TERMS.getId(),
                ((Boolean)FileManagerSettings.SETTINGS_SAVE_SEARCH_TERMS.
                        getDefaultValue()).booleanValue());
        if (saveSuggestions) {
            //Save every query for use as recent suggestions
            SearchRecentSuggestions suggestions =
                    new SearchRecentSuggestions(this,
                            RecentSearchesContentProvider.AUTHORITY,
                            RecentSearchesContentProvider.MODE);
            if (!voiceQuery) {
                List<String> queries = query.getQueries();
                int cc = queries.size();
                for (int i = 0; i < cc; i++) {
                    suggestions.saveRecentQuery(queries.get(i), null);
                }
            }
        }

        //Set the listview
        if (this.mSearchListView.getAdapter() != null) {
            ((SearchResultAdapter)this.mSearchListView.getAdapter()).dispose();
        }
        this.mResultList = new ArrayList<FileSystemObject>();
        SearchResultAdapter adapter =
                new SearchResultAdapter(this,
                        new ArrayList<SearchResult>(), R.layout.search_item, this.mQuery);
        this.mSearchListView.setAdapter(adapter);

        // Set terms in action bar title
        getActionBar().setTitle(getString(R.string.search) + ": \"" + query.getTerms() + "\"");  //$NON-NLS-1$

        //Now, do the search in background
        this.mSearchListView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    //Retrieve the terms of the search
                    String label = getString(R.string.searching_action_label);

                    //Show a dialog for the progress
                    SearchActivity.this.mDialog =
                            new MessageProgressDialog(
                                    SearchActivity.this,
                                    0,
                                    R.string.searching, label, true);
                    // Initialize the
                    setProgressMsg(0);

                    // Set the cancel listener
                    SearchActivity.this.mDialog.setOnCancelListener(
                            new MessageProgressDialog.OnCancelListener() {
                                @Override
                                public boolean onCancel() {
                                    //User has requested the cancellation of the search
                                    //Broadcast the cancellation
                                    if (!SearchActivity.this.mExecutable.isCancelled()) {
                                        if (SearchActivity.this.mExecutable.cancel()) {
                                            ListAdapter listAdapter =
                                                    SearchActivity.
                                                        this.mSearchListView.getAdapter();
                                            if (listAdapter != null) {
                                                SearchActivity.this.toggleResults(
                                                        listAdapter.getCount() > 0, true);
                                            }
                                            return true;
                                        }
                                        return false;
                                    }
                                    return true;
                                }
                            });
                    SearchActivity.this.mDialog.show();

                    //Execute the query (search are process in background)
                    SearchActivity.this.mExecutable =
                            CommandHelper.findFiles(
                                    SearchActivity.this,
                                    searchDirectory,
                                    SearchActivity.this.mQuery,
                                    SearchActivity.this,
                                    null);

                } catch (Throwable ex) {
                    //Remove all elements
                    try {
                        SearchActivity.this.removeAll();
                    } catch (Throwable ex2) {
                        /**NON BLOCK**/
                    }
                    try {
                        if (SearchActivity.this.mDialog != null) {
                            SearchActivity.this.mDialog.dismiss();
                        }
                    } catch (Throwable ex2) {
                        /**NON BLOCK**/
                    }

                    //Capture the exception
                    Log.e(TAG, "Search failed", ex); //$NON-NLS-1$
                    DialogHelper.showToast(
                            SearchActivity.this,
                            R.string.search_error_msg, Toast.LENGTH_SHORT);
                    SearchActivity.this.mSearchListView.setVisibility(View.GONE);
                }
            }
        });
    }

    /**
     * Method that restore the activity from the cached data.
     */
    private void loadFromCacheData() {
        this.mSearchListView.post(new Runnable() {
            @Override
            public void run() {
                //Toggle results
                List<SearchResult> list = SearchActivity.this.mRestoreState.getSearchResultList();
                String directory = SearchActivity.this.mRestoreState.getSearchDirectory();
                SearchActivity.this.toggleResults(list.size() > 0, true);
                setFoundItems(list.size(), directory);

                //Set terms
                Query query = SearchActivity.this.mRestoreState.getSearchQuery();
                String terms =
                        TextUtils.join(" | ",  //$NON-NLS-1$;
                                query.getQueries().toArray(new String[]{}));
                if (terms.endsWith(" | ")) { //$NON-NLS-1$;
                    terms = ""; //$NON-NLS-1$;
                }
                getActionBar().setTitle(getString(R.string.search) + ": \"" + query.getTerms() + "\"");  //$NON-NLS-1$

                try {
                    if (SearchActivity.this.mSearchWaiting != null) {
                        SearchActivity.this.mSearchWaiting.setVisibility(View.VISIBLE);
                    }

                    //Add list to the listview
                    if (SearchActivity.this.mSearchListView.getAdapter() != null) {
                        ((SearchResultAdapter)SearchActivity.this.
                                mSearchListView.getAdapter()).clear();
                    }
                    SearchResultAdapter adapter =
                            new SearchResultAdapter(
                                                SearchActivity.this.mSearchListView.getContext(),
                                                list,
                                                R.layout.search_item,
                                                query);
                    SearchActivity.this.mSearchListView.setAdapter(adapter);
                    SearchActivity.this.mSearchListView.setSelection(0);

                } catch (Throwable ex) {
                    //Capture the exception
                    ExceptionUtil.translateException(SearchActivity.this, ex);

                } finally {
                    //Hide waiting
                    if (SearchActivity.this.mSearchWaiting != null) {
                        SearchActivity.this.mSearchWaiting.setVisibility(View.GONE);
                    }
                }
            }
        });
    }

    /**
     * Method that filter the user queries for valid queries only.<br/>
     * <br/>
     * Only allow query strings with more that 3 characters
     *
     * @param original The original user queries
     * @return List<String> The list of queries filtered
     */
    @SuppressWarnings("static-method")
    private List<String> filterQuery(List<String> original) {
        List<String> dst = new ArrayList<String>(original);
        int cc = dst.size();
        for (int i = cc - 1; i >= 0; i--) {
            String query = dst.get(i);
            if (query == null || query.trim().length() < MIN_CHARS_SEARCH) {
                dst.remove(i);
            }
        }
        return dst;
    }

    /**
     * Method that removes all items and display a message.
     * @hide
     */
    void removeAll() {
        SearchResultAdapter adapter = (SearchResultAdapter)this.mSearchListView.getAdapter();
        adapter.clear();
        this.mSearchListView.setSelection(0);
        toggleResults(false, true);
    }

    /**
     * Method that toggle the views when there are results.
     *
     * @param hasResults Indicates if there are results
     * @param showEmpty Show the empty list message
     * @hide
     */
    void toggleResults(boolean hasResults, boolean showEmpty) {
        this.mSearchListView.setVisibility(hasResults ? View.VISIBLE : View.INVISIBLE);
        this.mEmptyListMsg.setVisibility(!hasResults && showEmpty ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Method that display the number of found items.
     *
     * @param items The number of items
     * @param searchDirectory The search directory path
     * @hide
     */
    void setFoundItems(final int items, final String searchDirectory) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String directory = searchDirectory;
                if (SearchActivity.this.mChRooted &&
                        directory != null && directory.length() > 0) {
                    directory = StorageHelper.getChrootedPath(directory);
                }
                String foundItems =
                        getResources().
                                getQuantityString(
                                        R.plurals.search_found_items, items, Integer.valueOf(items));
                if (mSearchFoundItems != null) {
                    mSearchFoundItems.setTitle(
                            getString(
                                    R.string.search_found_items_in_directory,
                                    foundItems,
                                    directory));
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                back(true, null, false);
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        try {
            SearchResult result = ((SearchResultAdapter)parent.getAdapter()).getItem(position);
            FileSystemObject fso = result.getFso();
            if (fso instanceof Directory) {
                back(false, fso, false);
                return;
            } else if (fso instanceof Symlink) {
                Symlink symlink = (Symlink)fso;
                if (symlink.getLinkRef() != null && symlink.getLinkRef() instanceof Directory) {
                    back(false, symlink.getLinkRef(), false);
                    return;
                }
                fso = symlink.getLinkRef();
            }

            // Open the file with the preferred registered app
            back(false, fso, false);

        } catch (Throwable ex) {
            ExceptionUtil.translateException(this.mSearchListView.getContext(), ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // Different actions depending on user preference

        // Get the adapter, the search result and the fso
        SearchResultAdapter adapter = ((SearchResultAdapter)parent.getAdapter());
        SearchResult searchResult = adapter.getItem(position);
        FileSystemObject fso = searchResult.getFso();

        // Open the actions menu
        onRequestMenu(fso);
        return true; //Always consume the event
    }

    /**
     * Method invoked when a request to show the menu associated
     * with an item is started.
     *
     * @param item The item for which the request was started
     */
    public void onRequestMenu(FileSystemObject item) {
        // Prior to show the dialog, refresh the item reference
        FileSystemObject fso = null;
        try {
            fso = CommandHelper.getFileInfo(this, item.getFullPath(), false, null);
            if (fso == null) {
                throw new NoSuchFileOrDirectory(item.getFullPath());
            }

        } catch (Exception e) {
            // Notify the user
            ExceptionUtil.translateException(this, e);

            // Remove the object
            if (e instanceof FileNotFoundException || e instanceof NoSuchFileOrDirectory) {
                removeItem(item);
            }
            return;
        }
    }

    /**
     * Method that removes the {@link FileSystemObject} reference
     *
     * @param fso The file system object
     */
    private void removeItem(FileSystemObject fso) {
        SearchResultAdapter adapter =
                (SearchResultAdapter)this.mSearchListView.getAdapter();
        if (adapter != null) {
            int pos = adapter.getPosition(fso);
            if (pos != -1) {
                SearchResult sr = adapter.getItem(pos);
                adapter.remove(sr);
            }

            // Toggle resultset?
            toggleResults(adapter.getCount() > 0, true);
            setFoundItems(adapter.getCount(), this.mSearchDirectory);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestRefresh(Object o, boolean clearSelection) {
        // Refresh only the item
        SearchResultAdapter adapter =
                (SearchResultAdapter)this.mSearchListView.getAdapter();
        if (adapter != null) {
            if (o instanceof FileSystemObject) {

                FileSystemObject fso = (FileSystemObject)o;
                int pos = adapter.getPosition(fso);
                if (pos >= 0) {
                    SearchResult sr = adapter.getItem(pos);
                    sr.setFso(fso);
                }
            } else if (o == null) {
                // Refresh all
                List<SearchResult> results = adapter.getData();
                this.mResultList = new ArrayList<FileSystemObject>(results.size());
                int cc = results.size();
                for (int i = 0; i < cc; i++) {
                    this.mResultList.add(results.get(i).getFso());
                }
                drawResults();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRequestRemove(Object o, boolean clearSelection) {
        if (o instanceof FileSystemObject) {
            removeItem((FileSystemObject)o);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNavigateTo(Object o) {
        if (o instanceof FileSystemObject) {
            back(false, (FileSystemObject) o, true);
        }
    }

    /**
     * Method that returns to previous activity.
     *
     * @param cancelled Indicates if the activity was cancelled
     * @param item The fso
     * @param isChecked If the fso was fully retrieve previously to this call. Otherwise, a
     * getFileInfo call is done to complete the fso information
     * @hide
     */
    void back(final boolean cancelled, FileSystemObject item, boolean isChecked) {
        final Context ctx = SearchActivity.this;
        final Intent intent =  new Intent();
        boolean finish = true;
        if (cancelled) {
            if (SearchActivity.this.mDrawingSearchResultTask != null
                    && SearchActivity.this.mDrawingSearchResultTask.isRunning()) {
                SearchActivity.this.mDrawingSearchResultTask.cancel(true);
            }
            if (this.mRestoreState != null) {
                intent.putExtra(
                        NavigationActivity.EXTRA_SEARCH_LAST_SEARCH_DATA,
                        (Parcelable)this.mRestoreState);
            }
            setResult(RESULT_CANCELED, intent);
        } else {
            // Check that the bookmark exists
            FileSystemObject fso = item;
            try {
                if (!isChecked) {
                    fso = CommandHelper.getFileInfo(ctx, item.getFullPath(), null);
                }
                finish = navigateTo(fso, intent);

            } catch (Exception e) {
                // Capture the exception
                final FileSystemObject fFso = fso;
                final OnRelaunchCommandResult relaunchListener = new OnRelaunchCommandResult() {
                    @Override
                    public void onSuccess() {
                        if (navigateTo(fFso, intent)) {
                            exit();
                        }
                    }
                    @Override
                    public void onFailed(Throwable cause) {
                        ExceptionUtil.translateException(ctx, cause, false, false);
                    }
                    @Override
                    public void onCancelled() { /** NON BLOCK**/}
                };
                ExceptionUtil.translateException(ctx, e, false, true, relaunchListener);
                if (!(e instanceof RelaunchableException)) {
                    if (e instanceof NoSuchFileOrDirectory || e instanceof FileNotFoundException) {
                        // The fso not exists, delete the fso from the search
                        try {
                            removeItem(fso);
                        } catch (Exception ex) {/**NON BLOCK**/}
                    }
                }
                return;
            }
        }

        // End this activity
        if (finish) {
            exit();
        }
    }

    /**
     * Method invoked when the activity needs to exit
     */
    private void exit() {
        if (this.mSearchListView.getAdapter() != null) {
            ((SearchResultAdapter)this.mSearchListView.getAdapter()).dispose();
        }
        finish();
    }

    /**
     * Method that navigate to the file system used the intent (NavigationActivity)
     *
     * @param fso The file system object to navigate to
     * @param intent The intent used to navigate to
     * @return boolean If the action implies finish this activity
     */
    boolean navigateTo(FileSystemObject fso, Intent intent) {
        if (fso != null) {
            if (FileHelper.isDirectory(fso)) {
                intent.putExtra(NavigationActivity.EXTRA_SEARCH_ENTRY_SELECTION, fso);
                intent.putExtra(
                        NavigationActivity.EXTRA_SEARCH_LAST_SEARCH_DATA,
                        (Parcelable)createSearchInfo());
                setResult(RESULT_OK, intent);
                return true;
            }

            // Open the file here, so when focus back to the app, the search activity
            // its in top of the stack
            IntentsActionPolicy.openFileSystemObject(this, fso, false, null, null);
        } else {
            // The fso not exists, delete the fso from the search
            try {
                removeItem(fso);
            } catch (Exception ex) {/**NON BLOCK**/}
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAsyncStart() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SearchActivity.this.toggleResults(false, false);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAsyncEnd(boolean cancelled) {
        this.mSearchListView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    //Dismiss the dialog
                    if (SearchActivity.this.mDialog != null) {
                        SearchActivity.this.mDialog.dismiss();
                    }

                    // Resolve the symlinks
                    FileHelper.resolveSymlinks(
                                SearchActivity.this, SearchActivity.this.mResultList);

                    // Draw the results
                    drawResults();

                } catch (Throwable ex) {
                    Log.e(TAG, "onAsyncEnd method fails", ex); //$NON-NLS-1$
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onPartialResult(final Object partialResults) {
        //Saved in the global result list, for save at the end
        if (partialResults instanceof FileSystemObject) {
            SearchActivity.this.mResultList.add((FileSystemObject)partialResults);
        } else {
            SearchActivity.this.mResultList.addAll((List<FileSystemObject>)partialResults);
        }

        //Notify progress
        this.mSearchListView.post(new Runnable() {
            @Override
            public void run() {
                if (SearchActivity.this.mDialog != null) {
                    int progress = SearchActivity.this.mResultList.size();
                    setProgressMsg(progress);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAsyncExitCode(int exitCode) {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    @Override
    public void onException(Exception cause) {
        //Capture the exception
        ExceptionUtil.translateException(this, cause);
    }

    /**
     * Method that draw the results in the listview
     * @hide
     */
    void drawResults() {
        //Toggle results
        this.toggleResults(this.mResultList.size() > 0, true);
        setFoundItems(this.mResultList.size(), this.mSearchDirectory);

        //Create the task for drawing the data
        this.mDrawingSearchResultTask =
                                new SearchResultDrawingAsyncTask(
                                        this.mSearchListView,
                                        this.mSearchWaiting,
                                        this.mResultList,
                                        this.mQuery);
        this.mDrawingSearchResultTask.execute();
    }

    /**
     * Method that creates a {@link SearchInfoParcelable} reference from
     * the current data.
     *
     * @return SearchInfoParcelable The search info reference
     */
    private SearchInfoParcelable createSearchInfo() {
        SearchInfoParcelable parcel = new SearchInfoParcelable();
        parcel.setSearchDirectory(this.mSearchDirectory);
        parcel.setSearchResultList(
                ((SearchResultAdapter)this.mSearchListView.getAdapter()).getData());
        parcel.setSearchQuery(this.mQuery);
        return parcel;
    }

    /**
     * Method that set the progress of the search
     *
     * @param progress The progress
     * @hide
     */
    void setProgressMsg(int progress) {
        String msg =
                getResources().getQuantityString(
                        R.plurals.search_found_items,
                        progress,
                        Integer.valueOf(progress));
        SearchActivity.this.mDialog.setProgress(Html.fromHtml(msg));
    }

    /**
     * Method that applies the current theme to the activity
     * @hide
     */
    void applyTheme() {
        Theme theme = ThemeManager.getCurrentTheme(this);
        theme.setBaseTheme(this, false);

        // ContentView
        theme.setBackgroundDrawable(
                this, getWindow().getDecorView(), "background_drawable"); //$NON-NLS-1$
        //ListView
        if (this.mSearchListView.getAdapter() != null) {
            ((SearchResultAdapter)this.mSearchListView.getAdapter()).notifyDataSetChanged();
        }
        this.mSearchListView.setDivider(
                theme.getDrawable(this, "horizontal_divider_drawable")); //$NON-NLS-1$
        this.mSearchListView.invalidate();
    }
}

