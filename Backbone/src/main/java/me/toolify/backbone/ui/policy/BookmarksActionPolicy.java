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

package me.toolify.backbone.ui.policy;

import android.content.Context;
import android.widget.Toast;

import me.toolify.backbone.R;
import me.toolify.backbone.model.Bookmark;
import me.toolify.backbone.model.Bookmark.BOOKMARK_TYPE;
import me.toolify.backbone.model.FileSystemObject;
import me.toolify.backbone.preferences.Bookmarks;
import me.toolify.backbone.util.DialogHelper;
import me.toolify.backbone.util.ExceptionUtil;

/**
 * A class with the convenience methods for resolve bookmarks related actions
 */
public final class BookmarksActionPolicy extends ActionsPolicy {

    /**
     * Method that adds the {@link FileSystemObject} to the bookmarks database.
     *
     * @param ctx The current context
     * @param fso The file system object
     */
    public static void addToBookmarks(final Context ctx, final FileSystemObject fso) {
        try {
            // Create the bookmark
            Bookmark bookmark =
                    new Bookmark(BOOKMARK_TYPE.USER_DEFINED, Bookmark.BOOKMARK_CATEGORY.USER_BOOKMARKS, fso.getName(), fso.getFullPath());
            bookmark = Bookmarks.addBookmark(ctx, bookmark);
            if (bookmark == null) {
                // The operation fails
                DialogHelper.showToast(
                        ctx,
                        R.string.msgs_operation_failure,
                        Toast.LENGTH_SHORT);
            } else {
                // Success
                DialogHelper.showToast(
                        ctx,
                        R.string.bookmarks_msgs_add_success,
                        Toast.LENGTH_SHORT);
            }

        } catch (Exception e) {
            ExceptionUtil.translateException(ctx, e);
        }
    }

}