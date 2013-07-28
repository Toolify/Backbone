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

package me.toolify.backbone.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import me.toolify.backbone.FileManagerApplication;
import me.toolify.backbone.R;
import me.toolify.backbone.adapters.BookmarksAdapter;
import me.toolify.backbone.bus.BusProvider;
import me.toolify.backbone.bus.events.BookmarkDeleteEvent;
import me.toolify.backbone.bus.events.BookmarkOpenEvent;
import me.toolify.backbone.bus.events.BookmarkRefreshEvent;
import me.toolify.backbone.model.Bookmark;
import me.toolify.backbone.model.Bookmark.BOOKMARK_TYPE;
import me.toolify.backbone.model.MountPoint;
import me.toolify.backbone.preferences.AccessMode;
import me.toolify.backbone.preferences.Bookmarks;
import me.toolify.backbone.preferences.FileManagerSettings;
import me.toolify.backbone.preferences.Preferences;
import me.toolify.backbone.ui.ThemeManager;
import me.toolify.backbone.ui.ThemeManager.Theme;
import me.toolify.backbone.ui.dialogs.InitialDirectoryDialog;
import me.toolify.backbone.ui.widgets.FlingerListView;
import me.toolify.backbone.ui.widgets.FlingerListView.OnItemFlingerListener;
import me.toolify.backbone.ui.widgets.FlingerListView.OnItemFlingerResponder;
import me.toolify.backbone.util.*;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.List;

/**
 * An fragment for showing bookmarks and links.
 */
public class BookmarksFragment extends Fragment implements OnItemClickListener, OnClickListener {

    private static final String TAG = "BookmarksActivity"; //$NON-NLS-1$

    private static boolean DEBUG = false;
    
    public interface OnBookmarkSelectedListener {
    	public void onBookmarkSelected(String path);
    }
    
    /**
     * A listener for flinging events from {@link me.toolify.backbone.ui.widgets.FlingerListView}
     */
    private final OnItemFlingerListener mOnItemFlingerListener = new OnItemFlingerListener() {

        @Override
        public boolean onItemFlingerStart(
                AdapterView<?> parent, View view, int position, long id) {
            try {
                // Response if the item can be removed
                BookmarksAdapter adapter = (BookmarksAdapter)parent.getAdapter();
                Bookmark bookmark = adapter.getItem(position);
                if (bookmark != null &&
                    bookmark.mType.compareTo(BOOKMARK_TYPE.USER_DEFINED) == 0) {
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
                BookmarksAdapter adapter = (BookmarksAdapter)parent.getAdapter();
                Bookmark bookmark = adapter.getItem(position);
                if (bookmark != null &&
                        bookmark.mType.compareTo(BOOKMARK_TYPE.USER_DEFINED) == 0) {
                    boolean result = Bookmarks.removeBookmark(mActivity, bookmark);
                    if (!result) {
                        //Show warning
                        DialogHelper.showToast(mActivity,
                                R.string.msgs_operation_failure, Toast.LENGTH_SHORT);
                        responder.cancel();
                        return;
                    }
                    responder.accept();
                    adapter.remove(bookmark);
                    adapter.notifyDataSetChanged();
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

    // Bookmark list XML tags
    private static final String TAG_BOOKMARKS = "Bookmarks"; //$NON-NLS-1$
    private static final String TAG_BOOKMARK = "bookmark"; //$NON-NLS-1$

    /**
     * @hide
     */
    ListView mBookmarksListView;

    private Activity mActivity;
    
    OnBookmarkSelectedListener mListener;

    private boolean mChRooted;

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bookmarks_fragment, container, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(Bundle state) {
        if (DEBUG) {
            Log.d(TAG, "BookmarksFragment.onActivityCreated"); //$NON-NLS-1$
        }

        // Is ChRooted?
        this.mChRooted = FileManagerApplication.getAccessMode().compareTo(AccessMode.SAFE) == 0;
        
        mActivity = getActivity();

        //Initialize action bars and data
        initBookmarks();

        // Apply the theme
        applyTheme();

        //Save state
        super.onCreate(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "BookmarksFragment.onDestroy"); //$NON-NLS-1$
        }

        //All destroy. Continue
        super.onDestroy();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        BusProvider.getInstance().register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        BusProvider.getInstance().unregister(this);
    }

    /**
     * Method that initializes the titlebar of the activity.
     */
    private void initBookmarks() {
        this.mBookmarksListView = (ListView)mActivity.findViewById(R.id.bookmarks_listview);
        List<Bookmark> bookmarks = new ArrayList<Bookmark>();
        BookmarksAdapter adapter = new BookmarksAdapter(mActivity, bookmarks, this);
        this.mBookmarksListView.setAdapter(adapter);
        this.mBookmarksListView.setOnItemClickListener(this);

        // If we should set the listview to response to flinger gesture detection
        boolean useFlinger =
                Preferences.getSharedPreferences().getBoolean(
                        FileManagerSettings.SETTINGS_USE_FLINGER.getId(),
                            ((Boolean)FileManagerSettings.
                                    SETTINGS_USE_FLINGER.
                                        getDefaultValue()).booleanValue());
        if (useFlinger) {
            ((FlingerListView)this.mBookmarksListView).
                setOnItemFlingerListener(this.mOnItemFlingerListener);
        }

        // Reload the data
        refresh();
    }

    @Subscribe
    public void onBookmarkDeleteEvent (BookmarkDeleteEvent event) {
    	Bookmark b = Bookmarks.getBookmark(mActivity.getContentResolver(), event.path);
        Bookmarks.removeBookmark(mActivity, b);
        refresh();
    }
    
    @Subscribe
    public void onBookmarkRefreshEvent (BookmarkRefreshEvent event) {
    	refresh();
    }
    
    /**
     * Method that makes the refresh of the data.
     */
    public void refresh() {
        // Retrieve the loading view
        final View waiting = mActivity.findViewById(R.id.bookmarks_waiting);
        final BookmarksAdapter adapter = (BookmarksAdapter)this.mBookmarksListView.getAdapter();

        // Load the history in background
        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            Exception mCause;
            List<Bookmark> mBookmarks;

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    this.mBookmarks = loadBookmarks();
                    return Boolean.TRUE;

                } catch (Exception e) {
                    this.mCause = e;
                    return Boolean.FALSE;
                }
            }

            @Override
            protected void onPreExecute() {
                waiting.setVisibility(View.VISIBLE);
                adapter.clear();
            }

            @Override
            protected void onPostExecute(Boolean result) {
                waiting.setVisibility(View.GONE);
                if (result.booleanValue()) {
                    adapter.addAll(this.mBookmarks);
                    adapter.notifyDataSetChanged();
                    BookmarksFragment.this.mBookmarksListView.setSelection(0);

                } else {
                    if (this.mCause != null) {
                        ExceptionUtil.translateException(mActivity, this.mCause);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                waiting.setVisibility(View.GONE);
            }
        };
        task.execute();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Bookmark bookmark = ((BookmarksAdapter)parent.getAdapter()).getItem(position);
        BusProvider.getInstance().post(new BookmarkOpenEvent(bookmark.mPath));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(View v) {
      //Retrieve the position
      final int position = ((Integer)v.getTag()).intValue();
      final BookmarksAdapter adapter = (BookmarksAdapter)this.mBookmarksListView.getAdapter();
      final Bookmark bookmark = adapter.getItem(position);

      //Configure home
      if (bookmark.mType.compareTo(BOOKMARK_TYPE.HOME) == 0) {
          //Show a dialog for configure initial directory
          InitialDirectoryDialog dialog = new InitialDirectoryDialog(mActivity);
          dialog.setOnValueChangedListener(new InitialDirectoryDialog.OnValueChangedListener() {
              @Override
              public void onValueChanged(String newInitialDir) {
                  adapter.getItem(position).mPath = newInitialDir;
                  adapter.notifyDataSetChanged();
              }
          });
          dialog.show();
          return;
      }

      //Remove bookmark
      if (bookmark.mType.compareTo(BOOKMARK_TYPE.USER_DEFINED) == 0) {
          boolean result = Bookmarks.removeBookmark(mActivity, bookmark);
          if (!result) {
              //Show warning
              DialogHelper.showToast(mActivity, R.string.msgs_operation_failure, Toast.LENGTH_SHORT);
              return;
          }
          adapter.remove(bookmark);
          adapter.notifyDataSetChanged();
          return;
      }
    }

    /**
     * Method that loads all kind of bookmarks and join in
     * an array to be used in the listview adapter.
     *
     * @return List<Bookmark>
     * @hide
     */
    List<Bookmark> loadBookmarks() {
        // Bookmarks = HOME + FILESYSTEM + SD STORAGES + USER DEFINED
        // In ChRooted mode = SD STORAGES + USER DEFINED (from SD STORAGES)
        List<Bookmark> bookmarks = new ArrayList<Bookmark>();
        if (!this.mChRooted) {
            bookmarks.add(loadHomeBookmarks());
            bookmarks.addAll(loadFilesystemBookmarks());
        }
        bookmarks.addAll(loadSdStorageBookmarks());
        bookmarks.addAll(loadUserBookmarks());
        return bookmarks;
    }

    /**
     * Method that loads the home bookmark from the user preference.
     *
     * @return Bookmark The bookmark loaded
     */
    private Bookmark loadHomeBookmarks() {
        String initialDir = Preferences.getSharedPreferences().getString(
                                FileManagerSettings.SETTINGS_INITIAL_DIR.getId(),
                                (String)FileManagerSettings.SETTINGS_INITIAL_DIR.getDefaultValue());
        return new Bookmark(BOOKMARK_TYPE.HOME, Bookmark.BOOKMARK_CATEGORY.LOCATIONS, getString(R.string.bookmarks_home), initialDir);
    }

    /**
     * Method that loads the filesystem bookmarks from the internal xml file.
     * (defined by this application)
     *
     * @return List<Bookmark> The bookmarks loaded
     */
    private List<Bookmark> loadFilesystemBookmarks() {
        try {
            //Initialize the bookmarks
            List<Bookmark> bookmarks = new ArrayList<Bookmark>();

            //Read the command list xml file
            XmlResourceParser parser = getResources().getXml(R.xml.filesystem_bookmarks);

            try {
                //Find the root element
                XmlUtils.beginDocument(parser, TAG_BOOKMARKS);
                while (true) {
                    XmlUtils.nextElement(parser);
                    String element = parser.getName();
                    if (element == null) {
                        break;
                    }

                    if (TAG_BOOKMARK.equals(element)) {
                        CharSequence name = null;
                        CharSequence directory = null;

                        try {
                            name =
                                    getString(parser.getAttributeResourceValue(
                                            R.styleable.Bookmark_name, 0));
                        } catch (Exception e) {/**NON BLOCK**/}
                        try {
                            directory =
                                    getString(parser.getAttributeResourceValue(
                                            R.styleable.Bookmark_directory, 0));
                        } catch (Exception e) {/**NON BLOCK**/}
                        if (directory == null) {
                            directory =
                                    parser.getAttributeValue(R.styleable.Bookmark_directory);
                        }
                        if (name != null && directory != null) {
                            bookmarks.add(
                                    new Bookmark(
                                            BOOKMARK_TYPE.FILESYSTEM,
                                            Bookmark.BOOKMARK_CATEGORY.LOCATIONS,
                                            name.toString(),
                                            directory.toString()));
                        }
                    }
                }

                //Return the bookmarks
                return bookmarks;

            } finally {
                parser.close();
            }
        } catch (Throwable ex) {
            Log.e(TAG, "Load filesystem bookmarks failed", ex); //$NON-NLS-1$
        }

        //No data
        return new ArrayList<Bookmark>();
    }

    /**
     * Method that loads the secure digital card storage bookmarks from the system.
     *
     * @return List<Bookmark> The bookmarks loaded
     */
    private List<Bookmark> loadSdStorageBookmarks() {
        //Initialize the bookmarks
        List<Bookmark> bookmarks = new ArrayList<Bookmark>();

        try {
            //Recovery sdcards from storage manager
            Object[] volumes = StorageHelper.getStorageVolumes(mActivity.getApplication());
            int cc = volumes.length;
            for (int i = 0; i < cc ; i++) {
                String path = StorageHelper.getStoragePath(volumes[i]);
                if(!StorageHelper.isValidMount(path)) continue;
                if (path.toLowerCase().indexOf("usb") != -1) { //$NON-NLS-1$
                    bookmarks.add(
                            new Bookmark(
                                    BOOKMARK_TYPE.USB,
                                    Bookmark.BOOKMARK_CATEGORY.LOCATIONS,
                                    StorageHelper.getStorageVolumeDescription(
                                            mActivity.getApplication(), volumes[i]),
                                    path));
                } else {
                    bookmarks.add(
                            new Bookmark(
                                    BOOKMARK_TYPE.SDCARD,
                                    Bookmark.BOOKMARK_CATEGORY.LOCATIONS,
                                    StorageHelper.getStorageVolumeDescription(
                                            mActivity.getApplication(), volumes[i]),
                                    path));
                }
            }

            //Return the bookmarks
            return bookmarks;
        } catch (Throwable ex) {
            Log.e(TAG, "Load filesystem bookmarks failed", ex); //$NON-NLS-1$
        }

        //No data
        return new ArrayList<Bookmark>();
    }

    /**
     * Method that loads the user bookmarks (added by the user).
     *
     * @return List<Bookmark> The bookmarks loaded
     */
    private List<Bookmark> loadUserBookmarks() {
        List<Bookmark> bookmarks = new ArrayList<Bookmark>();
        Cursor cursor = Bookmarks.getAllBookmarks(mActivity.getContentResolver());
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Bookmark bm = new Bookmark(cursor);
                    if (this.mChRooted && !StorageHelper.isPathInStorageVolume(bm.mPath)) {
                        continue;
                    }
                    bookmarks.add(bm);
                } while (cursor.moveToNext());
            }
        } finally {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Exception e) {/**NON BLOCK**/}
        }
        return bookmarks;
    }

    /**
     * Method that applies the current theme to the activity
     * @hide
     */
    void applyTheme() {
        Theme theme = ThemeManager.getCurrentTheme(mActivity);

        if (((BookmarksAdapter)this.mBookmarksListView.getAdapter()) != null) {
            ((BookmarksAdapter)this.mBookmarksListView.getAdapter()).notifyThemeChanged();
            ((BookmarksAdapter)this.mBookmarksListView.getAdapter()).notifyDataSetChanged();
        }
        this.mBookmarksListView.setBackgroundColor(
        		theme.getColor(mActivity, "menu_drawer_background_color"));
        this.mBookmarksListView.setDivider(
                theme.getDrawable(mActivity, "dark_horizontal_divider_drawable")); //$NON-NLS-1$
        this.mBookmarksListView.invalidate();
    }
}
