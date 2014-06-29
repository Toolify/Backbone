/*
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

package me.toolify.backbone.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

import me.toolify.backbone.R;
import me.toolify.backbone.util.FileHelper;

public class BreadcrumbSpinnerAdapter extends BaseAdapter implements SpinnerAdapter{

    private Context mContext;
    private ArrayList<File> fileList;
    public final static int HISTORY_ID = 1000001;

    public BreadcrumbSpinnerAdapter(Context context, ArrayList<File> fileList) {
        this.mContext = context;
        this.fileList = fileList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        // Tell the spinner that there will always be n file items + an extra for the history button
        return fileList.size() + 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getItem(int position) {
        if(position > fileList.size()) {
            // We're looking at the history button
            return null;
        } else {
            // Just part of the regular file list
            return fileList.get(position);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int position) {
        if (position == fileList.size()) {
            // This is our history item
            return HISTORY_ID;
        } else {
            return position;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RelativeLayout row = (RelativeLayout) View.inflate(mContext, R.layout.breadcrumb_spinner_selected_item, null);
        TextView title = (TextView)row.findViewById(R.id.breadcrumb_spinner_item_title);
        TextView subtitle = (TextView)row.findViewById(R.id.breadcrumb_spinner_item_subtitle);
        if(position == fileList.size()) {
            // History item
        } else {
            title.setText(buildTitleString(fileList.get(position)));
            subtitle.setText(buildSubtitleString(fileList.get(position)));
        }
        return row;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        LinearLayout row = (LinearLayout) View.inflate(mContext, R.layout.breadcrumb_spinner_dropdown_item, null);
        ImageView iconView = (ImageView)row.findViewById(R.id.breadcrumb_spinner_dropdown_icon);
        TextView labelView = (TextView)row.findViewById(R.id.breadcrumb_spinner_dropdown_label);
        TextView aliasView = (TextView)row.findViewById(R.id.breadcrumb_spinner_dropdown_alias);
        if(position == fileList.size()) {
            // This is the history item.  Text is already set so just let it be.
            iconView.setVisibility(View.VISIBLE);
        } else {
            labelView.setText(buildTitleString(fileList.get(position)));
            aliasView.setText(buildAliasString(fileList.get(position)));
            iconView.setVisibility(View.GONE);
        }
        return row;
    }

    private String buildTitleString(File file) {
        String fileString;
        if (file.compareTo(new File(FileHelper.ROOT_DIRECTORY)) == 0){
            fileString = "/";
        } else {
            fileString = file.getName();
        }
        return fileString;
    }

    private String buildSubtitleString(File file) {
        String fileString;
        if (file.compareTo(new File(FileHelper.ROOT_DIRECTORY)) == 0){
            fileString = "Filesystem Root";
        } else {
            fileString = file.getParent();
        }
        return fileString;
    }

    private String buildAliasString(File file) {
        String fileString = "";
        if (file.compareTo(new File(FileHelper.ROOT_DIRECTORY)) == 0){
            fileString = "Filesystem Root";
        }
        return fileString;
    }
}
