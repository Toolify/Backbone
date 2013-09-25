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

package me.toolify.backbone.actionmode;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.ActionMode;
import android.view.InflateException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ShareActionProvider;

import java.util.List;

import me.toolify.backbone.FileManagerApplication;
import me.toolify.backbone.R;
import me.toolify.backbone.bus.BusProvider;
import me.toolify.backbone.bus.events.BookmarkRefreshEvent;
import me.toolify.backbone.bus.events.ClosePropertiesDrawerEvent;
import me.toolify.backbone.listeners.OnCopyMoveListener;
import me.toolify.backbone.listeners.OnRequestRefreshListener;
import me.toolify.backbone.listeners.OnSelectionListener;
import me.toolify.backbone.model.FileSystemObject;
import me.toolify.backbone.preferences.AccessMode;
import me.toolify.backbone.ui.dialogs.InputNameDialog;
import me.toolify.backbone.ui.policy.BookmarksActionPolicy;
import me.toolify.backbone.ui.policy.CompressActionPolicy;
import me.toolify.backbone.ui.policy.CopyMoveActionPolicy;
import me.toolify.backbone.ui.policy.DeleteActionPolicy;
import me.toolify.backbone.ui.policy.ExecutionActionPolicy;
import me.toolify.backbone.ui.policy.InfoActionPolicy;
import me.toolify.backbone.ui.policy.IntentsActionPolicy;
import me.toolify.backbone.ui.policy.NavigationActionPolicy;
import me.toolify.backbone.ui.policy.NewActionPolicy;
import me.toolify.backbone.util.FileHelper;
import me.toolify.backbone.util.MimeTypeHelper;
import me.toolify.backbone.util.StorageHelper;

public class PropertiesModeCallback implements ActionMode.Callback {

    private MenuItem mActionCreateLinkGlobal;
    private MenuItem mActionOpen;
    private MenuItem mActionOpenWith;
    private MenuItem mActionDelete;
    private MenuItem mActionRename;
    private MenuItem mActionCompress;
    private MenuItem mActionExtract;
    private MenuItem mActionCreateLink;
    private MenuItem mActionExecute;
    private MenuItem mActionSend;
    private MenuItem mActionAddBookmark;
    private MenuItem mActionAddShortcut;
    private MenuItem mActionChecksum;
    private ShareActionProvider mShareActionProvider;

    private boolean pasteReady = false;

    boolean mClosedByUser = true;
    private Activity mActivity;
    private ActionMode mPropertiesMode;
    private Boolean mGlobal;
    private final Boolean mChRooted;

    /**
     * @hide
     */
    OnRequestRefreshListener mOnRequestRefreshListener;
    /**
     * @hide
     */
    OnSelectionListener mOnSelectionListener;
    /**
     * @hide
     */
    OnCopyMoveListener onCopyMoveListener;

    private FileSystemObject mFso;

    /**
     * Constructor for <code>SelectionModeCallback</code>.
     *
     * @param activity The current Activity context
     * @param fso The FileSystemObject to present options for
     */
    public PropertiesModeCallback(Activity activity, FileSystemObject fso) {
        this.mActivity = activity;
        this.mFso = fso;
        this.mChRooted = FileManagerApplication.getAccessMode().compareTo(AccessMode.SAFE) == 0;
    }

    /**
     * Method that sets the listener for communicate a refresh request.
     *
     * @param onRequestRefreshListener The request refresh listener
     */
    public void setOnRequestRefreshListener(OnRequestRefreshListener onRequestRefreshListener) {
        this.mOnRequestRefreshListener = onRequestRefreshListener;
    }

    /**
     * Method that sets the listener for requesting selection data
     *
     * @param onSelectionListener The request selection data listener
     */
    public void setOnSelectionListener(OnSelectionListener onSelectionListener) {
        this.mOnSelectionListener = onSelectionListener;
    }

    /**
     * Method that sets the listener for marking file selections for paste
     *
     * @param onCopyMoveListener The request selection data listener
     */
    public void setOnCopyMoveListener(OnCopyMoveListener onCopyMoveListener) {
        this.onCopyMoveListener = onCopyMoveListener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mPropertiesMode = mode;

        MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.properties_actionmode, menu);

        mActionCreateLinkGlobal = menu.findItem(R.id.mnu_actions_create_link_global);
        mActionOpen = menu.findItem(R.id.mnu_actions_open);
        mActionOpenWith = menu.findItem(R.id.mnu_actions_open_with);
        mActionDelete = menu.findItem(R.id.mnu_actions_delete);
        mActionRename = menu.findItem(R.id.mnu_actions_rename);
        mActionCompress = menu.findItem(R.id.mnu_actions_compress);
        mActionExtract = menu.findItem(R.id.mnu_actions_extract);
        mActionCreateLink = menu.findItem(R.id.mnu_actions_create_link);
        mActionExecute = menu.findItem(R.id.mnu_actions_execute);
        mActionSend = menu.findItem(R.id.mnu_actions_send);
        mActionAddBookmark = menu.findItem(R.id.mnu_actions_add_to_bookmarks);
        mActionAddShortcut = menu.findItem(R.id.mnu_actions_add_shortcut);
        mActionChecksum = menu.findItem(R.id.mnu_actions_compute_checksum);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {

        //TODO: Figure out how to change/fix the mActionCreateLinkGlobal flag
        this.mGlobal = false;

        // Reset action item visibility
        mActionCreateLinkGlobal.setVisible(true);
        mActionOpen.setVisible(true);
        mActionOpenWith.setVisible(true);
        mActionDelete.setVisible(true);
        mActionRename.setVisible(true);
        mActionCompress.setVisible(true);
        mActionExtract.setVisible(true);
        mActionCreateLink.setVisible(true);
        mActionExecute.setVisible(true);
        mActionSend.setVisible(true);
        mActionAddBookmark.setVisible(true);
        mActionAddShortcut.setVisible(true);
        mActionChecksum.setVisible(true);

        /*
         * Single file mode
         *
         * Remove the following actions if we are dealing with a single file
         */

        // Check actions that needs a valid reference
        if (this.mFso != null) {

            // Hide Send/Open/Open With actions if the fso is folder or a system file
            if (FileHelper.isDirectory(this.mFso) || FileHelper.isSystemFile(this.mFso)) {
                mActionOpen.setVisible(false);
                mActionOpenWith.setVisible(false);
                mActionSend.setVisible(false);
            }

            // Create link (not allow in storage volume)
            if (StorageHelper.isPathInStorageVolume(this.mFso.getFullPath())) {
                mActionCreateLink.setVisible(false);
            }

            //Hide Execute action unless the mime/type category is EXEC
            MimeTypeHelper.MimeTypeCategory category = MimeTypeHelper.getCategory(this.mActivity, this.mFso);
            if (category.compareTo(MimeTypeHelper.MimeTypeCategory.EXEC) != 0) {
                mActionExecute.setVisible(false);
            }
        }

        // Hide "Add Bookmark" if the fso is the root directory (Already has a bookmark!)
        if (this.mFso != null && FileHelper.isRootDirectory(this.mFso)) {
            mActionAddBookmark.setVisible(false);
        }

        /*
         * Multi-file mode
         *
         * Remove the following actions if we are dealing with a multi-fso selection
         */

        if (!this.mGlobal) {
            // Create link (not allow in storage volume)
            if (StorageHelper.isPathInStorageVolume(this.mFso.getFullPath())) {
                mActionCreateLink.setVisible(false);
            }
        }

        // Hide extract (uncompress/unzip) action for non-supported files
        if (!FileHelper.isSupportedUncompressedFile(this.mFso)) {
            mActionExtract.setVisible(false);
        }

        // Hide actions that can't be present when running in unprivileged mode)
        if (this.mChRooted) {
            mActionCreateLink.setVisible(false);
            mActionCreateLinkGlobal.setVisible(false);
            mActionExecute.setVisible(false);

            // NOTE: This actions are not implemented in chrooted environments. The reason is
            // that the main target of this application is CyanogenMod (a rooted environment).
            // Adding this actions requires the use of commons-compress, an external Apache
            // library that will add more size to the ending apk.
            // For now, will maintain without implementation. Maybe, in the future.
            mActionCompress.setVisible(false);
            mActionExtract.setVisible(false);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem menuItem) {

        switch (menuItem.getItemId()) {

            //- Rename
            case R.id.mnu_actions_rename:
                showFsoInputNameDialog(menuItem, this.mFso, false);
                finish();
                return true;

            //- Create link
            case R.id.mnu_actions_create_link:
                showFsoInputNameDialog(menuItem, this.mFso, true);
                finish();
                return true;

            case R.id.mnu_actions_create_link_global:
                showFsoInputNameDialog(menuItem, this.mFso, true);
                finish();
                return true;

            //- Delete
            case R.id.mnu_actions_delete:
                DeleteActionPolicy.removeFileSystemObject(
                        this.mActivity,
                        this.mFso,
                        this.mOnSelectionListener,
                        this.mOnRequestRefreshListener,
                        null);
                finish();
                break;

            //- Open
            case R.id.mnu_actions_open:
                IntentsActionPolicy.openFileSystemObject(
                        this.mActivity, this.mFso, false, null, null);
                finish();
                break;
            //- Open with
            case R.id.mnu_actions_open_with:
                IntentsActionPolicy.openFileSystemObject(
                        this.mActivity, this.mFso, true, null, null);
                finish();
                break;

            //- Execute
            case R.id.mnu_actions_execute:
                ExecutionActionPolicy.execute(this.mActivity, this.mFso);
                finish();
                break;

            //- Send
            case R.id.mnu_actions_send:
                IntentsActionPolicy.sendFileSystemObject(
                        this.mActivity, this.mFso, null, null);
                finish();
                break;

            //- Create copy
            case R.id.mnu_actions_copy:
                // Create a copy of the fso
                if (this.mOnSelectionListener != null) {
                    List<FileSystemObject> selection =
                            this.mOnSelectionListener.onRequestSelectedFiles();
                    CopyMoveActionPolicy.createCopyFileSystemObject(
                            selection,
                            this.onCopyMoveListener);
                    finish();
                }
                break;

            // Move selection
            case R.id.mnu_actions_move:
                if (this.mOnSelectionListener != null) {
                    List<FileSystemObject> selection =
                            this.mOnSelectionListener.onRequestSelectedFiles();
                    CopyMoveActionPolicy.createMoveFileSystemObject(
                            selection,
                            this.onCopyMoveListener);
                    finish();
                }
                break;

            //- Uncompress
            case R.id.mnu_actions_extract:
                CompressActionPolicy.uncompress(
                        this.mActivity,
                        this.mFso,
                        this.mOnRequestRefreshListener);
                break;
            //- Checksum
            case R.id.mnu_actions_compute_checksum:
                InfoActionPolicy.showComputeChecksumDialog(
                        this.mActivity,
                        this.mFso);
                break;
            //- Compress
            case R.id.mnu_actions_compress:
                if (this.mOnSelectionListener != null) {
                    CompressActionPolicy.compress(
                            this.mActivity,
                            this.mFso,
                            this.mOnSelectionListener,
                            this.mOnRequestRefreshListener);
                    finish();
                }
                break;

            //- Add to bookmarks
            case R.id.mnu_actions_add_to_bookmarks:
                BookmarksActionPolicy.addToBookmarks(this.mActivity, this.mFso);
                BusProvider.postEvent(new BookmarkRefreshEvent());
                break;

            //- Add shortcut
            case R.id.mnu_actions_add_shortcut:
                IntentsActionPolicy.createShortcut(this.mActivity, this.mFso);
                break;

            //- Navigate to parent
            case R.id.mnu_actions_open_parent_folder:
                NavigationActionPolicy.openParentFolder(
                        this.mActivity, this.mFso, this.mOnRequestRefreshListener);
                break;

            default:
                break;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mPropertiesMode = null;
        BusProvider.postEvent(new ClosePropertiesDrawerEvent());
    }

    public void finish() {
        mPropertiesMode.finish();
    }

    public void setClosedByUser(boolean closedByUser) {
        this.mClosedByUser = closedByUser;
    }

    public boolean inPropertiesActionMode() {
        return mPropertiesMode != null;
    }

    public void refresh() {
        mPropertiesMode.invalidate();
    }

    /**
     * Method that show a new dialog for input a name for an existing fso.
     *
     * @param menuItem The item menu associated
     * @param fso The file system object
     * @param allowFsoName If allow that the name of the fso will be returned
     */
    private void showFsoInputNameDialog(
            final MenuItem menuItem, final FileSystemObject fso, final boolean allowFsoName) {

        //Show the input name dialog
        final InputNameDialog inputNameDialog =
                new InputNameDialog(
                        this.mActivity,
                        this.mOnSelectionListener.onRequestCurrentItems(),
                        fso,
                        allowFsoName,
                        menuItem.getTitle().toString());
        inputNameDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                //Retrieve the name an execute the action
                try {
                    String name = inputNameDialog.getName();
                    switch (menuItem.getItemId()) {
                        case R.id.mnu_actions_rename:
                            // Rename the fso
                            if (PropertiesModeCallback.this.mOnSelectionListener != null) {
                                CopyMoveActionPolicy.renameFileSystemObject(
                                        PropertiesModeCallback.this.mActivity,
                                        inputNameDialog.mFso,
                                        name,
                                        PropertiesModeCallback.this.mOnSelectionListener,
                                        PropertiesModeCallback.this.mOnRequestRefreshListener);
                            }
                            break;

                        case R.id.mnu_actions_create_link:
                        case R.id.mnu_actions_create_link_global:
                            // Create a link to the fso
                            if (PropertiesModeCallback.this.mOnSelectionListener != null) {
                                NewActionPolicy.createSymlink(
                                        PropertiesModeCallback.this.mActivity,
                                        inputNameDialog.mFso,
                                        name,
                                        PropertiesModeCallback.this.mOnSelectionListener,
                                        PropertiesModeCallback.this.mOnRequestRefreshListener);
                            }
                            break;

                        default:
                            break;
                    }

                } catch (InflateException e) {
                    //TODO: Catch this exception properly
                }
            }
        });
        inputNameDialog.show();
    }
}
