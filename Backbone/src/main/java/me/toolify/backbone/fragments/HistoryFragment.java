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
import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import me.toolify.backbone.FileManagerApplication;
import me.toolify.backbone.R;
import me.toolify.backbone.activities.HistoryActivity;
import me.toolify.backbone.adapters.HistoryAdapter;
import me.toolify.backbone.model.History;
import me.toolify.backbone.preferences.AccessMode;
import me.toolify.backbone.ui.ThemeManager;
import me.toolify.backbone.util.ExceptionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryFragment extends Fragment implements OnItemClickListener {

    private static final String TAG = "HistoryFragment"; //$NON-NLS-1$

    private static boolean DEBUG = false;

    /**
     * @hide
     */
    ListView mListView;
    /**
     * @hide
     */
    HistoryAdapter mAdapter;
    /**
     * @hide
     */
    private Activity mActivity;

    private boolean mChRooted;

    boolean mIsEmpty;
    public boolean mIsClearHistory;

    /**
     * Intent extra parameter for the history data.
     */
    public static final String EXTRA_HISTORY_LIST = "extra_history_list";  //$NON-NLS-1$

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.history_fragment, container, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(Bundle state) {
        if (DEBUG) {
            Log.d(TAG, "HistoryFragment.onActivityCreated"); //$NON-NLS-1$
        }

        mActivity = getActivity();


        this.mIsEmpty = false;
        this.mIsClearHistory = false;

        // Is ChRooted?
        this.mChRooted = FileManagerApplication.getAccessMode().compareTo(AccessMode.SAFE) == 0;

        //Initialize action bars and data
        initHistory();

        // Apply the theme
        applyTheme();

        //Save state
        super.onCreate(state);
    }

    /**
     * Method that initializes the history listview of the activity.
     */
    @SuppressWarnings("unchecked")
    private void initHistory() {
        // Retrieve the loading view
        final View waiting = mActivity.findViewById(R.id.history_waiting);

        this.mListView = (ListView)mActivity.findViewById(R.id.history_listview);

        // Load the history in background
        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            Exception mCause;
            List<History> mHistory;

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    this.mHistory =
                            (List<History>)mActivity.getIntent().getSerializableExtra(EXTRA_HISTORY_LIST);
                    if (this.mHistory.isEmpty()) {
                        View msg = mActivity.findViewById(R.id.history_empty_msg);
                        msg.setVisibility(View.VISIBLE);
                        return Boolean.TRUE;
                    }
                    HistoryFragment.this.mIsEmpty = this.mHistory.isEmpty();

                    //Show inverted history
                    final List<History> adapterList = new ArrayList<History>(this.mHistory);
                    Collections.reverse(adapterList);
                    HistoryFragment.this.mAdapter =
                            new HistoryAdapter(mActivity, adapterList);

                    return Boolean.TRUE;

                } catch (Exception e) {
                    this.mCause = e;
                    return Boolean.FALSE;
                }
            }

            @Override
            protected void onPreExecute() {
                waiting.setVisibility(View.VISIBLE);
            }

            @Override
            protected void onPostExecute(Boolean result) {
                waiting.setVisibility(View.GONE);
                if (result.booleanValue()) {
                    if (HistoryFragment.this.mListView != null &&
                            HistoryFragment.this.mAdapter != null) {

                        HistoryFragment.this.mListView.
                                setAdapter(HistoryFragment.this.mAdapter);
                        HistoryFragment.this.mListView.
                                setOnItemClickListener(HistoryFragment.this);
                    }

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
        History history = ((HistoryAdapter)parent.getAdapter()).getItem(position);
        back(false, history);
    }

    /**
     * Method that clean the history and return back to navigation view
     *  @hide
     */
    public void clearHistory() {
        if (this.mAdapter != null) {
            this.mAdapter.clear();
            this.mAdapter.notifyDataSetChanged();
            View msg = mActivity.findViewById(R.id.history_empty_msg);
            msg.setVisibility(View.VISIBLE);
            this.mIsClearHistory = true;
        }
    }

    /**
     * Method that returns to previous activity and.
     *
     * @param cancelled Indicates if the activity was cancelled
     * @param history The selected history
     */
    private void back(final boolean cancelled, final History history) {
        if (mActivity instanceof HistoryActivity) {
            ((HistoryActivity) mActivity).back(cancelled, history);
        }
    }

    /**
     * Method that applies the current theme to the activity
     * @hide
     */
    void applyTheme() {
        ThemeManager.Theme theme = ThemeManager.getCurrentTheme(mActivity);
        theme.setBaseTheme(mActivity, false);

        if (this.mAdapter != null) {
            this.mAdapter.notifyThemeChanged();
            this.mAdapter.notifyDataSetChanged();
        }
        this.mListView.setDivider(
                theme.getDrawable(mActivity, "horizontal_divider_drawable")); //$NON-NLS-1$
        this.mListView.invalidate();
    }

}
