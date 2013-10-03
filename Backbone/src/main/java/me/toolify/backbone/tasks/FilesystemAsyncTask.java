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

package me.toolify.backbone.tasks;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import me.toolify.backbone.bus.BusProvider;
import me.toolify.backbone.bus.events.FilesystemStatusUpdateEvent;
import me.toolify.backbone.model.DiskUsage;
import me.toolify.backbone.model.MountPoint;
import me.toolify.backbone.ui.ThemeManager;
import me.toolify.backbone.ui.ThemeManager.Theme;
import me.toolify.backbone.ui.widgets.BreadcrumbView;
import me.toolify.backbone.util.MountPointHelper;

/**
 * A class for recovery information about filesystem status (mount point, disk usage, ...).
 */
public class FilesystemAsyncTask extends AsyncTask<String, Integer, Boolean> {

    private static final String TAG = "FilesystemAsyncTask"; //$NON-NLS-1$

    /**
     * @hide
     */
    final Context mContext;
    /**
     * @hide
     */
    final BreadcrumbView mBreadcrumbView;
    /**
     * @hide
     */
    final int mFreeDiskSpaceWarningLevel;
    private boolean mRunning;

    /**
     * @hide
     */
    static int sColorFilterNormal;

    /**
     * Constructor of <code>FilesystemAsyncTask</code>.
     *
     * @param context The current context
     * @param breadcrumbView The breadcrumbView calling this task
     * @param freeDiskSpaceWarningLevel The free disk space warning level
     */
    public FilesystemAsyncTask(
            Context context, BreadcrumbView breadcrumbView, int freeDiskSpaceWarningLevel) {
        super();
        this.mContext = context;
        this.mBreadcrumbView = breadcrumbView;
        this.mFreeDiskSpaceWarningLevel = freeDiskSpaceWarningLevel;
        this.mRunning = false;
    }

    /**
     * Method that returns if there is a task running.
     *
     * @return boolean If there is a task running
     */
    public boolean isRunning() {
        return this.mRunning;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Boolean doInBackground(String... params) {
        //Running
        this.mRunning = true;

        //Extract the directory from arguments
        String dir = params[0];

        //Extract filesystem mount point from directory
        if (isCancelled()) {
            return Boolean.TRUE;
        }
        MountPoint mp1 = MountPointHelper.getMountPointFromDirectory(dir);
        if(mp1.getMountPoint().equals("/") && dir.equals("/storage/emulated/0")) // AOSP 4.3 bug
            mp1 = MountPointHelper.getMountPointFromDirectory("/mnt/shell/emulated");
        final MountPoint mp = mp1;
        if (mp == null) {
            //There is no information about
            if (isCancelled()) {
                return Boolean.TRUE;
            }
            this.mBreadcrumbView.post(new Runnable() {
                @Override
                public void run() {
                    BusProvider.postEvent(new FilesystemStatusUpdateEvent(
                            FilesystemStatusUpdateEvent.INDICATOR_WARNING));
                    FilesystemAsyncTask.this.mBreadcrumbView.setMountPointInfo(null);
                }
            });
        } else {
            //Set image icon an save the mount point info
            if (isCancelled()) {
                return Boolean.TRUE;
            }
            this.mBreadcrumbView.post(new Runnable() {
                @Override
                public void run() {
                   int eventType =
                            MountPointHelper.isReadOnly(mp)
                            ? FilesystemStatusUpdateEvent.INDICATOR_LOCKED
                            : FilesystemStatusUpdateEvent.INDICATOR_UNLOCKED;
                    BusProvider.postEvent(new FilesystemStatusUpdateEvent(eventType));
                    FilesystemAsyncTask.this.mBreadcrumbView.setMountPointInfo(mp);
                }
            });

            //Load information about disk usage
            if (isCancelled()) {
                return Boolean.TRUE;
            }
            this.mBreadcrumbView.post(new Runnable() {
                @Override
                public void run() {
                    DiskUsage du = null;
                    try {
                        du = MountPointHelper.getMountPointDiskUsage(mp);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to retrieve disk usage information", e); //$NON-NLS-1$
                        du = new DiskUsage(
                                mp.getMountPoint(), 0, 0, 0);
                    }
                    int usage = 0;
                    if (du != null && du.getTotal() != 0) {
                        usage = (int)(du.getUsed() * 100 / du.getTotal());
                        //FilesystemAsyncTask.this.fileSystemInfo.setProgress(usage);  ** CM progress bar removed
                        FilesystemAsyncTask.this.mBreadcrumbView.setDiskUsageInfo(du);
                    } else {
                        usage = du == null ? 0 : 100;
                        //FilesystemAsyncTask.this.fileSystemInfo.setProgress(usage); ** CM progress bar removed
                        FilesystemAsyncTask.this.mBreadcrumbView.setDiskUsageInfo(null);
                    }

                    //TODO point this at another view in the action bar, now that diskusage is gone
                    // Advise about diskusage (>=mFreeDiskSpaceWarningLevel) with other color
                    Theme theme = ThemeManager.getCurrentTheme(FilesystemAsyncTask.this.mContext);
                    int filter =
                            usage >= FilesystemAsyncTask.this.mFreeDiskSpaceWarningLevel ?
                                    theme.getColor(
                                            FilesystemAsyncTask.this.mContext,
                                            "disk_usage_filter_warning_color") : //$NON-NLS-1$
                                    theme.getColor(
                                            FilesystemAsyncTask.this.mContext,
                                            "disk_usage_filter_normal_color"); //$NON-NLS-1$
/*                    FilesystemAsyncTask.this.fileSystemInfo.
                            getProgressDrawable().setColorFilter(
                            new PorterDuffColorFilter(filter, Mode.MULTIPLY));  ** CM progress bar removed */
                }
            });
        }
        return Boolean.TRUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPostExecute(Boolean result) {
        this.mRunning = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCancelled(Boolean result) {
        this.mRunning = false;
        super.onCancelled(result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCancelled() {
        this.mRunning = false;
        super.onCancelled();
    }

}
