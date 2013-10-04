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

package me.toolify.backbone.commands.java;

import android.util.Log;

import me.toolify.backbone.BuildConfig;
import me.toolify.backbone.commands.DiskUsageExecutable;
import me.toolify.backbone.console.ExecutionException;
import me.toolify.backbone.console.InsufficientPermissionsException;
import me.toolify.backbone.console.NoSuchFileOrDirectory;
import me.toolify.backbone.model.DiskUsage;
import me.toolify.backbone.model.MountPoint;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;


/**
 * A class for get information about disk usage.
 */
public class DiskUsageCommand extends Program implements DiskUsageExecutable {

    private static final String TAG = "DiskUsageCommand"; //$NON-NLS-1$

    private final String mMountsFile;
    private final String mSrc;
    private final Hashtable<String, DiskUsage> mDisksUsage = new Hashtable<String, DiskUsage>();

    /**
     * Constructor of <code>DiskUsageCommand</code>.
     *
     * @param mountsFile The system mounts file
     */
    public DiskUsageCommand(String mountsFile) {
        this(mountsFile, null);
    }

    /**
     * Constructor of <code>DiskUsageCommand</code>.
     *
     * @param mountsFile The system mounts file
     * @param dir The directory of which obtain its disk usage
     */
    public DiskUsageCommand(String mountsFile, String dir) {
        super();
        this.mMountsFile = mountsFile;
        this.mSrc = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DiskUsage> getResult() {
        List<DiskUsage> ret = new ArrayList<DiskUsage>();
        for(DiskUsage du : mDisksUsage.values())
            ret.add(du);
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute()
            throws InsufficientPermissionsException, NoSuchFileOrDirectory, ExecutionException {

        if (isTrace()) {
            Log.v(TAG,
                    String.format("Getting usage for: %s", //$NON-NLS-1$
                            this.mSrc == null ? "all" : this.mSrc)); //$NON-NLS-1$
        }

        if (this.mSrc == null) {
            // Retrieve the mount points
            MountPointInfoCommand cmd = new MountPointInfoCommand(this.mMountsFile);
            cmd.setBufferSize(getBufferSize());
            cmd.setTrace(isTrace());
            cmd.execute();
            List<MountPoint> mp = cmd.getResult();

            // Get every disk usage
            for (int i = 0; i < mp.size(); i++) {
                String mpp = mp.get(i).getMountPoint();
                File root = new File(mpp);
                mDisksUsage.put(mpp, createDiskUsage(root));
            }
        } else {
            mDisksUsage.put(mSrc, createDiskUsage(new File(this.mSrc)));
        }

        if (isTrace()) {
            Log.v(TAG, "Result: OK"); //$NON-NLS-1$
        }
    }

    /**
     * Method that create a reference of the disk usage from the root file
     *
     * @param root The root file
     * @return DiskUsage The disk usage
     */
    private DiskUsage createDiskUsage(File root) {
        long total = root.getTotalSpace();
        long free = root.getFreeSpace();
        DiskUsage du = new DiskUsage(
                root.getAbsolutePath(),
                total,
                total - free,
                free);
        if (isTrace()) {
            Log.v(TAG, du.toString());
        }
        return du;
    }

}
