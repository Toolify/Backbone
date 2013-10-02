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

package me.toolify.backbone.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import me.toolify.backbone.R;
import me.toolify.backbone.model.Bookmark;
import me.toolify.backbone.model.Bookmark.BOOKMARK_TYPE;
import me.toolify.backbone.ui.IconHolder;
import me.toolify.backbone.ui.ThemeManager;
import me.toolify.backbone.ui.ThemeManager.Theme;
import me.toolify.backbone.util.BookmarksHelper;

import java.util.List;

/**
 * An implementation of {@link ArrayAdapter} for display bookmarks.
 */
public class BookmarksAdapter extends ArrayAdapter<Bookmark> {

    /**
     * A class that conforms with the ViewHolder pattern to performance
     * the list view rendering.
     */
    private static class ViewHolder {
        /**
         * @hide
         */
        public ViewHolder() {
            super();
        }
        TextView mTvSeparator;
        ImageView mTvIcon;
        TextView mTvName;
        TextView mTvPath;
        ImageButton mBtAction;
    }

    /**
     * A class that holds the full data information.
     */
    private static class DataHolder {
        /**
         * @hide
         */
        public DataHolder() {
            super();
        }
        Drawable mDwIcon;
        String mName;
        String mPath;
        String mCategoryTitle;
        Drawable mDwAction;
        String mActionCd;
    }



    private DataHolder[] mData;
    private int[] mCellStates;
    private IconHolder mIconHolder;
    private final OnClickListener mOnActionClickListener;

    // The resource item layout
    private static final int RESOURCE_LAYOUT = R.layout.bookmarks_item;

    // The resource of the item separator
    private static final int RESOURCE_ITEM_SEPARATOR = R.id.bookmarks_item_separator;
    // The resource of the item icon
    private static final int RESOURCE_ITEM_ICON = R.id.bookmarks_item_icon;
    // The resource of the item name
    private static final int RESOURCE_ITEM_NAME = R.id.bookmarks_item_name;
    // The resource of the item directory
    private static final int RESOURCE_ITEM_PATH = R.id.bookmarks_item_path;
    // The resource of the item button action
    private static final int RESOURCE_ITEM_ACTION = R.id.bookmarks_item_action;

    // State of ListView item that has never been determined.
    private static final int STATE_UNKNOWN = 0;
    //State of a ListView item that is sectioned. A sectioned item must display the separator.
    private static final int STATE_SECTIONED_CELL = 1;
    //State of a ListView item that is not sectioned and therefore does not display the separator.
    private static final int STATE_REGULAR_CELL = 2;

    /**
     * Constructor of <code>BookmarksAdapter</code>.
     *
     * @param context The current context
     * @param bookmarks The bookmarks
     * @param onActionClickListener The listener for listen action clicks
     */
    public BookmarksAdapter(
            Context context, List<Bookmark> bookmarks, OnClickListener onActionClickListener) {
        super(context, RESOURCE_ITEM_NAME, bookmarks);
        this.mIconHolder = new IconHolder(context);
        this.mOnActionClickListener = onActionClickListener;

        //Do cache of the data for better performance
        processData(bookmarks);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyDataSetChanged() {
        processData(null);
        super.notifyDataSetChanged();
    }

    /**
     * Method that dispose the elements of the adapter.
     */
    public void dispose() {
        clear();
        this.mData = null;
        if (mIconHolder != null) {
            mIconHolder.cleanup();
            mIconHolder = null;
        }
    }

    /**
     * Method that process the data before use {@link #getView} method.
     *
     * @param bookmarks The list of bookmarks (to better performance) or null.
     */
    private void processData(List<Bookmark> bookmarks) {
        this.mData = new DataHolder[getCount()];
        this.mCellStates = new int[getCount()];
        int cc = (bookmarks == null) ? getCount() : bookmarks.size();
        for (int i = 0; i < cc; i++) {
            //Bookmark info
            Bookmark bookmark = (bookmarks == null) ? getItem(i) : bookmarks.get(i);

            //Build the data holder
            this.mData[i] = new BookmarksAdapter.DataHolder();
            this.mData[i].mDwIcon =
                    this.mIconHolder.getDrawable(BookmarksHelper.getIcon(bookmark));
            this.mData[i].mName = bookmark.mName;
            this.mData[i].mPath = bookmark.mPath;
            switch (bookmark.mCategory) {
                case LOCATIONS:
                    this.mData[i].mCategoryTitle = getContext().getString(R.string.bookmarks_header_locations);
                    break;
                case USER_BOOKMARKS:
                    this.mData[i].mCategoryTitle = getContext().getString(R.string.bookmarks_header_user_bookmarks);
                    break;
                case CLOUD:
                    this.mData[i].mCategoryTitle = getContext().getString(R.string.bookmarks_header_cloud);
                    break;
            }
            this.mData[i].mDwAction = null;
            this.mData[i].mActionCd = null;
            if (bookmark.mType.compareTo(BOOKMARK_TYPE.HOME) == 0) {
                this.mData[i].mDwAction =
                        this.mIconHolder.getDrawable("ic_config_drawable"); //$NON-NLS-1$
                this.mData[i].mActionCd =
                        getContext().getString(R.string.bookmarks_button_config_cd);
            } else if (bookmark.mType.compareTo(BOOKMARK_TYPE.USER_DEFINED) == 0) {
                this.mData[i].mDwAction =
                        this.mIconHolder.getDrawable("ic_close_drawable"); //$NON-NLS-1$
                this.mData[i].mActionCd =
                        getContext().getString(R.string.bookmarks_button_remove_bookmark_cd);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        // Separator List View Header
        boolean needSeparator = false;
        switch (mCellStates[position]) {
            case STATE_SECTIONED_CELL:
                needSeparator = true;
                break;

            case STATE_REGULAR_CELL:
                needSeparator = false;
                break;

            case STATE_UNKNOWN:
            default:
                // A separator is needed if it's the first itemview of the
                // ListView or if the group of the current cell is different
                // from the previous itemview.
                if (position == 0) {
                    needSeparator = true;
                } else {
                    // Test to see if the category has changed since the last bookmark
                    if (getItem(position).mCategory != null && getItem(position-1).mCategory != null &&
                            getItem(position).mCategory != getItem(position-1).mCategory) {
                        needSeparator = true;
                    }
                }

                // Cache the result
                mCellStates[position] = needSeparator ? STATE_SECTIONED_CELL : STATE_REGULAR_CELL;
                break;
        }

        //Check to reuse view
        View v = convertView;
        if (v == null) {
            //Create the view holder
            LayoutInflater li =
                    (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = li.inflate(RESOURCE_LAYOUT, parent, false);
            ViewHolder viewHolder = new BookmarksAdapter.ViewHolder();
            viewHolder.mTvSeparator = (TextView)v.findViewById(RESOURCE_ITEM_SEPARATOR);
            viewHolder.mTvIcon = (ImageView)v.findViewById(RESOURCE_ITEM_ICON);
            viewHolder.mTvName = (TextView)v.findViewById(RESOURCE_ITEM_NAME);
            viewHolder.mTvPath = (TextView)v.findViewById(RESOURCE_ITEM_PATH);
            viewHolder.mBtAction = (ImageButton)v.findViewById(RESOURCE_ITEM_ACTION);
            viewHolder.mBtAction.setTag(Integer.valueOf(position));
            v.setTag(viewHolder);

            // Apply the current theme
            Theme theme = ThemeManager.getCurrentTheme(getContext());
            theme.setBackgroundDrawable(
                    getContext(), v, "selectors_deselected_drawable"); //$NON-NLS-1$
/*          Legacy CM theme code. We should be getting this stuff exclusively
            from the layout, because this is a nightmare to keep orderly.
            theme.setTextColor(
                    getContext(), viewHolder.mTvName, "nav_drawer_text_color"); //$NON-NLS-1$
            theme.setTextColor(
                    getContext(), viewHolder.mTvPath, "nav_drawer_text_color"); //$NON-NLS-1$*/
        }

        //Retrieve data holder
        final DataHolder dataHolder = this.mData[position];

        //Retrieve the view holder
        ViewHolder viewHolder = (ViewHolder)v.getTag();

        //Set the data
        if (needSeparator) {
            viewHolder.mTvSeparator.setText(dataHolder.mCategoryTitle);
            viewHolder.mTvSeparator.setVisibility(View.VISIBLE);
        } else {
            viewHolder.mTvSeparator.setVisibility(View.GONE);
        }
        viewHolder.mTvIcon.setImageDrawable(dataHolder.mDwIcon);
        viewHolder.mTvName.setText(dataHolder.mName);
        viewHolder.mTvPath.setText(dataHolder.mPath);
        boolean hasAction = dataHolder.mDwAction != null;
        viewHolder.mBtAction.setImageDrawable(hasAction ? dataHolder.mDwAction : null);
        viewHolder.mBtAction.setVisibility(hasAction ? View.VISIBLE : View.GONE);
        viewHolder.mBtAction.setOnClickListener(this.mOnActionClickListener);
        viewHolder.mBtAction.setContentDescription(dataHolder.mActionCd);

        //Return the view
        return v;
    }

    /**
     * Method that should be invoked when the theme of the app was changed
     */
    public void notifyThemeChanged() {
        if (mIconHolder != null) {
            mIconHolder.cleanup();
        }
        // Empty icon holder
        this.mIconHolder = new IconHolder(getContext());
    }
}
